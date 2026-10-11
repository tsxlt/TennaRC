package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvKind
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
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class PickerSwitchShotTest {
    @get:Rule val compose = createComposeRule()

    private val prefs = Prefs(
        autoKeyboard = false,
        tvName = "Tenna",
        otherTvs = listOf(SavedTv("Smart TV Pro", "192.168.1.50", TvKind.ANDROID_TV, ""), SavedTv("Bedroom", "192.168.1.51", TvKind.ROKU, "")),
        demoListed = true,
    )

    private fun show() {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON), prefs, NoRemoteActions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun film(name: String, until: Long) {
        var at = 0L
        while (at <= until) {
            captureScreenRoboImage("build/shots/picker/${name}_${at.toString().padStart(3, '0')}ms.png")
            compose.mainClock.advanceTimeBy(FRAME_MS)
            compose.waitForIdle()
            at += FRAME_MS
        }
    }

    @Test fun switching() {
        show()
        compose.onNodeWithContentDescription("TVs, now Tenna").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Bedroom").performClick()
        film("switch", 320)
    }

    @Test fun switchingFar() {
        show()
        compose.onNodeWithContentDescription("TVs, now Tenna").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Demo TV").performClick()
        film("switch_far", 320)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
