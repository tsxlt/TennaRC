package lt.tsx.tennarc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GridSlideTest {
    private val out = FloatArray(64)
    private val alpha = FloatArray(32)

    @Test fun atRestEveryDotIsHomeAndWhole() {
        val n = slide(0f, 0f, 10f, across = 2, rows = 3, offX = 0f, offY = 0f, out, alpha)
        assertEquals(6, n)
        assertEquals(listOf(0f, 0f, 10f, 0f, 0f, 10f, 10f, 10f, 0f, 20f, 10f, 20f), out.take(12))
        assertEquals(List(6) { 1f }, alpha.take(6))
    }

    @Test fun halfAStepDownTheLastRowFadesOutAsASpareFadesIn() {
        val n = slide(0f, 0f, 10f, across = 1, rows = 3, offX = 0f, offY = 5f, out, alpha)
        val ys = (0 until n).map { out[2 * it + 1] }
        assertEquals(listOf(-5f, 5f, 15f, 25f), ys)
        assertEquals(listOf(0.5f, 1f, 1f, 0.5f), alpha.take(n))
    }

    @Test fun aWholeStepLooksLikeRest() {
        val n = slide(0f, 0f, 10f, across = 1, rows = 3, offX = 0f, offY = 10f, out, alpha)
        val shown = (0 until n).filter { alpha[it] > 0f }.map { out[2 * it + 1] }.sorted()
        assertEquals(listOf(0f, 10f, 20f), shown)
    }
}
