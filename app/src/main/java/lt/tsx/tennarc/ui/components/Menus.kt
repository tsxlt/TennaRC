package lt.tsx.tennarc.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.ui.theme.CornerExtraLarge
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.ICON_CORNER
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.ui.theme.ProvideGround
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.darkness
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.glassPressed
import lt.tsx.tennarc.ui.theme.scrimAlpha

@Composable
fun ChoiceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    source: MenuSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = remember { MenuMotion() }
    source?.away = { motion.alpha.value.coerceIn(0f, 1f) }
    if (!expanded && !motion.present) return
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    val provider = remember(motion) { WholeWindow(motion) }
    val dismiss by rememberUpdatedState(onDismiss)
    Popup(popupPositionProvider = provider, onDismissRequest = { dismiss() }, properties = MENU_POPUP) {
        val blurred = rememberBlurBehind(LocalView.current, expanded)
        LaunchedEffect(expanded) { motion.run(expanded) }
        CompositionLocalProvider(LocalMenuOpen provides expanded, LocalMenuMotion provides motion, LocalMenuSource provides source) {
            MenuWindow(motion, expanded, top, bottom, offset, source, onOutside = { if (expanded) dismiss() }) {
                MenuPanel(motion, glass = blurred, modifier, content)
            }
        }
    }
}

@Composable
fun ChoiceMenuItem(
    text: @Composable () -> Unit,
    glyph: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    glyphTint: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    MenuRow(
        onClick = {
            haptics.key()
            onClick()
        },
        selected = selected,
        stays = true,
        glyph = glyph,
        glyphTint = glyphTint.takeOrElse { if (selected) colors.onSurface else colors.onSurfaceVariant },
        content = text,
    )
}

@Composable
fun ChoiceMenuItem(label: String, glyph: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    ChoiceMenuItem(text = { Text(label, maxLines = LABEL_LINES, overflow = TextOverflow.Ellipsis) }, glyph = glyph, selected = selected, onClick = onClick)
}

const val LABEL_LINES = 2

@Composable
fun ConfirmMenuItem(label: String, confirm: String, glyph: ImageVector, onConfirm: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MS)
            armed = false
        }
    }
    val heat by animateFloatAsState(if (armed) 1f else 0f, tween(ARM_FADE_MS), label = "menuArmed")
    MenuRow(
        onClick = {
            if (armed) {
                haptics.confirm()
                onConfirm()
            } else {
                haptics.tick()
                armed = true
            }
        },
        stays = armed,
        heat = { heat },
        glyph = glyph,
        glyphTint = colors.error,
        ink = colors.error,
    ) {
        Layout(
            content = {
                Text(label, maxLines = LABEL_LINES, modifier = Modifier.graphicsLayer { alpha = 1f - heat }.then(if (armed) Modifier.clearAndSetSemantics {} else Modifier))
                Text(confirm, maxLines = LABEL_LINES, modifier = Modifier.graphicsLayer { alpha = heat }.then(if (armed) Modifier else Modifier.clearAndSetSemantics {}))
            },
        ) { (idle, warning), constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val a = idle.measure(loose)
            val b = warning.measure(loose)
            val width = maxOf(a.width, b.width).coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = (a.height + (b.height - a.height) * heat).roundToInt()
            layout(width, height) {
                a.placeRelative(0, (height - a.height) / 2)
                b.placeRelative(0, (height - b.height) / 2)
            }
        }
    }
}

@Composable
fun MenuDivider() {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .padding(horizontal = DIVIDER_INSET, vertical = DIVIDER_SPACE)
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawRect(ink.copy(alpha = DIVIDER_ALPHA), topLeft = Offset(0f, (size.height - 1f) / 2f), size = size.copy(height = 1f))
            },
    )
}

@Composable
fun MenuEntry(
    glyph: ImageVector,
    title: String,
    onClick: () -> Unit,
    status: String? = null,
    dot: Color? = null,
    pulse: Boolean = false,
    outlined: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    MenuRow(
        onClick = {
            haptics.key()
            onClick()
        },
        stays = true,
        glyph = null,
        glyphTint = colors.onSurface,
        padding = ENTRY_PAD,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(IconShape)
                    .drawWithCache {
                        val line = 1.dp.toPx()
                        val inset = if (outlined) line / 2 else 0f
                        val tile = Path().apply {
                            addSquircle(Rect(inset, inset, size.width - inset, size.height - inset), size.minDimension * ICON_CORNER - inset)
                        }
                        val stroke = Stroke(line)
                        onDrawBehind {
                            if (outlined) drawPath(tile, colors.outlineVariant, style = stroke) else drawPath(tile, colors.onSurface.copy(alpha = BADGE_ALPHA))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(GLYPH))
            }
            Column {
                Text(title, maxLines = LABEL_LINES)
                if (status != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dot != null) {
                            val glow = if (pulse) {
                                rememberInfiniteTransition(label = "menuDot")
                                    .animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "menuDotAlpha")
                            } else {
                                null
                            }
                            Box(Modifier.size(6.dp).graphicsLayer { alpha = glow?.value ?: 1f }.clip(CircleShape).background(dot))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun menuLitFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = IN_USE_INK)

fun Modifier.menuPill(corner: Dp = PILL_CORNER, color: () -> Color): Modifier = drawWithCache {
    val pill = Path().apply { addSquircle(Rect(Offset.Zero, size), corner.toPx()) }
    onDrawBehind {
        val fill = color()
        if (fill.alpha > 0f) drawPath(pill, fill)
    }
}

fun Modifier.menuFadeEdges(state: ScrollableState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fade = MENU_FADE.toPx()
        val left = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade)
        val right = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width)
        val ltr = layoutDirection == LayoutDirection.Ltr
        onDrawWithContent {
            drawContent()
            if (state.canScrollBackward) drawRect(if (ltr) left else right, blendMode = BlendMode.DstIn)
            if (state.canScrollForward) drawRect(if (ltr) right else left, blendMode = BlendMode.DstIn)
        }
    }

val PILL_CORNER = CornerLarge - 6.dp

val MENU_INSET = 6.dp

@Composable
private fun MenuRow(
    onClick: () -> Unit,
    glyph: ImageVector?,
    glyphTint: Color,
    selected: Boolean = false,
    stays: Boolean = false,
    heat: () -> Float = NO_HEAT,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    padding: Dp = ROW_PAD,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val wash = rememberPressWash(PILL_CORNER)
    var picked by remember { mutableStateOf(false) }
    val lit = menuLitFill()
    val held = colors.glassPressed
    val warm = colors.error.copy(alpha = ARMED_FILL)
    val open = LocalMenuOpen.current
    if (selected) {
        val menu = LocalMenuMotion.current
        if (menu != null) {
            DisposableEffect(menu) {
                menu.rowsInUse++
                onDispose { menu.rowsInUse-- }
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MENU_INSET)
            .menuPill {
                var fill = if (selected) lit else Color.Transparent
                val h = heat()
                if (h > 0f) fill = lerp(fill, warm, h)
                if (picked) fill = held.compositeOver(fill)
                fill
            }
            .clickable(interactionSource = null, indication = wash, enabled = open, role = Role.Button) {
                if (stays) picked = true
                onClick()
            }
            .then(if (selected) InUse else Modifier)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = padding, vertical = ROW_PAD_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GLYPH_GAP),
    ) {
        Row(
            Modifier.weight(1f, fill = false).then(if (selected) InUseStart else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GLYPH_GAP),
        ) {
            if (glyph != null) Icon(glyph, contentDescription = null, tint = glyphTint, modifier = Modifier.size(GLYPH))
            Box(Modifier.weight(1f, fill = false)) {
                CompositionLocalProvider(
                    LocalContentColor provides ink,
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
                    content = content,
                )
            }
        }
    }
}

private val LocalMenuOpen = staticCompositionLocalOf { true }

private val LocalMenuMotion = staticCompositionLocalOf<MenuMotion?> { null }

private val LocalMenuSource = staticCompositionLocalOf<MenuSource?> { null }

@Stable
class MenuSource {
    var rect by mutableStateOf<Rect?>(null)
    var away: () -> Float = { 0f }
        internal set
}

private val InUseLine = HorizontalAlignmentLine(::minOf)

private val InUseStartLine = VerticalAlignmentLine(::minOf)

private val InUseStart = Modifier.layout { measurable, constraints ->
    val content = measurable.measure(constraints)
    val start = if (layoutDirection == LayoutDirection.Ltr) 0 else content.width
    layout(content.width, content.height, mapOf(InUseStartLine to start)) { content.place(0, 0) }
}

private val InUse = Modifier
    .semantics { selected = true }
    .layout { measurable, constraints ->
        val row = measurable.measure(constraints)
        layout(row.width, row.height, mapOf(InUseLine to row.height / 2)) { row.place(0, 0) }
    }

@Stable
private class MenuMotion {
    val scale = Animatable(SCALE_FROM)
    val alpha = Animatable(0f)

    val travel = Animatable(0f)

    var slide = Offset.Zero

    var present by mutableStateOf(false)

    var anchor by mutableStateOf<IntRect?>(null)

    var origin by mutableStateOf(TransformOrigin.Center)

    var at = IntOffset.Zero
    var placed = false

    var rowsInUse = 0

    suspend fun run(open: Boolean) = coroutineScope {
        if (open) {
            present = true
            if (alpha.value == 0f) {
                scale.snapTo(SCALE_FROM)
                travel.snapTo(0f)
            }
            launch { scale.animateTo(1f, OPEN_SPRING) }
            launch { travel.animateTo(1f, TRAVEL) }
            alpha.animateTo(1f, OPEN_FADE)
        } else {
            launch { scale.animateTo(CLOSE_SCALE, CLOSE) }
            alpha.animateTo(0f, CLOSE)
            present = false
        }
    }
}

private class WholeWindow(private val motion: MenuMotion) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        if (motion.anchor != anchorBounds) motion.anchor = anchorBounds
        return IntOffset.Zero
    }
}

@Composable
private fun MenuWindow(motion: MenuMotion, open: Boolean, top: Int, bottom: Int, offset: DpOffset, source: MenuSource?, onOutside: () -> Unit, panel: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dim = androidx.compose.ui.util.lerp(DIM_LIGHT, DIM_DARK, colors.darkness)
    val density = LocalDensity.current
    val margin = with(density) { MENU_MARGIN.roundToPx() }
    val inset = with(density) { offset.x.roundToPx() }
    val gap = with(density) { (MENU_GAP + offset.y).roundToPx() }
    val grow = with(density) { WIDE_ANCHOR_LEAD.roundToPx() }
    val lead = if (inset != 0) 0 else grow
    val outside by rememberUpdatedState(onOutside)
    Layout(
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = dim * motion.alpha.value.coerceIn(0f, 1f)) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial).consume()
                            outside()
                        }
                    },
            )
            panel()
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scrim = measurables[0].measure(Constraints.fixed(width, height))
        val room = Constraints(
            maxWidth = (width - 2 * margin).coerceAtLeast(0),
            maxHeight = (height - top - bottom - 2 * margin).coerceAtLeast(0),
        )
        val pane = measurables[1].measure(room)
        layout(width, height) {
            scrim.place(0, 0)
            if (!open && motion.placed) {
                pane.place(motion.at)
                return@layout
            }
            val anchor = (motion.anchor ?: IntRect(width / 2, height / 2, width / 2, height / 2))
                .let { if (inset != 0 && it.width > 2 * inset) IntRect(it.left + inset, it.top, it.right - inset, it.bottom) else it }
            val w = pane.width
            val h = pane.height
            val ltr = layoutDirection == LayoutDirection.Ltr
            val wide = anchor.width >= w
            val toStart = if (wide) ltr else anchor.center.x < width / 2
            val inUse = if (motion.rowsInUse > 0) pane[InUseLine] else AlignmentLine.Unspecified
            val pops = inUse != AlignmentLine.Unspecified && inUse in 0..h
            val value = source?.rect
            val start = if (pops && value != null) pane[InUseStartLine] else AlignmentLine.Unspecified
            val valueStart = value?.let { if (ltr) it.left else it.right }?.roundToInt()
            val x = (
                if (start != AlignmentLine.Unspecified && valueStart != null) valueStart - start
                else if (toStart) anchor.left + (if (wide) lead else 0) else anchor.right - w - (if (wide) lead else 0)
                ).coerceIn(margin, (width - margin - w).coerceAtLeast(margin))
            val under = anchor.bottom + gap
            val over = anchor.top - gap - h
            val floor = height - bottom - margin
            val ceiling = top + margin
            val below = under + h <= floor || (over < ceiling && floor - anchor.bottom >= anchor.top - ceiling)
            val y = (if (pops) anchor.center.y - inUse else if (below) under else over).coerceIn(ceiling, (floor - h).coerceAtLeast(ceiling))
            val pivotX = if (start != AlignmentLine.Unspecified) x + start else if (wide) (if (ltr) x + grow else x + w - grow) else anchor.center.x
            val pivotY = if (pops) y + inUse else if (below) anchor.bottom else anchor.top
            motion.origin = TransformOrigin(
                if (w > 0) ((pivotX - x).toFloat() / w).coerceIn(0f, 1f) else 0.5f,
                if (h > 0) (pivotY - y).toFloat() / h else 0f,
            )
            motion.slide = if (start != AlignmentLine.Unspecified && value != null && valueStart != null) {
                Offset((valueStart - (x + start)).toFloat(), value.center.y - (y + inUse))
            } else {
                Offset.Zero
            }
            motion.at = IntOffset(x, y)
            motion.placed = true
            pane.place(x, y)
        }
    }
}

@Composable
private fun MenuPanel(motion: MenuMotion, glass: Boolean, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .menuGlass(MENU_SHAPE, blurred = glass) {
                val s = motion.scale.value
                scaleX = s
                scaleY = s
                val away = 1f - motion.travel.value
                translationX = motion.slide.x * away
                translationY = motion.slide.y * away
                alpha = motion.alpha.value.coerceIn(0f, 1f)
                transformOrigin = motion.origin
            }
            .width(IntrinsicSize.Max)
            .widthIn(min = MENU_MIN_WIDTH)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0)
            .padding(vertical = MENU_INSET),
        content = content,
    )
}

@Composable
private fun Modifier.menuGlass(shape: SquircleShape, blurred: Boolean, move: GraphicsLayerScope.() -> Unit): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.darkness
    val pane = glass(dialogPane(blurred), floating = true)
    val shadow = Color.Black.copy(alpha = androidx.compose.ui.util.lerp(SHADOW_LIGHT, SHADOW_DARK, dark))
    return this
        .graphicsLayer {
            move()
            shadowElevation = MENU_SHADOW.toPx()
            spotShadowColor = shadow
            ambientShadowColor = shadow
            this.shape = shape
            clip = true
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .glass(pane, shape)
}

@Composable
@ReadOnlyComposable
private fun dialogPane(blurred: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.darkness
    val solid = lerp(colors.surfaceContainerLowest, colors.surfaceContainerHighest, dark)
    return if (blurred) solid.copy(alpha = androidx.compose.ui.util.lerp(GLASS_LIGHT, GLASS_DARK, dark)) else solid
}

@Composable
fun GlassDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismissButton: () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MaterialTheme.colorScheme
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val dim = colors.scrimAlpha
        val radius = with(LocalDensity.current) { MENU_BLUR.roundToPx() }
        var blurred by remember { mutableStateOf(false) }
        DisposableEffect(window) {
            if (window != null) {
                window.setWindowAnimations(R.style.GlassDialogMotion)
                window.setDimAmount(dim)
                if (Build.VERSION.SDK_INT >= 31 && catching { view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true }.getOrDefault(false)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = radius }
                    blurred = true
                }
            }
            onDispose {}
        }
        val scale = remember { Animatable(DIALOG_FROM) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, DIALOG_SPRING) }
            alpha.animateTo(1f, OPEN_FADE)
        }
        Column(
            Modifier
                .padding(horizontal = DIALOG_MARGIN)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .menuGlass(DIALOG_SHAPE, blurred) {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value.coerceIn(0f, 1f)
                }
                .padding(DIALOG_PAD),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(DIALOG_TITLE_GAP))
            ProvideGround(colors.surfaceContainerLow) {
                content()
                Spacer(Modifier.height(DIALOG_BUTTONS_GAP))
                ButtonPair(
                    quiet = { QuietButton(dismiss, onDismissButton) },
                    main = { AccentButton(confirm, onConfirm) },
                )
            }
        }
    }
}

@Composable
private fun rememberBlurBehind(view: View, open: Boolean): Boolean {
    var blurred by remember { mutableStateOf(false) }
    val radius = with(LocalDensity.current) { MENU_BLUR.roundToPx() }
    DisposableEffect(view, open) {
        if (Build.VERSION.SDK_INT >= 31) {
            catching {
                val root = view.rootView
                val params = root.layoutParams as? WindowManager.LayoutParams
                val windows = view.context.getSystemService(WindowManager::class.java)
                if (params != null && windows != null) {
                    val on = open && windows.isCrossWindowBlurEnabled
                    val flags = if (on) params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND else params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    if (flags != params.flags) {
                        params.flags = flags
                        params.blurBehindRadius = radius
                        windows.updateViewLayout(root, params)
                    }
                    if (open) blurred = on
                }
            }.logged("Menus.rememberBlurBehind")
        }
        onDispose {}
    }
    return blurred
}

private val MENU_POPUP = PopupProperties(focusable = true, clippingEnabled = false)
private val MENU_SHAPE = SquircleShape(CornerLarge)
private val MENU_MIN_WIDTH = 176.dp
private val MENU_MARGIN = 12.dp
private val MENU_GAP = 6.dp
private val WIDE_ANCHOR_LEAD = 20.dp
private val MENU_SHADOW = 16.dp
private val MENU_FADE = 28.dp
private val MENU_BLUR = 6.dp
private val ROW_HEIGHT = 48.dp
private val ROW_PAD = 12.dp
private val ROW_PAD_V = 8.dp
private val ENTRY_PAD = 8.dp
private val GLYPH = 20.dp
private val GLYPH_GAP = 14.dp
private val DIVIDER_INSET = 16.dp
private val DIVIDER_SPACE = 6.dp
private const val DIVIDER_ALPHA = 0.1f
private const val BADGE_ALPHA = 0.08f

private const val DIM_LIGHT = 0.14f
private const val DIM_DARK = 0.34f
private const val GLASS_LIGHT = 0.88f
private const val GLASS_DARK = 0.9f
private const val SHADOW_LIGHT = 0.3f
private const val SHADOW_DARK = 0.6f
private const val ARMED_FILL = 0.14f
internal const val ARMED_MS = 3_000L
private const val ARM_FADE_MS = 180

private const val SCALE_FROM = 0.5f
private val OPEN_SPRING = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
private val OPEN_FADE = tween<Float>(110)
private const val CLOSE_SCALE = 0.95f
private val CLOSE = tween<Float>(110, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
private val TRAVEL = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
private val NO_HEAT = { 0f }

private val DIALOG_SHAPE = SquircleShape(CornerExtraLarge)
private val DIALOG_MARGIN = 24.dp
private val DIALOG_MAX_WIDTH = 440.dp
private val DIALOG_PAD = 24.dp
private val DIALOG_TITLE_GAP = 16.dp
private val DIALOG_BUTTONS_GAP = 20.dp
private const val DIALOG_FROM = 0.9f
private val DIALOG_SPRING = spring<Float>(dampingRatio = 0.78f, stiffness = 520f)
