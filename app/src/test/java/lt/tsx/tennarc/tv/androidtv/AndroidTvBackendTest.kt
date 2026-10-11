package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTvBackendTest {
    private val host = "127.0.0.1"
    private lateinit var tv: FakeAndroidTv
    private lateinit var settings: Settings
    private lateinit var backend: AndroidTvBackend
    private val phone = TestIdentity("Tenna RC")

    private lateinit var main: ExecutorCoroutineDispatcher

    @Before
    fun start() {
        tv = FakeAndroidTv()
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.update { it.copy(host = host, kind = TvKind.ANDROID_TV) }
        backend = AndroidTvBackend(settings, phone, CoroutineScope(SupervisorJob() + main), tv.remotePort, tv.pairingPort)
    }

    @After
    fun stop() {
        on { backend.close() }
        tv.close()
        main.close()
    }

    @Test
    fun `pairs with the code on screen, then drives the TV`() {
        on { backend.hold() }
        awaitState { it.connection == Connection.NeedsPairing() }

        on { backend.pair() }
        awaitState { it.connection == Connection.AwaitingCode() }
        val code = tv.codes.poll(5, TimeUnit.SECONDS)!!
        val typo = "%02X".format((code.take(2).toInt(16) + 1) % 256) + code.drop(2)
        on { backend.submitPairingCode(typo) }
        awaitState { it.connection == Connection.AwaitingCode(wrong = true) }
        on { backend.submitPairingCode(code.lowercase()) }

        val connected = awaitState { it.isConnected && it.volume != null && it.foregroundAppId != null }
        assertEquals(TvPower.ON, connected.power)
        assertEquals(12, connected.volume)
        assertEquals(AndroidTvApps.YOUTUBE, connected.foregroundAppId)
        assertEquals("YouTube", connected.appTitle(connected.foregroundAppId))
        assertEquals(TvPin(tv.fingerprint, tv.fingerprint), TvPin.decode(settings.clientKey(host)))
        assertEquals(615, tv.next().message(1)?.int(1))
        assertEquals(615, tv.next().message(2)?.int(1))

        on { backend.press(TvButton.HOME) }
        assertEquals(KeyCode.HOME, tv.next().message(10)?.int(1))

        assertTrue(runBlocking { backend.open(LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ"), LinkAction.APP) })
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", tv.next().message(90)?.string(1))
        assertTrue(runBlocking { backend.search("lo-fi", SearchTarget.InApp(AndroidTvApps.YOUTUBE)) })
        assertEquals("https://www.youtube.com/results?search_query=lo-fi", tv.next().message(90)?.string(1))

        on { backend.type(TextEdit(delete = 1, insert = "ab")) }
        assertEquals(KeyCode.DEL, tv.next().message(10)?.int(1))
        assertEquals("ab", tv.next().message(21)?.message(3)?.message(2)?.string(3))

        tv.send(bytes(66, 4, 8, 25, 16, 7))
        assertEquals(25, tv.next().message(9)?.int(1))

        on { backend.setMuted(false) }
        assertTrue("already unmuted: no toggle", tv.nothingSent())
        on { backend.setMuted(true) }
        assertEquals(KeyCode.VOLUME_MUTE, tv.next().message(10)?.int(1))
    }

    @Test
    fun `standby is reported, and the power key wakes it`() {
        pairFirst()
        tv.send(bytes(194, 2, 0))
        awaitState { it.power == TvPower.STANDBY }
        on { backend.wake() }
        assertEquals(KeyCode.POWER, tv.next().message(10)?.int(1))
    }

    @Test
    fun `the TV asking for text brings the keyboard, another app takes it away`() {
        pairFirst()
        tv.send(bytes(178, 1, 6, 18, 4, 18, 2, "hi"))
        assertEquals(2, awaitState { it.keyboard != null }.keyboard?.textLength)
        tv.send(bytes(162, 1, 21, 10, 19, 98, 17, AndroidTvApps.NETFLIX))
        assertNull(awaitState { it.foregroundAppId != AndroidTvApps.YOUTUBE }.keyboard)
    }

    @Test
    fun `a code the TV refuses gets a fresh one`() {
        tv.refuseCodes = 1
        on { backend.pair() }
        submitShownCode()
        awaitState { it.connection == Connection.AwaitingCode(wrong = true) }
        submitShownCode()
        awaitState { it.isConnected }
    }

    @Test
    fun `cancelling on the TV ends the pairing`() {
        tv.hangUpAfterCode = true
        on { backend.pair() }
        awaitState { it.connection == Connection.NeedsPairing(declined = true) }
    }

    @Test
    fun `a TV that has forgotten the phone asks to pair again`() {
        settings.setClientKey(host, TvPin(tv.fingerprint, tv.fingerprint).encode())
        on { backend.hold() }
        awaitState { it.connection == Connection.NeedsPairing(forgotten = true) }
        assertNull(settings.clientKey(host))
    }

    @Test
    fun `another TV at the address isn't trusted`() {
        pairFirst()
        on { backend.close() }
        settings.setClientKey(host, TvPin(tv.fingerprint, remote = "00").encode())
        backend = AndroidTvBackend(settings, phone, CoroutineScope(SupervisorJob() + main), tv.remotePort, tv.pairingPort)
        on { backend.hold() }
        awaitState { it.connection == Connection.NeedsPairing(forgotten = true) }
    }

    private fun pairFirst() {
        on { backend.pair() }
        submitShownCode()
        awaitState { it.isConnected && it.foregroundAppId != null }
        tv.next()
        tv.next()
    }

    private fun submitShownCode() {
        val code = checkNotNull(tv.codes.poll(5, TimeUnit.SECONDS)) { "The TV showed no code" }
        on { backend.submitPairingCode(code) }
    }

    private fun on(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun awaitState(predicate: (TvState) -> Boolean): TvState =
        runBlocking { withTimeout(10_000) { backend.state.first(predicate) } }
}
