package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.KnownApps
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.firetv.AdbPortClosedException
import lt.tsx.tennarc.tv.firetv.AdbShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.io.IOException
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTvAdbTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var settings: Settings
    private val notices = mutableListOf<Notice>()

    @Before
    fun setUp() {
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.setClientKey(adbKey(HOST), AdbLink.ADB_PORT.toString())
    }

    private inner class FakeTv {
        var unreachable = false
        var refuses = false
        var backlight = 40
        var takes = true
        var probe = "40\n0\nService tcl_tv_display: not found"
        var backlightRead: (() -> String)? = null
        val commands = mutableListOf<String>()

        fun shell() = object : AdbShell {
            override suspend fun trusted(): Boolean {
                if (unreachable) throw IOException("No route to host")
                if (refuses) throw AdbPortClosedException(HOST)
                return true
            }

            override suspend fun connect() = Unit

            override suspend fun shell(command: String): String {
                if (unreachable) throw IOException("Connection reset")
                commands += command
                return when {
                    command == INPUTS_COMMAND -> javaClass.getResource("/androidtv/tv_input-mediatek-philips.txt")!!.readText() + "\n@labels\nnull\n@hidden\nnull"
                    command == APPS_COMMAND -> "com.netflix.ninja/.MainActivity\norg.xbmc.kodi/.Splash"
                    command == INPUT_HANDLERS_COMMAND -> "org.droidtv.playtv/.PlayTvActivity"
                    command == TvBacklight.PROBE -> probe
                    command == TvBacklight.MediaTek.readCommand -> backlight.toString()
                    command == TvBacklight.Tcl.readCommand -> backlightRead!!()
                    command.startsWith("settings put global picture_backlight ") -> {
                        if (!takes) return "Error: bad setting"
                        backlight = command.substringAfterLast(' ').toInt()
                        ""
                    }
                    command.startsWith("am start") -> "Starting: Intent { act=android.intent.action.VIEW }"
                    else -> ""
                }
            }

            override fun close() = Unit
        }
    }

    private fun TestScope.adb(tv: FakeTv, known: KnownApps? = null): Pair<AdbLink, AndroidTvAdb> {
        val link = AdbLink(settings, HOST, backgroundScope, { _, _ -> tv.shell() }, AdbPorts.None, Random(1))
        return link to AndroidTvAdb(link, backgroundScope, HOST, known, notify = { notices += it })
    }

    private fun TestScope.up(link: AdbLink) {
        link.remoteUp(HOST)
        advanceTimeBy(AdbLink.START_DELAY_MS + 1)
        runCurrent()
    }

    @Test
    fun `once up, the TV's own inputs, apps and backlight, with the features that go with them`() = runTest {
        val tv = FakeTv()
        val (link, adb) = adb(tv)
        assertEquals(emptySet<Feature>(), adb.state.value.features)
        up(link)
        val reading = adb.state.value
        assertEquals(DevLink.Up, reading.link)
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "TV"), reading.inputs?.map { it.title })
        assertEquals(listOf(AndroidTvApps.NETFLIX, "org.xbmc.kodi"), reading.apps?.map { it.id })
        assertEquals(TvPicture(null, 40), reading.picture)
        assertEquals(setOf(Feature.APP_LIST, Feature.PICTURE), reading.features)
    }

    @Test
    fun `the lists outlast the link, the backlight goes with it`() = runTest {
        val tv = FakeTv()
        val (link, adb) = adb(tv)
        up(link)
        tv.unreachable = true
        assertNull(link.call("echo"))
        advanceTimeBy(AdbLink.FIRST_RETRY_MS + AdbLink.RETRY_JITTER_MS)
        assertEquals(DevLink.Down(), adb.state.value.link)
        assertEquals(5, adb.state.value.inputs?.size)
        assertNotNull(adb.state.value.apps)
        assertEquals(emptySet<Feature>(), adb.state.value.features)
        tv.unreachable = false
        tv.refuses = true
        advanceTimeBy(10 * 60_000)
        assertEquals(DevLink.Down(DevProblem.DEBUGGING_OFF), adb.state.value.link)
        assertNull(adb.state.value.picture)
        assertEquals(5, adb.state.value.inputs?.size)
    }

    @Test
    fun `after a restart, last time's lists are there before the link is`() = runTest {
        val dir = folder.newFolder("tv-adb")
        val tv = FakeTv()
        val (link, _) = adb(tv, KnownApps(dir, backgroundScope))
        up(link)
        link.close()
        awaitFile(dir)
        val (_, again) = adb(FakeTv().apply { unreachable = true }, KnownApps(dir, backgroundScope))
        assertEquals(DevLink.Down(), again.state.value.link)
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "TV"), again.state.value.inputs?.map { it.title })
        assertEquals(emptySet<Feature>(), again.state.value.features)
    }

    @Test
    fun `turning it off forgets the lists, here and on disk`() = runTest {
        val dir = folder.newFolder("tv-adb")
        val tv = FakeTv()
        val (link, adb) = adb(tv, KnownApps(dir, backgroundScope))
        up(link)
        link.turnOff()
        runCurrent()
        assertEquals(AndroidTvAdb.Reading(DevLink.Off), adb.state.value)
        assertTrue(eventually { KnownApps(dir, backgroundScope).get(HOST).let { it == null || it.inputs.isEmpty() && it.apps.isEmpty() } })
    }

    @Test
    fun `an input opens through the TV's own live TV app, asked for once`() = runTest {
        val tv = FakeTv()
        val (link, adb) = adb(tv)
        up(link)
        val id = adb.state.value.inputs!![1].id
        assertEquals(true, adb.switchInput(id))
        assertEquals(true, adb.switchInput(id))
        assertEquals(1, tv.commands.count { it == INPUT_HANDLERS_COMMAND })
        assertEquals(
            "am start -n 'org.droidtv.playtv/.PlayTvActivity' -a android.intent.action.VIEW -d 'content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW6'",
            tv.commands.last(),
        )
        assertTrue(adb.onlyHandler)
        link.turnOff()
        assertNull(adb.switchInput(id))
    }

    @Test
    fun `the backlight shows at once, then as the TV has it`() = runTest {
        val tv = FakeTv()
        val (link, adb) = adb(tv)
        up(link)
        adb.setBacklight(70)
        assertEquals(TvPicture(null, 70), adb.state.value.picture)
        runCurrent()
        assertEquals(70, tv.backlight)
        tv.takes = false
        adb.setBacklight(10)
        runCurrent()
        assertEquals(TvPicture(null, 70), adb.state.value.picture)
        assertEquals(listOf(Notice.COMMAND_FAILED), notices)
    }

    @Test
    fun `TCL's backlight through its display service, and none where its SELinux says no`() = runTest {
        val tcl = FakeTv().apply {
            probe = "null\nnull\nService tcl_tv_display: found"
            backlightRead = { "Result: Parcel(00000000 00000032   '........2...')" }
        }
        val (link, adb) = adb(tcl)
        up(link)
        assertEquals(TvPicture(null, 50), adb.state.value.picture)
        link.close()

        val denied = FakeTv().apply {
            probe = "null\nnull\nService tcl_tv_display: found"
            backlightRead = { "Result: Parcel(ffffffff    '....')" }
        }
        val (deniedLink, deniedAdb) = adb(denied)
        up(deniedLink)
        assertNull(deniedAdb.state.value.picture)
        assertTrue(Feature.PICTURE !in deniedAdb.state.value.features)
    }

    @Test
    fun `a TV with neither maker's backlight offers none, and isn't asked again on every refresh`() = runTest {
        val tv = FakeTv().apply { probe = "null\nnull\nService tcl_tv_display: not found" }
        val (link, adb) = adb(tv)
        up(link)
        adb.refresh()
        runCurrent()
        assertNull(adb.state.value.picture)
        assertEquals(1, tv.commands.count { it == TvBacklight.PROBE })
        assertEquals(2, tv.commands.count { it == INPUTS_COMMAND })
    }

    private fun eventually(done: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + 5_000
        while (!done()) {
            if (System.currentTimeMillis() > until) return false
            Thread.sleep(20)
        }
        return true
    }

    private fun awaitFile(dir: java.io.File) = eventually { dir.listFiles().orEmpty().any { it.name.endsWith(".json") } }

    private companion object {
        const val HOST = "192.0.2.20"
    }
}
