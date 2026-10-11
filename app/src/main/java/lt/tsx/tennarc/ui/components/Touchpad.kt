package lt.tsx.tennarc.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.ScrollStrip
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.Glass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.ground
import kotlin.math.abs
import kotlin.math.hypot

private const val TAP_TIMEOUT_MS = 280L
private val STRIP_WIDTH = 44.dp
private val GRID_STEP = 22.dp

@Composable
@ReadOnlyComposable
internal fun touchpadPane(colors: ColorScheme): Glass {
    val ground = ground()
    return clearGlass(lerp(ground, colors.surfaceContainerLow, PANE), ground, floating = true, sheen = PANE).let {
        it.copy(rim = it.rim.copy(alpha = it.rim.alpha * PANE), rimLow = it.rimLow.copy(alpha = it.rimLow.alpha * PANE))
    }
}

private val GRID_EDGE = 16.dp

private const val PANE = 0.5f

private const val AXIS_LOCK = 1.5f

private enum class Gesture { Undecided, Pointer, Strip, TwoFinger }

@Composable
fun Touchpad(
    onMove: (Float, Float) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onClick: () -> Unit,
    speed: Float,
    naturalScroll: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    strip: ScrollStrip = ScrollStrip.OFF,
    inset: Dp = 0.dp,
    drawGlass: Boolean = true,
) {
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val move by rememberUpdatedState(onMove)
    val scroll by rememberUpdatedState(onScroll)
    val click by rememberUpdatedState(onClick)
    val currentSpeed by rememberUpdatedState(speed)
    val natural by rememberUpdatedState(naturalScroll)

    val scope = rememberCoroutineScope()
    val trail = remember(scope) { LightTrail(scope) }
    val wheelY = remember(scope) {
        ScrollWheel(scope, onSteps = { steps -> scroll(0f, if (natural) -steps.toFloat() else steps.toFloat()) }, onTick = { haptics.faintTick() })
    }
    val wheelX = remember(scope) {
        ScrollWheel(scope, onSteps = { steps -> scroll(if (natural) -steps.toFloat() else steps.toFloat(), 0f) }, onTick = { haptics.faintTick() })
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            wheelY.stop()
            wheelX.stop()
        }
    }
    var gridWheel by remember { mutableStateOf<ScrollWheel?>(null) }

    val colors = MaterialTheme.colorScheme
    val dot = colors.outline.copy(alpha = 0.45f)
    val pane = touchpadPane(colors)
    val accent = colors.primary
    val description = stringResource(R.string.touchpad_label)

    Box(
        modifier = modifier
            .pointerInput(enabled, strip, inset) {
                if (!enabled) return@pointerInput
                val stripWidth = STRIP_WIDTH.toPx()
                val margin = inset.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val caught = wheelY.stop() or wheelX.stop()
                    trail.down(down.id.value, down.position, density)
                    val inStrip = when (strip) {
                        ScrollStrip.RIGHT -> down.position.x > size.width - margin - stripWidth
                        ScrollStrip.LEFT -> down.position.x < margin + stripWidth
                        ScrollStrip.OFF -> false
                    }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var sampledAt = down.uptimeMillis
                    val startedAt = down.uptimeMillis
                    var endedAt = startedAt
                    var gesture = Gesture.Undecided
                    var fingers = 1
                    var fingerSpeed = 0f
                    var pendingX = 0f
                    var pendingY = 0f
                    var twoFingerWheel: ScrollWheel? = null
                    var twoFingersDown = false
                    val starts = HashMap<PointerId, Offset>()
                    val moving = HashSet<PointerId>()
                    var finished = false

                    fun sample(at: Long, position: Offset) {
                        if (at <= sampledAt) return
                        tracker.addPosition(at, position)
                        sampledAt = at
                    }

                    fun point(x: Float, y: Float) {
                        val dx = x / density
                        val dy = y / density
                        val boost = 1f + (hypot(dx, dy) / 6f).coerceAtMost(1.5f)
                        move(dx * currentSpeed * boost, dy * currentSpeed * boost)
                    }

                    fun releaseTwoFingers() {
                        val velocity = tracker.calculateVelocity()
                        when (twoFingerWheel) {
                            wheelY -> wheelY.release(velocity.y / density)
                            wheelX -> wheelX.release(velocity.x / density)
                        }
                        twoFingersDown = false
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            for (change in event.changes) {
                                if (change.pressed) trail.touch(change.id.value, change.position)
                                else if (change.previousPressed) trail.up(change.id.value)
                            }
                            val now = event.changes.maxOf { it.uptimeMillis }
                            val elapsed = (now - endedAt).toFloat()
                            endedAt = now
                            if (active.isEmpty()) {
                                if (gesture == Gesture.TwoFinger) sample(now, Offset(pendingX, pendingY))
                                else sample(now, event.changes.first().position)
                                break
                            }
                            fingers = maxOf(fingers, active.size)
                            if (active.size >= 2 && gesture != Gesture.TwoFinger) {
                                if (gesture == Gesture.Strip) wheelY.release(0f)
                                trail.pill(false)
                                gesture = Gesture.TwoFinger
                                twoFingersDown = true
                                pendingX = 0f
                                pendingY = 0f
                                tracker.resetTracking()
                                sampledAt = Long.MIN_VALUE
                                active.forEach { starts[it.id] = it.position }
                            }
                            if (gesture == Gesture.TwoFinger) {
                                if (!twoFingersDown) {
                                } else if (active.size < 2) {
                                    releaseTwoFingers()
                                } else {
                                    var sumX = 0f
                                    var sumY = 0f
                                    var count = 0
                                    for (change in active) {
                                        val start = starts.getOrPut(change.id) { change.position }
                                        if (change.id !in moving && (change.position - start).getDistance() >= slop / 2) moving += change.id
                                        if (change.id in moving) {
                                            val delta = change.positionChange()
                                            sumX += delta.x
                                            sumY += delta.y
                                            count++
                                        }
                                    }
                                    if (count > 0) {
                                        val dx = sumX / count
                                        val dy = sumY / count
                                        pendingX += dx
                                        pendingY += dy
                                        sample(now, Offset(pendingX, pendingY))
                                        fingerSpeed = smoothSpeed(fingerSpeed, hypot(dx, dy) / density, elapsed)
                                        val wheel = twoFingerWheel
                                        if (wheel != null) {
                                            wheel.drag((if (wheel === wheelY) dy else dx) / density, fingerSpeed)
                                        } else if (hypot(pendingX, pendingY) >= slop) {
                                            val across = abs(pendingX)
                                            val along = abs(pendingY)
                                            val chosen = when {
                                                along >= AXIS_LOCK * across -> wheelY
                                                across >= AXIS_LOCK * along -> wheelX
                                                hypot(pendingX, pendingY) >= 2 * slop -> if (along >= across) wheelY else wheelX
                                                else -> null
                                            }
                                            if (chosen != null) {
                                                twoFingerWheel = chosen
                                                gridWheel = chosen
                                                chosen.grab(lights = false)
                                                chosen.drag((if (chosen === wheelY) pendingY else pendingX) / density, fingerSpeed)
                                            }
                                        }
                                    }
                                }
                            } else {
                                val change = active.first()
                                val delta = change.positionChange()
                                sample(change.uptimeMillis, change.position)
                                fingerSpeed = smoothSpeed(fingerSpeed, delta.getDistance() / density, elapsed)
                                when (gesture) {
                                    Gesture.Undecided -> {
                                        pendingX += delta.x
                                        pendingY += delta.y
                                        if (hypot(pendingX, pendingY) > slop) {
                                            if (inStrip && abs(pendingY) >= AXIS_LOCK * abs(pendingX)) {
                                                gesture = Gesture.Strip
                                                gridWheel = null
                                                trail.pill(true)
                                                wheelY.grab(lights = true)
                                                wheelY.glowY = change.position.y
                                                wheelY.drag(pendingY / density, fingerSpeed)
                                            } else {
                                                gesture = Gesture.Pointer
                                                point(pendingX, pendingY)
                                            }
                                            pendingX = 0f
                                            pendingY = 0f
                                        }
                                    }
                                    Gesture.Pointer -> point(delta.x, delta.y)
                                    Gesture.Strip -> {
                                        wheelY.glowY = change.position.y
                                        wheelY.drag(delta.y / density, fingerSpeed)
                                    }
                                    Gesture.TwoFinger -> Unit
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                        finished = true
                    } finally {
                        if (!finished) {
                            wheelY.stop()
                            wheelX.stop()
                            trail.lift(tap = false)
                        }
                    }
                    when (gesture) {
                        Gesture.Strip -> wheelY.release(tracker.calculateVelocity().y / density)
                        Gesture.TwoFinger -> if (twoFingersDown) releaseTwoFingers()
                        else -> Unit
                    }
                    val tap = gesture == Gesture.Undecided && fingers == 1 && endedAt - startedAt < TAP_TIMEOUT_MS && !caught
                    trail.lift(tap)
                    if (tap) {
                        haptics.key()
                        click()
                    }
                }
            }
            .semantics {
                contentDescription = description
                if (enabled) {
                    onClick {
                        click()
                        true
                    }
                } else {
                    disabled()
                }
            }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = inset)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .then(if (drawGlass) Modifier.glass(pane, MaterialTheme.shapes.extraLarge) else Modifier)
                .drawWithCache {
                    val step = GRID_STEP.toPx()
                    val margin = inset.toPx()
                    val edge = GRID_EDGE.toPx()
                    val width = size.width
                    val height = size.height
                    val columns = ((width - 2 * edge) / step).toInt().coerceAtLeast(0) + 1
                    val rows = ((height - 2 * edge) / step).toInt().coerceAtLeast(0) + 1
                    val left = (width - (columns - 1) * step) / 2
                    val top = (height - (rows - 1) * step) / 2
                    val ribColumn = when {
                        columns == 0 -> -1
                        strip == ScrollStrip.RIGHT -> columns - 1
                        strip == ScrollStrip.LEFT -> 0
                        else -> -1
                    }
                    val ribX = if (ribColumn >= 0) left + ribColumn * step else Float.NaN
                    val points = buildList {
                        for (row in 0 until rows) {
                            for (column in 0 until columns) {
                                if (column != ribColumn) add(Offset(left + column * step, top + row * step))
                            }
                        }
                    }
                    val dotWidth = 2.dp.toPx()
                    val home = FloatArray(points.size * 2).also { pairs ->
                        points.forEachIndexed { i, point ->
                            pairs[2 * i] = point.x
                            pairs[2 * i + 1] = point.y
                        }
                    }
                    val bentDots = FloatArray(home.size)
                    val dotColumns = (0 until columns).filter { it != ribColumn }
                    val firstX = if (dotColumns.isEmpty()) 0f else left + dotColumns.first() * step
                    val across = dotColumns.size
                    val sliding = FloatArray((rows + 1) * (across + 1) * 2)
                    val slidingAlpha = FloatArray((rows + 1) * (across + 1))
                    val slidBent = FloatArray(sliding.size)
                    val dotPaint = Paint().apply {
                        color = dot
                        strokeWidth = dotWidth
                        strokeCap = StrokeCap.Round
                    }
                    val ribs = RibLook(this, colors.outline, accent)
                    val light = LightLook(this, accent)
                    onDrawBehind {
                        val wheel = gridWheel
                        val offX = if (wheel === wheelX) wheel.phase * step else 0f
                        val offY = if (wheel === wheelY) wheel.phase * step else 0f
                        if (abs(offX) > 0.01f || abs(offY) > 0.01f) {
                            val count = slide(firstX, top, step, across, rows, offX, offY, sliding, slidingAlpha)
                            val pairs = count * 2
                            val bent = trail.bent
                            if (bent) trail.bend(sliding, slidBent, margin)
                            val at = if (bent) slidBent else sliding
                            for (i in 0 until count) {
                                drawCircle(dot, dotWidth / 2, Offset(at[2 * i], at[2 * i + 1]), alpha = slidingAlpha[i])
                            }
                            with(trail) { tintDots(at, accent, dotWidth / 2, margin, size = pairs) }
                        } else {
                            val bent = trail.bent
                            if (bent) {
                                trail.bend(home, bentDots, margin)
                                drawIntoCanvas { it.drawRawPoints(PointMode.Points, bentDots, dotPaint) }
                            } else {
                                drawPoints(points, PointMode.Points, dot, strokeWidth = dotWidth, cap = StrokeCap.Round)
                            }
                            with(trail) { tintDots(if (bent) bentDots else home, accent, dotWidth / 2, margin) }
                        }
                        if (ribColumn >= 0) drawRibs(wheelY, ribs, ribX, top)
                        with(trail) { drawTrail(light, margin, ribX) }
                    }
                },
        )
    }
}

internal fun slide(x0: Float, y0: Float, step: Float, across: Int, rows: Int, offX: Float, offY: Float, out: FloatArray, alpha: FloatArray): Int {
    val linesX = if (offX != 0f) across + 1 else across
    val linesY = if (offY != 0f) rows + 1 else rows
    var n = 0
    for (j in 0 until linesY) {
        val y = lineAt(y0, step, rows, j, offY)
        val ay = lineShown(y0, step, rows, y, offY)
        if (ay <= 0f) continue
        for (i in 0 until linesX) {
            val x = lineAt(x0, step, across, i, offX)
            val ax = lineShown(x0, step, across, x, offX)
            if (ax <= 0f) continue
            out[2 * n] = x
            out[2 * n + 1] = y
            alpha[n] = ax * ay
            n++
        }
    }
    return n
}

private fun lineAt(start: Float, step: Float, lines: Int, index: Int, offset: Float): Float {
    if (offset == 0f) return start + index * step
    val loop = (lines + 1) * step
    return start - step + ((index * step + offset) % loop + loop) % loop
}

private fun lineShown(start: Float, step: Float, lines: Int, at: Float, offset: Float): Float {
    if (offset == 0f) return 1f
    val end = start + (lines - 1) * step
    val shown = when {
        at < start -> (at - (start - step)) / step
        at > end -> 1f - (at - end) / step
        else -> 1f
    }
    return shown.coerceIn(0f, 1f)
}
