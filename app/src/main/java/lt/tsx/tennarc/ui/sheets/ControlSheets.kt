package lt.tsx.tennarc.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import lt.tsx.tennarc.ui.theme.PillShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.TextMirror
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.ui.components.DISABLED_ALPHA
import lt.tsx.tennarc.ui.components.dimmedUnless
import lt.tsx.tennarc.ui.components.FocusWhenSettled
import lt.tsx.tennarc.ui.components.accentGlass
import lt.tsx.tennarc.ui.components.accentPressed
import lt.tsx.tennarc.ui.components.KeyButton
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.ICON_WELL_CORNER
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.components.rememberPressWash
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.SegmentKey
import lt.tsx.tennarc.ui.components.fadeTopEdge
import lt.tsx.tennarc.ui.theme.TennaColors
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.glassPressed
import lt.tsx.tennarc.ui.theme.ground
import lt.tsx.tennarc.ui.theme.seeThrough
import lt.tsx.tennarc.ui.theme.slightlySmaller

@Composable
fun KeyboardSheetContent(
    keyboard: TvKeyboard?,
    enabled: Boolean,
    text: String,
    onText: (String) -> Unit,
    onType: (TextEdit) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    if (autoFocus) FocusWhenSettled(focus)
    val ready = keyboard != null
    val length = keyboard?.textLength ?: 0
    var held by remember { mutableIntStateOf(0) }
    var openedAt by remember { mutableIntStateOf(0) }
    var echoed by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (!ready) {
            onText("")
        } else {
            openedAt = length
            held = length
            echoed = false
            if (text.isNotEmpty()) onType(TextEdit(delete = 0, insert = text))
        }
    }
    LaunchedEffect(length) { if (ready && length != openedAt) echoed = true }
    val ghost = if (ready) minOf(held, length) else 0
    val delivered = if (echoed) (length - ghost).coerceIn(0, text.length) else text.length
    val clearCount = maxOf(text.length + ghost, length)

    var ghostShown by remember { mutableIntStateOf(ghost) }
    LaunchedEffect(ghost) { if (ghost > 0) ghostShown = ghost }
    val ghostOpen = animateFloatAsState(if (ghost > 0) 1f else 0f, tween(200), label = "ghostChip")

    var sent by remember { mutableStateOf("") }
    val sentShown = remember { Animatable(0f) }
    fun send() {
        sent = text
        onEnter()
        onText("")
        scope.launch {
            sentShown.snapTo(1f)
            sentShown.animateTo(0f, tween(SENT_FADE_MS))
        }
    }

    val fieldGlass = clearGlass(animateColorBetween(ready, colors.surfaceContainerLow, colors.surfaceContainer, tween(200), label = "fieldColor"))
    val waiting by animateFloatAsState(if (ready) 0f else 1f, tween(200), label = "fieldHint")
    val shape = MaterialTheme.shapes.small

    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                if (ready) TextMirror.diff(text, new)?.let(onType)
                onText(new)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = PendingText(delivered, colors.onSurfaceVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { if (ready) send() }),
            modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(focus),
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxSize().glass(fieldGlass, shape).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GhostChip(ghostShown, visible = ghost > 0, open = { ghostOpen.value })
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.keyboard_placeholder_waiting),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = slightlySmaller(MaterialTheme.typography.bodyLarge),
                                modifier = Modifier
                                    .graphicsLayer { alpha = waiting }
                                    .then(if (ready) Modifier.clearAndSetSemantics {} else Modifier),
                            )
                            Text(
                                sent,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.graphicsLayer { alpha = sentShown.value }.clearAndSetSemantics {},
                            )
                        }
                        field()
                    }
                    ClearKey(shown = enabled && clearCount > 0) {
                        haptics.key()
                        if (ready) onType(TextEdit(delete = clearCount, insert = ""))
                        held = 0
                        onText("")
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val lit = colors.glassPressed
            KeyButton(
                onPress = {
                    if (ready) onType(TextEdit(delete = 1, insert = ""))
                    if (text.isEmpty()) held = (ghost - 1).coerceAtLeast(0)
                    onText(text.dropLast(1))
                },
                contentDescription = stringResource(R.string.keyboard_backspace),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .dimmedUnless(enabled)
                    .glass(panelGlass(), shape),
                enabled = enabled,
                repeat = true,
                color = lit.copy(alpha = 0f),
                pressedColor = lit,
                dimWhenDisabled = false,
            ) { pressed ->
                Icon(Lucide.Delete, contentDescription = null, tint = if (pressed) colors.primary else colors.onSurface)
            }
            val canSend = enabled && ready
            val deeper = if (canSend) colors.accentPressed else lit
            KeyButton(
                onPress = ::send,
                contentDescription = stringResource(R.string.keyboard_enter),
                modifier = Modifier.weight(2f).fillMaxHeight().glass(if (canSend) accentGlass() else panelGlass(), shape),
                enabled = canSend,
                color = deeper.copy(alpha = 0f),
                pressedColor = deeper,
                dimWhenDisabled = false,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardReturn,
                    contentDescription = null,
                    tint = if (canSend) colors.primary else colors.onSurface.copy(alpha = DISABLED_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun GhostChip(count: Int, visible: Boolean, open: () -> Float) {
    val colors = MaterialTheme.colorScheme
    val description = pluralStringResource(R.plurals.keyboard_on_tv, count, count)
    Box(
        Modifier
            .graphicsLayer {
                alpha = open()
                clip = true
            }
            .layout { measurable, constraints ->
                val chip = measurable.measure(constraints.copy(minWidth = 0))
                layout((chip.width * open()).roundToInt(), chip.height) { chip.placeRelative(0, 0) }
            }
            .then(if (visible) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics {}),
    ) {
        Row(
            Modifier
                .padding(end = 8.dp)
                .clip(PillShape)
                .background(seeThrough(colors.surfaceContainerHighest, colors.surfaceContainer))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ClearKey(shown: Boolean, onClear: () -> Unit) {
    val pop = animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "clearPop",
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                val scale = 0.6f + 0.4f * pop.value
                scaleX = scale
                scaleY = scale
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER), enabled = shown, role = Role.Button, onClick = onClear)
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.keyboard_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PendingText(val from: Int, val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val start = from.coerceIn(0, text.length)
        if (start == text.length) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = color), start, text.length)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun MoreSheetContent(
    enabled: Boolean,
    onPress: (TvButton) -> Unit,
    modifier: Modifier = Modifier,
    captions: Boolean = false,
    liveTv: Boolean = false,
    numberKeys: Boolean = true,
    channel: String? = null,
    settings: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
) {
    val title = stringResource(R.string.more_title)
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .semantics { paneTitle = title }
            .fadeTopEdge(scroll)
            .verticalScroll(scroll)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GAP * 2),
    ) {
        settings?.invoke()
        TvKeys(enabled, captions, onPress)
        if (liveTv) {
            if (numberKeys) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ColorKeys(enabled, onPress)
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        NumberPad(enabled, onPress, Modifier.weight(3f))
                        ChannelRocker(channel, enabled, onPress, across = false, Modifier.weight(1f).height(PAD_HEIGHT))
                    }
                }
            } else {
                ChannelRocker(channel, enabled, onPress, across = true, Modifier.fillMaxWidth().height(KEY_ROW))
            }
        }
    }
}

private val GAP = 8.dp

private class TvKey(val icon: () -> ImageVector, val label: Int, val button: TvButton, val name: Int = label)

private val KEY_SETTINGS = TvKey({ Lucide.Settings }, R.string.more_menu, TvButton.MENU)
private val KEY_QUICK = TvKey({ Lucide.Menu }, R.string.more_quick_short, TvButton.QMENU, name = R.string.more_quick)
private val KEY_INPUTS = TvKey({ Lucide.LogIn }, R.string.more_inputs, TvButton.INPUT_HUB)
private val KEY_EXIT = TvKey({ Lucide.LogOut }, R.string.more_exit, TvButton.EXIT)
private val KEY_INFO = TvKey({ Lucide.Info }, R.string.more_info, TvButton.INFO)
private val KEY_STOP = TvKey({ Lucide.Square }, R.string.key_stop, TvButton.STOP)
private val KEY_SUBTITLES = TvKey({ Lucide.Captions }, R.string.more_subtitles, TvButton.CAPTIONS)
private val KEY_AUDIO = TvKey({ Lucide.AudioLines }, R.string.more_audio_track, TvButton.AUDIO_TRACK)

@Composable
private fun TvKeys(enabled: Boolean, captions: Boolean, onPress: (TvButton) -> Unit) {
    val rows = if (captions) {
        listOf(listOf(KEY_SETTINGS, KEY_QUICK, KEY_INPUTS, KEY_EXIT), listOf(KEY_INFO, KEY_STOP, KEY_SUBTITLES, KEY_AUDIO))
    } else {
        listOf(listOf(KEY_SETTINGS, KEY_QUICK, KEY_INPUTS), listOf(KEY_INFO, KEY_STOP, KEY_EXIT))
    }
    Column(Modifier.panel(panelGlass(), MaterialTheme.shapes.large, PAD_INSET, enabled = enabled, clip = true), verticalArrangement = Arrangement.spacedBy(GAP)) {
        rows.forEach { row ->
            Row(Modifier.height(KEY_CELL), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { key ->
                    NamedKey(key.icon(), stringResource(key.label), stringResource(key.name), enabled, { onPress(key.button) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun NamedKey(icon: ImageVector, label: String, name: String, enabled: Boolean, onPress: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    KeyButton(
        onPress = onPress,
        contentDescription = name,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = colors.glassPressed.copy(alpha = 0f),
        pressedColor = colors.glassPressed,
        dimWhenDisabled = false,
    ) { pressed ->
        val ink = if (pressed) colors.primary else colors.onSurfaceVariant
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, softWrap = false, autoSize = shrinkToFit(MaterialTheme.typography.labelMedium))
        }
    }
}

private val KEY_CELL = 64.dp

private fun shrinkToFit(style: TextStyle) = TextAutoSize.StepBased(minFontSize = KEY_WORD_MIN, maxFontSize = style.fontSize, stepSize = 0.5.sp)

private val KEY_WORD_MIN = 9.sp

private val DIGIT_ROWS = listOf(
    listOf(TvButton.DIGIT_1, TvButton.DIGIT_2, TvButton.DIGIT_3),
    listOf(TvButton.DIGIT_4, TvButton.DIGIT_5, TvButton.DIGIT_6),
    listOf(TvButton.DIGIT_7, TvButton.DIGIT_8, TvButton.DIGIT_9),
    listOf(TvButton.CHANNEL_LIST, TvButton.DIGIT_0, TvButton.GUIDE),
)

@Composable
private fun NumberPad(enabled: Boolean, onPress: (TvButton) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.panel(panelGlass(), MaterialTheme.shapes.large, PAD_INSET, enabled = enabled, clip = true), verticalArrangement = Arrangement.spacedBy(GAP)) {
        DIGIT_ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth().height(KEY_ROW), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { button ->
                    val word = when (button) {
                        TvButton.CHANNEL_LIST -> stringResource(R.string.more_channel_list)
                        TvButton.GUIDE -> stringResource(R.string.more_guide)
                        else -> null
                    }
                    PadKey({ onPress(button) }, word ?: button.wire, enabled, Modifier.weight(1f).fillMaxHeight()) { pressed ->
                        if (word != null) {
                            Text(word, style = MaterialTheme.typography.labelLarge, color = if (pressed) colors.primary else colors.onSurfaceVariant, maxLines = 1, autoSize = shrinkToFit(MaterialTheme.typography.labelLarge))
                        } else {
                            Text(button.wire, style = MaterialTheme.typography.titleLarge, color = if (pressed) colors.primary else colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRocker(channel: String?, enabled: Boolean, onPress: (TvButton) -> Unit, across: Boolean, modifier: Modifier) {
    var up by remember { mutableStateOf(true) }
    val panel = modifier.panel(panelGlass(), MaterialTheme.shapes.large, if (across) PAD_INSET / 2 else PAD_INSET, enabled = enabled, clip = true)
    val channelUp = @Composable { key: Modifier ->
        RockerKey(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.more_channel_up), enabled, key) {
            up = true
            onPress(TvButton.CHANNEL_UP)
        }
    }
    val channelDown = @Composable { key: Modifier ->
        RockerKey(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.more_channel_down), enabled, key) {
            up = false
            onPress(TvButton.CHANNEL_DOWN)
        }
    }
    if (across) {
        Row(panel) {
            channelDown(Modifier.weight(1f).fillMaxHeight())
            ChannelNumber(channel, up, Modifier.weight(1f).fillMaxHeight())
            channelUp(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(panel, verticalArrangement = Arrangement.spacedBy(GAP)) {
            channelUp(Modifier.weight(1f).fillMaxWidth())
            ChannelNumber(channel, up, Modifier.fillMaxWidth().height(KEY_ROW))
            channelDown(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun RockerKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    onPress: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PadKey(onPress, description, enabled, modifier, repeat = true) { pressed ->
        Icon(icon, contentDescription = null, tint = if (pressed) colors.primary else colors.onSurface)
    }
}

@Composable
private fun ChannelNumber(channel: String?, up: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pop = remember { Animatable(1f) }
    val heard = remember { arrayOf(channel) }
    LaunchedEffect(channel) {
        val before = heard[0]
        heard[0] = channel
        if (before == null || channel == null || before == channel) return@LaunchedEffect
        pop.snapTo(POP_SCALE)
        pop.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    AnimatedContent(
        targetState = channel,
        transitionSpec = {
            val from = if (up) 1 else -1
            (slideInVertically { it * from } + fadeIn()) togetherWith (slideOutVertically { -it * from } + fadeOut())
        },
        contentAlignment = Alignment.Center,
        modifier = modifier.clipToBounds().graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
        label = "channelNumber",
    ) { number ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (number != null) {
                Text(
                    number,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("CH", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PadKey(
    onPress: () -> Unit,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    repeat: Boolean = false,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val lit = MaterialTheme.colorScheme.glassPressed
    KeyButton(
        onPress = onPress,
        contentDescription = description,
        modifier = modifier,
        enabled = enabled,
        repeat = repeat,
        shape = MaterialTheme.shapes.small,
        color = lit.copy(alpha = 0f),
        pressedColor = lit,
        dimWhenDisabled = false,
        content = content,
    )
}

private val KEY_ROW = 52.dp

private val PAD_INSET = PANEL_INSET
private val PAD_HEIGHT = KEY_ROW * 4 + GAP * 3 + PAD_INSET * 2

private const val POP_SCALE = 1.2f

private val COLOR_KEYS = listOf(
    Triple(TvButton.RED, TennaColors.TvRed, R.string.more_red),
    Triple(TvButton.GREEN, TennaColors.TvGreen, R.string.more_green),
    Triple(TvButton.YELLOW, TennaColors.TvYellow, R.string.more_yellow),
    Triple(TvButton.BLUE, TennaColors.TvBlue, R.string.more_blue),
)

@Composable
private fun ColorKeys(enabled: Boolean, onPress: (TvButton) -> Unit) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val pressed = MaterialTheme.colorScheme.surfaceContainerHighest
    val ground = ground()
    val wash = remember(base, ground) {
        Brush.horizontalGradient(
            *COLOR_KEYS.mapIndexed { i, (_, color, _) ->
                (i + 0.5f) / COLOR_KEYS.size to seeThrough(lerp(base, color, COLOR_KEY_TINT), ground)
            }.toTypedArray(),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .dimmedUnless(enabled)
            .clip(MaterialTheme.shapes.large)
            .background(wash)
            .glass(clearGlass(Color.Transparent, sheen = 0f), MaterialTheme.shapes.large),
    ) {
        COLOR_KEYS.forEach { (button, color, label) ->
            SegmentKey(
                onPress = { onPress(button) },
                label = stringResource(label),
                enabled = enabled,
                ground = base,
                glow = lerp(pressed, color, COLOR_KEY_TINT_PRESSED),
                inset = COLOR_KEY_INSET,
                corner = COLOR_KEY_CORNER,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { down -> ColorBar(color, down) }
        }
    }
}

@Composable
private fun ColorBar(color: Color, pressed: Boolean) {
    val sink by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh),
        label = "colorKeyBar",
    )
    Box(
        Modifier
            .size(width = 24.dp, height = 4.dp)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .clip(PillShape)
            .background(color),
    )
}

private const val COLOR_KEY_TINT = 0.12f
private val COLOR_KEY_INSET = 4.dp
private val COLOR_KEY_CORNER = 16.dp

internal fun colorKeyWashes(base: Color): List<Color> = COLOR_KEYS.map { (_, color, _) -> lerp(base, color, COLOR_KEY_TINT) }
private const val COLOR_KEY_TINT_PRESSED = 0.26f
private const val SENT_FADE_MS = 140
