package lt.tsx.tennarc.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScrollWheelTest {
    private var sent = 0
    private var ticks = 0

    private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(16)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private fun TestScope.wheel() = ScrollWheel(
        this + Frames(testScheduler),
        onSteps = { sent += it },
        onTick = { ticks++ },
        clock = { testScheduler.currentTime },
    ).also { it.grab(lights = false) }

    @Test
    fun `part steps carry over to the next move`() = runTest {
        val wheel = wheel()
        repeat(3) { wheel.drag(STEP_DP * 0.36f, speedDp = 0f) }
        assertEquals(1, sent)
        wheel.drag(-STEP_DP * 0.18f, speedDp = 0f)
        assertEquals(1, sent)
    }

    @Test
    fun `a finger wobbling on the spot sends nothing`() = runTest {
        val wheel = wheel()
        repeat(50) {
            wheel.drag(7f, speedDp = 0f)
            wheel.drag(-7f, speedDp = 0f)
        }
        assertEquals(0, sent)
    }

    @Test
    fun `a step more than half turned finishes on lift`() = runTest {
        val wheel = wheel()
        wheel.drag(-STEP_DP * 0.55f, speedDp = 0f)
        assertEquals(0, sent)
        wheel.release(0f)
        assertEquals(-1, sent)
        advanceUntilIdle()
        assertEquals(0f, wheel.phase - Math.round(wheel.phase), 0.01f)
    }

    @Test
    fun `less than half a step is dropped on lift`() = runTest {
        val wheel = wheel()
        wheel.drag(STEP_DP * 0.45f, speedDp = 0f)
        wheel.release(0f)
        advanceUntilIdle()
        assertEquals(0, sent)
    }

    @Test
    fun `fast fingers turn it further`() = runTest {
        val wheel = wheel()
        wheel.drag(STEP_DP * 10.2f, speedDp = 2_000f)
        assertEquals(22, sent)
    }

    @Test
    fun `a slow lift doesn't fling`() = runTest {
        val wheel = wheel()
        wheel.release(FLING_MIN_DP - 1f)
        advanceUntilIdle()
        assertEquals(0, sent)
    }

    @Test
    fun `a fling turns about its speed times its time constant`() = runTest {
        for (velocity in listOf(300f, 900f, -2_000f)) {
            sent = 0
            val wheel = wheel()
            wheel.release(velocity)
            advanceUntilIdle()
            val stepsPerSecond = (velocity * scrollGain(abs(velocity)) / STEP_DP).coerceIn(-MAX_FLING_STEPS, MAX_FLING_STEPS)
            assertEquals("fling at $velocity dp/s", stepsPerSecond * FLING_TAU, sent.toFloat(), 1.5f)
        }
    }

    @Test
    fun `stop ends a fling and says it was caught`() = runTest {
        val wheel = wheel()
        wheel.release(2_000f)
        advanceTimeBy(100)
        val before = sent
        assertTrue(before > 0)
        assertTrue(wheel.stop())
        advanceUntilIdle()
        assertEquals(before, sent)
        assertFalse(wheel.stop())
    }

    @Test
    fun `a fling that has run down isn't caught`() = runTest {
        val wheel = wheel()
        wheel.release(300f)
        advanceUntilIdle()
        assertFalse(wheel.stop())
    }

    @Test
    fun `ticks once per step while slow and keeps ticking spinning fast`() = runTest {
        val wheel = wheel()
        repeat(5) {
            advanceTimeBy(100)
            wheel.drag(STEP_DP, speedDp = 110f)
        }
        assertEquals(5, sent)
        assertEquals(5, ticks)
        repeat(5) {
            advanceTimeBy(100)
            wheel.drag(STEP_DP, speedDp = 1_000f)
        }
        assertTrue(sent > 10)
        assertEquals(10, ticks)
    }
}
