package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.data.DEFAULT_TV_NAME
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.icons.LocalIconLoader
import lt.tsx.tennarc.icons.LocalPosterLoader
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.ground
import lt.tsx.tennarc.ui.theme.seeThrough

private const val REPEAT_DELAY_MS = 380L
private const val REPEAT_INTERVAL_MS = 85L
const val DISABLED_ALPHA = 0.38f

fun Modifier.dimmedUnless(enabled: Boolean): Modifier = graphicsLayer {
    alpha = if (enabled) 1f else DISABLED_ALPHA
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

class Haptics(private val feedback: HapticFeedback?, private val enabled: Boolean) {
    fun key() = perform(HapticFeedbackType.VirtualKey)
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    fun faintTick() = perform(HapticFeedbackType.SegmentFrequentTick)
    fun confirm() = perform(HapticFeedbackType.Confirm)
    fun reject() = perform(HapticFeedbackType.Reject)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback?.performHapticFeedback(type)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null, enabled = false) }

val LocalTvName = staticCompositionLocalOf { DEFAULT_TV_NAME }

@Composable
fun ProvideTennaEnvironment(
    haptics: Boolean,
    iconLoader: IconLoader,
    tvName: String = DEFAULT_TV_NAME,
    posterLoader: IconLoader = IconLoader { null },
    content: @Composable () -> Unit,
) {
    val feedback = LocalHapticFeedback.current
    val provided = remember(feedback, haptics) { Haptics(feedback, haptics) }
    CompositionLocalProvider(
        LocalHaptics provides provided,
        LocalIconLoader provides iconLoader,
        LocalPosterLoader provides posterLoader,
        LocalTvName provides tvName,
        content = content,
    )
}

fun Modifier.pressAndRepeat(
    enabled: Boolean,
    repeat: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onHold: (() -> Unit)? = null,
    onFire: (repeated: Boolean) -> Unit,
): Modifier = if (!enabled) this else pointerInput(repeat, onHold != null) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown()
            onPressedChange(true)
            var holding = false
            val repeater = if (repeat) {
                launch {
                    delay(REPEAT_DELAY_MS)
                    holding = true
                    onFire(false)
                    while (true) {
                        delay(REPEAT_INTERVAL_MS)
                        onFire(true)
                    }
                }
            } else if (onHold != null) {
                launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    holding = true
                    onHold()
                }
            } else {
                null
            }
            try {
                val slop = viewConfiguration.touchSlop
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (holding) {
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) break
                        continue
                    }
                    if (!change.pressed) {
                        change.consume()
                        onFire(false)
                        break
                    }
                    if (change.isConsumed || (change.position - down.position).getDistance() > slop) break
                }
            } finally {
                repeater?.cancel()
                onPressedChange(false)
            }
        }
    }
}

@Composable
fun KeyButton(
    onPress: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeat: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    pressedColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    dimWhenDisabled: Boolean = true,
    well: Dp? = null,
    wellCorner: Float? = null,
    wellAt: Float = 0.5f,
    buzz: Boolean = true,
    role: Role = Role.Button,
    onHold: (() -> Unit)? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val hold by rememberUpdatedState(onHold)
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val fire by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val wellPath = remember { SquircleCache() }
    val resting = if (well != null && color.alpha == 0f) pressedColor.copy(alpha = 0f) else color
    val ground = ground()
    val lit = animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 40 else 180), label = "keyColor")
    val background = { seeThrough(lerp(resting, pressedColor, lit.value), ground) }
    val scale by animateFloatAsState(
        targetValue = if (!pressed) 1f else if (well == null) 0.94f else 0.9f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh),
        label = "keyScale",
    )
    Box(
        modifier = (if (well == null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .then(modifier)
            .graphicsLayer { alpha = if (enabled || !dimWhenDisabled) 1f else DISABLED_ALPHA }
            .clip(shape)
            .then(
                if (well == null) {
                    Modifier.drawBehind { drawRect(background()) }
                } else {
                    Modifier.drawBehind {
                        val fill = background()
                        if (fill.alpha <= 0f) return@drawBehind
                        val across = well.toPx()
                        val middle = size.width * if (layoutDirection == LayoutDirection.Rtl) 1f - wellAt else wellAt
                        if (wellCorner == null) {
                            drawCircle(fill, across / 2, Offset(middle, size.height / 2))
                        } else {
                            drawPath(wellPath.of(middle - across / 2f, (size.height - across) / 2f, across, across, across * wellCorner), fill)
                        }
                    }
                },
            )
            .pressAndRepeat(enabled, repeat, onPressedChange = { pressed = it }, onHold = if (onHold != null) { { hold?.invoke() } } else null) { repeated ->
                if (buzz) {
                    if (repeated) haptics.tick() else haptics.key()
                }
                fire()
            }
            .semantics(mergeDescendants = true) {
                this.role = role
                this.contentDescription = contentDescription
                if (enabled) {
                    onClick {
                        fire()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (well == null) {
            content(pressed)
        } else {
            Box(
                Modifier
                    .then(if (wellAt == 0.5f) Modifier else Modifier.standingAt(wellAt))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                content(pressed)
            }
        }
    }
}

private fun Modifier.standingAt(at: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = if (constraints.hasBoundedWidth) ((at - 0.5f) * constraints.maxWidth).roundToInt() else 0
    layout(placeable.width, placeable.height) { placeable.placeRelative(shift, 0) }
}

private val TILE_LABEL_MIN = 10.sp

@Composable
@ReadOnlyComposable
fun pickedFill(base: Color): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f).compositeOver(base)

@Composable
fun SegmentKey(
    onPress: () -> Unit,
    label: String,
    enabled: Boolean,
    glow: Color,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    selectedColor: Color = Color.Transparent,
    onClickLabel: String? = null,
    inset: Dp = 0.dp,
    corner: Dp = 0.dp,
    ground: Color = ground(),
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val fire by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val lit by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 40 else 180), label = "segmentGlow")
    val picked by animateFloatAsState(if (selected == true) 1f else 0f, tween(180), label = "segmentPicked")
    val glowTint = seeThrough(glow, ground)
    val pickTint = seeThrough(selectedColor, ground)
    Box(
        modifier
            .drawWithCache {
                val edge = inset.toPx()
                val block = Size(size.width - edge * 2, size.height - edge * 2)
                val well = Path().apply { if (block.width > 0f && block.height > 0f) addSquircle(Rect(Offset(edge, edge), block), corner.toPx()) }
                onDrawBehind {
                    if (picked > 0f) drawPath(well, pickTint, alpha = picked)
                    if (lit > 0f) drawPath(well, glowTint, alpha = lit)
                }
            }
            .pressAndRepeat(enabled, repeat = false, onPressedChange = { pressed = it }) {
                haptics.key()
                fire()
            }
            .semantics(mergeDescendants = true) {
                this.role = if (selected != null) Role.RadioButton else Role.Button
                contentDescription = label
                if (selected != null) this.selected = selected
                if (enabled) {
                    onClick(onClickLabel) {
                        fire()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(pressed)
    }
}

