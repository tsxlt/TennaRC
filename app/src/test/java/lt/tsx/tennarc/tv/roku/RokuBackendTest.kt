package lt.tsx.tennarc.tv.roku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RokuBackendTest {
    private val server = MockWebServer()
    private val tv = FakeRoku()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: Settings
    private lateinit var backend: RokuBackend

    @Before fun start() {
        server.dispatcher = tv
        server.start()
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.update { it.copy(kind = TvKind.ROKU, host = server.hostName) }
        backend = RokuBackend(settings, OkHttpClient(), scope, server.port)
    }

    @After fun stop() {
        backend.close()
        scope.cancel()
        server.close()
    }

    private class FakeRoku : Dispatcher() {
        @Volatile var deviceInfo = RokuEcpTest.ROKU_TV

        @Volatile var refused: Set<String> = emptySet()

        private val commands = LinkedBlockingQueue<String>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            if (refused.any { path.startsWith(it) }) {
                return MockResponse.Builder().code(403).body("ECP command not allowed in Limited mode.").build()
            }
            if (request.method == "POST") {
                commands.add(request.target)
                return MockResponse.Builder().build()
            }
            val body = when (path) {
                "/query/device-info" -> deviceInfo
                "/query/apps" -> RokuEcpTest.APPS
                "/query/active-app" -> RokuEcpTest.ACTIVE_NETFLIX
                "/query/media-player" -> """<player error="false" state="pause"/>"""
                else -> return MockResponse.Builder().code(404).build()
            }
            return MockResponse.Builder().body(body).build()
        }

        fun nextCommand(): String = commands.poll(5, TimeUnit.SECONDS) ?: error("the app sent nothing")

        fun noCommand(): Boolean = commands.poll(300, TimeUnit.MILLISECONDS) == null
    }

    private fun awaitState(predicate: (TvState) -> Boolean): TvState = runBlocking {
        withTimeout(10_000) { backend.state.first(predicate) }
    }

    private fun connect(): TvState {
        backend.onScreenStarted()
        return awaitState { it.isConnected && it.apps.isNotEmpty() && it.foregroundAppId != null }
    }

    private fun noticeAfter(action: () -> Unit): Notice = runBlocking {
        val notice: Deferred<Notice> = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { backend.notices.first() } }
        action()
        notice.await()
    }

    @Test
    fun `connects without pairing and reads the Roku's state`() {
        val state = connect()
        assertEquals(Connection.Connected, state.connection)
        assertEquals(TvKind.ROKU, state.kind)
        assertEquals(TvPower.ON, state.power)
        assertEquals(ROKU_FEATURES + Feature.LIVE_TV, state.features)
        assertEquals(ROKU_FEATURES + Feature.LIVE_TV, backend.features)
        assertEquals(listOf("12", "837"), state.apps.map { it.id })
        assertEquals(listOf("tvinput.hdmi1"), state.inputs.map { it.id })
        assertTrue(state.inputs.single().input)
        assertEquals("12", state.foregroundAppId)
        assertNull(state.volume)
        assertFalse(awaitState { !it.playing }.playing)
    }

    @Test
    fun `remembers the Roku's MACs for Wake-on-LAN`() {
        connect()
        assertEquals("02:00:00:00:00:0a, 02:00:00:00:00:0b", settings.prefs.value.mac)
    }

    @Test
    fun `keys reach the TV in the order they were pressed`() {
        connect()
        listOf(TvButton.UP, TvButton.RIGHT, TvButton.ENTER, TvButton.DOWN, TvButton.BACK).forEach(backend::press)
        assertEquals(
            listOf("/keypress/Up", "/keypress/Right", "/keypress/Select", "/keypress/Down", "/keypress/Back"),
            List(5) { tv.nextCommand() },
        )
    }

    @Test
    fun `a key a Roku doesn't have sends nothing`() {
        connect()
        backend.press(TvButton.RED)
        assertTrue(tv.noCommand())
    }

    @Test
    fun `typing backspaces, sends the letters, then Enter`() {
        connect()
        backend.type(TextEdit(delete = 1, insert = "hi"))
        backend.enter()
        assertEquals(
            listOf("/keypress/Backspace", "/keypress/Lit_h", "/keypress/Lit_i", "/keypress/Enter"),
            List(4) { tv.nextCommand() },
        )
    }

    @Test
    fun `volume and mute are keys`() {
        connect()
        backend.volumeUp()
        backend.setMuted(true)
        assertEquals(listOf("/keypress/VolumeUp", "/keypress/VolumeMute"), List(2) { tv.nextCommand() })
    }

    @Test
    fun `launching an app is a launch`() {
        connect()
        backend.launch("tvinput.hdmi1")
        assertEquals("/launch/tvinput.hdmi1", tv.nextCommand())
    }

    @Test
    fun `a refused key says why`() {
        connect()
        tv.refused = setOf("/keypress")
        assertEquals(Notice.ROKU_LIMITED, noticeAfter { backend.press(TvButton.HOME) })
    }

    @Test
    fun `limited mobile control is said on connecting`() {
        tv.deviceInfo = RokuEcpTest.ROKU_TV.replace("</device-info>", "<ecp-setting-mode>limited</ecp-setting-mode></device-info>")
        tv.refused = setOf("/query/apps")
        assertEquals(Notice.ROKU_LIMITED, noticeAfter { backend.onScreenStarted() })
        val state = awaitState { it.isConnected }
        assertEquals(TvPower.ON, state.power)
    }

    @Test
    fun `a Roku refusing everything is there, and says why`() {
        tv.refused = setOf("/")
        assertEquals(Notice.ROKU_LIMITED, noticeAfter { backend.onScreenStarted() })
        assertNull(awaitState { it.isConnected }.power)
    }

    @Test
    fun `an unreachable Roku goes offline and refuses commands`() {
        val closed = MockWebServer().apply { start() }
        val port = closed.port
        closed.close()
        val offline = RokuBackend(settings, OkHttpClient(), scope, port)
        offline.hold()
        runBlocking { withTimeout(10_000) { offline.state.first { it.connection == Connection.Offline } } }
        val notice = runBlocking {
            val next = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { offline.notices.first() } }
            offline.press(TvButton.HOME)
            next.await()
        }
        assertEquals(Notice.NOT_CONNECTED, notice)
        offline.close()
    }

    @Test
    fun `a YouTube link opens its channel at the video`() = runBlocking {
        connect()
        assertTrue(backend.open(LinkRouter.classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), LinkAction.APP))
        assertEquals("/launch/837?contentID=dQw4w9WgXcQ&mediaType=live", tv.nextCommand())
    }

    @Test
    fun `a Netflix link opens its channel at the title`() = runBlocking {
        connect()
        assertTrue(backend.open(LinkRouter.classify("https://www.netflix.com/watch/80057281"), LinkAction.APP))
        assertEquals("/launch/12?contentID=80057281&mediaType=movie", tv.nextCommand())
    }

    @Test
    fun `offers only what this Roku's apps can open`() {
        connect()
        assertEquals(listOf(LinkAction.APP), backend.actionsFor(LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkRouter.classify("http://nas/film.mp4")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkRouter.classify("https://example.com/")))
    }

    @Test
    fun `what a Roku can't open fails with a notice, sending nothing`() {
        connect()
        var opened = true
        val notice = noticeAfter { opened = runBlocking { backend.open(LinkRouter.classify("https://example.com/"), LinkAction.BROWSER) } }
        assertFalse(opened)
        assertEquals(Notice.OPEN_FAILED, notice)
        assertTrue(tv.noCommand())
    }

    @Test
    fun `search isn't something a Roku takes`() = runBlocking {
        connect()
        assertFalse(backend.search("cats", SearchTarget.InApp(RokuChannels.YOUTUBE)))
        assertFalse(backend.search("cats", SearchTarget.Web(SearchEngine.DUCKDUCKGO)))
        assertTrue(tv.noCommand())
    }

    @Test
    fun `wake turns a Roku TV in standby on`() {
        tv.deviceInfo = RokuEcpTest.ROKU_TV.replace("PowerOn", "DisplayOff")
        backend.onScreenStarted()
        assertEquals(TvPower.STANDBY, awaitState { it.isConnected }.power)
        assertEquals(Notice.WAKE_SENT, noticeAfter { backend.wake() })
        assertEquals("/keypress/PowerOn", tv.nextCommand())
    }

    @Test
    fun `play and pause share a key, and the guess flips with it`() {
        connect()
        val before = awaitState { !it.playing }.playing
        backend.playPause()
        assertEquals("/keypress/Play", tv.nextCommand())
        assertEquals(!before, backend.state.value.playing)
    }
}
