package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class SwipeTickTest {
    @get:Rule val compose = createComposeRule()

    private val ticks = mutableListOf<HapticFeedbackType>()

    private fun setUp() {
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackType == HapticFeedbackType.SegmentTick) ticks += hapticFeedbackType
            }
        }
        val state = TvState(
            host = "192.168.1.70",
            connection = Connection.Connected,
            power = TvPower.ON,
            apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
        )
        compose.setContent {
            TennaTheme {
                CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                    ProvideTennaEnvironment(haptics = true, iconLoader = IconLoader { null }) {
                        RemoteScreen(state, Prefs(autoKeyboard = false), NoRemoteActions, SnackbarHostState())
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.flick(up: Boolean = true, step: Int = 20) = performTouchInput {
        down(center)
        repeat(5) {
            advanceEventTime(6)
            moveBy(Offset(0f, (if (up) -step else step).dp.toPx()))
        }
        advanceEventTime(6)
        up()
    }

    private fun SemanticsNodeInteraction.drag(up: Boolean = true) = performTouchInput {
        down(center)
        repeat(30) {
            advanceEventTime(40)
            moveBy(Offset(0f, (if (up) -20 else 20).dp.toPx()))
        }
        advanceEventTime(400)
        up()
    }

    @Test fun flickingMoreOpenTicksOnce() {
        setUp()
        compose.onNodeWithContentDescription("Back").flick()
        compose.waitForIdle()
        assertEquals(1, ticks.size)
    }

    @Test fun draggingMoreOpenTicksOnce() {
        setUp()
        compose.onNodeWithContentDescription("Back").drag()
        compose.waitForIdle()
        assertEquals(1, ticks.size)
    }

    @Test fun flickingTheAppsOpenTicksOnce() {
        setUp()
        compose.onNodeWithContentDescription("YouTube").flick(up = false, step = 6)
        compose.waitForIdle()
        assertEquals(1, ticks.size)
    }

    @Test fun draggingTheAppsOpenTicksOnce() {
        setUp()
        compose.onNodeWithContentDescription("YouTube").drag(up = false)
        compose.waitForIdle()
        assertEquals(1, ticks.size)
    }
}
