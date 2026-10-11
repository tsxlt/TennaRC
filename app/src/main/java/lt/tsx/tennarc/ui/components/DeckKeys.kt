package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlinx.coroutines.delay
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.glass

@Composable
internal fun DeckIcon(
    app: TvApp,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: () -> Float = FULL,
    hold: List<HoldAction> = emptyList(),
) {
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val fire by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var peeking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val offers = hold.isNotEmpty()
    LaunchedEffect(pressed) {
        if (!pressed) {
            peeking = false
            return@LaunchedEffect
        }
        delay(PEEK_DELAY_MS)
        peeking = true
        haptics.faintTick()
    }
    val sink by animateFloatAsState(if (pressed) ICON_PRESSED else 1f, if (pressed) PRESS_IN else PRESS_OUT, label = "iconSink")
    val dim by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, tween(200), label = "iconDim")
    val description = if (active) stringResource(R.string.app_on_screen, app.title) else app.title
    Column(
        modifier
            .fillMaxHeight()
            .graphicsLayer { alpha = dim }
            .pressAndRepeat(
                enabled,
                repeat = false,
                onPressedChange = { pressed = it },
                onHold = {
                    if (offers) {
                        haptics.confirm()
                        menu = true
                    }
                },
            ) {
                haptics.key()
                fire()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick {
                        fire()
                        true
                    }
                    if (offers) customActions = holdActions(hold)
                } else {
                    disabled()
                }
            }
            .padding(top = DECK_TOP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.graphicsLayer {
                    scaleX = sink
                    scaleY = sink
                },
            ) {
                AppFace(app, pressed = pressed)
            }
            PeekBubble(app.title, peeking && !menu)
            HoldMenu(menu, hold) { menu = false }
        }
        Box(Modifier.fillMaxWidth().height(DECK_UNDER), contentAlignment = Alignment.Center) { OnScreenDot(active, dot) }
    }
}

@Composable
internal fun InputKey(
    input: TvApp,
    active: Boolean,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hold: List<HoldAction> = emptyList(),
) {
    SourceKey(
        name = input.title,
        description = stringResource(R.string.input_label, input.title),
        active = active,
        enabled = enabled,
        keyHeight = keyHeight,
        onClick = onClick,
        hold = hold,
        modifier = modifier,
    ) { ink -> Icon(inputGlyph(input), contentDescription = null, tint = ink, modifier = Modifier.size(SOURCE_MARK)) }
}

@Composable
internal fun PhoneKey(source: PhoneSource, enabled: Boolean, keyHeight: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SourceKey(
        name = source.name,
        description = source.description,
        active = source.active,
        enabled = enabled,
        keyHeight = keyHeight,
        onClick = onClick,
        modifier = modifier,
    ) { ink -> Icon(source.glyph, contentDescription = null, tint = ink, modifier = Modifier.size(SOURCE_MARK)) }
}

@Composable
internal fun ChannelKey(
    channel: TvChannel,
    active: Boolean,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    hold: List<HoldAction>,
    modifier: Modifier = Modifier,
) {
    SourceKey(
        name = channel.name,
        description = stringResource(R.string.channel_label, channel.number, channel.name),
        active = active,
        enabled = enabled,
        keyHeight = keyHeight,
        onClick = onClick,
        hold = hold,
        modifier = modifier,
    ) { ink ->
        Text(
            channel.number,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SourceKey(
    name: String,
    description: String,
    active: Boolean,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hold: List<HoldAction> = emptyList(),
    marker: @Composable (Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val fire by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val sink by animateFloatAsState(if (pressed) KEY_PRESSED else 1f, if (pressed) PRESS_IN else PRESS_OUT, label = "keySink")
    val key = clearGlass(animateColorBetween(pressed, colors.surfaceContainer, colors.surfaceContainerHighest, tween(if (pressed) 40 else 180), label = "keyFill"))
    val dim by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, tween(200), label = "keyDim")
    val onScreen = stringResource(R.string.on_screen_now)
    Column(
        modifier
            .graphicsLayer { alpha = dim }
            .pressAndRepeat(
                enabled,
                repeat = false,
                onPressedChange = { pressed = it },
                onHold = if (hold.isNotEmpty()) {
                    {
                        haptics.confirm()
                        menu = true
                    }
                } else {
                    null
                },
            ) {
                haptics.key()
                fire()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (active) stateDescription = onScreen
                if (enabled) {
                    onClick {
                        fire()
                        true
                    }
                    if (hold.isNotEmpty()) customActions = holdActions(hold)
                } else {
                    disabled()
                }
            }
            .padding(top = SOURCE_TOP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .height(keyHeight)
                .graphicsLayer {
                    scaleX = sink
                    scaleY = sink
                }
                .glass(key, SOURCE_SHAPE)
                .clip(SOURCE_SHAPE)
                .padding(horizontal = SOURCE_PAD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            marker(colors.outline)
            Spacer(Modifier.width(SOURCE_MARK_GAP))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = if (active || pressed) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.height(DECK_UNDER), contentAlignment = Alignment.Center) { OnScreenDot(active) }
        HoldMenu(menu, hold) { menu = false }
    }
}

@Composable
internal fun SourceRow(height: Dp, inset: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(height).padding(start = inset, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(SOURCE_GAP),
        content = content,
    )
}

@Composable
internal fun SourceLine(height: Dp, inset: Dp, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth().height(height).fadeEdges(list),
        contentPadding = PaddingValues(start = inset, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(SOURCE_GAP),
        content = content,
    )
}

private fun Modifier.fadeEdges(list: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = SOURCE_FADE.toPx()
        if (list.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade), blendMode = BlendMode.DstIn)
        }
        if (list.canScrollForward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width), blendMode = BlendMode.DstIn)
        }
    }

@Composable
internal fun sourceKeyHeight(): Dp {
    val line = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    return max(SOURCE_KEY.value, (line + 12.dp).value).dp
}

@Composable
private fun OnScreenDot(active: Boolean, handOff: () -> Float = FULL) {
    val grown by animateFloatAsState(if (active) 1f else 0f, DOT_SPRING, label = "dotSize")
    val seen by animateFloatAsState(if (active) 1f else 0f, tween(120), label = "dotAlpha")
    Box(
        Modifier
            .size(DOT)
            .graphicsLayer {
                alpha = seen * handOff()
                scaleX = grown.coerceAtLeast(0f)
                scaleY = scaleX
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun PeekBubble(text: String, visible: Boolean) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, if (visible) PEEK_SPRING else tween(100), label = "peek")
    val gone by remember { derivedStateOf { shown == 0f } }
    if (!visible && gone) return
    val gap = with(LocalDensity.current) { PEEK_GAP.roundToPx() }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(gap, margin) { Above(gap, margin) }
    val colors = MaterialTheme.colorScheme
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    scaleX = lerp(0.9f, 1f, shown)
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clearAndSetSemantics {}
                .clip(SquircleShape(PEEK_CORNER))
                .background(colors.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, maxLines = 1)
        }
    }
}

private class Above(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val right = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        return IntOffset(x.coerceIn(margin, right), anchorBounds.top - popupContentSize.height - gap)
    }
}

private val DOT = 5.dp

internal val SOURCE_TOP = 6.dp

private val SOURCE_KEY = 36.dp

private val SOURCE_SHAPE = SquircleShape(CornerSmall)

private val PEEK_CORNER = 8.dp

internal val SOURCE_PAD = 12.dp

internal val SOURCE_GAP = 8.dp

internal val SOURCE_MARK = 16.dp

internal val SOURCE_MARK_GAP = 6.dp

private val SOURCE_FADE = 24.dp

private const val PEEK_DELAY_MS = 150L

private val PEEK_GAP = 8.dp

private const val ICON_PRESSED = 0.88f

private const val KEY_PRESSED = 0.96f

private val PRESS_IN = spring<Float>(dampingRatio = 1f, stiffness = 1_500f)

private val PRESS_OUT = spring<Float>(dampingRatio = 0.5f, stiffness = 600f)

private val DOT_SPRING = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)

private val PEEK_SPRING = spring<Float>(dampingRatio = 0.7f, stiffness = 700f)
