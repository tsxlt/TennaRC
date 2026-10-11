package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.Glass
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.darkness
import lt.tsx.tennarc.ui.theme.glass

val PANEL_INSET = 8.dp

val PANEL_GAP = 8.dp

val PANEL_ROW_GAP = 4.dp

val PANEL_ROWS_PAD = PANEL_INSET - PANEL_ROW_GAP / 2

val PANEL_ROW_PAD = 10.dp

val PILL_WELL_INSET = 4.dp
val PILL_WELL_CORNER = 16.dp

@Composable
@ReadOnlyComposable
fun panelGlass(): Glass = clearGlass(MaterialTheme.colorScheme.surfaceContainer)

@Composable
@ReadOnlyComposable
fun panelGround(): Color = MaterialTheme.colorScheme.surfaceContainer

fun Modifier.panel(glass: Glass, shape: Shape, inset: Dp = PANEL_INSET, enabled: Boolean? = null, clip: Boolean = false): Modifier = this
    .dimmedUnless(enabled == null || enabled)
    .then(if (clip) Modifier.clip(shape) else Modifier)
    .glass(glass, shape)
    .padding(inset)

@Composable
fun rememberRowWash(): PressWash = rememberPressWash(CornerSmall, inset = PANEL_INSET, insetY = PANEL_ROW_GAP / 2)

fun Modifier.panelSegment(glass: Glass, corner: Dp, position: Int, count: Int): Modifier = drawWithCache {
    val first = position == 0
    val last = position == count - 1
    val r = corner.toPx()
    val hairline = 1.dp.toPx()
    val half = hairline / 2f
    val w = size.width
    val h = size.height
    val fill = Path().apply {
        addSquircle(Rect(Offset.Zero, size), if (first) r else 0f, if (first) r else 0f, if (last) r else 0f, if (last) r else 0f)
    }
    val over = if (first) 0f else hairline * 2f + r
    val under = if (last) 0f else hairline * 2f + r
    val rim = Path().apply {
        addSquircle(
            Rect(half, half - over, w - half, h - half + under),
            if (first) r - half else 0f,
            if (first) r - half else 0f,
            if (last) r - half else 0f,
            if (last) r - half else 0f,
        )
    }
    val middle = lerp(glass.rim, glass.rimLow, 0.5f)
    val light: Brush = when {
        first && last -> Brush.verticalGradient(listOf(glass.rim, glass.rimLow), startY = 0f, endY = h)
        first -> Brush.verticalGradient(listOf(glass.rim, middle), startY = 0f, endY = h)
        last -> Brush.verticalGradient(listOf(middle, glass.rimLow), startY = 0f, endY = h)
        else -> SolidColor(middle)
    }
    val stroke = Stroke(hairline)
    onDrawBehind {
        if (glass.fill.alpha > 0f) drawPath(fill, glass.fill)
        clipRect(0f, 0f, w, h) { drawPath(rim, light, style = stroke) }
    }
}

@Composable
@ReadOnlyComposable
fun accentGlass(): Glass = clearGlass(lerp(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, ACCENT_TINT))

val ColorScheme.accentPressed: Color get() = primary.copy(alpha = androidx.compose.ui.util.lerp(ACCENT_WASH_LIGHT, ACCENT_WASH_DARK, darkness))

@Composable
fun AccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val wash = remember(colors.accentPressed) { PressWash(colors.accentPressed, CornerLarge) }
    KeyBar(onClick, modifier, enabled, accentGlass(), wash, ink = colors.primary, pressedInk = colors.primary, content)
}

@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val wash = rememberPressWash(CornerLarge)
    KeyBar(onClick, modifier, enabled, panelGlass(), wash, ink = colors.onSurface, pressedInk = colors.primary, content)
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AccentButton(onClick, modifier, enabled) { ButtonText(text) }

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    QuietButton(onClick, modifier, enabled) { ButtonText(text) }

@Composable
fun ButtonText(text: String) {
    Text(text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ButtonPair(quiet: @Composable () -> Unit, main: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(quiet, main), modifier = modifier.fillMaxWidth()) { (first, second), constraints ->
        val gap = PANEL_GAP.roundToPx()
        val width = constraints.maxWidth
        val start = (width - gap) / 2
        val end = width - gap - start
        val a = first.single()
        val b = second.single()
        val besides = a.maxIntrinsicWidth(Constraints.Infinity) <= start && b.maxIntrinsicWidth(Constraints.Infinity) <= end
        if (besides) {
            val pa = a.measure(Constraints.fixedWidth(start))
            val pb = b.measure(Constraints.fixedWidth(end))
            val height = maxOf(pa.height, pb.height)
            layout(width, height) {
                pa.placeRelative(0, (height - pa.height) / 2)
                pb.placeRelative(start + gap, (height - pb.height) / 2)
            }
        } else {
            val pa = a.measure(Constraints.fixedWidth(width))
            val pb = b.measure(Constraints.fixedWidth(width))
            layout(width, pa.height + gap + pb.height) {
                pa.placeRelative(0, 0)
                pb.placeRelative(0, pa.height + gap)
            }
        }
    }
}

@Composable
private fun KeyBar(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    pane: Glass,
    wash: PressWash,
    ink: Color,
    pressedInk: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) KEY_SINK else 1f, KEY_SINK_SPRING, label = "keyBarSink")
    Row(
        modifier
            .dimmedUnless(enabled)
            .sizeIn(minWidth = BUTTON_MIN_WIDTH, minHeight = CONTROL_HEIGHT)
            .graphicsLayer { scaleX = sink.value; scaleY = sink.value }
            .glass(pane, MaterialTheme.shapes.large)
            .clickable(interactionSource = source, indication = wash, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = BUTTON_PAD, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (pressed) pressedInk else ink, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            content()
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER, insetY = 4.dp), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = MaterialTheme.shapes.small,
    glass: Glass = panelGlass(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = source,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> FieldFrame(value.isEmpty(), label, placeholder, isError, shape, glass, source, leading, trailing, inner) },
        )
        if (supporting != null) FieldNote(supporting, isError)
    }
}

@Composable
fun GlassField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.small,
    glass: Glass = panelGlass(),
) {
    val source = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = source,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> FieldFrame(value.text.isEmpty(), label, placeholder, isError, shape, glass, source, leading, trailing, inner) },
        )
        if (supporting != null) FieldNote(supporting, isError)
    }
}

@Composable
private fun FieldNote(text: String, isError: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) colors.error else colors.onSurfaceVariant,
        modifier = Modifier.padding(start = FIELD_PAD, end = FIELD_PAD, top = 4.dp),
    )
}

@Composable
fun FieldFrame(
    empty: Boolean,
    label: String?,
    placeholder: String?,
    isError: Boolean,
    shape: Shape,
    glass: Glass,
    source: MutableInteractionSource,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    inner: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focused by source.collectIsFocusedAsState()
    val lit by animateFloatAsState(if (focused || isError) 1f else 0f, tween(150), label = "fieldRim")
    val ring = if (isError) colors.error else colors.onSurface.copy(alpha = FOCUS_RIM)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FIELD_HEIGHT)
            .glass(glass, shape)
            .focusRim(shape) { ring.copy(alpha = ring.alpha * lit) }
            .padding(start = if (leading != null) 4.dp else FIELD_PAD, end = if (trailing != null) 4.dp else FIELD_PAD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (isError) colors.error else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(contentAlignment = Alignment.CenterStart) {
                if (empty && placeholder != null) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        }
        if (trailing != null) trailing()
    }
}

fun Modifier.focusRim(shape: Shape, color: () -> Color): Modifier = drawWithCache {
    val hairline = 1.dp.toPx()
    val outline = shape.createOutline(Size(size.width - hairline, size.height - hairline), layoutDirection, this)
    val stroke = Stroke(hairline)
    onDrawBehind {
        val c = color()
        if (c.alpha > 0f) translate(hairline / 2f, hairline / 2f) { drawOutline(outline, c, style = stroke) }
    }
}

val CONTROL_HEIGHT = 48.dp

val ICON_WELL_CORNER = 9.dp

private val BUTTON_PAD = 20.dp
private val BUTTON_MIN_WIDTH = 112.dp
private val FIELD_HEIGHT = 56.dp
private val FIELD_PAD = 16.dp
private const val FOCUS_RIM = 0.45f

private const val ACCENT_TINT = 0.07f

private const val ACCENT_WASH_LIGHT = 0.14f
private const val ACCENT_WASH_DARK = 0.16f

private const val KEY_SINK = 0.94f
private val KEY_SINK_SPRING = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)
