package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.DECK_HANDLE_TAG
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class TrackingActions : RemoteActions by NoRemoteActions {
    val presses = mutableListOf<TvButton>()
    override fun press(button: TvButton) {
        presses += button
    }
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class MoreSwipeShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )
    private val prefs = Prefs(autoKeyboard = false)

    private fun setUp(actions: RemoteActions = NoRemoteActions) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, actions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `follows the finger up, a frame at a time`() {
        setUp()
        val back = compose.onNodeWithContentDescription("Back")
        back.performTouchInput { down(center) }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/phone/30_more_swipe_0_down.png")

        listOf(40.dp to "1_slip", 140.dp to "2_quarter", 320.dp to "3_half", 560.dp to "4_most").forEach { (pull, label) ->
            back.performTouchInput { moveTo(center - Offset(0f, pull.toPx())) }
            compose.waitForIdle()
            captureScreenRoboImage("build/shots/phone/30_more_swipe_$label.png")
        }
        back.performTouchInput { up() }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/phone/30_more_swipe_5_settled_open.png")
    }

    @Test
    fun `a short slow drag falls back shut`() {
        setUp()
        val back = compose.onNodeWithContentDescription("Back")
        back.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 60.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/phone/30_more_swipe_6_settled_shut.png")
    }

    @Test
    fun `a press that becomes a swipe sends no key`() {
        val actions = TrackingActions()
        setUp(actions)
        val back = compose.onNodeWithContentDescription("Back")
        back.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 400.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        assertTrue("fired ${actions.presses}", actions.presses.isEmpty())
    }

    @Test
    fun `dragging the handle down closes it again`() {
        setUp()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        val openTop = handle.fetchSemanticsNode().boundsInRoot.top
        captureScreenRoboImage("build/shots/phone/30_more_swipe_7_handle_open.png")

        handle.performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 500.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/phone/30_more_swipe_8_handle_dragged_shut.png")

        val closedTop = handle.fetchSemanticsNode().boundsInRoot.top
        assertTrue("handle should have dropped well clear: open=$openTop closed=$closedTop", closedTop > openTop + 300)
    }

    @Test
    fun `a tap outside the grown panel closes it`() {
        setUp()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        val openTop = handle.fetchSemanticsNode().boundsInRoot.top

        compose.onRoot().performTouchInput { click(Offset(center.x, 40.dp.toPx())) }
        compose.waitForIdle()

        val closedTop = handle.fetchSemanticsNode().boundsInRoot.top
        assertTrue("handle should have dropped well clear: open=$openTop closed=$closedTop", closedTop > openTop + 300)
    }

    @Test
    fun `a swipe down from above the grown panel closes it`() {
        setUp()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        val openTop = handle.fetchSemanticsNode().boundsInRoot.top

        compose.onRoot().performTouchInput {
            down(Offset(center.x, 40.dp.toPx()))
            repeat(12) { moveBy(Offset(0f, 30.dp.toPx())) }
            up()
        }
        compose.waitForIdle()

        val closedTop = handle.fetchSemanticsNode().boundsInRoot.top
        assertTrue("handle should have dropped well clear: open=$openTop closed=$closedTop", closedTop > openTop + 300)
    }

    @Test
    fun `caught while still rising, it goes back down with the finger`() {
        setUp()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("More").performClick()
        repeat(5) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
        }
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        val caughtTop = handle.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, 30.dp.toPx())) }
        }
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        val heldTop = handle.fetchSemanticsNode().boundsInRoot.top
        assertTrue("the handle should follow the finger down: caught=$caughtTop held=$heldTop", heldTop > caughtTop + 100)
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val restTop = handle.fetchSemanticsNode().boundsInRoot.top
        assertTrue("let go low, it should shut: caught=$caughtTop rest=$restTop", restTop > caughtTop + 100)
    }

    @Test
    fun `the handle keeps up with a finger fixed on the screen`() {
        setUp()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        val openTop = handle.fetchSemanticsNode().boundsInRoot.top
        val grab = handle.fetchSemanticsNode().boundsInRoot.center
        val step = with(compose.density) { 12.dp.toPx() }
        compose.onRoot().performTouchInput { down(grab) }
        compose.waitForIdle()
        for (i in 1..20) {
            compose.onRoot().performTouchInput { moveTo(grab + Offset(0f, step * i)) }
            compose.waitForIdle()
        }
        val heldTop = handle.fetchSemanticsNode().boundsInRoot.top
        val travelled = step * 20
        assertTrue("held: open=$openTop held=$heldTop, finger went $travelled", heldTop - openTop > travelled - with(compose.density) { 16.dp.toPx() })
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun `a plain tap on the key still fires it`() {
        val actions = TrackingActions()
        setUp(actions)
        compose.onNodeWithContentDescription("Back").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(TvButton.BACK), actions.presses)
    }
}
