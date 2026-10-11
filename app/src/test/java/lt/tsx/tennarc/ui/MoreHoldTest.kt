package lt.tsx.tennarc.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreHoldTest {
    private val full = 900f
    private val shut = 96f
    private val fling = 2_000f
    private val flick = 40f

    private fun MoreHold.release(offset: Float, velocity: Float) = flicked(opens(offset, velocity, fling, flick))

    @Test fun aFlickOpenTicksOnRelease() {
        val hold = MoreHold().apply { take(0f, full, shut) }
        assertFalse(hold.moved(120f))
        assertTrue(hold.release(120f, velocity = 3_000f))
    }

    @Test fun aDragPastTheMarkTickedAlready() {
        val hold = MoreHold().apply { take(0f, full, shut) }
        assertTrue(hold.moved(600f))
        assertFalse(hold.release(600f, velocity = 3_000f))
        assertFalse(hold.release(600f, velocity = 0f))
    }

    @Test fun aShortSlowPullFallsBackQuietly() {
        val hold = MoreHold().apply { take(0f, full, shut) }
        hold.moved(120f)
        assertFalse(hold.release(120f, velocity = 0f))
    }

    @Test fun aFlickShutTicksOnRelease() {
        val hold = MoreHold().apply { take(full, full, shut) }
        assertFalse(hold.moved(full - 60f))
        assertTrue(hold.release(full - 60f, velocity = -3_000f))
    }
}
