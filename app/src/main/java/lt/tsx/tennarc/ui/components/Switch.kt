package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.ui.theme.TennaLight
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.darkness
import lt.tsx.tennarc.ui.theme.drawFrost
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.grainToRestore
import lt.tsx.tennarc.ui.theme.rimBrush
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.tanh

@Composable
fun GlassSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: InteractionSource? = null,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.darkness
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val change by rememberUpdatedState(onCheckedChange)
    val current by rememberUpdatedState(checked)
    val scope = rememberCoroutineScope()
    val motion = remember { SwitchMotion(checked) }

    val target = motion.landing ?: checked
    LaunchedEffect(checked) { motion.landing = null }
    LaunchedEffect(motion.landing) {
        if (motion.landing != null) {
            delay(SWITCH_LANDING_MS)
            motion.landing = null
        }
    }
    LaunchedEffect(target, motion.dragging) {
        if (!motion.dragging) motion.position.animateTo(if (target) 1f else 0f, SWITCH_SPRING)
    }
    val lit = if (motion.dragging) motion.side else target
    LaunchedEffect(lit) { motion.fill.animateTo(if (lit) 1f else 0f, SWITCH_FADE) }
    LaunchedEffect(interactionSource) {
        interactionSource?.interactions?.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> motion.press(true, scope)
                is PressInteraction.Release, is PressInteraction.Cancel -> motion.press(false, scope)
            }
        }
    }

    val thumb = lerp(Color.White, colors.onSurface, dark)
    val thumbFoot = lerp(thumb, Color.Black, SWITCH_THUMB_SHADE)
    val thumbEdge = Color.Black.copy(alpha = lerp(SWITCH_EDGE_LIGHT, SWITCH_EDGE_DARK, dark))
    val off = colors.onSurface.copy(alpha = lerp(SWITCH_TRACK_LIGHT, SWITCH_TRACK_DARK, dark))
    val on = lerp(colors.primary, TennaLight.Ember, SWITCH_ON_DEEPEN * dark)
    val onTop = lerp(on, Color.White, SWITCH_ON_LIFT)
    val onFoot = lerp(on, Color.Black, SWITCH_ON_SHADE)
    val pane = glass(Color.Transparent, floating = true)
    val shadow = Color.Black.copy(alpha = lerp(SWITCH_SHADOW_LIGHT, SWITCH_SHADOW_DARK, dark))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val grain = if (enabled) 1f else 0f

    Box(
        modifier
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .dimmedUnless(enabled)
            .then(
                if (!enabled || onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(rtl) {
                        dragThumb(motion, rtl, scope, tick = { haptics.tick() }) { to ->
                            if (to != current) {
                                motion.landing = to
                                change?.invoke(to)
                            }
                        }
                    }
                },
            )
            .drawWithCache {
                val w = size.width
                val h = size.height
                val hairline = 1.dp.toPx()
                val inset = SWITCH_THUMB_INSET.toPx()
                val radius = h / 2f - inset
                val across = radius * 2f
                val track = Path().apply { addSquircle(Rect(Offset.Zero, size), h / 2f) }
                val rim = Path().apply { addSquircle(Rect(hairline / 2f, hairline / 2f, w - hairline / 2f, h - hairline / 2f), h / 2f - hairline / 2f) }
                val rimLight = pane.rimBrush(0f, h)
                val rimStroke = Stroke(hairline)
                val onFill = Brush.verticalGradient(listOf(onTop, on, onFoot), startY = 0f, endY = h)
                val thumbFill = Brush.verticalGradient(listOf(thumb, thumbFoot), startY = inset, endY = h - inset)
                val thumbRim = Brush.verticalGradient(listOf(thumbEdge.copy(alpha = thumbEdge.alpha * 0.4f), thumbEdge), startY = inset, endY = h - inset)
                val thumbStroke = Stroke(hairline * SWITCH_EDGE_WIDTH)
                val blur = SWITCH_SHADOW_BLUR.toPx()
                val reach = radius + blur * 3f
                val drop = SWITCH_SHADOW_DROP.toPx()
                val round = Brush.radialGradient(*shadowStops(radius, blur, reach, shadow, mirrored = false), center = Offset.Zero, radius = reach)
                val flat = Brush.verticalGradient(*shadowStops(radius, blur, reach, shadow, mirrored = true), startY = -reach, endY = reach)
                onDrawBehind {
                    val f = motion.fill.value
                    if (f < 1f) drawPath(track, off)
                    if (f > 0f) {
                        drawPath(track, onFill, alpha = f)
                        drawFrost(grainToRestore(f) * grain) { grain, alpha, filter, blend -> drawPath(track, grain, alpha, colorFilter = filter, blendMode = blend) }
                    }
                    drawPath(rim, rimLight, alpha = SWITCH_RIM, style = rimStroke)

                    val long = across * (1f + SWITCH_STRETCH * motion.squeeze.value)
                    val along = inset + (w - inset * 2f - long) * motion.at
                    val left = if (layoutDirection == LayoutDirection.Ltr) along else w - along - long
                    drawShadow(round, flat, left + radius, left + long - radius, h / 2f + drop, reach)
                    val corner = CornerRadius(radius)
                    val at = Offset(left, inset)
                    val shape = Size(long, across)
                    drawRoundRect(thumbFill, at, shape, corner)
                    drawFrost(SWITCH_THUMB_GRAIN * grain) { grain, alpha, filter, blend -> drawRoundRect(grain, at, shape, corner, alpha = alpha, colorFilter = filter, blendMode = blend) }
                    val edge = thumbStroke.width / 2f
                    drawRoundRect(
                        thumbRim,
                        Offset(left + edge, inset + edge),
                        Size(long - edge * 2f, across - edge * 2f),
                        CornerRadius(radius - edge),
                        style = thumbStroke,
                    )
                }
            },
    )
}

private class SwitchMotion(on: Boolean) {
    val position = Animatable(if (on) 1f else 0f)
    val fill = Animatable(if (on) 1f else 0f)
    val squeeze = Animatable(0f)
    var dragging by mutableStateOf(false)
    var dragAt by mutableFloatStateOf(0f)

    var side by mutableStateOf(on)

    var landing by mutableStateOf<Boolean?>(null)
    private var pressed = false

    val at: Float get() = if (dragging) dragAt else position.value

    fun press(down: Boolean, scope: CoroutineScope) {
        pressed = down
        squeeze(scope)
    }

    fun squeeze(scope: CoroutineScope) {
        val held = pressed || dragging
        scope.launch {
            squeeze.animateTo(1f, SWITCH_SQUEEZE)
            if (!held) squeeze.animateTo(0f, SWITCH_LET_GO)
        }
    }
}

private suspend fun PointerInputScope.dragThumb(motion: SwitchMotion, rtl: Boolean, scope: CoroutineScope, tick: () -> Unit, lands: (Boolean) -> Unit) {
    fun move(dx: Float) {
        val inset = SWITCH_THUMB_INSET.toPx()
        val travel = size.width - inset * 2f - (size.height - inset * 2f) * (1f + SWITCH_STRETCH)
        motion.dragAt = (motion.dragAt + (if (rtl) -dx else dx) / travel).coerceIn(0f, 1f)
        val side = motion.dragAt >= 0.5f
        if (side != motion.side) {
            motion.side = side
            tick()
        }
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var overSlop = 0f
        val slipped = awaitHorizontalTouchSlopOrCancellation(down.id) { slip, over ->
            slip.consume()
            overSlop = over
        } ?: return@awaitEachGesture
        motion.dragAt = motion.position.value
        motion.side = motion.dragAt >= 0.5f
        motion.dragging = true
        motion.squeeze(scope)
        try {
            move(overSlop)
            holdSideways(slipped.id, ::move)
        } catch (gone: CancellationException) {
            motion.dragging = false
            motion.squeeze(scope)
            throw gone
        }
        val to = motion.dragAt >= 0.5f
        scope.launch {
            motion.position.snapTo(motion.dragAt)
            lands(to)
            motion.dragging = false
            motion.squeeze(scope)
        }
    }
}

private fun DrawScope.drawShadow(round: Brush, flat: Brush, from: Float, to: Float, y: Float, reach: Float) {
    val start = round(from)
    val end = round(to)
    if (end - start < 1f) {
        translate(from, y) { drawCircle(round, reach, Offset.Zero) }
        return
    }
    val far = reach * 2f
    clipRect(start - far, y - far, start, y + far) { translate(start, y) { drawCircle(round, reach, Offset.Zero) } }
    clipRect(end, y - far, end + far, y + far) { translate(end, y) { drawCircle(round, reach, Offset.Zero) } }
    translate(0f, y) { drawRect(flat, Offset(start, -reach), Size(end - start, reach * 2f)) }
}

private fun shadowStops(radius: Float, blur: Float, reach: Float, color: Color, mirrored: Boolean): Array<Pair<Float, Color>> {
    val steps = SHADOW_STOPS
    fun shade(d: Float) = color.copy(alpha = color.alpha * normalCdf((radius - d) / blur))
    return if (!mirrored) {
        Array(steps + 1) { i ->
            val p = i / steps.toFloat()
            p to shade(p * reach)
        }
    } else {
        Array(steps * 2 + 1) { i ->
            val p = i / (steps * 2f)
            p to shade(abs(p * 2f - 1f) * reach)
        }
    }
}

private fun normalCdf(x: Float): Float = 0.5f * (1f + tanh(0.7978846f * (x + 0.044715f * x * x * x)))

private val SWITCH_WIDTH = 51.dp
private val SWITCH_HEIGHT = 31.dp
private val SWITCH_THUMB_INSET = 2.dp

private const val SWITCH_STRETCH = 0.24f

private const val SWITCH_TRACK_LIGHT = 0.11f
private const val SWITCH_TRACK_DARK = 0.17f

private const val SWITCH_ON_DEEPEN = 0.5f

private const val SWITCH_ON_LIFT = 0.1f
private const val SWITCH_ON_SHADE = 0.06f

private const val SWITCH_RIM = 0.6f

private const val SWITCH_THUMB_GRAIN = 0.55f

private const val SWITCH_THUMB_SHADE = 0.05f

private const val SWITCH_EDGE_LIGHT = 0.1f
private const val SWITCH_EDGE_DARK = 0.16f
private const val SWITCH_EDGE_WIDTH = 0.6f

private const val SWITCH_SHADOW_LIGHT = 0.2f
private const val SWITCH_SHADOW_DARK = 0.38f
private val SWITCH_SHADOW_BLUR = 2.dp
private val SWITCH_SHADOW_DROP = 1.5.dp
private const val SHADOW_STOPS = 12

private const val SWITCH_LANDING_MS = 600L

private val SWITCH_SPRING = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
private val SWITCH_FADE = tween<Float>(180, easing = FastOutSlowInEasing)
private val SWITCH_SQUEEZE = spring<Float>(dampingRatio = 0.85f, stiffness = 900f)
private val SWITCH_LET_GO = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
