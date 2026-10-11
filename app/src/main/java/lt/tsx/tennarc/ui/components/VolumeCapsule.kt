package lt.tsx.tennarc.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import lt.tsx.tennarc.ui.theme.addSquircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.rimBrush
import lt.tsx.tennarc.ui.theme.seeThrough

private val READOUT_SLOT = 40.dp

private val READOUT_KEY = 48.dp

private const val ROLL_MS = 140

private const val ROLL_GAP_MS = 120L

private const val NUDGE_SCALE = 1.15f

private const val POP_SCALE = 1.2f

sealed interface VolumeReadout {
    data class Level(val level: Int) : VolumeReadout

    data object Pending : VolumeReadout

    data object Unsupported : VolumeReadout

    data object Muted : VolumeReadout

    companion object {
        fun of(volume: Int?, muted: Boolean, reportsLevel: Boolean): VolumeReadout = when {
            muted -> Muted
            volume != null -> Level(volume)
            reportsLevel -> Pending
            else -> Unsupported
        }
    }
}

@Composable
internal fun VolumeCapsule(
    readout: VolumeReadout,
    enabled: Boolean,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onMute: (Boolean) -> Unit,
    nudges: Flow<Int>,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val metrics = LocalDeckMetrics.current
    val muted = readout == VolumeReadout.Muted
    val base = colors.surfaceContainer
    val fill = animateColorBetween(muted, base, colors.error.copy(alpha = MUTED_FILL).compositeOver(base), tween(MUTE_MS), label = "capsule")
    val stepTint = animateColorBetween(muted, colors.onSurfaceVariant, colors.error, tween(MUTE_MS), label = "capsuleKeys")
    val pressedWell = if (muted) colors.error.copy(alpha = MUTED_WELL) else colors.onSurface.copy(alpha = PRESSED_WELL)
    val meterInk = colors.onSurface.copy(alpha = METER_ALPHA)
    val glass = glass(seeThrough(fill), floating = true, sheen = 0f, grain = 0f)

    val level = (readout as? VolumeReadout.Level)?.level
    val reach = remember { Animatable(meterFraction(level ?: 0)) }
    val meter = remember { Animatable(0f) }
    val pokes = remember { mutableIntStateOf(0) }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(Unit) {
        snapshotFlow { pokes.intValue }.collectLatest { poked ->
            if (poked == 0 || currentLevel == null) return@collectLatest
            meter.animateTo(1f, tween(METER_IN_MS))
            delay(METER_HOLD_MS)
            meter.animateTo(0f, tween(METER_OUT_MS))
        }
    }
    LaunchedEffect(level == null) { if (level == null) meter.animateTo(0f, tween(METER_OUT_MS)) }
    val heard = remember { intArrayOf(level ?: -1) }
    LaunchedEffect(level) {
        if (level == null) return@LaunchedEffect
        val before = heard[0]
        heard[0] = level
        if (meter.value > 0f) {
            launch { reach.animateTo(meterFraction(level), spring(dampingRatio = 1f, stiffness = METER_STIFFNESS)) }
        } else {
            reach.snapTo(meterFraction(level))
        }
        if (before >= 0 && before != level) pokes.intValue++
    }

    val up = remember { Animatable(1f) }
    val down = remember { Animatable(1f) }
    val upGlyph = remember(up) { Modifier.scaled { up.value } }
    val downGlyph = remember(down) { Modifier.scaled { down.value } }
    val scope = rememberCoroutineScope()
    val rock = rememberRockerState()
    fun nudge(direction: Int) {
        pokes.intValue++
        val bounce = if (direction > 0) up else down
        scope.launch {
            bounce.snapTo(NUDGE_SCALE)
            bounce.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
        }
    }
    LaunchedEffect(nudges) {
        nudges.collect { direction ->
            nudge(direction)
            rock.tap(direction)
        }
    }
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val volumeDown by rememberUpdatedState(onVolumeDown)
    val volumeUp by rememberUpdatedState(onVolumeUp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier
            .rocker(rock)
            .pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                val stepPx = VOLUME_SLIDE_STEP.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    var anchor = 0f
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                        val moved = change.position - down.position
                        if (moved.getDistance() <= slop) continue
                        if (abs(moved.x) <= abs(moved.y)) return@awaitEachGesture
                        change.consume()
                        anchor = down.position.x
                        break
                    }
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        var along = (change.position.x - anchor) * if (rtl) -1f else 1f
                        while (along >= stepPx) {
                            nudge(1)
                            rock.fire(1)
                            haptics.tick()
                            volumeUp()
                            anchor += if (rtl) -stepPx else stepPx
                            along -= stepPx
                        }
                        while (along <= -stepPx) {
                            nudge(-1)
                            rock.fire(-1)
                            haptics.tick()
                            volumeDown()
                            anchor -= if (rtl) -stepPx else stepPx
                            along += stepPx
                        }
                    }
                }
            }
            .drawWithCache {
            val height = metrics.capsule.toPx()
            val top = (size.height - height) / 2
            val capsule = Path().apply {
                addSquircle(Rect(Offset(0f, top), Size(size.width, height)), height / 2)
            }
            val hairline = 1.dp.toPx()
            val half = hairline / 2
            val edge = Path().apply {
                addSquircle(Rect(half, top + half, size.width - half, top + height - half), height / 2 - half)
            }
            val rim = glass.rimBrush(top, top + height)
            val stroke = Stroke(hairline)
            onDrawBehind {
                drawPath(capsule, glass.fill)
                val shown = meter.value
                val width = size.width * reach.value
                if (shown > 0f && width > 0f) {
                    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    clipPath(capsule) {
                        drawRect(meterInk, Offset(start, top), Size(width, height), alpha = shown)
                    }
                }
                drawPath(edge, rim, style = stroke)
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val step = Modifier.weight(1f).fillMaxHeight()
        DeckKey(
            Lucide.Minus, stringResource(R.string.key_volume_down), { nudge(-1); rock.fire(-1); onVolumeDown() }, enabled, step,
            tint = stepTint, repeat = true, well = metrics.capsuleWell, wellCorner = null, pressedColor = pressedWell, glyph = downGlyph,
            onPressedChange = { if (it) rock.press(-1) else rock.release(-1) },
        )
        VolumeReadoutKey(
            readout, enabled,
            onMute = {
                rock.thump()
                onMute(it)
            },
            pressedWell, Modifier.width(READOUT_KEY).fillMaxHeight(),
        )
        DeckKey(
            Lucide.Plus, stringResource(R.string.key_volume_up), { nudge(1); rock.fire(1); onVolumeUp() }, enabled, step,
            tint = stepTint, repeat = true, well = metrics.capsuleWell, wellCorner = null, pressedColor = pressedWell, glyph = upGlyph,
            onPressedChange = { if (it) rock.press(1) else rock.release(1) },
        )
    }
}

private val VOLUME_SLIDE_STEP = 14.dp

private const val MUTED_FILL = 0.16f

private const val MUTED_WELL = 0.18f

private const val PRESSED_WELL = 0.10f

private fun meterFraction(level: Int): Float = (level / 100f).coerceIn(0f, 1f)

private const val METER_ALPHA = 0.14f

private const val METER_IN_MS = 120

private const val METER_OUT_MS = 800

private const val METER_HOLD_MS = 2_500L

private const val METER_STIFFNESS = 500f

@Composable
private fun VolumeReadoutKey(readout: VolumeReadout, enabled: Boolean, onMute: (Boolean) -> Unit, pressedColor: Color, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val muted = readout == VolumeReadout.Muted
    val description = when (readout) {
        VolumeReadout.Muted -> stringResource(R.string.volume_muted)
        is VolumeReadout.Level -> stringResource(R.string.volume_value, readout.level)
        else -> stringResource(R.string.volume_unknown)
    }
    val lastChange = remember { longArrayOf(0L) }
    val quick = remember(readout) {
        val now = SystemClock.uptimeMillis()
        (now - lastChange[0] < ROLL_GAP_MS).also { lastChange[0] = now }
    }
    val pop = remember { Animatable(1f) }
    val level = (readout as? VolumeReadout.Level)?.level
    val heard = remember { intArrayOf(level ?: -1) }
    LaunchedEffect(level) {
        if (level == null) return@LaunchedEffect
        val before = heard[0]
        heard[0] = level
        if (before < 0 || before == level) return@LaunchedEffect
        pop.snapTo(POP_SCALE)
        pop.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    KeyButton(
        onPress = { onMute(!muted) },
        contentDescription = "$description. ${stringResource(if (muted) R.string.key_unmute else R.string.key_mute)}",
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        color = Color.Transparent,
        pressedColor = pressedColor,
        dimWhenDisabled = false,
        well = LocalDeckMetrics.current.capsuleWell,
    ) { pressed ->
        AnimatedContent(
            targetState = readout,
            transitionSpec = {
                val from = initialState
                val to = targetState
                val change = if (from is VolumeReadout.Level && to is VolumeReadout.Level) {
                    if (quick) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val rising = to.level > from.level
                        (slideInVertically(tween(ROLL_MS)) { if (rising) it else -it } + fadeIn(tween(ROLL_MS)))
                            .togetherWith(slideOutVertically(tween(ROLL_MS)) { if (rising) -it else it } + fadeOut(tween(ROLL_MS)))
                    }
                } else {
                    fadeIn(tween(ROLL_MS)) togetherWith fadeOut(tween(ROLL_MS))
                }
                change.using(SizeTransform(clip = true))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
            label = "volume",
        ) { shown ->
            Box(Modifier.width(READOUT_SLOT), contentAlignment = Alignment.Center) {
                val quiet = if (pressed) colors.primary else colors.onSurfaceVariant
                val style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                when (shown) {
                    is VolumeReadout.Level -> Text(shown.level.toString(), style = style, color = if (pressed) colors.primary else colors.onSurface)
                    VolumeReadout.Pending -> Text("–", style = style, color = quiet)
                    VolumeReadout.Unsupported -> Icon(Lucide.Volume2, contentDescription = null, tint = quiet)
                    VolumeReadout.Muted -> Icon(Lucide.VolumeX, contentDescription = null, tint = if (pressed) colors.primary else colors.error)
                }
            }
        }
    }
}
