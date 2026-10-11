package lt.tsx.tennarc.tv

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DlnaTest {
    private val server = MockWebServer()
    private val client = DlnaClient(OkHttpClient())

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun renderer() = UpnpDevice(
        location = server.url("/desc.xml").toString(),
        name = "Tenna",
        services = mapOf("urn:schemas-upnp-org:service:AVTransport:1" to server.url("/AVTransport/ctrl").toString()),
    )

    @Test
    fun `reads the renderer's name and resolves its control URL`() {
        server.enqueue(MockResponse.Builder().body(DESCRIPTION).build())
        val device = client.describe(server.url("/upnp/desc.xml").toString())
        assertEquals("[LG] webOS TV UM7450PLA", device.name)
        assertEquals(server.url("/AVTransport/ctrl").toString(), device.avTransport)
        assertEquals(server.hostName, device.host)
    }

    @Test
    fun `soap calls put InstanceID first and parse the reply`() {
        server.enqueue(MockResponse.Builder().body(POSITION_REPLY).build())
        val reply = client.call(renderer(), "GetPositionInfo")
        val request = server.takeRequest()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"", request.headers["SOAPAction"])
        assertTrue(request.body!!.utf8().contains("<u:GetPositionInfo xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID></u:GetPositionInfo>"))
        assertEquals("0:12:30", reply["RelTime"])
        assertEquals("1:30:00", reply["TrackDuration"])
    }

    @Test
    fun `play stops, sets the URI with DLNA metadata, then plays`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse.Builder().body(EMPTY_REPLY).build()) }
        client.play(renderer(), "http://nas/a&b.mp4", "A & B", "video/mp4")
        val actions = List(3) { server.takeRequest().headers["SOAPAction"]!!.substringAfter('#').trimEnd('"') }
        assertEquals(listOf("Stop", "SetAVTransportURI", "Play"), actions)
    }

    @Test
    fun `refusals carry the UPnP error`() {
        server.enqueue(MockResponse.Builder().code(500).body(FAULT_606).build())
        try {
            client.call(renderer(), "SetVolume")
            fail("expected a refusal")
        } catch (e: DlnaException) {
            assertTrue(e.message!!, e.message!!.contains("606"))
        }
    }

    @Test
    fun `status maps transport state and times`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(TRANSPORT_REPLY).build())
        server.enqueue(MockResponse.Builder().body(POSITION_REPLY).build())
        assertEquals(TransportStatus("PLAYING", 750, 5400), client.status(renderer()))
    }

    @Test
    fun `didl escapes text and advertises seeking`() {
        val didl = didl("http://nas/a&b.mp4", "Tom & Jerry <1>", "video/mp4")
        assertTrue(didl.contains("<dc:title>Tom &amp; Jerry &lt;1&gt;</dc:title>"))
        assertTrue(didl.contains("""protocolInfo="http-get:*:video/mp4:$DLNA_FLAGS">http://nas/a&amp;b.mp4</res>"""))
        assertTrue(didl.contains("object.item.videoItem"))
    }

    @Test
    fun `the renderer's sink list says which pictures it takes`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(PROTOCOL_INFO_REPLY).build())
        val withManager = renderer().let {
            it.copy(services = it.services + ("urn:schemas-upnp-org:service:ConnectionManager:1" to server.url("/ConnectionManager/ctrl").toString()))
        }
        val sinks = client.sinkProtocols(withManager)
        val request = server.takeRequest()
        assertEquals("\"urn:schemas-upnp-org:service:ConnectionManager:1#GetProtocolInfo\"", request.headers["SOAPAction"])
        assertTrue(request.body!!.utf8().contains("<u:GetProtocolInfo xmlns:u=\"urn:schemas-upnp-org:service:ConnectionManager:1\"></u:GetProtocolInfo>"))
        assertTrue(acceptsType(sinks, "image/jpeg"))
        assertTrue(acceptsType(sinks, "IMAGE/PNG"))
        assertFalse(acceptsType(sinks, "image/heic"))
        assertFalse(acceptsType(sinks, "image/gif"))
        assertTrue(acceptsType(listOf("http-get:*:*:*"), "image/webp"))
        assertEquals(emptyList<String>(), client.sinkProtocols(renderer()))
    }

    @Test
    fun `times round-trip`() {
        assertEquals(0, parseTime("NOT_IMPLEMENTED"))
        assertEquals(3723, parseTime("1:02:03"))
        assertEquals(3723, parseTime("1:02:03.500"))
        assertEquals("1:02:03", formatTime(3723))
        assertEquals("0:00:00", formatTime(-5))
    }

    @Test
    fun `ssdp headers are case-insensitive`() {
        val headers = Ssdp.parseHeaders("HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.70:1214/\r\nServer: WebOS/4.5\r\n\r\n")
        assertEquals("http://192.168.1.70:1214/", headers["location"])
        assertEquals("WebOS/4.5", headers["server"])
    }

    @Test
    fun `the device UUID comes out of the USN`() {
        val reply = SsdpReply("http://x/", "", "uuid:7b3c1c4e-0000-1000-8000-402f862c0b1c::urn:lge-com:service:webos-second-screen:1")
        assertEquals("7b3c1c4e-0000-1000-8000-402f862c0b1c", reply.uuid)
        assertEquals(null, SsdpReply("http://x/", "", "").uuid)
    }

    private companion object {
        val DESCRIPTION = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>[LG] webOS TV UM7450PLA</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/AVTransport/ctrl</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/RenderingControl/ctrl</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        fun envelope(body: String) = """<?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>$body</s:Body></s:Envelope>"""

        val EMPTY_REPLY = envelope("""<u:Reply xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>""")

        val POSITION_REPLY = envelope(
            """<u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <Track>1</Track><TrackDuration>1:30:00</TrackDuration><RelTime>0:12:30</RelTime>
            </u:GetPositionInfoResponse>""",
        )

        val PROTOCOL_INFO_REPLY = envelope(
            """<u:GetProtocolInfoResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1"><Source></Source>
                <Sink>http-get:*:video/mp4:*,http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG, http-get:*:image/png:*,rtsp-rtp-udp:*:image/gif:*</Sink>
            </u:GetProtocolInfoResponse>""",
        )

        val TRANSPORT_REPLY = envelope(
            """<u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <CurrentTransportState>PLAYING</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus>
            </u:GetTransportInfoResponse>""",
        )

        val FAULT_606 = envelope(
            """<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>
                <UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>606</errorCode>
                <errorDescription>Action not authorized</errorDescription></UPnPError></detail></s:Fault>""",
        )
    }
}
