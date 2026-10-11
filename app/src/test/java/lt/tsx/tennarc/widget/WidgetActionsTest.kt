package lt.tsx.tennarc.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRemote(start: TvState) : WidgetRemote {
    override val state: StateFlow<TvState> = MutableStateFlow(start)
    private val mutable get() = state as MutableStateFlow<TvState>
    val sent = mutableListOf<String>()
    var holders = 0
        private set
    var held = 0
        private set

    fun becomes(next: TvState) {
        mutable.value = next
    }

    override fun hold() {
        holders++
        held++
    }

    override fun release() {
        holders--
    }

    override fun playPause() { sent += "playPause" }
    override fun volumeUp() { sent += "volumeUp" }
    override fun volumeDown() { sent += "volumeDown" }
    override fun setMuted(muted: Boolean) { sent += "muted=$muted" }
    override fun launch(appId: String) { sent += "launch $appId" }
    override fun wake() { sent += "wake" }
    override fun powerOff() { sent += "powerOff" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetActionsTest {
    private val on = TvState(host = "h", connection = Connection.Connected, power = TvPower.ON)
    private var prefs = Prefs()
    private val update: ((Prefs) -> Prefs) -> Unit = { prefs = it(prefs) }

    @Test
    fun `a favourite opens its app on the TV and counts as used`() = runTest {
        val remote = FakeRemote(on)
        assertTrue(launchApp(remote, TvApps.NETFLIX, update, demo = false) { 1_000L })
        assertEquals(listOf("launch ${TvApps.NETFLIX}"), remote.sent)
        assertEquals(1f, prefs.appUse[TvApps.NETFLIX])
        assertEquals(1_000L, prefs.appUseAt)
        assertEquals(1, remote.held)
        assertEquals(0, remote.holders)
        assertEquals(SEND_MS, currentTime)
    }

    @Test
    fun `a favourite waits for a TV still connecting`() = runTest {
        val remote = FakeRemote(on.copy(connection = Connection.Connecting))
        val launched = async { launchApp(remote, TvApps.YOUTUBE, update, demo = false) }
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(remote.sent.isEmpty())
        assertEquals(1, remote.holders)
        remote.becomes(on)
        assertTrue(launched.await())
        assertEquals(listOf("launch ${TvApps.YOUTUBE}"), remote.sent)
        assertEquals(0, remote.holders)
    }

    @Test
    fun `nothing opens on a TV that's off or doesn't answer, and the connection is let go`() = runTest {
        val off = FakeRemote(on.copy(power = TvPower.STANDBY))
        assertFalse(launchApp(off, TvApps.YOUTUBE, update, demo = false))
        val away = FakeRemote(on.copy(connection = Connection.Offline))
        assertFalse(launchApp(away, TvApps.YOUTUBE, update, demo = false))
        val silent = FakeRemote(on.copy(connection = Connection.Connecting))
        assertFalse(launchApp(silent, TvApps.YOUTUBE, update, demo = false))
        assertEquals(CONNECT_MS, currentTime)
        for (remote in listOf(off, away, silent)) {
            assertTrue(remote.sent.isEmpty())
            assertEquals(0, remote.holders)
        }
        assertTrue(prefs.appUse.isEmpty())
    }

    @Test
    fun `the demo's apps aren't counted as anyone's habit`() = runTest {
        val remote = FakeRemote(on)
        assertTrue(launchApp(remote, TvApps.YOUTUBE, update, demo = true))
        assertTrue(prefs.appUse.isEmpty())
    }

    @Test
    fun `the chip moves to the next TV and waits to hear from it`() = runTest {
        prefs = Prefs(tvName = "Tenna", host = "192.168.1.10", otherTvs = listOf(SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, "")))
        val remote = FakeRemote(on.copy(host = "192.168.1.10"))
        var told = 0
        val swapping: ((Prefs) -> Prefs) -> Unit = { change ->
            prefs = change(prefs)
            remote.becomes(TvState(host = prefs.host, connection = Connection.Connecting))
        }
        val switched = async { switchTv(remote, swapping) { told++ } }
        runCurrent()
        assertEquals("192.168.1.50", prefs.host)
        assertEquals("Bedroom", prefs.tvName)
        assertEquals(1, told)
        assertEquals(1, remote.holders)
        advanceTimeBy(1_000)
        remote.becomes(TvState(host = "192.168.1.50", connection = Connection.Offline))
        assertEquals(Connection.Offline, switched.await()?.connection)
        assertEquals(0, remote.holders)
        assertTrue(remote.sent.isEmpty())
    }

    @Test
    fun `with one TV the chip does nothing`() = runTest {
        prefs = Prefs(host = "192.168.1.10")
        val remote = FakeRemote(on)
        assertEquals(null, switchTv(remote, update))
        assertEquals(0, remote.held)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `keys go out once connected, and mute flips what the TV says`() = runTest {
        val remote = FakeRemote(on.copy(muted = true))
        assertTrue(sendKey(remote, WidgetKey.MUTE))
        assertTrue(sendKey(remote, WidgetKey.VOLUME_UP))
        assertTrue(sendKey(remote, WidgetKey.PLAY_PAUSE))
        assertEquals(listOf("muted=false", "volumeUp", "playPause"), remote.sent)
        assertEquals(0, remote.holders)
        assertFalse(sendKey(FakeRemote(on.copy(connection = Connection.Offline)), WidgetKey.VOLUME_DOWN))
    }
}
