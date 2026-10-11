package lt.tsx.tennarc.tv.androidtv

import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class AndroidTvFinderTest {
    private val ipv6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xFE.toByte(); it[1] = 0x80.toByte(); it[15] = 1 })
    private val ipv4 = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 50))

    @Test
    fun `a resolved service becomes an Android TV, IPv4 first`() {
        assertEquals(
            FoundTv(host = "192.168.1.50", name = "Living Room TV", id = "AA:BB:CC:DD:EE:FF", kind = TvKind.ANDROID_TV),
            AndroidTvFinder.foundTv("Living Room TV", listOf(ipv6, ipv4), mapOf("bt" to "AA:BB:CC:DD:EE:FF".encodeToByteArray())),
        )
    }

    @Test
    fun `a service with no address is no use`() {
        assertNull(AndroidTvFinder.foundTv("Living Room TV", emptyList(), emptyMap()))
    }

    @Test
    fun `no name and no Bluetooth address still make a TV`() {
        val found = AndroidTvFinder.foundTv("", listOf(ipv4), mapOf("bt" to null))
        assertEquals("Android TV", found?.name)
        assertNull(found?.id)
    }

    @Test
    fun `DNS escapes left in a name are undone, UTF-8 included`() {
        assertEquals("Living Room", AndroidTvFinder.unescape("Living\\032Room"))
        assertEquals("Köket", AndroidTvFinder.unescape("K\\195\\182ket"))
        assertEquals("a.b\\c", AndroidTvFinder.unescape("a\\.b\\\\c"))
        assertEquals("Plain", AndroidTvFinder.unescape("Plain"))
    }
}
