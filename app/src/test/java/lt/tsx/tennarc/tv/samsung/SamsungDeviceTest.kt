package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.runBlocking
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungDeviceTest {
    @Test
    fun `reads a 2017 Frame, which doesn't report its power`() {
        val device = SamsungDevice.parse(FRAME_2017)!!
        assertEquals("Samsung Frame (43)", device.name)
        assertEquals("UE43LS003", device.model)
        assertNull(device.power)
        assertEquals(true, device.tokenAuth)
        assertTrue(device.frame)
        assertEquals("aa:ee:tt:hh:ee:rr", device.mac)
        assertEquals("be9554b9-c9fb-41f4-8920-22da015376a4", device.id)
        assertTrue(device.isTv)
    }

    @Test
    fun `newer sets say whether they're on or in standby`() {
        assertEquals(TvPower.ON, SamsungDevice.parse(newer("on"))!!.power)
        assertEquals(TvPower.STANDBY, SamsungDevice.parse(newer("standby"))!!.power)
        assertNull(SamsungDevice.parse(newer("something new"))!!.power)
        assertFalse(SamsungDevice.parse(newer("on"))!!.frame)
    }

    @Test
    fun `a 2015 set without token auth says nothing about it`() {
        val device = SamsungDevice.parse("""{"device":{"name":"[TV] TV-UE48JU6470","type":"Samsung SmartTV","modelName":"UE48JU6400"},"id":"uuid:abc"}""")!!
        assertNull(device.tokenAuth)
        assertEquals("TV-UE48JU6470", device.name)
        assertEquals("abc", device.id)
    }

    @Test
    fun `other Samsung gear and garbage aren't TVs`() {
        assertFalse(SamsungDevice.parse("""{"device":{"name":"Soundbar","type":"Samsung Speaker"}}""")!!.isTv)
        assertNull(SamsungDevice.parse("""{"name":"no device block"}"""))
        assertNull(SamsungDevice.parse("<html>"))
    }

    @Test
    fun `apps are looked up by id over REST, flat or wrapped`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when (request.target) {
                    "/api/v2/applications/111299001912" ->
                        MockResponse(body = """{"id":"111299001912","name":"YouTube","running":false,"version":"2.1.1","visible":false}""")
                    "/api/v2/applications/3201907018807" ->
                        MockResponse(body = """{"app":{"id":"3201907018807","name":"Netflix","running":true,"visible":true}}""")
                    else -> MockResponse(code = 404, body = """{"code":404,"message":"Not Found"}""")
                }
            }
            server.start()
            val rest = SamsungRest(OkHttpClient(), server.hostName, server.port)
            assertEquals(SamsungApp("111299001912", "YouTube", 2), rest.app("111299001912"))
            assertEquals(SamsungApp("3201907018807", "Netflix", 2), rest.app("3201907018807"))
            assertNull(rest.app("3201606009684"))
        }
    }

    @Test
    fun `buttons have Samsung's key names`() {
        assertEquals("KEY_RETURN", samsungKey(TvButton.BACK))
        assertEquals("KEY_SOURCE", samsungKey(TvButton.INPUT_HUB))
        assertEquals("KEY_FF", samsungKey(TvButton.FAST_FORWARD))
        assertEquals("KEY_CHDOWN", samsungKey(TvButton.CHANNEL_DOWN))
        assertEquals("KEY_CYAN", samsungKey(TvButton.BLUE))
        assertEquals("KEY_7", samsungKey(TvButton.DIGIT_7))
        assertEquals(TvButton.entries.size, TvButton.entries.map(::samsungKey).toSet().size)
    }

    @Test
    fun `apps open the way their type says`() {
        assertEquals("DEEP_LINK", SamsungApps.actionType(2))
        assertEquals("NATIVE_LAUNCH", SamsungApps.actionType(4))
        assertEquals("DEEP_LINK", SamsungApps.actionType(null))
    }

    private companion object {
        const val FRAME_2017 = """{
            "device": {
                "FrameTVSupport": "true", "GamePadSupport": "true", "ImeSyncedSupport": "true", "OS": "Tizen",
                "TokenAuthSupport": "true", "VoiceSupport": "true", "countryCode": "FR", "description": "Samsung DTV RCR",
                "developerIP": "0.0.0.0", "developerMode": "0", "duid": "uuid:be9554b9-c9fb-41f4-8920-22da015376a4",
                "firmwareVersion": "Unknown", "id": "uuid:be9554b9-c9fb-41f4-8920-22da015376a4", "ip": "1.2.3.4",
                "model": "17_KANTM_UHD", "modelName": "UE43LS003", "name": "[TV] Samsung Frame (43)", "networkType": "wired",
                "resolution": "3840x2160", "smartHubAgreement": "true", "type": "Samsung SmartTV",
                "udn": "uuid:be9554b9-c9fb-41f4-8920-22da015376a4", "wifiMac": "aa:ee:tt:hh:ee:rr"
            },
            "id": "uuid:be9554b9-c9fb-41f4-8920-22da015376a4",
            "name": "[TV] Samsung Frame (43)", "remote": "1.0", "type": "Samsung SmartTV",
            "uri": "https://1.2.3.4:8002/api/v2/", "version": "2.0.25"
        }"""

        fun newer(power: String) = """{"device":{"FrameTVSupport":"false","PowerState":"$power","TokenAuthSupport":"true",
            "modelName":"QE55Q80TATXXC","name":"[TV] Samsung Q80 Series (55)","type":"Samsung SmartTV"}}"""
    }
}
