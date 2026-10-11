package lt.tsx.tennarc.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class TvPickTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aTapThatDriftsStillPicksTheTv() {
        compose.mainClock.autoAdvance = false
        onWifi()
        val hub = FakeHub(listOf(500L to philipsTv), endless = true)
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize()) {
                        RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, modifier = Modifier.clearAndSetSemantics {})
                        FirstRun(hub.prefs, hub.state, hub)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(philipsTv.name).performTouchInput {
            down(center)
            moveBy(Offset(3f, 2f))
            up()
        }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(philipsTv.host, hub.prefs.host)
    }

    @Test fun theRemoteBeneathGetsNoTouches() {
        compose.mainClock.autoAdvance = false
        onWifi()
        val hub = FakeHub(endless = true, start = Prefs(tvChosen = true, host = philipsTv.host))
        hub.state = hub.state.copy(connection = Connection.Connected)
        val pressed = mutableListOf<TvButton>()
        val actions = object : RemoteActions by hub {
            override fun press(button: TvButton) {
                pressed += button
            }
        }
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize()) {
                        RemoteScreen(hub.state, hub.prefs, actions, remember { SnackbarHostState() })
                        FirstRun(hub.prefs.copy(tvChosen = false), hub.state, actions)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().performTouchInput {
            for (y in listOf(0.5f, 0.65f, 0.8f, 0.92f)) {
                down(percentOffset(0.5f, y))
                moveBy(Offset(2f, 1f))
                up()
            }
        }
        compose.mainClock.advanceTimeBy(500)
        assertTrue("pressed $pressed", pressed.isEmpty())
    }
}
