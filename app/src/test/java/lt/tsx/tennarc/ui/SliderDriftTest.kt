package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class SliderDriftTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON)
    private val prefs = Prefs(autoKeyboard = false, tvName = "Living room")

    private fun setUp() {
        compose.setContent {
            TennaTheme(true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), requestedSheet = Sheet.SETTINGS)
                }
            }
        }
        compose.waitForIdle()
    }

    private val slider get() = compose.onNodeWithContentDescription(SENSITIVITY)

    private val sheetRoot: SemanticsNodeInteraction get() = compose.onNode(isRoot() and hasAnyDescendant(hasContentDescription(SENSITIVITY)))

    private fun bounds(): Rect = slider.fetchSemanticsNode().boundsInRoot

    private fun level(): Float = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun moveTo(to: Offset) {
        sheetRoot.performTouchInput { moveTo(to) }
        compose.waitForIdle()
    }

    private fun drift(vertical: Float, jitter: Boolean) {
        setUp()
        slider.performScrollTo()
        compose.waitForIdle()
        val before = bounds()
        val start = before.center
        val from = level()
        sheetRoot.performTouchInput { down(start) }
        compose.waitForIdle()

        val dp = compose.density.density
        var at = start
        repeat(8) {
            at += Offset(5 * dp, 0f)
            moveTo(at)
        }
        val sideways = level()
        assertNotEquals("the slider follows the finger sideways", from, sideways, 1e-4f)
        assertEquals("sideways alone moves nothing", before.top, bounds().top, 0.5f)

        val steps = 20
        repeat(steps) { i ->
            at += Offset(if (jitter) (if (i % 2 == 0) 1f else -1f) else 0f, vertical * 100 * dp / steps)
            moveTo(at)
            assertEquals("the sheet stays put mid-drag (step $i)", before.top, bounds().top, 0.5f)
        }
        repeat(4) {
            at += Offset(-5 * dp, 0f)
            moveTo(at)
        }
        assertEquals("the sheet stays put mid-drag", before.top, bounds().top, 0.5f)
        assertNotEquals("the slider still follows the finger", sideways, level(), 1e-4f)

        sheetRoot.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("the sheet stays put after letting go", before.top, bounds().top, 0.5f)
    }

    @Test fun `a drift down leaves the sheet where it is`() = drift(1f, jitter = false)

    @Test fun `a drift up leaves the page where it is`() = drift(-1f, jitter = false)

    @Test fun `a wavering drift down leaves the sheet where it is`() = drift(1f, jitter = true)

    @Test
    fun `a drag that starts down is the sheet's`() {
        setUp()
        slider.performScrollTo()
        compose.waitForIdle()
        val before = bounds()
        val from = level()
        val dp = compose.density.density
        var at = before.center
        sheetRoot.performTouchInput { down(at) }
        compose.waitForIdle()
        repeat(10) {
            at += Offset(0f, 10 * dp)
            moveTo(at)
        }
        assertTrue("the sheet or the page follows the finger down: ${before.top} to ${bounds().top}", bounds().top > before.top + 40 * dp)
        assertEquals("the slider stays where it was", from, level(), 1e-4f)
        sheetRoot.performTouchInput { up() }
        compose.waitForIdle()
    }

    private companion object {
        const val SENSITIVITY = "Sensitivity"
    }
}
