package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import javax.imageio.ImageIO
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class TouchpadSettingsShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON)

    private val prefs = Prefs(autoKeyboard = false, tvName = "Living room", touchpadSpeed = 2.2f)

    private fun show(dark: Boolean, direction: LayoutDirection = LayoutDirection.Ltr, prefs: Prefs = this.prefs) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                TennaTheme(dark) {
                    ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                        RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), requestedSheet = Sheet.SETTINGS)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun touchpad(dark: Boolean, direction: LayoutDirection = LayoutDirection.Ltr, speed: Float? = null) {
        show(dark, direction, if (speed == null) prefs else prefs.copy(touchpadSpeed = speed))
        compose.onNodeWithText("Theme").performScrollTo()
        compose.waitForIdle()
        val name = "touchpad_${if (dark) "dark" else "light"}" + (if (direction == LayoutDirection.Rtl) "_rtl" else "") + (if (speed != null) "_through_label" else "")
        val whole = "build/shots/settings/$name.png"
        captureScreenRoboImage(whole)
        if (!File(whole).exists()) return
        val top = compose.onNodeWithText("Touchpad", ignoreCase = true).fetchSemanticsNode().boundsInWindow.top
        val bottom = compose.onNodeWithText("Theme").fetchSemanticsNode().boundsInWindow.top
        val image = ImageIO.read(File(whole))
        val y = (top - MARGIN).toInt().coerceAtLeast(0)
        val h = ((bottom - MARGIN).toInt() - y).coerceAtMost(image.height - y)
        ImageIO.write(image.getSubimage(0, y, image.width, h), "png", File("build/shots/settings/${name}_section.png"))
    }

    @Test fun touchpadDark() = touchpad(true)

    @Test fun touchpadLight() = touchpad(false)

    @Test fun touchpadRtl() = touchpad(true, LayoutDirection.Rtl)

    @Test fun throughLabelDark() = touchpad(true, speed = THROUGH_LABEL)

    @Test fun throughLabelLight() = touchpad(false, speed = THROUGH_LABEL)

    @Test fun sliderKeepsItsDetent() {
        show(dark = true)
        compose.onAllNodesWithContentDescription("Sensitivity").assertCountEquals(1)
        compose.onAllNodesWithText("Sensitivity").assertCountEquals(0)
        compose.onAllNodesWithText("Sensitivity", useUnmergedTree = true).assertCountEquals(0)
        val slider = compose.onNodeWithContentDescription("Sensitivity")
        slider.performScrollTo()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.505f) }
        compose.waitForIdle()
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.5f, range.current, 0f)
        assertEquals(0f..1f, range.range)
    }

    private companion object {
        const val MARGIN = 48f

        const val THROUGH_LABEL = 1.2f
    }
}
