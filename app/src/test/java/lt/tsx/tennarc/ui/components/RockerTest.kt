package lt.tsx.tennarc.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RockerTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var rocker: RockerState

    @Before fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent { rocker = rememberRockerState() }
    }

    @Test fun pressTipsItALittleAndItLingersPastTheRelease() {
        compose.runOnUiThread { rocker.press(1) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(5f, rocker.tilt.value, 0.2f)
        compose.runOnUiThread { rocker.release(1) }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(5f, rocker.tilt.value, 0.2f)
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0f, rocker.tilt.value, 0.2f)
    }

    @Test fun holdingBuildsTheTipUpTowardsItsMost() {
        compose.runOnUiThread {
            rocker.press(1)
            repeat(4) { rocker.fire(1) }
        }
        compose.mainClock.advanceTimeBy(300)
        val built = rocker.tilt.value
        assertTrue("built $built", built > 8f && built < 10f)
        compose.runOnUiThread { repeat(100) { rocker.fire(1) } }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("capped ${rocker.tilt.value}", rocker.tilt.value <= 15f)
    }

    @Test fun tapTooQuickForItsPressStillTipsAndLingers() {
        compose.runOnUiThread { rocker.fire(-1) }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(-5f, rocker.tilt.value, 0.2f)
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0f, rocker.tilt.value, 0.2f)
    }

    @Test fun quickStepsFromElsewhereBuildLikeAHold() {
        compose.runOnUiThread { rocker.tap(1) }
        compose.mainClock.advanceTimeBy(50)
        compose.runOnUiThread { rocker.tap(1) }
        compose.mainClock.advanceTimeBy(50)
        compose.runOnUiThread { rocker.tap(1) }
        compose.mainClock.advanceTimeBy(150)
        assertTrue("built ${rocker.tilt.value}", rocker.tilt.value > 7f)
    }

    @Test fun releaseOfTheOtherKeyLeavesItTipped() {
        compose.runOnUiThread {
            rocker.press(1)
            rocker.release(-1)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(5f, rocker.tilt.value, 0.2f)
    }
}
