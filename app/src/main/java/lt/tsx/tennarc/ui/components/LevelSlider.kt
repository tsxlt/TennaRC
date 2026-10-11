package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.grainToRestore

@Composable
fun LevelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    detent: Float? = null,
    label: String? = null,
    valueLabel: String? = null,
    contentDescription: String? = label,
    onValueChangeFinished: (() -> Unit)? = null,
    fillColor: Color? = null,
    trackColor: Color? = null,
    shape: Shape = MaterialTheme.shapes.large,
    height: Dp = CONTROL_HEIGHT,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    iconSize: Dp = 20.dp,
) {
    val colors = MaterialTheme.colorScheme
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val span = valueRange.endInclusive - valueRange.start
    fun fraction(v: Float) = if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange)
        if (detent != null && abs(fraction(clamped) - fraction(detent)) < DETENT_PULL) return detent
        if (steps <= 0) return clamped
        val step = span / (steps + 1)
        return valueRange.start + ((clamped - valueRange.start) / step).roundToInt() * step
    }
    fun reachesTenth(from: Float, to: Float): Boolean {
        val a = fraction(from) * 10
        val b = fraction(to) * 10
        return when {
            b > a -> floor(b + EPSILON) > floor(a + EPSILON)
            b < a -> ceil(b - EPSILON) < ceil(a - EPSILON)
            else -> false
        }
    }

    var dragging by remember { mutableStateOf(false) }
    val fill = remember { Animatable(fraction(value)) }
    LaunchedEffect(value, dragging) {
        if (dragging) {
            fill.snapTo(fraction(value))
        } else {
            fill.animateTo(fraction(value), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val tint = fillColor ?: colors.onSurface.copy(alpha = FILL_ALPHA)
    val pane = if (trackColor == null) panelGlass() else null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .dimmedUnless(enabled)
            .clip(shape)
            .then(
                if (trackColor == null) {
                    Modifier
                } else {
                    Modifier.background(trackColor).frostBehind(grainToRestore(trackColor.alpha))
                },
            )
            .drawBehind {
                val width = size.width * fill.value
                if (width > 0f) drawRect(tint, topLeft = Offset(if (rtl) size.width - width else 0f, 0f), size = Size(width, size.height))
            }
            .then(if (pane != null) Modifier.glass(pane, shape) else Modifier)
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(valueRange, steps, rtl) {
                            var at = 0f
                            var shown = 0f
                            fun move(dx: Float) {
                                at = (at + (if (rtl) -dx else dx) / size.width * span).coerceIn(valueRange)
                                val next = snap(at)
                                if (reachesTenth(shown, next)) haptics.tick()
                                shown = next
                                change(next)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var overSlop = 0f
                                val slipped = awaitHorizontalTouchSlopOrCancellation(down.id) { slip, over ->
                                    slip.consume()
                                    overSlop = over
                                } ?: return@awaitEachGesture
                                dragging = true
                                at = current
                                shown = current
                                move(overSlop)
                                try {
                                    holdSideways(slipped.id, ::move)
                                } finally {
                                    dragging = false
                                    finish?.invoke()
                                }
                            }
                        }
                        .pointerInput(valueRange, steps, rtl) {
                            detectTapGestures { offset ->
                                val tapped = (if (rtl) size.width - offset.x else offset.x) / size.width
                                val nudge = if (steps > 0) span / (steps + 1) else span * NUDGE
                                val next = snap(current + if (tapped > fraction(current)) nudge else -nudge)
                                haptics.tick()
                                change(next)
                                finish?.invoke()
                            }
                        }
                },
            )
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (valueLabel != null) stateDescription = valueLabel
                if (enabled) {
                    setProgress { target ->
                        change(snap(target))
                        finish?.invoke()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(iconSize).clearAndSetSemantics {})
        if (label != null) {
            Spacer(Modifier.size(LABEL_GAP))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (valueLabel != null) {
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

internal suspend fun AwaitPointerEventScope.holdSideways(pointerId: PointerId, onDrag: (Float) -> Unit) {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val held = event.changes.fastFirstOrNull { it.id == pointer } ?: return
        if (held.isConsumed) return
        if (held.changedToUpIgnoreConsumed()) {
            held.consume()
            pointer = event.changes.fastFirstOrNull { it.pressed }?.id ?: return
            continue
        }
        val dx = held.positionChange().x
        held.consume()
        if (dx != 0f) onDrag(dx)
    }
}

private val LABEL_GAP = 14.dp

private const val DETENT_PULL = 0.012f

private const val EPSILON = 1e-4f

private const val FILL_ALPHA = 0.14f

private const val NUDGE = 0.1f
