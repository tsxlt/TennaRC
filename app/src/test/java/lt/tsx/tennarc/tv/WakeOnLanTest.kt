package lt.tsx.tennarc.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanTest {
    @Test
    fun `magic packet is six FFs then the MAC sixteen times`() {
        val packet = WakeOnLan.packet("02:1a:2b:3c:4d:5e")
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        val mac = listOf(0x02, 0x1a, 0x2b, 0x3c, 0x4d, 0x5e).map { it.toByte() }
        for (i in 0 until 16) assertEquals(mac, packet.drop(6 + i * 6).take(6))
    }

    @Test
    fun `accepts the usual MAC spellings and rejects junk`() {
        assertTrue(WakeOnLan.isValidMac("02:00:00:00:00:01"))
        assertTrue(WakeOnLan.isValidMac("40-2f-86-2c-0b-1c"))
        assertTrue(WakeOnLan.isValidMac("402f862c0b1c"))
        assertFalse(WakeOnLan.isValidMac("40:2f:86:2c:0b"))
        assertFalse(WakeOnLan.isValidMac("zz:2f:86:2c:0b:1c"))
    }

    @Test
    fun `a setting holds one MAC or several`() {
        assertEquals(listOf("02:00:00:00:00:01"), WakeOnLan.macs(" 02:00:00:00:00:01 "))
        assertEquals(listOf("02:00:00:00:00:01", "402f862c0b1c"), WakeOnLan.macs("02:00:00:00:00:01, 402f862c0b1c"))
        assertTrue(WakeOnLan.isValidSetting("02:00:00:00:00:01 40-2f-86-2c-0b-1c"))
        assertFalse(WakeOnLan.isValidSetting(""))
        assertFalse(WakeOnLan.isValidSetting("02:00:00:00:00:01, nope"))
    }
}
