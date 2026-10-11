package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class PadMorphTouchTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
    )
    private val prefs = Prefs(autoKeyboard = false, tvName = "Living room")

    private class Sent : RemoteActions by NoRemoteActions {
        val presses = mutableListOf<TvButton>()
        var clicks = 0
        override fun press(button: TvButton) {
            presses += button
        }
        override fun pointerClick() {
            clicks++
        }
    }

    private lateinit var sent: Sent

    @Before fun setUp() {
        sent = Sent()
    }

    private fun show(mode: PadMode) {
        compose.setContent {
            TennaTheme(dark = true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, sent, SnackbarHostState(), initialMode = mode)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun morphFor(ms: Long) = repeat((ms / FRAME_MS).toInt()) {
        compose.mainClock.advanceTimeBy(FRAME_MS)
        compose.waitForIdle()
    }

    private lateinit var pad: Rect

    private fun readPad() {
        pad = compose.onNodeWithContentDescription("Touchpad. Drag", substring = true).fetchSemanticsNode().boundsInRoot
    }

    private fun tapDiscCentre() = compose.onRoot().performTouchInput { click(pad.center) }

    private fun tapPaneCorner() = compose.onRoot().performTouchInput {
        click(Offset(pad.left + pad.width * 0.2f, pad.top + pad.height * 0.25f))
    }

    @Test fun touchpadToArrows_onlyTheDiscTakesTapsMidMorph() {
        show(PadMode.TOUCHPAD)
        readPad()
        compose.onNodeWithContentDescription("Arrows").performClick()
        compose.waitForIdle()
        morphFor(MID_MORPH_MS)

        tapPaneCorner()
        compose.waitForIdle()
        assertEquals(0, sent.clicks)
        assertTrue(sent.presses.isEmpty())

        tapDiscCentre()
        compose.waitForIdle()
        assertEquals(0, sent.clicks)
        assertTrue(sent.presses.isNotEmpty())
    }

    @Test fun arrowsToTouchpad_onlyTheTouchpadTakesTapsMidMorph() {
        show(PadMode.TOUCHPAD)
        readPad()
        compose.onNodeWithContentDescription("Arrows").performClick()
        compose.waitForIdle()
        morphFor(SETTLED_MS)
        compose.onNodeWithContentDescription("Touchpad").performClick()
        compose.waitForIdle()
        morphFor(MID_MORPH_MS)

        tapDiscCentre()
        compose.waitForIdle()
        assertTrue(sent.presses.isEmpty())
        val clicksAtCentre = sent.clicks

        tapPaneCorner()
        compose.waitForIdle()
        assertTrue(sent.presses.isEmpty())
        assertTrue(sent.clicks > clicksAtCentre)
    }

    private companion object {
        const val FRAME_MS = 16L

        const val MID_MORPH_MS = 96L

        const val SETTLED_MS = 1000L
    }
}
