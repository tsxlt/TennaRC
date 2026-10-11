package lt.tsx.tennarc.ui.sheets

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.components.CONTROL_HEIGHT
import lt.tsx.tennarc.ui.components.dimmedUnless
import lt.tsx.tennarc.ui.components.IN_USE_INK
import lt.tsx.tennarc.ui.components.accentGlass
import lt.tsx.tennarc.ui.components.accentPressed
import lt.tsx.tennarc.ui.components.KeyButton
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROW_PAD
import lt.tsx.tennarc.ui.components.PILL_WELL_CORNER
import lt.tsx.tennarc.ui.components.PILL_WELL_INSET
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.components.panelGround
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.glassPressed
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.SegmentKey
import lt.tsx.tennarc.ui.theme.SectionLabel
import lt.tsx.tennarc.ui.theme.SquircleShape
import java.util.Date
import lt.tsx.tennarc.ui.theme.sectionCase
import lt.tsx.tennarc.ui.theme.slightlySmaller

enum class PowerMode {
    ON,
    SCREEN_OFF,

    OFF,

    UNKNOWN,
}

@Composable
fun PowerSheetContent(
    mode: PowerMode,
    canWake: Boolean,
    onScreenOff: ((Boolean) -> Unit)?,
    onPowerOff: () -> Unit,
    onWake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    sleep: @Composable ColumnScope.() -> Unit = {},
) {
    val title = stringResource(R.string.power_title)
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onScreenOff != null) ScreenKey(mode, onScreenOff, onDone, Modifier.weight(1f).fillMaxHeight())
            PowerKey(mode, canWake, onPowerOff, onWake, onDone, Modifier.weight(2f).fillMaxHeight())
        }
        sleep()
    }
}

@Composable
private fun PowerKey(
    mode: PowerMode,
    canWake: Boolean,
    onPowerOff: () -> Unit,
    onWake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val off = mode == PowerMode.OFF
    var armed by remember(mode) { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }
    val drain = remember { Animatable(0f) }
    LaunchedEffect(armed, fired) {
        when {
            fired -> Unit
            armed -> {
                drain.snapTo(1f)
                drain.animateTo(0f, tween(ARM_MS, easing = LinearEasing))
                armed = false
            }
            else -> drain.snapTo(0f)
        }
    }
    val face = when {
        off -> PowerFace.TURN_ON
        armed -> PowerFace.AGAIN
        else -> PowerFace.TURN_OFF
    }
    val canPress = !off || canWake
    val deeper = colors.accentPressed
    val ink by animateColorAsState(
        when (face) {
            PowerFace.TURN_ON -> if (canPress) colors.primary else colors.onSurface
            PowerFace.AGAIN -> colors.error
            PowerFace.TURN_OFF -> colors.onSurface
        },
        tween(LABEL_FADE_MS),
        label = "powerInk",
    )
    fun later(action: () -> Unit) {
        scope.launch {
            delay(DONE_DELAY_MS)
            action()
        }
    }
    KeyButton(
        onPress = {
            when {
                fired -> Unit
                off -> {
                    fired = true
                    haptics.key()
                    onWake()
                    later(onDone)
                }
                armed -> {
                    fired = true
                    haptics.confirm()
                    onPowerOff()
                    later(onDone)
                }
                else -> {
                    haptics.key()
                    armed = true
                }
            }
        },
        contentDescription = stringResource(face.description),
        modifier = modifier
            .dimmedUnless(canPress)
            .glass(if (face == PowerFace.TURN_ON && canPress) accentGlass() else panelGlass(), POWER_KEY_SHAPE),
        shape = POWER_KEY_SHAPE,
        enabled = canPress,
        color = when (face) {
            PowerFace.TURN_ON -> deeper.copy(alpha = 0f)
            PowerFace.AGAIN -> colors.errorContainer
            PowerFace.TURN_OFF -> colors.glassPressed.copy(alpha = 0f)
        },
        pressedColor = when (face) {
            PowerFace.TURN_ON -> deeper
            PowerFace.AGAIN -> lerp(colors.errorContainer, colors.error, 0.12f)
            PowerFace.TURN_OFF -> colors.glassPressed
        },
        dimWhenDisabled = false,
        buzz = false,
    ) {
        val tint = colors.error.copy(alpha = 0.16f)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val left = drain.value
                    if (left > 0f) {
                        val width = size.width * left
                        val x = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
                        drawRect(tint, topLeft = Offset(x, 0f), size = Size(width, size.height))
                    }
                },
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).size(24.dp),
            )
            Box(Modifier.align(Alignment.CenterStart).padding(start = 24.dp + 24.dp + 16.dp, end = 16.dp)) {
                PowerFace.entries.forEach { each ->
                    val shown by animateFloatAsState(if (each == face) 1f else 0f, tween(LABEL_FADE_MS), label = "powerLabel")
                    Text(
                        stringResource(each.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = slightlySmaller(MaterialTheme.typography.labelLarge),
                        modifier = Modifier.graphicsLayer { alpha = shown },
                    )
                }
            }
        }
    }
}

private enum class PowerFace(val label: Int, val description: Int) {
    TURN_OFF(R.string.power_turn_off, R.string.power_off),
    AGAIN(R.string.power_tap_again, R.string.power_off_confirm),
    TURN_ON(R.string.action_turn_on, R.string.action_turn_on),
}

@Composable
private fun ScreenKey(mode: PowerMode, onScreenOff: (Boolean) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val reported = mode == PowerMode.SCREEN_OFF
    var pending by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(PENDING_MS)
            pending = null
        }
    }
    LaunchedEffect(reported) { pending = null }
    val lit = pending ?: reported
    val litFill = colors.onSurface.copy(alpha = IN_USE_INK)
    val state = stringResource(if (lit) R.string.switch_on else R.string.switch_off)
    KeyButton(
        onPress = {
            pending = !lit
            onScreenOff(!lit)
            scope.launch {
                delay(DONE_DELAY_MS)
                onDone()
            }
        },
        contentDescription = stringResource(R.string.power_screen_off),
        modifier = modifier
            .semantics { stateDescription = state }
            .dimmedUnless(mode != PowerMode.OFF)
            .glass(panelGlass(), POWER_KEY_SHAPE),
        shape = POWER_KEY_SHAPE,
        enabled = mode != PowerMode.OFF,
        color = if (lit) litFill else colors.glassPressed.copy(alpha = 0f),
        pressedColor = if (lit) colors.glassPressed.compositeOver(litFill) else colors.glassPressed,
        dimWhenDisabled = false,
        role = Role.Switch,
    ) { pressed ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Rounded.DesktopAccessDisabled,
                contentDescription = null,
                tint = if (pressed) colors.primary else colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(R.string.power_screen_off),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = slightlySmaller(MaterialTheme.typography.labelMedium),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

private val SLEEP_MINUTES = listOf(15, 30, 60, 90)

@Composable
fun ColumnScope.SleepTimerControls(sleepAt: Long, minutes: Int, onStart: (Int) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis(), sleepAt) {
        while (true) {
            value = System.currentTimeMillis()
            val left = sleepAt - value
            if (left <= 0) break
            delay((left - 1) % 1_000 + 1)
        }
    }
    val running = sleepAt > now
    var endsAt by remember { mutableStateOf(if (sleepAt > 0) clockTime(context, sleepAt) else "") }
    LaunchedEffect(sleepAt) { if (sleepAt > 0) endsAt = clockTime(context, sleepAt) }
    val endsShown by animateFloatAsState(if (running) 1f else 0f, tween(200), label = "sleepEnds")
    Spacer(Modifier.size(PANEL_GAP * 3))
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = PANEL_INSET + PANEL_ROW_PAD),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sectionCase(stringResource(R.string.sleep_title)), style = SectionLabel)
        Text(
            stringResource(R.string.sleep_at, endsAt),
            style = SectionLabel,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = endsShown },
        )
    }
    Spacer(Modifier.size(PANEL_GAP))
    val lit = colors.onSurface.copy(alpha = IN_USE_INK)
    val slotStyle = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slot = remember(slotStyle, density) { with(density) { measurer.measure("88:88", slotStyle).size.width.toDp() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(CONTROL_HEIGHT)
            .glass(panelGlass(), MaterialTheme.shapes.large)
            .selectableGroup(),
    ) {
        SLEEP_MINUTES.forEach { choice ->
            val on = running && choice == minutes
            SegmentKey(
                onPress = { if (on) onCancel() else onStart(choice) },
                label = stringResource(R.string.sleep_minutes, choice),
                enabled = true,
                glow = if (on) colors.glassPressed.compositeOver(lit) else colors.glassPressed,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                selected = on,
                selectedColor = lit,
                ground = panelGround(),
                onClickLabel = if (on) stringResource(R.string.sleep_cancel) else null,
                inset = PILL_WELL_INSET,
                corner = PILL_WELL_CORNER,
            ) { pressed ->
                val counting = animateFloatAsState(if (on) 1f else 0f, tween(SEGMENT_FADE_MS), label = "sleepCounting")
                var left by remember { mutableStateOf(if (on) countdown(sleepAt - now) else "") }
                LaunchedEffect(on, now) { if (on) left = countdown(sleepAt - now) }
                Text(
                    choice.toString(),
                    style = slotStyle,
                    color = if (pressed) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer { alpha = 1f - counting.value },
                )
                Row(
                    Modifier.graphicsLayer { alpha = counting.value },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
                        Text(left, style = slotStyle, color = colors.onSurface, maxLines = 1, softWrap = false)
                    }
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun clockTime(context: Context, at: Long): String = DateFormat.getTimeFormat(context).format(Date(at))

private fun countdown(leftMs: Long): String {
    val seconds = ((leftMs.coerceAtLeast(0) + 999) / 1_000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val POWER_KEY_SHAPE = SquircleShape(16.dp)

private const val ARM_MS = 3_000
private const val LABEL_FADE_MS = 150
private const val SEGMENT_FADE_MS = 150

private const val DONE_DELAY_MS = 220L

private const val PENDING_MS = 2_000L
