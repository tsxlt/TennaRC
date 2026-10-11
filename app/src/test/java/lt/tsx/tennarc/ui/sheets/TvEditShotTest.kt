package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.components.LocalSheetSettled
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.onboarding.FakeHub
import lt.tsx.tennarc.ui.onboarding.TvSetup
import lt.tsx.tennarc.ui.onboarding.onWifi
import lt.tsx.tennarc.ui.theme.LocalGround
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.frostBehind
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
class TvEditShotTest {
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

    private fun shot(name: String) = captureScreenRoboImage("build/shots/tvedit/$name.png")

    private fun edit(dark: Boolean) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).frostBehind()) {
                        CompositionLocalProvider(LocalGround provides MaterialTheme.colorScheme.surfaceContainerLow) {
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
        }
        compose.waitForIdle()
        compose.onNodeWithText("Smart TV Pro").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
    }

    private fun connect(dark: Boolean, settled: Boolean = false) {
        onWifi()
        val hub = FakeHub(start = Prefs(otherTvs = listOf(SavedTv("Bedroom", "192.168.1.30", TvKind.SAMSUNG, ""))))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).frostBehind(), contentAlignment = Alignment.TopCenter) {
                        CompositionLocalProvider(
                            LocalGround provides MaterialTheme.colorScheme.surfaceContainerLow,
                            LocalSheetSettled provides mutableStateOf(settled),
                        ) {
                            TvSetup(hub.state, hub.prefs, hub, onDone = {}, modifier = Modifier.padding(top = 48.dp), contentPadding = PaddingValues(bottom = 32.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("Enter IP address").performClick()
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Test fun editDark() {
        edit(dark = true)
        shot("edit_dark")
    }

    @Test fun editLight() {
        edit(dark = false)
        shot("edit_light")
    }

    @Test fun editFocused() {
        edit(dark = true)
        compose.onAllNodes(hasSetTextAction())[1].performClick()
        compose.waitForIdle()
        shot("edit_focused_dark")
    }

    @Test fun editFocusedLight() {
        edit(dark = false)
        compose.onAllNodes(hasSetTextAction())[1].performClick()
        compose.waitForIdle()
        shot("edit_focused_light")
    }

    @Test fun editInvalid() {
        edit(dark = true)
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("192.168.1")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        shot("edit_invalid_dark")
        assertEquals("192.168.1.123", prefs.host)
    }

    @Test fun editInvalidLight() {
        edit(dark = false)
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("192.168.1")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        shot("edit_invalid_light")
    }

    @Test fun connectDark() {
        connect(dark = true)
        shot("connect_dark")
    }

    @Test fun connectLight() {
        connect(dark = false)
        shot("connect_light")
    }

    @Test fun connectFocused() {
        connect(dark = true)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("192.168.1.123")
        compose.mainClock.advanceTimeBy(500)
        shot("connect_focused_dark")
    }

    @Test fun connectFocusedLight() {
        connect(dark = false)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("192.168.1.123")
        compose.mainClock.advanceTimeBy(500)
        shot("connect_focused_light")
    }

    @Test fun connectMissed() {
        connect(dark = true)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("192.168.1.9")
        compose.onNodeWithContentDescription("Connect").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        shot("connect_missed_dark")
    }

    private fun rename(dark: Boolean) {
        val tv = TvState(
            host = "192.168.1.70",
            connection = Connection.Connected,
            power = TvPower.ON,
            inputs = listOf(TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true), TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true)),
        )
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(tv, Prefs(autoKeyboard = false, tvName = "Living room"), NoRemoteActions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("HDMI 2, input").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        shot("rename_${if (dark) "dark" else "light"}")
    }

    @Test fun renameDark() = rename(dark = true)

    @Test fun renameLight() = rename(dark = false)
}
