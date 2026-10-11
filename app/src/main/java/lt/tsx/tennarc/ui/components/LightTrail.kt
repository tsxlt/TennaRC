package lt.tsx.tennarc.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

private const val HEAD_DP = 8.5f

private const val FEATHER_DP = 1.5f
private const val HALO_DP = 36f
private const val HALO_ALPHA = 0.08f

private const val TINT_ALPHA = 0.7f

private const val HEAD_OMEGA = 100f
private const val SCALE_OMEGA = 30f
private const val MORPH_OMEGA = 22f

private const val PILL_OMEGA = 20f
private const val PILL_ZETA = 0.8f

private const val APPEAR_FROM = 0.7f
private const val LIFT_SIZE = 0.85f

private const val SMALL = 0.25f

private const val MAX_TOUCHES = 5

private const val LIFT_FADE_S = 0.18f
private const val TAP_FADE_S = 0.14f

private const val PULSE_S = 0.3f
private const val PULSE_FROM_DP = 8f
private const val PULSE_TO_DP = 18f
private const val PULSE_ALPHA = 0.35f

private const val SHEET_SIGMA_DP = 46f
private const val SHEET_DENT_DP = 3.5f
private const val SHEET_DRAG_S = 0.022f
private const val SHEET_MAX_DRAG_DP = 9f
private const val SHEET_OMEGA = 16f
private const val SHEET_ZETA = 0.42f

private const val MAX_SUBSTEP_S = 1f / 240f
private const val MAX_FRAME_S = 1f / 20f

private class Touch {
    var id = -1L
    var live = false
    var touching = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var targetX = 0f
    var targetY = 0f
    var size = 0f
    var sizeV = 0f
    var sizeTarget = 0f
    var fade = 0f
    var fadeSeconds = LIFT_FADE_S
    var press = 0f
    var pressV = 0f
    var dragX = 0f
    var dragY = 0f
    var dragVX = 0f
    var dragVY = 0f

    fun start(id: Long, at: Offset) {
        this.id = id
        live = true
        touching = true
        x = at.x
        y = at.y
        vx = 0f
        vy = 0f
        targetX = at.x
        targetY = at.y
        size = APPEAR_FROM
        sizeV = 0f
        sizeTarget = 1f
        fade = 1f
        press = 0f
        pressV = 0f
        dragX = 0f
        dragY = 0f
        dragVX = 0f
        dragVY = 0f
    }
}

@Stable
internal class LightTrail(private val scope: CoroutineScope) {
    private val touches = Array(MAX_TOUCHES) { Touch() }
    private var primary: Touch? = null
    private var density = 1f
    private var pill = 0f
    private var pillV = 0f
    private var pillTarget = 0f
    private var many = 0f
    private var manyV = 0f
    private var pulse = -1f
    private var pulseX = 0f
    private var pulseY = 0f
    private val giving = IntArray(MAX_TOUCHES)

    private var loop: Job? = null
    private var dirty = false

    private var frame by mutableIntStateOf(0)

    fun down(id: Long, at: Offset, density: Float) {
        this.density = density
        pillTarget = 0f
        primary = claim(id, at)
        frame++
        kick()
    }

    fun touch(id: Long, at: Offset) {
        val touch = touches.firstOrNull { it.live && it.touching && it.id == id }
        if (touch == null) {
            claim(id, at)
        } else {
            touch.targetX = at.x
            touch.targetY = at.y
        }
        kick()
    }

    fun up(id: Long) {
        touches.firstOrNull { it.live && it.touching && it.id == id }?.let { release(it, LIFT_FADE_S) }
        kick()
    }

    fun pill(on: Boolean) {
        pillTarget = if (on) 1f else 0f
        kick()
    }

    fun lift(tap: Boolean) {
        val first = primary
        if (tap && first != null) {
            pulse = 0f
            pulseX = first.targetX
            pulseY = first.targetY
            first.fadeSeconds = TAP_FADE_S
        }
        for (touch in touches) if (touch.live && touch.touching) release(touch, if (tap) TAP_FADE_S else LIFT_FADE_S)
        pillTarget = 0f
        kick()
    }

    private fun claim(id: Long, at: Offset): Touch {
        val slot = touches.firstOrNull { !it.live } ?: touches.firstOrNull { !it.touching } ?: touches.last()
        slot.start(id, at)
        return slot
    }

    private fun release(touch: Touch, fadeSeconds: Float) {
        touch.touching = false
        touch.sizeTarget = LIFT_SIZE
        touch.fadeSeconds = fadeSeconds
    }

    private fun kick() {
        dirty = true
        if (loop?.isActive == true) return
        loop = scope.launch {
            var last = -1L
            while (true) {
                dirty = false
                val busy = withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, MAX_FRAME_S)
                    last = now
                    val more = step(dt)
                    frame++
                    more
                }
                if (!busy && !dirty) break
            }
        }
    }

    private fun step(dt: Float): Boolean {
        val substeps = ceil(dt / MAX_SUBSTEP_S).toInt().coerceAtLeast(1)
        val h = dt / substeps
        val down = touches.count { it.live && it.touching }
        val manyTarget = if (down >= 2) 1f else 0f
        repeat(substeps) {
            for (touch in touches) if (touch.live) advance(touch, h)
            pillV += (PILL_OMEGA * PILL_OMEGA * (pillTarget - pill) - 2f * PILL_ZETA * PILL_OMEGA * pillV) * h
            pill += pillV * h
            manyV += (MORPH_OMEGA * MORPH_OMEGA * (manyTarget - many) - 2f * MORPH_OMEGA * manyV) * h
            many += manyV * h
        }
        var busy = false
        for (touch in touches) {
            if (!touch.live) continue
            if (!touch.touching) touch.fade = (touch.fade - dt / touch.fadeSeconds).coerceAtLeast(0f)
            if (!touch.touching && touch.fade == 0f && touchSettled(touch)) {
                touch.live = false
                if (primary === touch) primary = null
            } else if (!touch.touching || !touchSettled(touch)) {
                busy = true
            }
        }
        if (pulse >= 0f) {
            pulse += dt / PULSE_S
            if (pulse >= 1f) pulse = -1f
        }
        val shapes = abs(pill - pillTarget) > 0.002f || abs(pillV) > 0.02f || abs(many - manyTarget) > 0.002f || abs(manyV) > 0.02f
        return busy || shapes || pulse >= 0f
    }

    private fun advance(t: Touch, h: Float) {
        val stiffness = HEAD_OMEGA * HEAD_OMEGA
        val damping = 2f * HEAD_OMEGA
        t.vx += (stiffness * (t.targetX - t.x) - damping * t.vx) * h
        t.vy += (stiffness * (t.targetY - t.y) - damping * t.vy) * h
        t.x += t.vx * h
        t.y += t.vy * h
        t.sizeV += (SCALE_OMEGA * SCALE_OMEGA * (t.sizeTarget - t.size) - 2f * SCALE_OMEGA * t.sizeV) * h
        t.size += t.sizeV * h
        val pressTarget = if (!t.touching) 0f else if (t === primary) 1f - pill.coerceIn(0f, 1f) else 1f
        var dragTargetX = 0f
        var dragTargetY = 0f
        if (t.touching) {
            val max = SHEET_MAX_DRAG_DP * density
            var tx = t.vx * SHEET_DRAG_S
            var ty = t.vy * SHEET_DRAG_S
            val reach = hypot(tx, ty)
            if (reach > max) {
                tx *= max / reach
                ty *= max / reach
            }
            dragTargetX = tx * pressTarget
            dragTargetY = ty * pressTarget
        }
        val k = SHEET_OMEGA * SHEET_OMEGA
        val c = 2f * SHEET_ZETA * SHEET_OMEGA
        t.pressV += (k * (pressTarget - t.press) - c * t.pressV) * h
        t.press += t.pressV * h
        t.dragVX += (k * (dragTargetX - t.dragX) - c * t.dragVX) * h
        t.dragVY += (k * (dragTargetY - t.dragY) - c * t.dragVY) * h
        t.dragX += t.dragVX * h
        t.dragY += t.dragVY * h
    }

    private fun touchSettled(t: Touch): Boolean {
        val near = 0.25f * density
        val still = 3f * density
        if (abs(t.x - t.targetX) > near || abs(t.y - t.targetY) > near) return false
        if (abs(t.vx) > still || abs(t.vy) > still) return false
        if (abs(t.size - t.sizeTarget) > 0.002f || abs(t.sizeV) > 0.02f) return false
        val pressTarget = if (!t.touching) 0f else if (t === primary) 1f - pill.coerceIn(0f, 1f) else 1f
        if (abs(t.press - pressTarget) > 0.003f || abs(t.pressV) > 0.01f) return false
        val dragNear = 0.05f * density
        return abs(t.dragX) < dragNear && abs(t.dragY) < dragNear && abs(t.dragVX) + abs(t.dragVY) < 0.5f * density
    }

    private fun gatherGiving(): Int {
        var n = 0
        for ((i, t) in touches.withIndex()) {
            if (t.live && (abs(t.press) > 0.003f || abs(t.dragX) + abs(t.dragY) > 0.05f * density)) giving[n++] = i
        }
        return n
    }

    val bent: Boolean
        get() = touches.any { it.live && (abs(it.press) > 0.003f || abs(it.dragX) + abs(it.dragY) > 0.05f * density) }

    fun bend(home: FloatArray, out: FloatArray, offsetX: Float) {
        val n = gatherGiving()
        val sigma = SHEET_SIGMA_DP * density
        val spread = 1f / (2f * sigma * sigma)
        val reach = 9f * sigma * sigma
        var i = 0
        while (i < home.size) {
            val px = home[i]
            val py = home[i + 1]
            var ox = 0f
            var oy = 0f
            for (j in 0 until n) {
                val t = touches[giving[j]]
                val dx = t.x - offsetX - px
                val dy = t.y - py
                val d2 = dx * dx + dy * dy
                if (d2 > reach) continue
                val give = exp(-d2 * spread)
                val d = sqrt(d2)
                val toward = if (d > 0.5f) SHEET_DENT_DP * density * t.press * give * (d / sigma).coerceAtMost(1f) / d else 0f
                ox += dx * toward + t.dragX * give
                oy += dy * toward + t.dragY * give
            }
            out[i] = px + ox
            out[i + 1] = py + oy
            i += 2
        }
    }

    fun DrawScope.drawTrail(look: LightLook, offsetX: Float, ribX: Float) {
        if (frame == 0) return
        val shrink = 1f - SMALL * many.coerceIn(0f, 1f)
        val morph = if (ribX.isNaN()) 0f else pill.coerceIn(0f, 1f)
        for (t in touches) {
            if (!t.live) continue
            val shown = smoothstep(0f, 1f, t.fade)
            if (shown <= 0f) continue
            val thumb = t === primary && morph > 0f
            val x = if (thumb) lerp(t.x - offsetX, ribX, pill) else t.x - offsetX
            val scale = t.size * shrink
            translate(x, t.y) {
                scale(scale, Offset.Zero) {
                    drawCircle(look.halo, look.haloRadius, Offset.Zero, alpha = HALO_ALPHA * shown * (1f - 0.6f * (if (thumb) morph else 0f)))
                }
            }
        }
        if (pulse >= 0f) {
            val left = 1f - pulse
            val grow = 1f - left * left * left
            drawCircle(
                color = look.color,
                radius = lerp(look.pulseFrom, look.pulseTo, grow),
                center = Offset(pulseX - offsetX, pulseY),
                alpha = PULSE_ALPHA * left,
                style = look.ring,
            )
        }
    }

    fun DrawScope.tintDots(dots: FloatArray, color: Color, radius: Float, offsetX: Float, size: Int = dots.size) {
        val n = gatherGiving()
        if (n == 0) return
        val sigma = SHEET_SIGMA_DP * density
        val spread = 1f / (2f * sigma * sigma)
        val reach = 9f * sigma * sigma
        var i = 0
        while (i < size) {
            var warm = 0f
            for (j in 0 until n) {
                val t = touches[giving[j]]
                val dx = dots[i] - (t.x - offsetX)
                val dy = dots[i + 1] - t.y
                val d2 = dx * dx + dy * dy
                if (d2 < reach) warm += exp(-d2 * spread) * t.press.coerceIn(0f, 1f)
            }
            if (warm > 0.02f) drawCircle(color, radius, Offset(dots[i], dots[i + 1]), alpha = TINT_ALPHA * warm.coerceAtMost(1f))
            i += 2
        }
    }
}

internal class LightLook(density: Density, val color: Color) {
    val radius: Float
    val discRadius: Float
    val haloRadius: Float
    val pillRadius: Float
    val pillReach: Float
    val pulseFrom: Float
    val pulseTo: Float
    val ring: Stroke

    val disc: Brush
    val halo: Brush

    init {
        with(density) {
            radius = HEAD_DP.dp.toPx()
            discRadius = (HEAD_DP + FEATHER_DP).dp.toPx()
            haloRadius = HALO_DP.dp.toPx()
            pillRadius = 14.dp.toPx()
            pillReach = 14.dp.toPx()
            pulseFrom = PULSE_FROM_DP.dp.toPx()
            pulseTo = PULSE_TO_DP.dp.toPx()
            ring = Stroke(1.25.dp.toPx())
        }
        disc = Brush.radialGradient(
            0f to color,
            radius / discRadius to color,
            1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = discRadius,
        )
        halo = Brush.radialGradient(
            0f to color,
            0.4f to color.copy(alpha = 0.35f),
            1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = haloRadius,
        )
    }
}
