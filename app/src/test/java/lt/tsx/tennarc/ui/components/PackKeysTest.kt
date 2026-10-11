package lt.tsx.tennarc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PackKeysTest {
    @Test
    fun fillsEachLineThenWraps() {
        assertEquals(listOf(3, 1), packKeys(listOf(100f, 100f, 100f, 100f), room = 320f, gap = 8f))
    }

    @Test
    fun aNameTooLongForAnyLineGetsOneToItself() {
        assertEquals(listOf(1, 1, 1), packKeys(listOf(50f, 500f, 50f), room = 300f, gap = 8f))
    }

    @Test
    fun exactFitStaysOnOneLine() {
        assertEquals(listOf(2), packKeys(listOf(146f, 146f), room = 300f, gap = 8f))
    }

    @Test
    fun nothingMakesNoLines() {
        assertEquals(emptyList<Int>(), packKeys(emptyList(), room = 300f, gap = 8f))
    }
}
