package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
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
class PadMorphShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
    )
    private val prefs = Prefs(autoKeyboard = false, tvName = "Living room")

    private fun show(mode: PadMode) {
        compose.setContent {
            TennaTheme(dark = true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), initialMode = mode)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun frames(ms: Long) = repeat((ms / FRAME_MS).toInt()) {
        compose.mainClock.advanceTimeBy(FRAME_MS)
        compose.waitForIdle()
    }

    private fun film(name: String, from: PadMode, label: String) {
        show(from)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/padmorph/${name}_0ms.png")
        for (step in 1..FRAMES) {
            frames(FRAME_MS)
            captureScreenRoboImage("build/shots/padmorph/${name}_${step * FRAME_MS}ms.png")
        }
    }

    @Test fun touchpadToArrows() = film("to_dpad", PadMode.TOUCHPAD, label = "Arrows")

    @Test fun arrowsToTouchpad() = film("to_touchpad", PadMode.DPAD, label = "Touchpad")

    private companion object {
        const val FRAME_MS = 16L

        const val FRAMES = 40
    }
}
