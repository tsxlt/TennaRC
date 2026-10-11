package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.warning

@Composable
fun statusText(state: TvState): String = when (state.connection) {
    Connection.Connected -> when (state.power) {
        TvPower.STANDBY -> stringResource(R.string.status_standby)
        TvPower.SCREEN_OFF -> stringResource(R.string.status_screen_off)
        else -> state.appTitle(state.foregroundAppId)
            ?.let { stringResource(R.string.status_playing_app, it) }
            ?: stringResource(R.string.status_connected)
    }
    Connection.Connecting -> stringResource(R.string.status_connecting)
    Connection.AwaitingApproval -> stringResource(R.string.status_awaiting)
    is Connection.AwaitingCode -> stringResource(R.string.status_awaiting_code)
    Connection.Offline -> stringResource(R.string.status_offline)
    is Connection.NeedsPairing -> stringResource(R.string.status_not_paired)
}

@Composable
fun StatusDot(connection: Connection, modifier: Modifier = Modifier, power: TvPower? = null) {
    val color = when (connection) {
        Connection.Connected -> if (power == TvPower.STANDBY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
        Connection.Connecting, Connection.AwaitingApproval, is Connection.AwaitingCode -> MaterialTheme.colorScheme.warning
        Connection.Offline -> MaterialTheme.colorScheme.error
        is Connection.NeedsPairing -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pulsing = connection == Connection.Connecting || connection == Connection.AwaitingApproval
    val alpha = if (pulsing) pulseAlpha() else null
    Box(
        modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun pulseAlpha(): State<Float> {
    return rememberInfiniteTransition(label = "statusPulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "statusPulseAlpha",
    )
}

@Composable
fun StatePanel(
    icon: ImageVector?,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    pulse: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val roomy = maxHeight >= 360.dp
        val badge = if (roomy) 72.dp else 48.dp
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = max(24.dp, stateBadgeRingRoom(badge))),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (roomy || busy) StateBadge(Modifier.padding(bottom = 16.dp), size = badge, pulse = pulse) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(30.dp), color = colors.onSurfaceVariant, strokeWidth = 3.dp)
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Heading), textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            if (actions != null) {
                Spacer(Modifier.size(24.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        }
    }
}

@Composable
fun StateBadge(modifier: Modifier = Modifier, size: Dp = 72.dp, pulse: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ring = if (pulse) ringProgress() else null
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val disc = Path().apply { addOval(Rect(Offset.Zero, this@drawWithCache.size)) }
                val radius = this.size.minDimension / 2
                val reach = this.size.minDimension * RIPPLE_REACH
                val from = RIPPLE_FROM.toPx()
                val hairline = Stroke(1.dp.toPx())
                onDrawBehind {
                    if (ring == null) return@onDrawBehind
                    clipPath(disc, ClipOp.Difference) {
                        for (i in 0 until RIPPLES) {
                            val t = (ring.value + i.toFloat() / RIPPLES) % 1f
                            val out = radius + from + (reach - from) * (1f - (1f - t) * (1f - t) * (1f - t))
                            val fade = 1f - t
                            drawCircle(colors.onSurface, out, alpha = RIPPLE_FILL * fade * fade)
                            drawCircle(colors.onSurface, out, alpha = RIPPLE_EDGE * fade, style = hairline)
                        }
                    }
                }
            }
            .glass(panelGlass(), shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

fun stateBadgeRingRoom(size: Dp): Dp = size * RIPPLE_REACH + RIPPLE_SPILL

@Composable
private fun ringProgress(): State<Float> {
    return rememberInfiniteTransition(label = "panelPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RIPPLE_MS, easing = LinearEasing)),
        label = "panelPulseRing",
    )
}

@Composable
fun PairingCodePanel(wrong: Boolean, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(wrong) { if (wrong) code = "" }
    val ready = code.length >= PAIRING_CODE_LENGTH
    StatePanel(
        icon = Icons.Rounded.Tv,
        title = stringResource(R.string.pair_code_title),
        body = if (wrong) stringResource(R.string.pair_code_wrong) else null,
        modifier = modifier,
    ) {
        GlassField(
            value = code,
            onValueChange = { typed -> code = typed.uppercase().filter { it.isLetterOrDigit() }.take(PAIRING_CODE_LENGTH) },
            label = stringResource(R.string.pair_code_label),
            isError = wrong,
            textStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { if (ready) onSubmit(code) }),
            modifier = Modifier.width(150.dp),
        )
        AccentButton(stringResource(R.string.pair_code_submit), { onSubmit(code) }, enabled = ready)
    }
}

private const val PAIRING_CODE_LENGTH = 6

private const val RIPPLE_REACH = 0.3f
private const val RIPPLE_FILL = 0.10f
private const val RIPPLE_EDGE = 0.28f
private const val RIPPLE_MS = 2400
private val RIPPLE_FROM = 2.dp
private val RIPPLE_SPILL = 1.5.dp
private const val RIPPLES = 2
