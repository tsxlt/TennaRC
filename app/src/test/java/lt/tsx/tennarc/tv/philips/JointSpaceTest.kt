package lt.tsx.tennarc.tv.philips

import kotlinx.coroutines.runBlocking
import lt.tsx.tennarc.tv.AmbilightStyle
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JointSpaceTest {
    private val tv = FakePhilipsTv()
    private val http = OkHttpClient()
    private val device = JointSpaceDevice("Ab3dE5gH1jK2mN4p", "Pixel 9")

    @After fun stop() = tv.close()

    @Test
    fun `the grant is signed with HMAC-SHA1 of the timestamp and PIN under the shared secret`() {
        assertEquals("OSL7Ysxn0nv5V8I8R0tB5C0aI/k=", pairingSignature(1696183223L, "4271"))
    }

    @Test
    fun `digest answers match RFC 2617's own example`() {
        val challenge = DigestChallenge.parse(
            """Digest realm="testrealm@host.com", qop="auth,auth-int", nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", opaque="5ccc069c403ebaf9f0171e9517f40e41"""",
        )!!
        assertEquals("testrealm@host.com", challenge.realm)
        assertEquals("auth,auth-int", challenge.qop)
        val header = digestHeader("Mufasa", "Circle Of Life", challenge, "GET", "/dir/index.html", 1, "0a4f113b")
        assertTrue(header, "response=\"6629fae49393a05397450978507c4ef1\"" in header)
        assertTrue(header, "qop=auth, nc=00000001, cnonce=\"0a4f113b\"" in header)
        assertTrue(header, "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"" in header)
    }

    @Test
    fun `basic challenges and junk aren't digest ones`() {
        assertNull(DigestChallenge.parse("""Basic realm="XTV""""))
        assertNull(DigestChallenge.parse("""Digest qop="auth""""))
    }

    @Test
    fun `an Android set's system says API 6, Ambilight, pairing and HTTPS`() {
        val system = JointSpaceSystem.parse(FakePhilipsTv.SYSTEM_ANDROID)!!
        assertEquals("65OLED855/12", system.name)
        assertEquals(6, system.apiVersion)
        assertEquals("MSAF_2019_P", system.osType)
        assertTrue(system.ambilight)
        assertTrue(system.needsPairing)
        assertTrue(system.secure)
    }

    @Test
    fun `a Saphi set keeps its OS among the system features, and a set without Ambilight says so`() {
        val saphi = JointSpaceSystem.parse(
            """{"name":"43PUS6501","api_version":{"Major":6,"Minor":1,"Patch":0},"featuring":{"jsonfeatures":
            {"ambilight":["LoungeLight","Hue"]},"systemfeatures":{"os_type":"Linux","pairing_type":"digest_auth_pairing"}}}""",
        )!!
        assertEquals("Linux", saphi.osType)
        assertFalse(saphi.ambilight)
        assertFalse(saphi.secure)
    }

    @Test
    fun `only a JointSPACE answer counts as a Philips`() {
        assertNull(JointSpaceSystem.parse("""{"device":{"name":"[TV] Samsung"}}"""))
        assertNull(JointSpaceSystem.parse("<html>"))
        val old = JointSpaceSystem.parse("""{"name":"42PFL7606","api_version":{"Major":1,"Minor":0,"Patch":4}}""")!!
        assertEquals(1, old.apiVersion)
        assertFalse(old.ambilight)
        assertFalse(old.needsPairing)
    }

    @Test
    fun `styles map both ways, lounge under either name`() {
        assertEquals(AmbilightStyle.FOLLOW_VIDEO, ambilightStyle(parseAmbilightConfig("""{"styleName":"FOLLOW_VIDEO","isExpert":false,"menuSetting":"STANDARD"}""")))
        assertEquals(AmbilightStyle.FOLLOW_AUDIO, ambilightStyle(parseAmbilightConfig("""{"styleName":"FOLLOW_AUDIO","isExpert":false,"menuSetting":"VU_METER"}""")))
        assertEquals(AmbilightStyle.LOUNGE, ambilightStyle(AmbilightConfig("Lounge light", "HOT_LAVA")))
        assertEquals(AmbilightStyle.LOUNGE, ambilightStyle(AmbilightConfig("FOLLOW_COLOR", null, expert = true)))
        assertEquals(AmbilightStyle.OFF, ambilightStyle(AmbilightConfig("OFF", null)))
        assertNull(ambilightStyle(AmbilightConfig("MANUAL", null)))
        assertEquals(AmbilightConfig("FOLLOW_AUDIO", "VU_METER"), parseAmbilightConfig("""{"styleName":"FOLLOW_AUDIO","isExpert":false,"menuSetting":"VU_METER"}"""))
        assertNull(parseAmbilightConfig("{}"))
        assertEquals(listOf("Lounge light", "FOLLOW_COLOR"), ambilightConfigs(AmbilightStyle.LOUNGE).map { it.styleName })
        assertEquals(true, parseAmbilightPower("""{"power":"On"}"""))
        assertEquals(false, parseAmbilightPower("""{"power":"Off"}"""))
        assertNull(parseAmbilightPower("""{"power":"Standby"}"""))
    }

    @Test
    fun `a login survives being stored`() {
        val login = JointSpaceLogin(device.id, FakePhilipsTv.AUTH_KEY)
        assertEquals(login, JointSpaceLogin.decode(login.encode()))
        assertNull(JointSpaceLogin.decode(null))
        assertNull(JointSpaceLogin.decode("just-one-part"))
    }

    @Test
    fun `the probe falls back to plain HTTP when nothing answers HTTPS`() = runBlocking {
        val found = JointSpace.probe(http, tv.host, tv.ports)!!
        assertEquals("http://${tv.host}:${tv.port}/6/", found.base.toString())
        assertTrue(found.system!!.ambilight)
        assertNull(JointSpace.probe(http, tv.host, JointSpacePorts(FakePhilipsTv.closedPort(), FakePhilipsTv.closedPort())))
    }

    @Test
    fun `plain HTTP answering for a set that wants HTTPS points the API at the HTTPS port`() = runBlocking {
        FakePhilipsTv(system = FakePhilipsTv.SYSTEM_ANDROID).use { secureTv ->
            val secure = FakePhilipsTv.closedPort()
            val found = JointSpace.probe(http, secureTv.host, JointSpacePorts(secure = secure, plain = secureTv.port))!!
            assertEquals("https://${secureTv.host}:$secure/6/", found.base.toString())
        }
    }

    @Test
    fun `pairs with the PIN on screen, then reads and sets the light under the login`() = runBlocking {
        val api = JointSpace.probe(http, tv.host, tv.ports)!!
        val offer = api.pairRequest(device)
        assertEquals(PairingOffer(FakePhilipsTv.AUTH_KEY, FakePhilipsTv.TIMESTAMP, 60), offer)
        val (path, request) = tv.nextPost()
        assertEquals("/6/pair/request", path)
        assertEquals(device.id, request.getJSONObject("device").getString("id"))
        assertTrue(request.getJSONObject("access").has("featuring"))

        assertNull("a wrong PIN", api.pairGrant(device, offer, "0000"))
        tv.posts.clear()
        val login = api.pairGrant(device, offer, FakePhilipsTv.PIN)
        assertEquals(JointSpaceLogin(device.id, FakePhilipsTv.AUTH_KEY), login)
        val grant = tv.nextPost().second.getJSONObject("auth")
        assertEquals(FakePhilipsTv.TIMESTAMP, grant.getLong("auth_timestamp"))
        assertEquals("1", grant.getString("auth_appId"))

        api.login = login
        tv.config = JSONObject("""{"styleName":"Lounge light","isExpert":false,"menuSetting":"DEEP_WATER"}""")
        assertEquals(AmbilightReading(true, AmbilightConfig("Lounge light", "DEEP_WATER")), api.ambilight())
        val asked = tv.challenges.get()
        assertTrue(api.setAmbilightPower(false))
        assertTrue(api.setAmbilightConfig(AmbilightConfig("FOLLOW_AUDIO", "VU_METER")))
        assertEquals("Off", tv.power)
        assertEquals("FOLLOW_AUDIO", tv.config.getString("styleName"))
        assertFalse(tv.config.getBoolean("isExpert"))
        assertEquals("the nonce is reused, not asked for again", asked, tv.challenges.get())
    }

    @Test
    fun `a login the TV doesn't know is refused, not retried forever`() = runBlocking {
        val api = JointSpace.probe(http, tv.host, tv.ports, JointSpaceLogin("Forgotten0000000", "nope"))!!
        try {
            api.ambilight()
            fail("read the light without a login the TV knows")
        } catch (_: JointSpaceUnauthorized) {
        }
        assertEquals(2, tv.challenges.get())
    }
}
