package lt.tsx.tennarc.ui.sheets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostTest {
    @Test
    fun `whole addresses and host names pass`() {
        assertTrue(isValidHost("192.168.1.70"))
        assertTrue(isValidHost(" 10.0.0.5 "))
        assertTrue(isValidHost("home-lgtv.local"))
        assertTrue(isValidHost("tenna"))
    }

    @Test
    fun `half-typed or impossible addresses don't`() {
        assertFalse(isValidHost(""))
        assertFalse(isValidHost("192.168.1"))
        assertFalse(isValidHost("192.168.1."))
        assertFalse(isValidHost("192.168.1.256"))
        assertFalse(isValidHost("192..1.188"))
        assertFalse(isValidHost("tv .local"))
    }
}
