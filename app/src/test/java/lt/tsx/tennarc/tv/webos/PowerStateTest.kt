package lt.tsx.tennarc.tv.webos
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerStateTest {
    @Test
    fun `maps webOS power states`() {
        assertEquals(TvPower.ON, parsePower("Active", ""))
        assertEquals(TvPower.ON, parsePower("Screen Saver", ""))
        assertEquals(TvPower.SCREEN_OFF, parsePower("Screen Off", ""))
        assertEquals(TvPower.STANDBY, parsePower("Active Standby", ""))
        assertEquals(TvPower.STANDBY, parsePower("Suspend", ""))
        assertEquals(null, parsePower("", ""))
    }

    @Test
    fun `a TV on its way down counts as standby`() {
        assertEquals(TvPower.STANDBY, parsePower("Active", "Request Power Off"))
        assertEquals(TvPower.STANDBY, parsePower("Active", "Prepare Suspend"))
    }

    @Test
    fun `standby makes the remote unusable but keeps the connection`() {
        val state = TvState(host = "h", connection = Connection.Connected, power = TvPower.STANDBY)
        assertEquals(true, state.isConnected)
        assertEquals(false, state.isUsable)
    }
}
