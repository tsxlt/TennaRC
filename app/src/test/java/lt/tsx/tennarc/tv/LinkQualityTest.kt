package lt.tsx.tennarc.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkQualityTest {
    private fun judge(vararg rtts: Long) = LinkQuality().apply { rtts.forEach(::add) }.report()

    @Test
    fun `nothing to say until the window is full`() {
        assertNull(judge(*LongArray(19) { 3 }))
    }

    @Test
    fun `quick replies are good`() {
        assertEquals(LinkVerdict.GOOD, judge(*LongArray(20) { 3 + it % 5L })?.verdict)
    }

    @Test
    fun `a TV that naps every second is stalling`() {
        val rtts = LongArray(20) { if (it % 5 < 2) 300L + it * 7 else 2L }
        val report = judge(*rtts)!!
        assertEquals(LinkVerdict.STALLING, report.verdict)
        assertEquals(0.4f, report.slowShare, 0.001f)
    }

    @Test
    fun `slow all the time is slow, not stalling`() {
        assertEquals(LinkVerdict.SLOW, judge(*LongArray(20) { 200L + it })?.verdict)
    }

    @Test
    fun `a single hiccup is not worth a warning`() {
        assertEquals(LinkVerdict.GOOD, judge(*LongArray(20) { if (it == 7) 400L else 3L })?.verdict)
    }

    @Test
    fun `once flagged it takes a clean window to clear`() {
        val quality = LinkQuality()
        repeat(20) { quality.add(if (it % 5 < 2) 300L else 2L) }
        assertEquals(LinkVerdict.STALLING, quality.report()?.verdict)
        repeat(20) { quality.add(if (it % 10 == 0) 300L else 2L) }
        assertEquals(LinkVerdict.STALLING, quality.report()?.verdict)
        repeat(20) { quality.add(2L) }
        assertEquals(LinkVerdict.GOOD, quality.report()?.verdict)
    }
}
