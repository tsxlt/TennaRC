package lt.tsx.tennarc.tv.samsung

import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Ssdp
import lt.tsx.tennarc.tv.SsdpReply
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamsungFinderTest {
    private fun reply(response: String): SsdpReply {
        val headers = Ssdp.parseHeaders(response)
        return SsdpReply(headers["location"].orEmpty(), headers["server"].orEmpty(), headers["usn"].orEmpty())
    }

    @Test
    fun `a remote control receiver's answer gives its address and UUID`() {
        val candidate = SamsungFinder.candidate(reply(RECEIVER))
        assertEquals(SamsungFinder.Candidate("10.10.12.34", "068e7781-006e-1000-bbbf-84a4668d8423"), candidate)
    }

    @Test
    fun `anything else that answers is left out`() {
        val renderer = RECEIVER.replace("urn:samsung.com:device:RemoteControlReceiver:1", "urn:schemas-upnp-org:device:MediaRenderer:1")
        assertNull(SamsungFinder.candidate(reply(renderer)))
        assertNull(SamsungFinder.candidate(reply(RECEIVER.replace("LOCATION: http://10.10.12.34:7676/smp_7_", "LOCATION: nowhere"))))
    }

    @Test
    fun `the REST API's answer names the TV`() {
        val candidate = SamsungFinder.Candidate("10.10.12.34", "068e7781-006e-1000-bbbf-84a4668d8423")
        val tv = SamsungDevice.parse("""{"device":{"name":"[TV] Living Room","type":"Samsung SmartTV","duid":"uuid:other"}}""")!!
        assertEquals(
            FoundTv("10.10.12.34", "Living Room", "068e7781-006e-1000-bbbf-84a4668d8423", TvKind.SAMSUNG),
            SamsungFinder.found(candidate, tv),
        )
        assertEquals("other", SamsungFinder.found(candidate.copy(uuid = null), tv)?.id)
        val soundbar = SamsungDevice.parse("""{"device":{"name":"Soundbar","type":"Samsung Speaker"}}""")!!
        assertNull(SamsungFinder.found(candidate, soundbar))
    }

    private companion object {
        val RECEIVER = listOf(
            "HTTP/1.1 200 OK",
            "CACHE-CONTROL: max-age=1800",
            "Date: Thu, 01 Jan 1970 00:06:48 GMT",
            "EXT:",
            "LOCATION: http://10.10.12.34:7676/smp_7_",
            "SERVER: SHP, UPnP/1.0, Samsung UPnP SDK/1.0",
            "ST: urn:samsung.com:device:RemoteControlReceiver:1",
            "USN: uuid:068e7781-006e-1000-bbbf-84a4668d8423::urn:samsung.com:device:RemoteControlReceiver:1",
            "Content-Length: 0",
        ).joinToString("\r\n", postfix = "\r\n\r\n")
    }
}
