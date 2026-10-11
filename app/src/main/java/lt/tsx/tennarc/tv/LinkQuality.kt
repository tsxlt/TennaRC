package lt.tsx.tennarc.tv

enum class LinkVerdict {
    GOOD,

    STALLING,

    SLOW,
}

data class LinkReport(
    val verdict: LinkVerdict,
    val slowShare: Float,
    val medianMs: Long,
    val worstMs: Long,
)

class LinkQuality(private val window: Int = WINDOW) {
    private val samples = ArrayDeque<Long>()
    private var flagged: LinkVerdict? = null

    fun add(rttMs: Long) {
        samples.addLast(rttMs)
        while (samples.size > window) samples.removeFirst()
    }

    fun clear() {
        samples.clear()
        flagged = null
    }

    fun report(): LinkReport? {
        if (samples.size < window) return null
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val slowShare = samples.count { it > SLOW_MS }.toFloat() / samples.size
        val fastest = sorted.first()
        val verdict = when {
            median > SLOW_MS -> LinkVerdict.SLOW
            slowShare >= STALL_SHARE && fastest < AWAKE_MS -> LinkVerdict.STALLING
            flagged != null && slowShare > CLEAR_SHARE -> flagged!!
            else -> LinkVerdict.GOOD
        }
        flagged = verdict.takeIf { it != LinkVerdict.GOOD }
        return LinkReport(verdict, slowShare, median, sorted.last())
    }

    companion object {
        const val WINDOW = 20

        const val SLOW_MS = 150L

        const val AWAKE_MS = 40L

        const val STALL_SHARE = 0.2f
        const val CLEAR_SHARE = 0.05f
    }
}
