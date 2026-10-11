package lt.tsx.tennarc.tv.androidtv

import lt.tsx.tennarc.tv.TvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdbCommandsTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/androidtv/$name")) { name }.readText()

    private fun inputs(dump: String, labels: String = "null", hidden: String = "null") =
        parseTvInputs("$dump\n@labels\n$labels\n@hidden\n$hidden\n")

    private val List<TvInput>.titles get() = map { it.app.title }

    @Test
    fun `a MediaTek set's HDMI ports by number, its tuner as TV, and not its spare hardware or CEC devices`() {
        val found = inputs(fixture("tv_input-mediatek-philips.txt"))
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "TV"), found.titles)
        assertEquals(
            listOf("HW5", "HW6", "HW7", "HW8", "HW0").map { "tvinput:com.mediatek.tvinput/.hdmi.HDMIInputService/$it" }.take(4) +
                "tvinput:com.mediatek.tvinput/.tuner.TunerInputService/HW0",
            found.map { it.app.id },
        )
        assertEquals(listOf(1, 2, 3, 4, null), found.map { it.hdmiPort })
        assertTrue(found.all { it.app.input })
        assertTrue(found.none { it.app.plugged == false })
    }

    @Test
    fun `a soundbar's ARC and AUX are inputs too, named from their service`() {
        val found = inputs(fixture("tv_input-amlogic-soundbar.txt"))
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3", "SPDIF", "AUX", "ARC"), found.titles)
        assertEquals("tvinput:com.droidlogic.tvinput/.services.ArcInputService/HW18", found.last().app.id)
    }

    @Test
    fun `a projector with two HDMI ports and an app's channels`() {
        val found = inputs(fixture("tv_input-xgimi.txt"))
        assertEquals(listOf("HDMI 1", "HDMI 2"), found.titles)
        assertEquals("tvinput:com.xgimi.tvinput/.inputservice.Hdmi1TvInputService/HW1", found.first().app.id)
    }

    @Test
    fun `names given in the TV's settings, and inputs hidden there`() {
        val labels = "com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW5,Apple%20TV:com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW7,PS5%3A%20Pro"
        val hidden = "com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW8"
        val found = inputs(fixture("tv_input-mediatek-philips.txt"), labels, hidden)
        assertEquals(listOf("Apple TV", "HDMI 2", "PS5: Pro", "TV"), found.titles)
    }

    @Test
    fun `a TV that goes around the hardware manager is read from its ids`() {
        val dump = """
            User Ids (Current user: 0):
              0
            UserState (0):
              inputMap: inputId -> TvInputState
                ${state("com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384", 0)}
                ${state("com.tcl.tvinput/.TDTVInputService/HW1413742848", 0)}
                ${state("com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128", 0)}
                ${state("com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104", 2)}
                ${state("com.vt.source.external/.hdmi.HdmiTvInputService/HW3", 0)}
                ${state("com.google.android.videos/.tv.usecase.tvinput.playback.TvInputService", 0)}
            TvInputHardwareManager Info:
              mConnections: deviceId -> Connection
              mHardwareInputIdMap: deviceId -> inputId
              mHdmiInputIdMap: id -> inputId
        """.trimIndent()
        val found = inputs(dump)
        assertEquals(listOf("HDMI 1", "Input 1", "Input 2", "Input 3", "TV"), found.titles)
        assertEquals(listOf(null, false, null, null, null), found.map { it.app.plugged })
    }

    @Test
    fun `only the inputs of the user in front`() {
        val dump = """
            User Ids (Current user: 10):
              0
              10
            UserState (0):
              inputMap: inputId -> TvInputState
                ${state("com.example.tvinput/.Hdmi1InputService/HW1", 0)}
            UserState (10):
              inputMap: inputId -> TvInputState
                ${state("com.example.tvinput/.Hdmi2InputService/HW2", 0)}
        """.trimIndent()
        assertEquals(listOf("HDMI 2"), inputs(dump).titles)
    }

    @Test
    fun `a streamer, or a shell that says nothing useful, has no inputs`() {
        val streamer = """
            User Ids (Current user: 0):
              0
            UserState (0):
              inputMap: inputId -> TvInputState
                ${state("com.google.android.videos/.tv.usecase.tvinput.playback.TvInputService", 0)}
            TvInputHardwareManager Info:
              mConnections: deviceId -> Connection
              mHardwareInputIdMap: deviceId -> inputId
        """.trimIndent()
        assertEquals(emptyList<TvApp>(), inputs(streamer).map { it.app })
        assertEquals(emptyList<TvApp>(), parseTvInputs("/system/bin/sh: dumpsys: inaccessible or not found").map { it.app })
        assertEquals(emptyList<TvApp>(), parseTvInputs("").map { it.app })
    }

    @Test
    fun `an input opens by its passthrough URI, the id one encoded path segment`() {
        val id = "com.tcl.tvinput/.TvPassThroughService/HW15"
        assertEquals("content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHW15", passthroughUri(id))
        assertEquals(
            "am start -n 'com.tcl.tv/.TVActivity' -a android.intent.action.VIEW -d 'content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHW15'",
            passthroughCommand(id, "com.tcl.tv/.TVActivity"),
        )
        assertEquals(
            "am start -a android.intent.action.VIEW -d 'content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHW15'",
            passthroughCommand(id, null),
        )
        assertEquals(id, inputIdOf("tvinput:$id"))
        assertEquals(null, inputIdOf("input:hdmi1"))
    }

    @Test
    fun `the TV maker's live TV app opens inputs before Google's`() {
        val output = """
            com.google.android.tv/com.android.tv.MainActivity
            org.droidtv.playtv/.PlayTvActivity
        """.trimIndent()
        assertEquals(listOf("org.droidtv.playtv/.PlayTvActivity", "com.google.android.tv/com.android.tv.MainActivity"), inputHandlers(output))
        assertEquals(emptyList<String>(), inputHandlers("No activities found"))
    }

    @Test
    fun `am start's own words say whether it started`() {
        assertTrue(started("Starting: Intent { act=android.intent.action.VIEW dat=content://android.media.tv/... }"))
        assertTrue(started("Starting: Intent { act=android.intent.action.VIEW }\nWarning: Activity not started, intent has been delivered to currently running top-most instance."))
        assertFalse(started("Starting: Intent { act=android.intent.action.VIEW }\nError: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW }"))
    }

    @Test
    fun `labels and hidden inputs that were never set`() {
        assertEquals(emptyMap<String, String>(), parseCustomLabels("null"))
        assertEquals(emptySet<String>(), parseHiddenInputs(""))
        assertEquals(mapOf("a/b" to "Box"), parseCustomLabels("a%2Fb,Box:c%2Fd,"))
    }

    @Test
    fun `the home screen's apps, curated ones as curated, system parts left out`() {
        val output = """
            com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity
            com.netflix.ninja/.MainActivity
            com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity
            org.xbmc.kodi/.Splash
            com.liskovsoft.smarttubetv.beta/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
            tv.pluto.android/.leanback.controller.LeanbackSplashOnboardActivity
            com.google.android.tungsten.setupwraith/.MainActivity
            com.netflix.ninja/.MainActivity
        """.trimIndent()
        val apps = parseTvApps(output)
        assertEquals(
            listOf(AndroidTvApps.YOUTUBE, AndroidTvApps.NETFLIX, "org.xbmc.kodi", "tv.pluto.android", "com.liskovsoft.smarttubetv.beta"),
            apps.map { it.id },
        )
        assertEquals(listOf("YouTube", "Netflix", "Kodi", "Pluto TV", "SmartTube"), apps.map { it.title })
        assertEquals(AndroidTvApps.curated.first(), apps.first())
        assertEquals("store://org.xbmc.kodi", apps[2].iconUrl)
    }

    @Test
    fun `no cmd, no apps, and the curated list stays`() {
        assertEquals(emptyList<TvApp>(), parseTvApps("/system/bin/sh: cmd: not found"))
        assertEquals(emptyList<TvApp>(), parseTvApps("No activities found"))
    }

    @Test
    fun `which maker's backlight a TV has, if either`() {
        assertEquals(TvBacklight.MediaTek, TvBacklight.find("45\n2\nService tcl_tv_display: not found"))
        assertEquals(null, TvBacklight.find("45\nnull\nService tcl_tv_display: not found"))
        assertEquals(TvBacklight.Tcl, TvBacklight.find("null\nnull\nService tcl_tv_display: found"))
        assertEquals(null, TvBacklight.find("null\nnull\nService tcl_tv_display: not found"))
        assertEquals(null, TvBacklight.find(""))
    }

    @Test
    fun `backlight levels as each maker's way reads them`() {
        assertEquals(45, TvBacklight.MediaTek.parse("45\n"))
        assertEquals(null, TvBacklight.MediaTek.parse("null"))
        assertEquals(null, TvBacklight.MediaTek.parse("512"))
        assertEquals(50, TvBacklight.Tcl.parse("Result: Parcel(00000000 00000032   '........2...')"))
        assertEquals(null, TvBacklight.Tcl.parse("Result: Parcel(ffffffec 00000000   '........')"))
        assertEquals(null, TvBacklight.Tcl.parse("service: Service tcl_tv_display does not exist"))
        assertEquals("service call tcl_tv_display 100 i32 0 i32 70 i32 0", TvBacklight.Tcl.command(70))
        assertTrue(TvBacklight.Tcl.took("Result: Parcel(00000000    '....')"))
        assertFalse(TvBacklight.Tcl.took("Result: Parcel(ffffffb5    '....')"))
        assertEquals("settings put global picture_backlight 70", TvBacklight.MediaTek.command(70))
        assertTrue(TvBacklight.MediaTek.took(""))
    }

    private fun state(id: String, state: Int): String {
        val pkg = id.substringBefore('/')
        val service = id.substringAfter('/').substringBefore('/').let { if (it.startsWith(".")) pkg + it else it }
        return "$id: info: TvInputInfo{id=$id, pkg=$pkg, service=$service}; state: $state"
    }
}
