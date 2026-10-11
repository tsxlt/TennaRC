package lt.tsx.tennarc.ui.components

import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.hdmiInputs
import org.junit.Assert.assertEquals
import org.junit.Test
import lt.tsx.tennarc.tv.androidtv.inputKey as androidTvInputKey
import lt.tsx.tennarc.tv.firetv.inputKey as fireTvInputKey

class InputsTest {
    private val youtube = TvApp(TvApps.YOUTUBE, "YouTube")
    private val pc = TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true)
    private val console = TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = null)
    private val empty = TvApp("com.webos.app.hdmi3", "HDMI 3", input = true, plugged = false)

    @Test fun fullListLeavesOutInputsWithNothingPluggedIn() {
        assertEquals(listOf(youtube, pc, console), sortedApps(listOf(youtube), listOf(pc, console, empty)))
    }

    @Test fun keyedInputsMapToTheirKeys() {
        val ids = hdmiInputs { n -> "input:hdmi$n" }.map { it.id }
        assertEquals(listOf(243, 244, 245, 246), ids.map { androidTvInputKey(it) })
        assertEquals("KEYCODE_TV_INPUT_HDMI_2", fireTvInputKey(ids[1]))
        assertEquals(null, androidTvInputKey(TvApps.YOUTUBE))
        assertEquals(null, fireTvInputKey("input:hdmi9"))
    }

    @Test fun sticksHaveNoInputs() {
        assertEquals(false, lt.tsx.tennarc.tv.androidtv.hasInputs("Google", "Chromecast"))
        assertEquals(false, lt.tsx.tennarc.tv.androidtv.hasInputs("NVIDIA", "SHIELD Android TV"))
        assertEquals(true, lt.tsx.tennarc.tv.androidtv.hasInputs("Sony", "BRAVIA 4K VH2"))
    }
}
