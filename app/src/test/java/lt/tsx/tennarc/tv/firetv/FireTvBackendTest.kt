package lt.tsx.tennarc.tv.firetv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FireTvBackendTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var settings: Settings

    @Before
    fun setUp() {
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.update { it.copy(kind = TvKind.FIRE_TV, host = HOST) }
    }

    private class FakeTv(var trusts: Boolean = true) {
        var refuses = false
        var unreachable = false
        var dead = false
        var stalls = false
        var apps = APPS
        var status = ""
        var gate: CompletableDeferred<Unit>? = null
        val replies = mutableMapOf<String, String>()
        val commands = mutableListOf<String>()
        var probes = 0
        var connects = 0
        private val approval = CompletableDeferred<Unit>()

        val inputs get() = commands.filter { it.startsWith("input") }

        fun allow() = approval.complete(Unit)

        fun shell() = object : AdbShell {
            override suspend fun trusted(): Boolean {
                probes++
                if (refuses) throw AdbPortClosedException(HOST)
                if (unreachable) throw IOException("No route to host")
                return trusts
            }

            override suspend fun connect() {
                connects++
                if (stalls) awaitCancellation()
                if (!trusts) {
                    approval.await()
                    trusts = true
                }
            }

            override suspend fun shell(command: String): String {
                if (dead) throw IOException("Connection reset")
                commands += command
                if (command.startsWith("input")) gate?.await()
                return when (command) {
                    APPS_COMMAND -> apps
                    STATUS_COMMAND -> status
                    else -> replies.entries.firstOrNull { command.startsWith(it.key) }?.value.orEmpty()
                }
            }

            override fun close() = Unit
        }
    }

    private val keyDir get() = File(folder.root, "adb")

    private fun TestScope.backend(tv: FakeTv, paired: Boolean = true): FireTvBackend {
        if (paired) settings.setClientKey(HOST, "adb")
        return FireTvBackend(settings, folder.root, backgroundScope, AdbKeys(keyDir), shells = { _, _ -> tv.shell() })
    }

    private fun TestScope.connected(tv: FakeTv = FakeTv()): FireTvBackend =
        backend(tv).also {
            it.hold()
            runCurrent()
            assertEquals(Connection.Connected, it.state.value.connection)
        }

    private fun TestScope.notices(backend: FireTvBackend): List<Notice> {
        val notices = mutableListOf<Notice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { backend.notices.toList(notices) }
        return notices
    }

    @Test
    fun `an unpaired TV hears nothing until the user asks`() = runTest {
        val tv = FakeTv(trusts = false)
        val backend = backend(tv, paired = false)
        backend.hold()
        runCurrent()
        assertEquals(Connection.NeedsPairing(), backend.state.value.connection)
        assertEquals(0, tv.probes)
    }

    @Test
    fun `pairing waits on the TV's prompt`() = runTest {
        val tv = FakeTv(trusts = false)
        val backend = backend(tv, paired = false)
        backend.pair()
        runCurrent()
        assertEquals(Connection.AwaitingApproval, backend.state.value.connection)
        tv.allow()
        runCurrent()
        assertEquals(Connection.Connected, backend.state.value.connection)
        assertEquals("adb", settings.clientKey(HOST))
    }

    @Test
    fun `a paired TV connects without asking`() = runTest {
        val tv = FakeTv()
        val backend = backend(tv)
        val seen = mutableListOf<Connection>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { backend.state.collect { seen += it.connection } }
        backend.hold()
        runCurrent()
        assertEquals(Connection.Connected, backend.state.value.connection)
        assertFalse(Connection.AwaitingApproval in seen)
        assertEquals(TvKind.FIRE_TV, backend.state.value.kind)
    }

    @Test
    fun `a TV that forgot this phone asks the user before it prompts`() = runTest {
        val tv = FakeTv(trusts = false)
        val backend = backend(tv)
        backend.hold()
        runCurrent()
        assertEquals(Connection.NeedsPairing(forgotten = true), backend.state.value.connection)
        assertEquals(0, tv.connects)
        assertNull(settings.clientKey(HOST))
    }

    @Test
    fun `an unanswered prompt times out`() = runTest {
        val tv = FakeTv(trusts = false)
        val backend = backend(tv, paired = false)
        backend.pair()
        runCurrent()
        advanceTimeBy(59_000)
        assertEquals(Connection.AwaitingApproval, backend.state.value.connection)
        advanceTimeBy(2_000)
        assertEquals(Connection.NeedsPairing(timedOut = true), backend.state.value.connection)
    }

    @Test
    fun `a stalled handshake with a trusted key is a dropped connection, not a pairing timeout`() = runTest {
        val tv = FakeTv().apply { stalls = true }
        val backend = backend(tv)
        backend.hold()
        advanceTimeBy(16_000)
        assertEquals(Connection.Connecting, backend.state.value.connection)
        tv.stalls = false
        advanceTimeBy(2_000)
        assertEquals(Connection.Connected, backend.state.value.connection)
    }

    @Test
    fun `cancelling pairing stops waiting`() = runTest {
        val backend = backend(FakeTv(trusts = false), paired = false)
        backend.pair()
        runCurrent()
        backend.cancelPairing()
        runCurrent()
        assertEquals(Connection.NeedsPairing(), backend.state.value.connection)
    }

    @Test
    fun `a refused port is ADB debugging switched off, said once`() = runTest {
        val tv = FakeTv().apply { refuses = true }
        val backend = backend(tv)
        val notices = notices(backend)
        backend.hold()
        runCurrent()
        assertEquals(Connection.Connecting, backend.state.value.connection)
        assertEquals(emptyList<Notice>(), notices)
        advanceTimeBy(2_000)
        assertEquals(Connection.Offline, backend.state.value.connection)
        assertEquals(listOf(Notice.ADB_OFF), notices)
        advanceTimeBy(30_000)
        assertEquals(listOf(Notice.ADB_OFF), notices)
        tv.refuses = false
        advanceTimeBy(11_000)
        assertEquals(Connection.Connected, backend.state.value.connection)
    }

    @Test
    fun `an unreachable TV is just offline`() = runTest {
        val tv = FakeTv().apply { unreachable = true }
        val backend = backend(tv)
        val notices = notices(backend)
        backend.hold()
        advanceTimeBy(2_000)
        assertEquals(Connection.Offline, backend.state.value.connection)
        assertEquals(emptyList<Notice>(), notices)
    }

    @Test
    fun `a dead connection reconnects`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        tv.dead = true
        backend.press(TvButton.UP)
        runCurrent()
        assertEquals(Connection.Connecting, backend.state.value.connection)
        tv.dead = false
        advanceTimeBy(2_000)
        assertEquals(Connection.Connected, backend.state.value.connection)
        assertEquals(2, tv.connects)
    }

    @Test
    fun `the connection drops a minute after the last release`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        backend.release()
        advanceTimeBy(59_000)
        backend.press(TvButton.UP)
        runCurrent()
        assertEquals(listOf("input keyevent KEYCODE_DPAD_UP"), tv.inputs)
        advanceTimeBy(2_000)
        val notices = notices(backend)
        backend.press(TvButton.UP)
        assertEquals(listOf(Notice.NOT_CONNECTED), notices)
    }

    @Test
    fun `forgetting takes a new key`() = runTest {
        AdbKeys(keyDir).pair()
        val backend = connected()
        backend.forgetPairing()
        runCurrent()
        assertEquals(Connection.NeedsPairing(), backend.state.value.connection)
        assertNull(settings.clientKey(HOST))
        assertEquals(emptyList<String>(), keyDir.list()?.toList())
    }

    @Test
    fun `keys that pile up behind a slow one go out together`() = runTest {
        val tv = FakeTv().apply { gate = CompletableDeferred() }
        val backend = connected(tv)
        backend.press(TvButton.UP)
        runCurrent()
        backend.press(TvButton.UP)
        backend.press(TvButton.RIGHT)
        backend.press(TvButton.ENTER)
        runCurrent()
        tv.gate!!.complete(Unit)
        runCurrent()
        assertEquals(
            listOf("input keyevent KEYCODE_DPAD_UP", "input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER"),
            tv.inputs,
        )
    }

    @Test
    fun `a held key on a slow TV doesn't pile up without end`() = runTest {
        val tv = FakeTv().apply { gate = CompletableDeferred() }
        val backend = connected(tv)
        backend.press(TvButton.DOWN)
        runCurrent()
        repeat(30) { backend.press(TvButton.DOWN) }
        tv.gate!!.complete(Unit)
        runCurrent()
        assertEquals(2, tv.inputs.size)
        assertEquals(12, tv.inputs[1].split(" ").count { it == "KEYCODE_DPAD_DOWN" })
    }

    @Test
    fun `typing deletes and types as the TV sees it`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        backend.type(TextEdit(0, "Ąžuolas it's"))
        runCurrent()
        backend.type(TextEdit(4, "ąs"))
        runCurrent()
        assertEquals(
            listOf("input text 'Azuolas%sit'\\''s'", "input keyevent KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL; input text 'as'"),
            tv.inputs,
        )
    }

    @Test
    fun `enter comes after the typing before it`() = runTest {
        val tv = FakeTv().apply { gate = CompletableDeferred() }
        val backend = connected(tv)
        backend.press(TvButton.DOWN)
        runCurrent()
        backend.type(TextEdit(0, "cats"))
        backend.enter()
        tv.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("input keyevent KEYCODE_DPAD_DOWN", "input text 'cats'; input keyevent KEYCODE_ENTER"), tv.inputs)
    }

    @Test
    fun `volume, mute, play-pause and power are keys`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        backend.volumeUp()
        backend.setMuted(true)
        backend.playPause()
        backend.powerOff()
        runCurrent()
        assertEquals(
            listOf("input keyevent KEYCODE_VOLUME_UP KEYCODE_VOLUME_MUTE KEYCODE_MEDIA_PLAY_PAUSE KEYCODE_SLEEP"),
            tv.inputs,
        )
        assertFalse(backend.state.value.playing)
    }

    @Test
    fun `keys without a connection say so`() = runTest {
        val backend = backend(FakeTv(), paired = false)
        val notices = notices(backend)
        backend.press(TvButton.HOME)
        assertEquals(listOf(Notice.NOT_CONNECTED), notices)
    }

    @Test
    fun `waking sends the wake key`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        val notices = notices(backend)
        backend.wake()
        runCurrent()
        assertEquals(listOf("input keyevent KEYCODE_WAKEUP"), tv.inputs)
        assertEquals(listOf(Notice.WAKE_SENT), notices)
    }

    @Test
    fun `the app list arrives with the connection`() = runTest {
        val backend = connected()
        val state = backend.state.value
        assertEquals(listOf(FireTvApps.YOUTUBE, FireTvApps.NETFLIX, FireTvApps.PRIME_VIDEO, FireTvApps.SILK, "org.videolan.vlc"), state.apps.map { it.id })
        assertEquals("Netflix", state.appTitle(FireTvApps.NETFLIX))
        assertTrue(Feature.WEB_LINKS in backend.features)
        assertFalse(Feature.POINTER in backend.features)
    }

    @Test
    fun `apps start from their launcher activity`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        backend.launch(FireTvApps.NETFLIX)
        runCurrent()
        assertEquals(
            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n 'com.netflix.ninja/.MainActivity'",
            tv.commands.last(),
        )
    }

    @Test
    fun `a Fire TV Edition set offers its HDMI inputs and switches by key`() = runTest {
        val tv = FakeTv().apply { replies["pm list features"] = "feature:android.hardware.wifi\nfeature:com.amazon.hardware.tv_screen" }
        val backend = connected(tv)
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4"), backend.state.value.inputs.map { it.title })
        backend.launch(backend.state.value.inputs[1].id)
        runCurrent()
        assertTrue(tv.inputs.any { "KEYCODE_TV_INPUT_HDMI_2" in it })
    }

    @Test
    fun `a stick has no inputs`() = runTest {
        assertEquals(emptyList<TvApp>(), connected(FakeTv()).state.value.inputs)
    }

    @Test
    fun `an app without a launcher activity falls back until one works`() = runTest {
        val tv = FakeTv().apply { replies["am start"] = "Error type 3\nError: Activity class does not exist." }
        val backend = connected(tv)
        val notices = notices(backend)
        backend.launch(FireTvApps.PRIME_VIDEO)
        runCurrent()
        assertEquals(listOf("am start", "am start", "monkey"), tv.commands.drop(2).map { it.substringBefore(" -") })
        assertEquals(emptyList<Notice>(), notices)
        tv.replies["monkey"] = "** No activities found to run, monkey aborted."
        backend.launch(FireTvApps.PRIME_VIDEO)
        runCurrent()
        assertEquals(listOf(Notice.LAUNCH_FAILED), notices)
    }

    @Test
    fun `the app in front and the power state come from polling while a screen shows`() = runTest {
        val tv = FakeTv().apply { status = "  mWakefulness=Awake\n  mCurrentFocus=Window{2a1b3c4 u0 com.netflix.ninja/com.netflix.ninja.MainActivity}" }
        val backend = backend(tv)
        backend.hold()
        runCurrent()
        assertNull(backend.state.value.foregroundAppId)
        assertFalse(STATUS_COMMAND in tv.commands)
        backend.onScreenStarted()
        advanceTimeBy(4_000)
        assertEquals(FireTvApps.NETFLIX, backend.state.value.foregroundAppId)
        assertEquals(TvPower.ON, backend.state.value.power)
        tv.status = "  mWakefulness=Asleep\n  mCurrentFocus=Window{1 u0 com.amazon.tv.launcher/.ui.HomeActivity_vNext}"
        advanceTimeBy(4_000)
        assertEquals(FireTvApps.HOME, backend.state.value.foregroundAppId)
        assertFalse(backend.state.value.isUsable)
    }

    @Test
    fun `links go to their apps, pages to the browser, files nowhere`() = runTest {
        val backend = connected()
        assertEquals(listOf(LinkAction.APP, LinkAction.BROWSER), backend.actionsFor(YOUTUBE_LINK))
        assertEquals(listOf(LinkAction.APP), backend.actionsFor(NETFLIX_LINK))
        assertEquals(listOf(LinkAction.APP), backend.actionsFor(StreamingService.PRIME_VIDEO.sampleLink))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(StreamingService.DISNEY_PLUS.sampleLink))
        assertEquals(listOf(LinkAction.BROWSER), backend.actionsFor(LinkTarget.Web("https://example.com/")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkTarget.Media("http://nas/a.mkv", "video/x-matroska", "a")))
    }

    @Test
    fun `before the app list arrives every app counts as there`() = runTest {
        val backend = backend(FakeTv(), paired = false)
        assertEquals(listOf(LinkAction.APP), backend.actionsFor(StreamingService.DISNEY_PLUS.sampleLink))
        assertTrue(Feature.WEB_LINKS in backend.features)
    }

    @Test
    fun `without a browser web links aren't offered`() = runTest {
        val tv = FakeTv().apply { apps = "package:com.netflix.ninja\n@launcher\ncom.netflix.ninja/.MainActivity\n@leanback\n" }
        val backend = connected(tv)
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkTarget.Web("https://example.com/")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(YOUTUBE_LINK))
        assertEquals(setOf(Feature.TEXT_INPUT, Feature.CAPTIONS, Feature.APP_LIST, Feature.WAKE, Feature.NETFLIX_LINKS), backend.features)
    }

    @Test
    fun `opening links sends the right intents`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        assertTrue(backend.open(YOUTUBE_LINK, LinkAction.APP))
        assertEquals(viewCommand("https://www.youtube.com/watch?v=dQw4w9WgXcQ", FireTvApps.YOUTUBE, browsable = true), tv.commands.last())
        assertTrue(backend.open(YOUTUBE_LINK, LinkAction.BROWSER))
        assertEquals(viewCommand(YOUTUBE_LINK.url, FireTvApps.SILK), tv.commands.last())
        assertTrue(backend.open(NETFLIX_LINK, LinkAction.APP))
        assertEquals(netflixCommand("80057281"), tv.commands.last())
        val prime = LinkTarget.Streaming("https://www.primevideo.com/detail/0ABCDEF/", StreamingService.PRIME_VIDEO)
        assertTrue(backend.open(prime, LinkAction.APP))
        assertEquals(viewCommand(prime.url, FireTvApps.PRIME_VIDEO), tv.commands.last())
    }

    @Test
    fun `a link the TV can't open says so`() = runTest {
        val tv = FakeTv().apply { replies["am start"] = "Error: Activity not started, unable to resolve Intent" }
        val backend = connected(tv)
        val notices = notices(backend)
        assertFalse(backend.open(NETFLIX_LINK, LinkAction.APP))
        assertFalse(backend.open(StreamingService.DISNEY_PLUS.sampleLink, LinkAction.APP))
        assertEquals(listOf(Notice.OPEN_FAILED, Notice.OPEN_FAILED), notices)
    }

    @Test
    fun `opening without pairing says not connected`() = runTest {
        val backend = backend(FakeTv(), paired = false)
        val notices = notices(backend)
        assertFalse(backend.open(YOUTUBE_LINK, LinkAction.APP))
        assertEquals(listOf(Notice.NOT_CONNECTED), notices)
    }

    @Test
    fun `searches run in YouTube or the browser`() = runTest {
        val tv = FakeTv()
        val backend = connected(tv)
        assertTrue(backend.state.value.apps.first { it.id == FireTvApps.YOUTUBE }.searchable)
        assertTrue(backend.search("lo-fi beats", SearchTarget.InApp(FireTvApps.YOUTUBE)))
        assertEquals(viewCommand("https://www.youtube.com/results?search_query=lo-fi%20beats", FireTvApps.YOUTUBE, browsable = true), tv.commands.last())
        assertTrue(backend.search("lo-fi beats", SearchTarget.Web(SearchEngine.DUCKDUCKGO)))
        assertEquals(viewCommand("https://duckduckgo.com/?q=lo-fi%20beats", FireTvApps.SILK), tv.commands.last())
        assertFalse(backend.search("lo-fi beats", SearchTarget.InApp(FireTvApps.NETFLIX)))
    }

    private companion object {
        const val HOST = "10.0.0.9"
        val YOUTUBE_LINK = LinkTarget.YouTube("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ")
        val NETFLIX_LINK = LinkTarget.Netflix("https://www.netflix.com/title/80057281", "80057281")
        val APPS = """
            package:com.amazon.tv.launcher
            package:com.netflix.ninja
            package:com.amazon.firetv.youtube
            package:com.amazon.firebat
            package:com.amazon.cloud9
            package:org.videolan.vlc
            @launcher
            com.amazon.tv.launcher/.ui.HomeActivity_vNext
            com.netflix.ninja/.MainActivity
            com.amazon.firetv.youtube/dev.cobalt.app.MainActivity
            com.amazon.cloud9/.browsing.BrowserActivity
            @leanback
            org.videolan.vlc/.gui.tv.MainTvActivity
        """.trimIndent()
    }
}
