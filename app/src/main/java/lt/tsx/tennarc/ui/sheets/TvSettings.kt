package lt.tsx.tennarc.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.ui.components.dimmedUnless
import lt.tsx.tennarc.ui.components.LevelSlider
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.SegmentKey
import lt.tsx.tennarc.ui.components.SquircleCache
import lt.tsx.tennarc.ui.components.heavier
import lt.tsx.tennarc.ui.components.pressAndRepeat
import lt.tsx.tennarc.ui.components.smoothstep
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.darkness
import lt.tsx.tennarc.ui.theme.drawFrost
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.grainToRestore
import lt.tsx.tennarc.ui.theme.ground
import lt.tsx.tennarc.ui.theme.seeThrough

private val PICTURE_MODES = listOf(
    Choice("normal", R.string.picture_standard),
    Choice("vivid", R.string.picture_vivid),
    Choice("cinema", R.string.picture_cinema),
    Choice("game", R.string.picture_game),
    Choice("sports", R.string.picture_sports),
    Choice("eco", R.string.picture_eco),
    Choice("expert1", R.string.picture_expert),
)

private val SOUND_OUTPUTS = listOf(
    Choice("tv_speaker", R.string.sound_tv),
    Choice("external_arc", R.string.sound_arc),
    Choice("external_optical", R.string.sound_optical),
    Choice("bt_soundbar", R.string.sound_bluetooth),
    Choice("headphone", R.string.sound_headphones),
)

private data class Choice(val id: String, val label: Int)

private val AMBILIGHT_NAMES = mapOf(
    AmbilightStyle.OFF to R.string.ambilight_off,
    AmbilightStyle.FOLLOW_VIDEO to R.string.ambilight_video,
    AmbilightStyle.FOLLOW_AUDIO to R.string.ambilight_audio,
    AmbilightStyle.LOUNGE to R.string.ambilight_lounge,
)

private val AMBILIGHT_CHOICES = AMBILIGHT_NAMES.map { (style, label) -> Choice(style.name, label) }

private enum class SettingPanel(val onDark: Color, val onLight: Color) {
    BRIGHTNESS(Color(0xFFF2C25B), Color(0xFFB07A0E)),
    PICTURE(Color(0xFF7FA6FF), Color(0xFF3A68D8)),
    SOUND(Color(0xFFB79CFF), Color(0xFF7652D6)),
    AMBILIGHT(Color(0xFF5FD3C4), Color(0xFF16897C)),
}

private val SettingPanel.hue: Color
    @Composable @ReadOnlyComposable get() = lerp(onLight, onDark, MaterialTheme.colorScheme.darkness)

@Composable
fun TvSettingControls(
    picture: TvPicture?,
    output: String?,
    ambilight: Ambilight?,
    enabled: Boolean,
    onMode: (String) -> Unit,
    onBacklight: (Int) -> Unit,
    onOutput: (String) -> Unit,
    onAmbilight: (AmbilightStyle) -> Unit,
    onPairAmbilight: () -> Unit,
    onAmbilightPin: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val mode = picture?.mode
    val backlight = picture?.backlight
    var open by remember { mutableStateOf<SettingPanel?>(null) }
    var shown by remember { mutableStateOf(SettingPanel.BRIGHTNESS) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val level = dragging ?: backlight?.toFloat()
    val panels = listOfNotNull(
        SettingPanel.BRIGHTNESS.takeIf { level != null },
        SettingPanel.PICTURE.takeIf { mode != null },
        SettingPanel.SOUND.takeIf { output != null },
        SettingPanel.AMBILIGHT.takeIf { ambilight != null },
    )
    val shelf = MaterialTheme.shapes.large
    val keyWashes = colorKeyWashes(colors.surfaceContainerHigh)
    val hues = panels.map { it.hue }
    val ground = ground()
    val plain = colors.surfaceContainerHigh
    val wash = remember(keyWashes, hues, plain, ground) { shelfWash(shelfHues(keyWashes, hues), plain, ground) }
    Column(
        Modifier
            .fillMaxWidth()
            .dimmedUnless(enabled)
            .clip(shelf)
            .then(if (wash != null) Modifier.background(wash) else Modifier.background(seeThrough(colors.surfaceContainerHigh)))
            .glass(clearGlass(Color.Transparent, sheen = 0f), shelf)
            .padding(SHELF_INSET),
    ) {
        val tabs = panels.map { panel ->
            when (panel) {
                SettingPanel.BRIGHTNESS -> SettingTab(Lucide.Sun, stringResource(R.string.picture_backlight), level?.roundToInt()?.toString().orEmpty(), panel.hue)
                SettingPanel.PICTURE -> SettingTab(Lucide.Image, stringResource(R.string.picture_title), mode?.let { choiceName(PICTURE_MODES, it) }.orEmpty(), panel.hue)
                SettingPanel.SOUND -> SettingTab(Lucide.Speaker, stringResource(R.string.sound_title), output?.let { choiceName(SOUND_OUTPUTS, it) }.orEmpty(), panel.hue)
                SettingPanel.AMBILIGHT -> SettingTab(
                    Lucide.Lamp,
                    stringResource(R.string.ambilight_title),
                    ambilight?.current?.takeIf { ambilight.access == AmbilightAccess.Paired }?.let { stringResource(AMBILIGHT_NAMES.getValue(it)) }.orEmpty(),
                    panel.hue,
                )
            }
        }
        SettingTabs(tabs, picked = panels.indexOf(open).takeIf { it >= 0 }, enabled) { index ->
            val panel = panels[index]
            if (open == panel) {
                open = null
            } else {
                open = panel
                shown = panel
            }
        }
        AnimatedVisibility(open != null && open in panels) {
            AnimatedContent(
                shown,
                transitionSpec = {
                    val ahead = panels.indexOf(targetState) > panels.indexOf(initialState)
                    val side = if (ahead) 1 else -1
                    (fadeIn(tween(160)) + slideInHorizontally(SWAP_SPRING) { side * it / SWAP_SHARE })
                        .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(SWAP_SPRING) { -side * it / SWAP_SHARE })
                        .using(SizeTransform(clip = false))
                },
                label = "settingPanel",
            ) { panel ->
                Box(Modifier.padding(top = PANEL_GAP)) {
                    when (panel) {
                        SettingPanel.BRIGHTNESS -> LevelSlider(
                            value = level ?: 0f,
                            onValueChange = { dragging = it },
                            icon = Lucide.Sun,
                            enabled = enabled,
                            valueRange = 0f..100f,
                            valueLabel = level?.roundToInt()?.toString(),
                            contentDescription = stringResource(R.string.picture_backlight),
                            fillColor = SettingPanel.BRIGHTNESS.hue.copy(alpha = FILL_ALPHA),
                            trackColor = shelfInner(colors.surfaceContainerHigh),
                            onValueChangeFinished = {
                                dragging?.let { onBacklight(it.roundToInt()) }
                                dragging = null
                            },
                        )
                        SettingPanel.PICTURE -> if (mode != null) Options(PICTURE_MODES, mode, SettingPanel.PICTURE.hue, enabled, onMode)
                        SettingPanel.SOUND -> if (output != null) Options(SOUND_OUTPUTS, output, SettingPanel.SOUND.hue, enabled, onOutput)
                        SettingPanel.AMBILIGHT -> if (ambilight != null) {
                            if (ambilight.access == AmbilightAccess.Paired) {
                                Options(AMBILIGHT_CHOICES, ambilight.current?.name?.takeIf { name -> AMBILIGHT_CHOICES.any { it.id == name } }, SettingPanel.AMBILIGHT.hue, enabled) { onAmbilight(AmbilightStyle.valueOf(it)) }
                            } else {
                                AmbilightRow(ambilight, enabled, onAmbilight, onPairAmbilight, onAmbilightPin)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SettingTab(val icon: ImageVector, val name: String, val value: String, val hue: Color)

@Composable
private fun SettingTabs(tabs: List<SettingTab>, picked: Int?, enabled: Boolean, onTab: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    val opens = remember(tabs.size) { List(tabs.size) { Animatable(0f) } }
    LaunchedEffect(picked, opens) {
        opens.forEachIndexed { i, open -> launch { open.animateTo(if (i == picked) 1f else 0f, TAB_SPRING) } }
    }
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val gap = with(LocalDensity.current) { TAB_GAP.toPx() }
    val fullWidths = remember(tabs.size) { FloatArray(tabs.size) }
    val wash = remember { SquircleCache() }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(TAB_HEIGHT)
            .selectableGroup()
            .drawBehind {
                val shares = opens.map { it.value.coerceIn(0f, 1f) }
                val spans = tabSpans(shares, size.width, gap)
                val total = shares.sum()
                if (total < 0.001f) return@drawBehind
                var left = 0f
                var right = 0f
                var red = 0f
                var green = 0f
                var blue = 0f
                shares.forEachIndexed { i, share ->
                    val part = share / total
                    left += spans[i].start * part
                    right += spans[i].endInclusive * part
                    red += tabs[i].hue.red * part
                    green += tabs[i].hue.green * part
                    blue += tabs[i].hue.blue * part
                }
                val seen = total.coerceAtMost(1f)
                val tab = wash.of(left, 0f, right - left, size.height, TAB_CORNER.toPx())
                drawPath(tab, Color(red, green, blue, seen))
                drawFrost(grainToRestore(seen)) { grain, alpha, filter, blend ->
                    drawPath(tab, grain, alpha = alpha, colorFilter = filter, blendMode = blend)
                }
            },
        content = {
            tabs.forEachIndexed { i, tab ->
                TabCell(tab, { opens[i].value }, { fullWidths.getOrElse(i) { 0f } }, selected = i == picked, enabled, style, measurer) {
                    haptics.key()
                    onTab(i)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth.toFloat()
        fullWidths.indices.forEach { i ->
            val alone = tabSpans(List(tabs.size) { if (it == i) 1f else 0f }, width, gap)[i]
            fullWidths[i] = alone.endInclusive - alone.start
        }
        val spans = tabSpans(opens.map { it.value.coerceIn(0f, 1f) }, width, gap)
        val placeables = measurables.mapIndexed { i, measurable ->
            val left = spans[i].start.roundToInt()
            measurable.measure(Constraints.fixed(spans[i].endInclusive.roundToInt() - left, constraints.maxHeight))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable -> placeable.place(spans[i].start.roundToInt(), 0) }
        }
    }
}

private fun tabSpans(opens: List<Float>, width: Float, gap: Float): List<ClosedFloatingPointRange<Float>> {
    if (opens.size == 1) {
        val span = width * MAX_OPEN_SHARE
        val left = (width - span) / 2f
        return listOf(left..(left + span))
    }
    val picked = minOf(PICKED_WIDTH, (opens.size - 1) * MAX_OPEN_SHARE / (1f - MAX_OPEN_SHARE))
    val weights = opens.map { 1f + (picked - 1f) * it }
    val unit = (width - gap * (opens.size - 1)) / weights.sum()
    var x = 0f
    return weights.map { weight ->
        val span = x..(x + weight * unit)
        x += weight * unit + gap
        span
    }
}

@Composable
private fun TabCell(
    tab: SettingTab,
    open: () -> Float,
    fullWidth: () -> Float,
    selected: Boolean,
    enabled: Boolean,
    style: TextStyle,
    measurer: TextMeasurer,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 40 else 180), label = "tabGlow")
    val ink = animateColorBetween(selected, tab.hue, colors.surfaceContainerHigh, tween(180), label = "tabInk")
    val glyph = remember(tab.icon) { tab.icon.heavier(TAB_STROKE) }
    val nameWidth = remember(tab.name, style, measurer) { measurer.measure(tab.name, style, maxLines = 1).size.width }
    val well = remember { SquircleCache() }
    Layout(
        modifier = Modifier
            .drawBehind { if (glow > 0f) drawPath(well.of(size, TAB_CORNER.toPx()), colors.onSurface.copy(alpha = PRESS_ALPHA * glow)) }
            .pressAndRepeat(enabled, repeat = false, onPressedChange = { pressed = it }) { click() }
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = tab.name
                if (tab.value.isNotEmpty()) stateDescription = tab.value
                if (enabled) {
                    onClick {
                        click()
                        true
                    }
                } else {
                    disabled()
                }
            },
        content = {
            Icon(glyph, contentDescription = null, tint = ink, modifier = Modifier.size(TAB_GLYPH))
            Text(tab.name, style = style, color = colors.surfaceContainerHigh, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val icon = measurables[0].measure(Constraints())
        val name = measurables[1].measure(Constraints())
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val opened = open().coerceIn(0f, 1f)
        val gap = NAME_GAP.roundToPx()
        val group = icon.width + gap + nameWidth
        val full = fullWidth()
        val fits = group + TAB_PADDING.roundToPx() * 2 <= full
        val iconX = lerp((width - icon.width) / 2f, (width - group) / 2f, if (fits) opened else 0f).roundToInt()
        layout(width, height) {
            icon.place(iconX, (height - icon.height) / 2)
            if (fits) {
                name.placeWithLayer(iconX + icon.width + gap, (height - name.height) / 2) {
                    alpha = ((opened - NAME_FROM) / (1f - NAME_FROM)).coerceIn(0f, 1f)
                }
            }
        }
    }
}

fun hasTvSettings(picture: TvPicture?, output: String?, ambilight: Ambilight?): Boolean =
    picture?.mode != null || picture?.backlight != null || output != null || ambilight != null

@Composable
private fun choiceName(known: List<Choice>, id: String): String =
    known.firstOrNull { it.id == id }?.let { stringResource(it.label) } ?: id

@Composable
private fun Options(known: List<Choice>, current: String?, hue: Color, enabled: Boolean, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = shelfInner(colors.surfaceContainerHigh)
    val ids = known.map { it.id }.let { ids -> if (current == null || known.any { it.id == current }) ids else ids + current }
    val scroll = rememberScrollState()
    val spans = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }
    val pickStart = remember { Animatable(0f) }
    val pickEnd = remember { Animatable(0f) }
    var pickShown by remember { mutableStateOf(false) }
    val pickPath = remember { SquircleCache() }
    val pickColor = hue.copy(alpha = FILL_ALPHA).compositeOver(base)
    val pickGrain = grainToRestore(pickColor.alpha)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(OPTION_HEIGHT)
            .clip(MaterialTheme.shapes.large),
    ) {
        val share = maxWidth / ids.size
        val viewport = constraints.maxWidth
        val target = current?.let { spans[it] }
        LaunchedEffect(target) {
            val span = target ?: return@LaunchedEffect
            if (!pickShown) {
                pickStart.snapTo(span.start)
                pickEnd.snapTo(span.endInclusive)
                pickShown = true
                if (scroll.value == 0) scroll.scrollTo(((span.start + span.endInclusive) / 2 - viewport / 2f).roundToInt().coerceAtLeast(0))
                return@LaunchedEffect
            }
            val right = span.start > pickStart.value
            launch { pickStart.animateTo(span.start, if (right) PICK_TRAIL else PICK_LEAD) }
            pickEnd.animateTo(span.endInclusive, if (right) PICK_LEAD else PICK_TRAIL)
        }
        Row(
            Modifier
                .fillMaxHeight()
                .fadeEdges(scroll)
                .horizontalScroll(scroll)
                .drawBehind {
                    if (!pickShown) return@drawBehind
                    val edge = PICK_INSET.toPx()
                    val pill = pickPath.of(
                        pickStart.value + edge,
                        edge,
                        (pickEnd.value - pickStart.value - edge * 2).coerceAtLeast(0f),
                        size.height - edge * 2,
                        PICK_CORNER.toPx(),
                    )
                    drawPath(pill, pickColor)
                    drawFrost(pickGrain) { grain, alpha, filter, blend ->
                        drawPath(pill, grain, alpha = alpha, colorFilter = filter, blendMode = blend)
                    }
                }
                .selectableGroup(),
        ) {
            ids.forEach { id ->
                val on = id == current
                val label = known.firstOrNull { it.id == id }?.let { stringResource(it.label) } ?: id
                val ink = animateColorBetween(on, colors.onSurfaceVariant, hue, tween(PICK_INK_MS), label = "optionInk")
                SegmentKey(
                    onPress = { if (!on) onPick(id) },
                    label = label,
                    enabled = enabled,
                    glow = colors.surfaceContainerHighest,
                    ground = colors.surfaceContainerHigh,
                    selected = on,
                    inset = PICK_INSET,
                    corner = PICK_CORNER,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = share)
                        .onPlaced { placed ->
                            val x = placed.positionInParent().x
                            spans[id] = x..(x + placed.size.width)
                        },
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = ink,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

private val PICK_INSET = 4.dp
private val PICK_CORNER = 16.dp
private const val PICK_INK_MS = 220
private val PICK_LEAD = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
private val PICK_TRAIL = spring<Float>(dampingRatio = 0.8f, stiffness = 450f)

private fun Modifier.fadeEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = if (scroll.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithContent {
        drawContent()
        val fade = FADE.toPx()
        if (scroll.value > 0) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade), blendMode = BlendMode.DstIn)
        }
        if (scroll.value < scroll.maxValue) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width), blendMode = BlendMode.DstIn)
        }
    }

private val SHELF_INSET = 4.dp
private val PANEL_GAP = 8.dp

internal fun shelfInner(ground: Color): Color = ground.copy(alpha = INNER_ALPHA)

private const val INNER_ALPHA = 0.55f
private val TAB_HEIGHT = 40.dp
private val TAB_CORNER = 16.dp

internal fun shelfHues(keyWashes: List<Color>, hues: List<Color>): List<Color> {
    val hsv = FloatArray(3)
    var saturation = 0f
    var value = 0f
    for (wash in keyWashes) {
        android.graphics.Color.colorToHSV(wash.toArgb(), hsv)
        saturation += hsv[1] / keyWashes.size
        value += hsv[2] / keyWashes.size
    }
    return hues.map { hue ->
        android.graphics.Color.colorToHSV(hue.toArgb(), hsv)
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)))
    }
}

internal fun shelfWash(washes: List<Color>, plain: Color, ground: Color): Brush? {
    if (washes.isEmpty()) return null
    if (washes.size > 1) {
        return Brush.horizontalGradient(*washes.mapIndexed { i, wash -> (i + 0.5f) / washes.size to seeThrough(wash, ground) }.toTypedArray())
    }
    val wash = washes.single()
    fun faded(strength: Float) = seeThrough(lerp(plain, wash, strength), ground)
    val middle = 0.5f
    val first = middle - TAB_REACH / 2f
    val last = middle + TAB_REACH / 2f
    val stops = buildList {
        for (step in 0 until FADE_STEPS) {
            val along = step / FADE_STEPS.toFloat()
            add(first * along to faded(smoothstep(0f, 1f, along)))
        }
        add(first to seeThrough(wash, ground))
        add(middle to seeThrough(wash, ground))
        add(last to seeThrough(wash, ground))
        for (step in FADE_STEPS - 1 downTo 0) {
            val along = step / FADE_STEPS.toFloat()
            add(1f - (1f - last) * along to faded(smoothstep(0f, 1f, along)))
        }
    }
    return Brush.horizontalGradient(*stops.toTypedArray())
}

private const val TAB_REACH = 1f / 3f
private const val FADE_STEPS = 8
private val OPTION_HEIGHT = 48.dp
private val FADE = 24.dp
private const val PICKED_WIDTH = 2.3f
private val TAB_GLYPH = 22.dp
private val NAME_GAP = 8.dp
private val TAB_PADDING = 8.dp
private val TAB_GAP = 4.dp
private val TAB_SPRING = spring<Float>(dampingRatio = 1f, stiffness = 500f)
private const val MAX_OPEN_SHARE = 0.5f
private const val TAB_STROKE = 2.25f
private const val PRESS_ALPHA = 0.08f
private const val FILL_ALPHA = 0.22f
private const val NAME_FROM = 0.55f
private const val SWAP_SHARE = 12
private val SWAP_SPRING = spring(dampingRatio = 0.8f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))
