package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.frostBehind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h600dp-xxhdpi", fontScale = 1.15f)
class TvListShotTest {
    @get:Rule val compose = createComposeRule()

    private var prefs by mutableStateOf(
        Prefs(
            tvName = "Smart TV Pro",
            host = "192.168.1.123",
            kind = TvKind.ANDROID_TV,
            otherTvs = listOf(SavedTv("Tenna", "192.168.1.70", TvKind.WEBOS, "")),
            demoListed = true,
            tvOrder = listOf("192.168.1.123", "192.168.1.70", "demo"),
        ),
    )

    private fun show(dark: Boolean = true) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).frostBehind()) {
                        SettingsSheetContent(
                            prefs = prefs,
                            onUpdate = { change -> prefs = change(prefs) },
                            onFindTvs = { emptyList() },
                            onForgetPairing = {},
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = captureScreenRoboImage("build/shots/tvlist/$name.png")

    @Test fun rest() {
        show()
        shot("rest_dark")
    }

    @Test fun restLight() {
        show(dark = false)
        shot("rest_light")
    }

    @Test fun pressed() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tenna").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(220)
        shot("pressed_dark")
    }

    @Test fun switching() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tenna").performClick()
        compose.mainClock.advanceTimeBy(96)
        shot("switching_dark")
        compose.mainClock.advanceTimeBy(1_000)
        shot("switched_dark")
    }

    @Test fun switchingLight() {
        show(dark = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tenna").performClick()
        compose.mainClock.advanceTimeBy(96)
        shot("switching_light")
    }

    @Test fun lifted() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Smart TV Pro").performTouchInput {
            down(center)
            advanceEventTime(700)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Smart TV Pro").performTouchInput { repeat(6) { moveBy(Offset(0f, 10f)) } }
        compose.mainClock.advanceTimeBy(500)
        shot("lifted_dark")
    }

    @Test fun liftedLight() {
        show(dark = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Smart TV Pro").performTouchInput {
            down(center)
            advanceEventTime(700)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Smart TV Pro").performTouchInput { repeat(6) { moveBy(Offset(0f, 10f)) } }
        compose.mainClock.advanceTimeBy(500)
        shot("lifted_light")
    }

    @Test fun menu() {
        show()
        compose.onNodeWithText("Tenna").performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        compose.waitForIdle()
        shot("menu_dark")
    }

    @Test fun picker() {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(TvState(host = prefs.host, connection = Connection.Connected, power = TvPower.ON), prefs, NoRemoteActions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("TVs, now Smart TV Pro").performClick()
        compose.waitForIdle()
        shot("picker_dark")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tenna").performClick()
        compose.mainClock.advanceTimeBy(96)
        shot("picker_switching_dark")
    }
}
