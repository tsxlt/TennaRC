package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign

internal const val STEP_DP = 15f

internal const val FLING_MIN_DP = 250f

internal const val MAX_FLING_STEPS = 90f

private const val FLING_FRICTION = 0.72f

internal const val FLING_TAU = 1f / (4.2f * FLING_FRICTION)

private const val CATCH_SPEED = 3f

private const val GAIN_FROM_DP = 150f
private const val GAIN_TO_DP = 1_200f
private const val MAX_EXTRA_GAIN = 1.2f
private const val SPEED_TAU_MS = 50f

private const val TICK_GAP_MS = 60L

private const val STREAK_FROM = 20f

internal fun scrollGain(speedDp: Float): Float = 1f + MAX_EXTRA_GAIN * smoothstep(GAIN_FROM_DP, GAIN_TO_DP, speedDp)

internal fun smoothSpeed(current: Float, travelDp: Float, elapsedMs: Float): Float {
    if (elapsedMs <= 0f) return current
    val sample = abs(travelDp) / elapsedMs * 1_000f
    return current + (sample - current) * (1f - exp(-elapsedMs / SPEED_TAU_MS))
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Stable
class ScrollWheel(
    private val scope: CoroutineScope,
    private val onSteps: (Int) -> Unit,
    private val onTick: () -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    var phase by mutableFloatStateOf(0f)
        private set

    var speed by mutableFloatStateOf(0f)
        private set

    var glowY by mutableFloatStateOf(0f)

    var lit by mutableFloatStateOf(0f)
        private set

    private var carry = 0f
    private var dragging = false
    private var flinging = false
    private var lights = false
    private var lastTick = Long.MIN_VALUE / 2
    private var motion: Job? = null
    private var glow: Job? = null

    fun grab(lights: Boolean) {
        motion?.cancel()
        flinging = false
        dragging = true
        this.lights = lights
        carry = phase - round(phase)
        if (lights) fadeLit(to = 1f, millis = 120, fadeIn = true)
    }

    fun drag(dp: Float, speedDp: Float) {
        if (!dragging) grab(lights = false)
        val gain = scrollGain(speedDp)
        changeSpeed(speedDp * gain / STEP_DP)
        turn(dp * gain / STEP_DP)
    }

    fun release(velocityDp: Float) {
        if (!dragging) return
        dragging = false
        if (abs(velocityDp) < FLING_MIN_DP) {
            finish(velocity = 0f)
            return
        }
        val velocity = (velocityDp * scrollGain(abs(velocityDp)) / STEP_DP).coerceIn(-MAX_FLING_STEPS, MAX_FLING_STEPS)
        motion?.cancel()
        glow?.cancel()
        flinging = true
        motion = scope.launch {
            var turned = 0f
            AnimationState(initialValue = 0f, initialVelocity = velocity)
                .animateDecay(exponentialDecay(FLING_FRICTION, CATCH_SPEED)) {
                    changeSpeed(abs(this.velocity))
                    if (lights) lit = min(1f, abs(this.velocity) / 30f)
                    turn(value - turned)
                    turned = value
                }
            flinging = false
            finish(velocity = this@ScrollWheel.speed * sign(velocity))
        }
    }

    fun stop(): Boolean {
        val caught = flinging && speed > CATCH_SPEED
        if (flinging || dragging) {
            motion?.cancel()
            flinging = false
            dragging = false
            val sent = phase - carry
            carry = 0f
            changeSpeed(0f)
            settle(sent, velocity = 0f)
            fadeLit(to = 0f, millis = 240, fadeIn = false)
        }
        return caught
    }

    private fun turn(steps: Float) {
        phase += steps
        carry += steps
        val whole = carry.toInt()
        if (whole != 0) {
            carry -= whole
            send(whole)
        }
    }

    private fun finish(velocity: Float) {
        val sent = phase - carry
        val completed = if (abs(carry) >= 0.5f) sign(carry).toInt() else 0
        carry = 0f
        if (completed != 0) send(completed)
        changeSpeed(0f)
        settle(sent + completed, velocity)
        fadeLit(to = 0f, millis = 240, fadeIn = false)
    }

    private fun send(steps: Int) {
        onSteps(steps)
        val now = clock()
        if (now - lastTick >= TICK_GAP_MS) {
            lastTick = now
            onTick()
        }
    }

    private fun changeSpeed(stepsPerSecond: Float) {
        speed = stepsPerSecond
    }

    private fun settle(rest: Float, velocity: Float) {
        motion?.cancel()
        phase -= round(rest)
        motion = scope.launch {
            animate(phase, 0f, velocity, spring(dampingRatio = 0.75f, stiffness = 600f)) { value, _ -> phase = value }
        }
    }

    private fun fadeLit(to: Float, millis: Int, fadeIn: Boolean) {
        glow?.cancel()
        if (lit == to) return
        val easing = if (fadeIn) LinearOutSlowInEasing else FastOutSlowInEasing
        glow = scope.launch { animate(lit, to, animationSpec = tween(millis, easing = easing)) { value, _ -> lit = value } }
    }
}

internal class RibLook(density: Density, outline: Color, primary: Color) {
    val rest = outline.copy(alpha = 0.32f)
    val hot = primary.copy(alpha = 0.85f)
    val gap: Float
    val restHalf: Float
    val hotHalf: Float
    val thickness: Float
    val edge: Float
    val sigma: Float
    val blurWidth: Float
    val blur: Brush

    init {
        with(density) {
            gap = STEP_DP.dp.toPx()
            restHalf = 4.dp.toPx()
            hotHalf = 5.5.dp.toPx()
            thickness = 1.5.dp.toPx()
            edge = 33.dp.toPx()
            sigma = 56.dp.toPx()
            blurWidth = 2.dp.toPx()
        }
        blur = Brush.verticalGradient(
            0f to primary.copy(alpha = 0f),
            0.5f to primary,
            1f to primary.copy(alpha = 0f),
            startY = -2 * sigma,
            endY = 2 * sigma,
        )
    }
}

internal fun DrawScope.drawRibs(wheel: ScrollWheel, look: RibLook, x: Float, top: Float) {
    val height = size.height
    val gap = look.gap
    val lit = wheel.lit
    val glowY = wheel.glowY
    val streak = smoothstep(STREAK_FROM - 4f, STREAK_FROM + 4f, wheel.speed)
    val dim = lerp(1f, 0.4f, streak)
    val twoSigmaSquared = 2 * look.sigma * look.sigma
    val shift = (wheel.phase - floor(wheel.phase)) * gap
    var y = top + shift - gap * (floor((top + shift) / gap) + 1)
    while (y <= height) {
        val fade = smoothstep(0f, look.edge, min(y, height - y))
        if (fade > 0f) {
            val heat = if (lit > 0f) lit * exp(-(y - glowY) * (y - glowY) / twoSigmaSquared) else 0f
            val half = lerp(look.restHalf, look.hotHalf, heat)
            drawLine(
                color = mix(look.rest, look.hot, heat),
                start = Offset(x - half, y),
                end = Offset(x + half, y),
                strokeWidth = look.thickness,
                cap = StrokeCap.Round,
                alpha = fade * dim,
            )
        }
        y += gap
    }
    val blur = streak * lit
    if (blur > 0.01f) {
        val reach = 2 * look.sigma
        translate(top = glowY) {
            drawLine(
                brush = look.blur,
                start = Offset(x, (-reach).coerceAtLeast(-glowY)),
                end = Offset(x, reach.coerceAtMost(height - glowY)),
                strokeWidth = look.blurWidth,
                cap = StrokeCap.Round,
                alpha = blur,
            )
        }
    }
}

internal fun mix(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)
