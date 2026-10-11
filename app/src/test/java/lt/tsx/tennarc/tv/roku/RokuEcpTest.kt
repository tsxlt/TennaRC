package lt.tsx.tennarc.tv.roku

import kotlinx.coroutines.runBlocking
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvPower
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RokuEcpTest {
    private val server = MockWebServer()
    private lateinit var ecp: RokuEcp

    @Before fun start() {
        server.start()
        ecp = RokuEcp(OkHttpClient(), server.hostName, server.port)
    }

    @After fun stop() = server.close()

    private fun answer(body: String = "", code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun sent() = server.takeRequest().let { "${it.method} ${it.target}" }

    @Test
    fun `reads a Roku TV's name, power and abilities from device-info`() = runBlocking {
        answer(ROKU_TV)
        val info = ecp.deviceInfo()
        assertEquals("GET /query/device-info", sent())
        assertEquals("58\" Onn Roku TV", info.name)
        assertEquals(TvPower.ON, info.power)
        assertTrue(info.isTv)
        assertTrue(info.hasTuner)
        assertTrue(info.controlsPower)
        assertEquals(listOf("02:00:00:00:00:0a", "02:00:00:00:00:0b"), info.macs)
        assertNull(info.ecpMode)
        assertFalse(info.restricted)
        assertEquals(ROKU_FEATURES + Feature.LIVE_TV, info.features)
    }

    @Test
    fun `a stick has no tuner and no screen to turn on`() {
        val info = parseDeviceInfo(STICK)
        assertEquals("Roku Stick - 2L647N055555", info.name)
        assertFalse(info.hasTuner)
        assertFalse(info.controlsPower)
        assertEquals(listOf("02:00:00:00:00:0c"), info.macs)
        assertEquals(ROKU_FEATURES - Feature.WAKE, info.features)
    }

    @Test
    fun `a player allowed to switch the TV over HDMI-CEC can wake it`() {
        val info = parseDeviceInfo(STICK.replace("</device-info>", "<supports-tv-power-control>true</supports-tv-power-control></device-info>"))
        assertTrue(info.controlsPower)
        assertTrue(Feature.WAKE in info.features)
    }

    @Test
    fun `wired, the ethernet MAC comes first`() {
        val info = parseDeviceInfo(ROKU_TV.replace("<network-type>wifi</network-type>", "<network-type>ethernet</network-type>"))
        assertEquals(listOf("02:00:00:00:00:0b", "02:00:00:00:00:0a"), info.macs)
    }

    @Test
    fun `anything but PowerOn is asleep`() {
        assertEquals(TvPower.ON, rokuPower("PowerOn"))
        assertEquals(TvPower.STANDBY, rokuPower("DisplayOff"))
        assertEquals(TvPower.STANDBY, rokuPower("Ready"))
        assertEquals(TvPower.STANDBY, rokuPower("PowerOff"))
        assertNull(rokuPower(""))
    }

    @Test
    fun `limited mobile control is restricted`() {
        val info = parseDeviceInfo(STICK.replace("</device-info>", "<ecp-setting-mode>limited</ecp-setting-mode></device-info>"))
        assertEquals("limited", info.ecpMode)
        assertTrue(info.restricted)
        assertFalse(parseDeviceInfo(STICK.replace("</device-info>", "<ecp-setting-mode>enabled</ecp-setting-mode></device-info>")).restricted)
    }

    @Test
    fun `something else's XML isn't device-info`() {
        try {
            parseDeviceInfo("<root><friendlyName>Printer</friendlyName></root>")
            fail("expected a refusal")
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun `apps come with icons from the TV`() = runBlocking {
        answer(APPS)
        val apps = ecp.apps()
        assertEquals("GET /query/apps", sent())
        assertEquals(listOf("tvinput.hdmi1", "12", "837"), apps.map { it.id })
        assertEquals(listOf("Blu-ray player", "Netflix", "YouTube"), apps.map { it.title })
        assertEquals(server.url("/query/icon/837").toString(), apps.last().iconUrl)
        assertTrue(apps.none { it.searchable })
    }

    @Test
    fun `the home screen is no app`() = runBlocking {
        answer(ACTIVE_HOME)
        assertNull(ecp.activeApp())
        assertEquals("GET /query/active-app", sent())
        assertEquals("12", parseActiveApp(ACTIVE_NETFLIX))
    }

    @Test
    fun `the media player says whether it's playing`() {
        assertEquals(true, parsePlaying("""<player error="false" state="play"><is_live>false</is_live></player>"""))
        assertEquals(false, parsePlaying("""<player error="false" state="pause"/>"""))
        assertNull(parsePlaying("""<player error="false" state="close"/>"""))
    }

    @Test
    fun `keys are empty POSTs`() = runBlocking {
        answer()
        ecp.keypress("Select")
        val request = server.takeRequest()
        assertEquals("POST /keypress/Select", "${request.method} ${request.target}")
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun `typing backspaces, then sends each character URL-encoded`() = runBlocking {
        val keys = TextEdit(delete = 2, insert = "a b+é€😀").ecpKeys()
        repeat(keys.size) { answer() }
        keys.forEach { ecp.keypress(it) }
        val targets = List(keys.size) { server.takeRequest().target }
        assertEquals(
            listOf(
                "/keypress/Backspace", "/keypress/Backspace", "/keypress/Lit_a", "/keypress/Lit_%20", "/keypress/Lit_b",
                "/keypress/Lit_%2B", "/keypress/Lit_%C3%A9", "/keypress/Lit_%E2%82%AC", "/keypress/Lit_%F0%9F%98%80",
            ),
            targets,
        )
    }

    @Test
    fun `launch carries deep-link parameters`() = runBlocking {
        answer()
        ecp.launch("837", listOf("contentID" to "dQw4w9WgXcQ", "mediaType" to "live"))
        assertEquals("POST /launch/837?contentID=dQw4w9WgXcQ&mediaType=live", sent())
    }

    @Test
    fun `a media URL survives as one parameter`() = runBlocking {
        answer()
        ecp.launch("782875", listOf("u" to "http://nas/a b&c=d%20e.mp4", "t" to "v"))
        val url = server.takeRequest().url
        assertEquals("http://nas/a b&c=d%20e.mp4", url.queryParameter("u"))
        assertEquals("v", url.queryParameter("t"))
    }

    @Test
    fun `a 403 is mobile control saying no`() = runBlocking {
        answer("ECP command not allowed in Limited mode.", code = 403)
        try {
            ecp.keypress("Home")
            fail("expected a refusal")
        } catch (e: EcpRefusedException) {
            assertEquals(403, e.code)
        }
    }

    @Test
    fun `other errors carry their status`() = runBlocking {
        answer(code = 404)
        try {
            ecp.keypress("PowerOn")
            fail("expected an error")
        } catch (e: EcpException) {
            assertFalse(e is EcpRefusedException)
            assertEquals(404, e.code)
        }
    }

    internal companion object {
        val ROKU_TV = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <udn>015e5555-9000-5555-5555-b0a555555dfb</udn>
                <serial-number>YN00H5555555</serial-number>
                <model-name>100005844</model-name>
                <is-tv>true</is-tv>
                <is-stick>false</is-stick>
                <tuner-type>ATSC</tuner-type>
                <wifi-mac>02:00:00:00:00:0a</wifi-mac>
                <ethernet-mac>02:00:00:00:00:0b</ethernet-mac>
                <network-type>wifi</network-type>
                <friendly-device-name>58" Onn Roku TV</friendly-device-name>
                <friendly-model-name>Onn Roku TV</friendly-model-name>
                <default-device-name>Onn Roku TV - YN00H5555555</default-device-name>
                <user-device-name>58" Onn Roku TV</user-device-name>
                <power-mode>PowerOn</power-mode>
                <supports-wake-on-wlan>true</supports-wake-on-wlan>
            </device-info>
        """.trimIndent()

        val STICK = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <model-name>Roku Stick</model-name>
                <is-tv>false</is-tv>
                <is-stick>true</is-stick>
                <wifi-mac>02:00:00:00:00:0c</wifi-mac>
                <network-type>wifi</network-type>
                <friendly-device-name>Roku Stick - 2L647N055555</friendly-device-name>
                <power-mode>PowerOn</power-mode>
                <supports-wake-on-wlan>false</supports-wake-on-wlan>
            </device-info>
        """.trimIndent()

        val APPS = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <apps>
                <app id="tvinput.hdmi1" type="tvin" version="1.0.0">Blu-ray player</app>
                <app id="12" subtype="ndka" type="appl" version="4.1.218">Netflix</app>
                <app id="837" subtype="ndka" type="appl" version="2.24.229">YouTube</app>
            </apps>
        """.trimIndent()

        val ACTIVE_HOME = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <active-app>
                <app>Roku</app>
                <screensaver id="55545" type="ssvr" version="2.0.1">Default screensaver</screensaver>
            </active-app>
        """.trimIndent()

        val ACTIVE_NETFLIX = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <active-app>
                <app id="12" type="appl" version="4.1.218">Netflix</app>
            </active-app>
        """.trimIndent()
    }

    @Test
    fun `channels come by number, without the ones hidden on the TV`() {
        val xml = """
            <tv-channels>
              <channel><number>2.1</number><name>WCBS-HD</name><type>air-digital</type><user-hidden>false</user-hidden></channel>
              <channel><number>4.1</number><name>WNBC</name><type>air-digital</type><user-hidden>true</user-hidden></channel>
              <channel><number>7.1</number><name></name><type>air-digital</type></channel>
            </tv-channels>
        """.trimIndent()
        assertEquals(listOf(TvChannel("2.1", "2.1", "WCBS-HD"), TvChannel("7.1", "7.1", "")), parseChannels(xml))
        assertEquals("2.1", parseActiveChannel("<tv-channel><channel><number>2.1</number><name>WCBS-HD</name></channel></tv-channel>"))
    }
}
