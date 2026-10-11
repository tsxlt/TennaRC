package lt.tsx.tennarc

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

class FrameLog(private val window: Window) {
    private val thread = HandlerThread("TennaFps").apply { start() }
    private val handler = Handler(thread.looper)
    private var secondStart = 0L
    private var frames = 0
    private var missed = 0
    private var worstWorkNs = 0L
    private var workNs = 0L
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> take(metrics) }

    private val flushLate = Runnable { flush() }

    fun start() = window.addOnFrameMetricsAvailableListener(listener, handler)

    fun stop() = catching { window.removeOnFrameMetricsAvailableListener(listener) }

    private fun take(metrics: FrameMetrics) {
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        if (secondStart == 0L) secondStart = vsync
        if (vsync - secondStart >= SECOND) {
            flush()
            secondStart = vsync
        }
        val work = WORK.sumOf { metrics.getMetric(it) }
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else interval()
        frames++
        workNs += work
        if (work > worstWorkNs) worstWorkNs = work
        if (total > deadline) missed++
        handler.removeCallbacks(flushLate)
        handler.postDelayed(flushLate, SECOND / 1_000_000 + 100)
    }

    private fun interval(): Long = (1_000_000_000L / (window.windowManager.defaultDisplay.refreshRate.takeIf { it > 0f } ?: 60f)).toLong()

    private fun flush() {
        if (frames >= MIN_FRAMES) {
            Log.d(
                TAG,
                "frames=%d missed=%d work avg=%.1fms worst=%.1fms (frame every %.1fms)".format(
                    frames, missed, workNs / 1e6 / frames, worstWorkNs / 1e6, interval() / 1e6,
                ),
            )
        }
        frames = 0
        missed = 0
        worstWorkNs = 0
        workNs = 0
        secondStart = 0L
    }

    private companion object {
        const val TAG = "TennaFps"
        const val SECOND = 1_000_000_000L

        const val MIN_FRAMES = 6

        val WORK = intArrayOf(
            FrameMetrics.INPUT_HANDLING_DURATION,
            FrameMetrics.ANIMATION_DURATION,
            FrameMetrics.LAYOUT_MEASURE_DURATION,
            FrameMetrics.DRAW_DURATION,
            FrameMetrics.SYNC_DURATION,
            FrameMetrics.COMMAND_ISSUE_DURATION,
        )
    }
}
