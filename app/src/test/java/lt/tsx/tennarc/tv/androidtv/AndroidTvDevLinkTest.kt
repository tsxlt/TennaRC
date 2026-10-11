package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.firetv.AdbPortClosedException
import lt.tsx.tennarc.tv.firetv.AdbShell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTvDevLinkTest {
    private val host = "127.0.0.1"
    private val tv = FakeAndroidTv()
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val settings = Settings(RuntimeEnvironment.getApplication()).apply {
        update { it.copy(host = host, kind = TvKind.ANDROID_TV) }
        setClientKey(adbKey(host), AdbLink.ADB_PORT.toString())
    }
    private val commands = CopyOnWriteArrayList<String>()
    private lateinit var backend: AndroidTvBackend

    @After
    fun stop() {
        on { backend.close() }
        tv.close()
        main.close()
    }

    private fun backend(adbd: () -> AdbShell) {
        backend = AndroidTvBackend(
            settings,
            TestIdentity("Tenna RC"),
            CoroutineScope(SupervisorJob() + main),
            tv.remotePort,
            tv.pairingPort,
            adbShells = { _, _ -> adbd() },
        )
    }

    @Test
    fun `ADB turned away leaves the remote connected and its keys going`() {
        backend { shell(trusted = { throw AdbPortClosedException(host) }) }
        pairFirst()
        val state = awaitState { it.devLink == DevLink.Down(DevProblem.DEBUGGING_OFF) }
        assertEquals(Connection.Connected, state.connection)
        assertEquals((1..4).map { "input:hdmi$it" }, state.inputs.map { it.id })
        on { backend.press(TvButton.HOME) }
        assertEquals(KeyCode.HOME, tv.next().message(10)?.int(1))
    }

    @Test
    fun `a hung adbd holds nothing up`() {
        backend { shell(trusted = { awaitCancellation() }) }
        pairFirst()
        on { backend.press(TvButton.UP) }
        assertEquals(KeyCode.DPAD_UP, tv.next().message(10)?.int(1))
        Thread.sleep(AdbLink.START_DELAY_MS + 500)
        assertEquals(DevLink.Connecting, backend.state.value.devLink)
        on { backend.press(TvButton.DOWN) }
        assertEquals(KeyCode.DPAD_DOWN, tv.next().message(10)?.int(1))
        assertTrue(backend.state.value.isConnected)
    }

    @Test
    fun `with the link up, the TV's own inputs and apps, and an input switches over it`() {
        backend { shell() }
        pairFirst()
        val state = awaitState { it.inputs.any { input -> isTvInput(input.id) } && it.apps.any { app -> app.id == "org.xbmc.kodi" } }
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "TV"), state.inputs.map { it.title })
        assertTrue(Feature.APP_LIST in state.features)
        assertEquals(DevLink.Up, state.devLink)
        on { backend.launch(state.inputs[2].id) }
        awaitCommand { it.startsWith("am start") }
        assertEquals(
            "am start -n 'org.droidtv.playtv/.PlayTvActivity' -a android.intent.action.VIEW -d 'content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW7'",
            commands.last { it.startsWith("am start") },
        )
        assertTrue(tv.nothingSent())
    }

    private fun shell(trusted: suspend () -> Boolean = { true }) = object : AdbShell {
        override suspend fun trusted() = trusted()

        override suspend fun connect() = Unit

        override suspend fun shell(command: String): String {
            commands += command
            return when {
                command == INPUTS_COMMAND -> javaClass.getResource("/androidtv/tv_input-mediatek-philips.txt")!!.readText() + "\n@labels\nnull\n@hidden\nnull"
                command == APPS_COMMAND -> "com.netflix.ninja/.MainActivity\norg.xbmc.kodi/.Splash"
                command == INPUT_HANDLERS_COMMAND -> "org.droidtv.playtv/.PlayTvActivity"
                command.startsWith("am start") -> "Starting: Intent { act=android.intent.action.VIEW }"
                else -> "null"
            }
        }

        override fun close() = Unit
    }

    private fun pairFirst() {
        on { backend.pair() }
        val code = checkNotNull(tv.codes.poll(5, TimeUnit.SECONDS)) { "The TV showed no code" }
        on { backend.submitPairingCode(code) }
        awaitState { it.isConnected && it.foregroundAppId != null }
        tv.next()
        tv.next()
    }

    private fun awaitCommand(predicate: (String) -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (commands.none(predicate)) {
            check(System.currentTimeMillis() < until) { "adbd was never asked" }
            Thread.sleep(20)
        }
    }

    private fun on(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun awaitState(predicate: (TvState) -> Boolean): TvState =
        runBlocking { withTimeout(10_000) { backend.state.first(predicate) } }
}
