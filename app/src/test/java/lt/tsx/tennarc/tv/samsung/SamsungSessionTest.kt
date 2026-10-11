package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.tv.samsung.FakeSamsungTv.Companion.base64
import lt.tsx.tennarc.tv.samsung.FakeSamsungTv.Companion.ime
import lt.tsx.tennarc.tv.samsung.FakeSamsungTv.Companion.sameJson
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SamsungSessionTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val url get() = remoteUrl(secure = false, server.hostName, server.port, token = null)
    private val quick = SamsungSession.Timing(promptAfterMs = 300, answerMs = 800, pairingMs = 3_000, appsMs = 300)

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun serve(tv: FakeSamsungTv) = tv.also { server.enqueue(MockResponse.Builder().webSocketUpgrade(it).build()) }

    private fun open(allowPrompt: Boolean = false, onPrompt: () -> Unit = {}) = runBlocking {
        SamsungSession.open(http, url, allowPrompt, onPrompt, quick)
    }

    @Test
    fun `the connect event's token is kept, number or not`() {
        serve(FakeSamsungTv())
        assertEquals("123456789", open().apply { close() }.token)
    }

    @Test
    fun `a set without token auth connects without one`() {
        serve(FakeSamsungTv(onOpen = { it.send(FakeSamsungTv.CONNECT_WITHOUT_TOKEN) }))
        val session = open()
        assertNull(session.token)
        assertTrue(session.isOpen)
        session.close()
    }

    @Test
    fun `Deny on the TV is reported as declined`() {
        serve(FakeSamsungTv(onOpen = { it.send(FakeSamsungTv.UNAUTHORIZED) }))
        try {
            open(allowPrompt = true)
            fail("expected the TV to refuse")
        } catch (_: SamsungDeclinedException) {
        }
    }

    @Test
    fun `silence means the TV is asking, and Allow brings the token`() {
        serve(FakeSamsungTv(onOpen = { ws -> thread { Thread.sleep(700); ws.send(FakeSamsungTv.connect("fresh")) } }))
        var prompted = false
        val session = open(allowPrompt = true) { prompted = true }
        assertTrue(prompted)
        assertEquals("fresh", session.token)
        session.close()
    }

    @Test
    fun `a stored token the TV sits on fails instead of leaving its prompt up`() {
        val tv = serve(FakeSamsungTv(onOpen = {}))
        var prompted = false
        try {
            open(allowPrompt = false) { prompted = true }
            fail("expected the silence to count as a forgotten token")
        } catch (_: SamsungForgottenException) {
            assertFalse(prompted)
        }
        assertTrue("the socket should be closed", tv.closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a prompt nobody answers times out, by our clock or the TV's`() {
        serve(FakeSamsungTv(onOpen = {}))
        serve(FakeSamsungTv(onOpen = { ws -> thread { Thread.sleep(500); ws.send("""{"event":"ms.channel.timeOut"}""") } }))
        repeat(2) {
            try {
                open(allowPrompt = true)
                fail("expected the prompt to time out")
            } catch (_: SamsungPromptTimeoutException) {
            }
        }
    }

    @Test
    fun `keys go out as ms remote control clicks`() {
        val tv = serve(FakeSamsungTv())
        val session = open()
        session.key("KEY_HOME")
        session.key("KEY_POWER", "Press")
        assertJson("""{"Cmd":"Click","DataOfCmd":"KEY_HOME","Option":"false","TypeOfRemote":"SendRemoteKey"}""", tv.nextRemote())
        assertJson("""{"Cmd":"Press","DataOfCmd":"KEY_POWER","Option":"false","TypeOfRemote":"SendRemoteKey"}""", tv.nextRemote())
        session.close()
    }

    @Test
    fun `typing sends the whole field, announced once, and enter ends it`() = runBlocking {
        val tv = serve(FakeSamsungTv())
        val session = open()
        tv.send(ime("imeStart"))
        withTimeout(5_000) { session.keyboard.first { it != null } }

        session.type(TextEdit(delete = 0, insert = "héllo"))
        session.type(TextEdit(delete = 2, insert = "p!"))
        session.endText()

        val announce = tv.nextEmit("custom.remote.textReceived").getJSONObject("params")
        assertEquals("broadcast", announce.getString("to"))
        assertJson("""{"Cmd":"${base64("héllo")}","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}""", tv.nextRemote())
        assertJson("""{"Cmd":"${base64("hélp!")}","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}""", tv.nextRemote())
        assertJson("""{"TypeOfRemote":"SendInputEnd"}""", tv.nextRemote())
        assertEquals(0, tv.drain().count { it.toString().contains("textReceived") })
        assertEquals("hélp!", session.keyboard.value)

        tv.send(ime("imeEnd"))
        withTimeout(5_000) { session.keyboard.first { it == null } }
        session.close()
    }

    @Test
    fun `the TV's own typing replaces our copy, its echoes of ours don't`() = runBlocking {
        val tv = serve(FakeSamsungTv())
        val session = open()
        tv.send(ime("imeStart", "ab"))
        withTimeout(5_000) { session.keyboard.first { it == "ab" } }

        session.type(TextEdit(delete = 0, insert = "c"))
        session.type(TextEdit(delete = 0, insert = "d"))
        tv.send(ime("imeUpdate", "abc"))
        Thread.sleep(300)
        session.type(TextEdit(delete = 0, insert = "e"))
        assertEquals(base64("abcde"), tv.next { it.toString().contains(base64("abcde")) }.getJSONObject("params").getString("Cmd"))

        tv.send(ime("imeUpdate", "typed on the TV"))
        withTimeout(5_000) { session.keyboard.first { it == "typed on the TV" } }
        session.type(TextEdit(delete = 2, insert = "remote"))
        assertEquals(base64("typed on the remote"), tv.next { it.toString().contains(base64("typed on the remote")) }.getJSONObject("params").getString("Cmd"))
        session.close()
    }

    @Test
    fun `installed apps are asked for and read`() = runBlocking {
        val tv = serve(FakeSamsungTv(onEmit = { ws, params ->
            if (params.getString("event") == "ed.installedApp.get") ws.send(FakeSamsungTv.INSTALLED_APPS)
        }))
        val session = open()
        val apps = session.installedApps()
        assertJson("""{"method":"ms.channel.emit","params":{"event":"ed.installedApp.get","to":"host"}}""", tv.nextEmit("ed.installedApp.get"))
        assertEquals(
            listOf(
                SamsungApp("111299001912", "YouTube", 2),
                SamsungApp("3201608010191", "Deezer", 2),
                SamsungApp("3201606009684", "Spotify - Music and Podcasts", 2),
            ),
            apps,
        )
        session.close()
    }

    @Test
    fun `newer firmware that ignores the app list request gives null`() = runBlocking {
        serve(FakeSamsungTv())
        val session = open()
        assertNull(session.installedApps())
        session.close()
    }

    @Test
    fun `launches name the action type and meta tag`() {
        val tv = serve(FakeSamsungTv())
        val session = open()
        session.launch("111299001912", "DEEP_LINK", "v=dQw4w9WgXcQ")
        session.launch("org.tizen.browser", "NATIVE_LAUNCH")
        assertJson(
            """{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host",
                "data":{"action_type":"DEEP_LINK","appId":"111299001912","metaTag":"v=dQw4w9WgXcQ"}}}""",
            tv.nextEmit("ed.apps.launch"),
        )
        assertJson(
            """{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host",
                "data":{"action_type":"NATIVE_LAUNCH","appId":"org.tizen.browser","metaTag":""}}}""",
            tv.nextEmit("ed.apps.launch"),
        )
        session.close()
    }

    @Test
    fun `a dropped connection ends the session`() = runBlocking {
        serve(FakeSamsungTv(onOpen = { ws -> ws.send(FakeSamsungTv.CONNECT); thread { Thread.sleep(200); ws.close(1001, "standby") } }))
        val session = open()
        withTimeout(5_000) { session.awaitClosed() }
        assertFalse(session.isOpen)
        try {
            session.key("KEY_HOME")
            fail("expected the key to fail")
        } catch (_: SamsungException) {
        }
    }

    @Test
    fun `the secure port takes the TV's self-signed certificate and carries the token`() = runBlocking {
        server.useHttps(FakeSamsungTv.tls())
        serve(FakeSamsungTv())
        val secure = remoteUrl(secure = true, server.hostName, server.port, token = "123456789")
        val session = SamsungSession.open(TvHttp.tvClient(), secure, allowPrompt = false, timing = quick)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v2/channels/samsung.remote.control", request.url.encodedPath)
        assertEquals(base64("Tenna RC"), request.url.queryParameter("name"))
        assertEquals("123456789", request.url.queryParameter("token"))
        session.close()
    }

    @Test
    fun `the token never goes over the plain port`() {
        val plain = remoteUrl(secure = false, "tv.test", 8001, token = "123456789")
        assertEquals("ws://tv.test:8001/api/v2/channels/samsung.remote.control?name=VGVubmEgUkM%3D", plain)
        assertTrue(remoteUrl(secure = true, "tv.test", 8002, token = "123456789").endsWith("&token=123456789"))
        assertFalse(remoteUrl(secure = true, "tv.test", 8002, token = "").contains("token"))
    }

    private fun assertJson(expected: String, actual: JSONObject) =
        assertTrue("expected $expected but was $actual", sameJson(JSONObject(expected), actual))
}
