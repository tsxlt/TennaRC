package lt.tsx.tennarc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FrostTest {
    private val darkGrounds = listOf(TennaColors.Ink, TennaColors.Panel)
    private val darkSteps = listOf(TennaColors.Panel, TennaColors.Raised, TennaColors.RaisedHigh, TennaColors.RaisedHighest)
    private val lightGrounds = listOf(TennaLight.Ink, TennaLight.Panel)
    private val lightSteps = listOf(TennaLight.Raised, TennaLight.RaisedHigh, TennaLight.RaisedHighest)

    private fun assertLooksLike(expected: Color, actual: Color) {
        val off = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue))
        assertTrue("$actual looks like $expected (off by $off)", off < 0.5f / 255f)
    }

    @Test
    fun aSeeThroughStepLooksJustAsTheSolidOneOnItsGround() {
        for ((grounds, steps) in listOf(darkGrounds to darkSteps, lightGrounds to lightSteps)) {
            for (ground in grounds) for (step in steps) {
                val tint = seeThrough(step, ground)
                assertLooksLike(step, tint.compositeOver(ground))
            }
        }
    }

    @Test
    fun theStepsAreFaintTints() {
        for (step in darkSteps.drop(1)) assertTrue(seeThrough(step, TennaColors.Ink).alpha < 0.2f)
        for (step in lightSteps) assertTrue(seeThrough(step, TennaLight.Ink).alpha < 0.2f)
    }

    @Test
    fun theGroundItselfIsClear() {
        assertEquals(Color.Transparent, seeThrough(TennaColors.Panel, TennaColors.Panel))
    }

    @Test
    fun aPartClearFillIsSeenAsItLooksOnTheGround() {
        val wash = Color.White.copy(alpha = 0.1f)
        assertLooksLike(wash.compositeOver(TennaColors.Ink), seeThrough(wash, TennaColors.Ink).compositeOver(TennaColors.Ink))
    }

    @Test
    fun restoredGrainMakesUpWhatAFillHides() {
        assertEquals(0f, grainToRestore(0f), 1e-6f)
        assertEquals(1f, grainToRestore(1f), 1e-6f)
        val alpha = 0.55f
        val through = 1f - alpha
        val back = grainToRestore(alpha)
        assertEquals(1f, through * through + back * back, 1e-5f)
    }

    @Test
    fun theGrainIsCentredOnTheSurface() {
        val random = kotlin.random.Random(1)
        val out = FloatArray(3)
        var sum = 0.0
        val count = 100_000
        repeat(count) {
            val jitter = (random.nextFloat() + random.nextFloat() - 1f) * kotlin.math.sqrt(6f)
            frostOffset(random.nextFloat(), random.nextFloat().let { it * it }, jitter, out)
            sum += (out[0] + out[1] + out[2]) / 3.0
        }
        assertEquals(0.0, sum / count, 0.001)
    }
}
