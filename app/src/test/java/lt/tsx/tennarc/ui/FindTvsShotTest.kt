package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class FindTvsShotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun turningOver() {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(
                        TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON),
                        Prefs(autoKeyboard = false, tvName = "Living room"),
                        NoRemoteActions,
                        SnackbarHostState(),
                        requestedSheet = Sheet.SETTINGS,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Find TVs nearby").performScrollTo()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Find TVs nearby").performClick()
        var at = 0L
        for (step in listOf(0L, 48L, 96L, 144L, 192L, 288L)) {
            repeat(((step - at) / 16).toInt()) {
                compose.mainClock.advanceTimeBy(16)
                compose.waitForIdle()
            }
            at = step
            captureScreenRoboImage("build/shots/find/find_${step.toString().padStart(3, '0')}ms.png")
        }
    }
}
