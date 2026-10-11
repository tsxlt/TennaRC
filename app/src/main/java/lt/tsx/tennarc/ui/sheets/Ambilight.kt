package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.ui.components.DISABLED_ALPHA
import lt.tsx.tennarc.ui.components.dimmedUnless
import lt.tsx.tennarc.ui.components.PILL_WELL_CORNER
import lt.tsx.tennarc.ui.components.PILL_WELL_INSET
import lt.tsx.tennarc.ui.components.SegmentKey
import lt.tsx.tennarc.ui.components.pickedFill
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.grainToRestore
import lt.tsx.tennarc.ui.theme.slightlySmaller

private val STYLES = listOf(
    AmbilightStyle.OFF to R.string.ambilight_off,
    AmbilightStyle.FOLLOW_VIDEO to R.string.ambilight_video,
    AmbilightStyle.FOLLOW_AUDIO to R.string.ambilight_audio,
    AmbilightStyle.LOUNGE to R.string.ambilight_lounge,
)

@Composable
fun AmbilightRow(
    ambilight: Ambilight,
    enabled: Boolean,
    onStyle: (AmbilightStyle) -> Unit,
    onPair: () -> Unit,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val base = shelfInner(colors.surfaceContainerHigh)
    Row(
        modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .dimmedUnless(enabled)
            .clip(MaterialTheme.shapes.large)
            .background(base)
            .frostBehind(grainToRestore(base.alpha)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val access = ambilight.access) {
            AmbilightAccess.Paired -> Styles(ambilight.current, enabled, base, onStyle)
            AmbilightAccess.NeedsPairing -> PairPrompt(enabled, onPair)
            is AmbilightAccess.AwaitingPin -> PinEntry(access.wrong, enabled, onPin)
        }
    }
}

@Composable
private fun RowScope.Styles(current: AmbilightStyle?, enabled: Boolean, base: Color, onStyle: (AmbilightStyle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val picked = pickedFill(base)
    Row(Modifier.weight(1f).fillMaxHeight().selectableGroup()) {
        STYLES.forEach { (style, label) ->
            val on = style == current
            val ink = animateColorBetween(on, colors.onSurfaceVariant, colors.onSurface, label = "ambilightInk")
            SegmentKey(
                onPress = { if (!on) onStyle(style) },
                label = stringResource(label),
                enabled = enabled,
                glow = if (on) pickedFill(colors.surfaceContainerHighest) else colors.surfaceContainerHighest,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                selected = on,
                selectedColor = picked,
                ground = colors.surfaceContainerHigh,
                inset = SEGMENT_INSET,
                corner = SEGMENT_CORNER,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = slightlySmaller(MaterialTheme.typography.labelLarge),
                )
            }
        }
    }
}

@Composable
private fun RowScope.PairPrompt(enabled: Boolean, onPair: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.ambilight_needs_pairing),
        style = promptStyle(),
        color = colors.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = TEXT_START),
    )
    PairKey(enabled, onPair)
}

@Composable
private fun RowScope.PinEntry(wrong: Boolean, enabled: Boolean, onPin: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var pin by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(wrong) { if (wrong) pin = "" }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { catching { focus.requestFocus() } }
    val ready = pin.length >= PIN_LENGTH
    val hint = stringResource(if (wrong) R.string.ambilight_pin_wrong else R.string.ambilight_pin_hint)
    BasicTextField(
        value = pin,
        onValueChange = { typed -> pin = typed.filter { it.isDigit() }.take(PIN_MAX_LENGTH) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, letterSpacing = 4.sp),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { if (ready) onPin(pin) }),
        modifier = Modifier.weight(1f).padding(start = TEXT_START).focusRequester(focus).semantics { contentDescription = hint },
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (pin.isEmpty()) {
                    Text(hint, style = promptStyle(), color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                field()
            }
        },
    )
    PairKey(enabled && ready) { onPin(pin) }
}

@Composable
private fun PairKey(enabled: Boolean, onPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.pair_code_submit)
    SegmentKey(
        onPress = onPress,
        label = label,
        enabled = enabled,
        glow = colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxHeight().widthIn(min = PAIR_KEY_WIDTH),
        inset = SEGMENT_INSET,
        corner = SEGMENT_CORNER,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = PAIR_KEY_PADDING),
        )
    }
}

@Composable
private fun promptStyle() = MaterialTheme.typography.labelLarge.copy(lineHeight = 17.sp, lineBreak = LineBreak.Heading)

private val ROW_HEIGHT = 48.dp
private val PAIR_KEY_WIDTH = 80.dp
private val PAIR_KEY_PADDING = 16.dp

private val SEGMENT_INSET = PILL_WELL_INSET
private val SEGMENT_CORNER = PILL_WELL_CORNER

private val TEXT_START = 16.dp

private const val PIN_LENGTH = 4
private const val PIN_MAX_LENGTH = 6
