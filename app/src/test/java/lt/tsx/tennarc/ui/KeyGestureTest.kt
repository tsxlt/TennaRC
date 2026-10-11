package lt.tsx.tennarc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.ui.components.pressAndRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyGestureTest {
    @get:Rule val compose = createComposeRule()

    private var fired = 0

    private fun key() {
        compose.setContent {
            Box(Modifier.size(120.dp).testTag("key").pressAndRepeat(enabled = true, repeat = true, onPressedChange = {}) { fired++ })
        }
    }

    @Test
    fun `a tap fires once, on release`() {
        key()
        compose.onNodeWithTag("key").performTouchInput { down(center) }
        assertEquals(0, fired)
        compose.onNodeWithTag("key").performTouchInput { up() }
        assertEquals(1, fired)
    }

    @Test
    fun `a drag that starts on a key sends nothing`() {
        key()
        compose.onNodeWithTag("key").performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, fired)
    }

    @Test
    fun `a hold keeps firing until release`() {
        key()
        compose.onNodeWithTag("key").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("key").performTouchInput { up() }
        val held = fired
        assertTrue("fired $held times", held >= 3)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(held, fired)
    }
}
