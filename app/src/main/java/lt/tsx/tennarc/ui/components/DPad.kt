package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.ui.theme.Glass
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.drawFrost
import lt.tsx.tennarc.ui.theme.rimBrush
import lt.tsx.tennarc.ui.theme.sheenBrush
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private const val CENTER_RATIO = 0.40f

private const val ROLL_BIAS = 1.35f

private val CHEVRON_HALF = 9.dp
private val CHEVRON_DEPTH = 9.dp
private val CHEVRON_STROKE = 2.5.dp

private val CHEVRON_NUDGE = 3.dp

private const val HUB_SINK = 0.04f
private const val GLOW_ALPHA = 0.22f

private val SHEEN_DEPTH = 112.dp

private val PRESS_IN = spring<Float>(dampingRatio = 1f, stiffness = 2200f)
private val PRESS_OUT = spring<Float>(dampingRatio = 1f, stiffness = 260f)

@Composable
@ReadOnlyComposable
internal fun dpadGlass(colors: ColorScheme): Glass = clearGlass(colors.surfaceContainerLow, floating = true, sheen = 1f)

private enum class Zone(val button: TvButton, val arcStart: Float, val iconAngle: Float, val unit: Offset, val label: Int) {
    UP(TvButton.UP, 225f, 0f, Offset(0f, -1f), R.string.key_up),
    RIGHT(TvButton.RIGHT, -45f, 90f, Offset(1f, 0f), R.string.key_right),
    DOWN(TvButton.DOWN, 45f, 180f, Offset(0f, 1f), R.string.key_down),
    LEFT(TvButton.LEFT, 135f, 270f, Offset(-1f, 0f), R.string.key_left),
    OK(TvButton.ENTER, 0f, 0f, Offset.Zero, R.string.key_ok),
}

private val ARROWS = listOf(Zone.UP, Zone.RIGHT, Zone.DOWN, Zone.LEFT)

private fun zoneAt(position: Offset, width: Float, height: Float, bias: Float = 1f): Zone? {
    val dx = position.x - width / 2
    val dy = position.y - height / 2
    val radius = min(width, height) / 2
    val distance = hypot(dx, dy)
    return when {
        distance > radius -> null
        distance < radius * CENTER_RATIO -> Zone.OK
        abs(dx) > abs(dy) * bias -> if (dx > 0) Zone.RIGHT else Zone.LEFT
        abs(dy) > abs(dx) * bias -> if (dy > 0) Zone.DOWN else Zone.UP
        else -> null
    }
}

@Composable
fun DPad(onPress: (TvButton) -> Unit, enabled: Boolean, modifier: Modifier = Modifier, drawGlass: Boolean = true) {
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val press by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    val glows = remember { Zone.entries.associateWith { Animatable(0f) } }
    val lighting = remember { mutableMapOf<Zone, Job>() }

    fun lightUp(zone: Zone) {
        lighting[zone] = scope.launch { glows.getValue(zone).animateTo(1f, PRESS_IN) }
    }

    fun letGo(zone: Zone) {
        val lit = lighting[zone]
        scope.launch {
            lit?.join()
            if (lighting[zone] === lit) glows.getValue(zone).animateTo(0f, PRESS_OUT)
        }
    }

    val colors = MaterialTheme.colorScheme
    val glass = dpadGlass(colors)
    val ink = colors.onSurfaceVariant
    val accent = colors.primary
    val description = stringResource(R.string.dpad_label)
    val labels = Zone.entries.associateWith { stringResource(it.label) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    fun hold(zone: Zone): Job? {
                        haptics.key()
                        press(zone.button)
                        lightUp(zone)
                        if (zone == Zone.OK) return null
                        return launch {
                            delay(380)
                            while (true) {
                                haptics.tick()
                                press(zone.button)
                                delay(85)
                            }
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        var zone = zoneAt(down.position, width, height) ?: return@awaitEachGesture
                        down.consume()
                        var repeater = hold(zone)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                                if (zone == Zone.OK) continue
                                val at = event.changes.firstOrNull { it.id == down.id }?.position ?: continue
                                val next = zoneAt(at, width, height, ROLL_BIAS)
                                if (next != null && next != Zone.OK && next != zone) {
                                    repeater?.cancel()
                                    letGo(zone)
                                    zone = next
                                    repeater = hold(zone)
                                }
                            }
                        } finally {
                            repeater?.cancel()
                            letGo(zone)
                        }
                    }
                }
            }
            .semantics {
                contentDescription = description
                if (enabled) {
                    customActions = Zone.entries.map { zone ->
                        CustomAccessibilityAction(labels.getValue(zone)) {
                            press(zone.button)
                            true
                        }
                    }
                } else {
                    disabled()
                }
            }
            .drawWithCache {
                val center = size.center
                val radius = size.minDimension / 2
                val inner = radius * CENTER_RATIO
                val middle = (radius + inner) / 2
                val half = CHEVRON_HALF.toPx()
                val depth = CHEVRON_DEPTH.toPx()
                val chevron = Path().apply {
                    moveTo(-half, depth / 2)
                    lineTo(0f, -depth / 2)
                    lineTo(half, depth / 2)
                }
                val stroke = Stroke(CHEVRON_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val nudge = CHEVRON_NUDGE.toPx()
                val lights = ARROWS.associateWith { zone ->
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = GLOW_ALPHA), accent.copy(alpha = 0f)),
                        center = center + zone.unit * middle,
                        radius = radius * 0.8f,
                    )
                }
                val rim = Stroke(1.dp.toPx())
                val rimRadius = radius - rim.width / 2
                val edge = glass.rimBrush(center.y - radius, center.y + radius)
                val sheen = glass.sheenBrush(center.y - radius, center.y - radius + SHEEN_DEPTH.toPx())
                val band = Stroke(radius - inner)
                val bandCorner = Offset(center.x - middle, center.y - middle)
                val bandSquare = Size(middle * 2, middle * 2)
                onDrawBehind {
                    if (drawGlass) {
                        if (glass.fill.alpha > 0f) drawCircle(glass.fill, radius, center)
                        if (glass.sheen.alpha > 0f) drawCircle(sheen, radius, center)
                        drawFrost(glass.grain) { grain, alpha, filter, blend -> drawCircle(grain, radius, center, alpha, colorFilter = filter, blendMode = blend) }
                        drawCircle(edge, rimRadius, center, style = rim)
                    }
                    for (zone in ARROWS) {
                        val lit = glows.getValue(zone).value
                        if (lit > 0f) {
                            drawArc(lights.getValue(zone), zone.arcStart, 90f, useCenter = false, topLeft = bandCorner, size = bandSquare, alpha = lit, style = band)
                        }
                    }
                    for (zone in ARROWS) {
                        val lit = glows.getValue(zone).value
                        val at = center + zone.unit * (middle + nudge * lit)
                        translate(at.x, at.y) {
                            rotate(zone.iconAngle, pivot = Offset.Zero) {
                                drawPath(chevron, lerp(ink, accent, lit), style = stroke)
                            }
                        }
                    }
                    val sunk = glows.getValue(Zone.OK).value
                    val hubRadius = inner * (1f - HUB_SINK * sunk)
                    if (sunk > 0f) drawCircle(accent.copy(alpha = GLOW_ALPHA * sunk), hubRadius, center)
                }
            },
    ) {
        BasicText(
            text = labels.getValue(Zone.OK),
            style = MaterialTheme.typography.titleMedium,
            color = { lerp(ink, accent, glows.getValue(Zone.OK).value) },
            modifier = Modifier.graphicsLayer {
                val sunk = glows.getValue(Zone.OK).value
                val scale = 1f - HUB_SINK * sunk
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
