package lt.tsx.tennarc.tv.roku

import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Ssdp
import lt.tsx.tennarc.tv.SsdpReply
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RokuFinderTest {
    private val reply = SsdpReply("http://192.168.1.134:8060/", "Roku/9.2.0 UPnP/1.0 Roku/9.2.0", "uuid:roku:ecp:YN00H5555555")

    @Test
    fun `a Roku's SSDP reply carries its ECP address and serial`() {
        val headers = Ssdp.parseHeaders(
            "HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nST: roku:ecp\r\n" +
                "USN: uuid:roku:ecp:YN00H5555555\r\nLOCATION: http://192.168.1.134:8060/\r\n\r\n",
        )
        assertEquals("http://192.168.1.134:8060/", headers["location"])
        assertEquals("roku:ecp:YN00H5555555", SsdpReply(headers.getValue("location"), "", headers.getValue("usn")).uuid)
    }

    @Test
    fun `named from device-info`() {
        val info = parseDeviceInfo(RokuEcpTest.ROKU_TV)
        assertEquals(
            FoundTv("192.168.1.134", "58\" Onn Roku TV", "roku:ecp:YN00H5555555", TvKind.ROKU, model = "Onn Roku TV"),
            RokuFinder.found(reply, info),
        )
    }

    @Test
    fun `a Roku whose device-info didn't answer is still listed`() {
        assertEquals("Roku", RokuFinder.found(reply, null)?.name)
    }

    @Test
    fun `falls back through the names a Roku may leave blank`() {
        val info = parseDeviceInfo("<device-info><model-name>Roku Ultra</model-name></device-info>")
        assertEquals("Roku Ultra", info.name)
        assertEquals("Roku", parseDeviceInfo("<device-info/>").name)
    }

    @Test
    fun `a location that isn't plain HTTP to a host is skipped`() {
        assertNull(RokuFinder.found(reply.copy(location = "not a url"), null))
        assertNull(RokuFinder.found(reply.copy(location = "file:///etc/passwd"), null))
    }
}
