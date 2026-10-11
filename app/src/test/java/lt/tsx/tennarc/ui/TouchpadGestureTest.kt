package lt.tsx.tennarc.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.data.ScrollStrip
import lt.tsx.tennarc.ui.components.Touchpad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TouchpadGestureTest {
    @get:Rule val compose = createComposeRule()

    private val moves = mutableListOf<Offset>()
    private val scrolls = mutableListOf<Offset>()
    private var clicks = 0

    private val pad get() = compose.onNodeWithTag("pad")

    private fun touchpad() {
        compose.setContent {
            Touchpad(
                onMove = { dx, dy -> moves += Offset(dx, dy) },
                onScroll = { dx, dy -> scrolls += Offset(dx, dy) },
                onClick = { clicks++ },
                speed = 1f,
                naturalScroll = false,
                enabled = true,
                modifier = Modifier.size(300.dp, 400.dp).testTag("pad"),
                strip = ScrollStrip.RIGHT,
            )
        }
    }

    @Test
    fun `a drag down the strip scrolls down`() {
        touchpad()
        pad.performTouchInput {
            down(Offset(width - 10.dp.toPx(), 60.dp.toPx()))
            repeat(12) { moveBy(Offset(0f, 10.dp.toPx())) }
            up()
        }
        compose.waitForIdle()
        assertTrue("moved the pointer: $moves", moves.isEmpty())
        assertTrue("scrolled sideways: $scrolls", scrolls.all { it.x == 0f })
        val steps = scrolls.sumOf { it.y.toDouble() }
        assertTrue("scrolled $steps steps", steps >= 10)
    }

    @Test
    fun `a drag across the strip moves the pointer`() {
        touchpad()
        pad.performTouchInput {
            down(Offset(width - 10.dp.toPx(), 200.dp.toPx()))
            repeat(8) { moveBy(Offset(-10.dp.toPx(), 2.dp.toPx())) }
            up()
        }
        compose.waitForIdle()
        assertTrue("scrolled: $scrolls", scrolls.isEmpty())
        assertTrue("moved: $moves", moves.sumOf { it.x.toDouble() } < -50)
    }

    @Test
    fun `a tap that stops a spinning wheel doesn't click`() {
        touchpad()
        compose.mainClock.autoAdvance = false
        pad.performTouchInput {
            down(Offset(width - 10.dp.toPx(), 40.dp.toPx()))
            repeat(8) { moveBy(Offset(0f, 30.dp.toPx())) }
            up()
        }
        compose.mainClock.advanceTimeBy(100)
        pad.performTouchInput {
            down(center)
            up()
        }
        val caught = scrolls.size
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, clicks)
        assertEquals("kept spinning after the catch", caught, scrolls.size)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        pad.performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
