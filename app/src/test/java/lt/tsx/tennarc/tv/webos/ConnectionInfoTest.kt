package lt.tsx.tennarc.tv.webos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionInfoTest {
    @Test
    fun `reads the wired and Wi-Fi MACs and skips Wi-Fi Direct`() {
        val payload = JSONObject(
            """{"returnValue":true,"wiredInfo":{"macAddress":"74:e6:b8:44:0a:7e"},
            "wifiInfo":{"macAddress":"20:28:bc:1b:5f:46"},"p2pInfo":{"macAddress":"22:28:bc:1b:5f:46"}}""",
        )
        assertEquals(listOf("74:e6:b8:44:0a:7e", "20:28:bc:1b:5f:46"), macsFrom(payload))
    }

    @Test
    fun `a TV without Wi-Fi reports only its wired MAC`() {
        assertEquals(listOf("74:e6:b8:44:0a:7e"), macsFrom(JSONObject("""{"wiredInfo":{"macAddress":"74:e6:b8:44:0a:7e"}}""")))
        assertEquals(emptyList<String>(), macsFrom(JSONObject("""{"returnValue":true}""")))
    }
}
