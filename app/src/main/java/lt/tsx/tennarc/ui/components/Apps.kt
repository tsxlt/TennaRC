package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel

enum class DeckValue { Collapsed, Expanded }

data class DeckUse(val apps: Map<String, Float> = emptyMap(), val channels: Map<String, Float> = emptyMap())

data class DeckItems(val row: List<TvApp>, val rest: List<TvApp>, val inputs: List<TvApp>, val channels: List<TvChannel>)

fun deckItems(
    all: List<TvApp>,
    inputs: List<TvApp>,
    channels: List<TvChannel>,
    activeId: String?,
    use: DeckUse,
    slots: Int,
): DeckItems {
    val installed = all.filterNot { app -> inputs.any { it.id == app.id } }
        .ifEmpty { TvApps.favorites.map { TvApp(it, TvApps.knownTitles.getValue(it)) } }
    val favorite = { app: TvApp -> TvApps.favorites.indexOf(app.id).let { if (it < 0) Int.MAX_VALUE else it } }
    val ranked = installed.sortedWith(
        compareByDescending<TvApp> { use.apps[it.id] ?: 0f }.thenBy(favorite).thenBy { it.label.lowercase() },
    )
    var row = ranked.take(slots)
    val active = (installed + inputs).firstOrNull { it.id == activeId }
    if (active != null && row.none { it.id == active.id }) row = row.take(slots - 1) + active
    val rest = ranked.filterNot { app -> row.any { it.id == app.id } }
    val plugged = inputs
        .filter { input -> input.plugged != false && row.none { it.id == input.id } }
        .sortedByDescending { use.apps[it.id] ?: 0f }
    val tuned = channels.sortedWith(compareByDescending<TvChannel> { use.channels[it.id] ?: 0f }.then(ChannelNumberOrder))
    return DeckItems(row, rest, plugged, tuned)
}

private val ChannelNumberOrder = Comparator<TvChannel> { a, b ->
    val x = a.number.split(Regex("\\D+")).mapNotNull { it.toIntOrNull() }
    val y = b.number.split(Regex("\\D+")).mapNotNull { it.toIntOrNull() }
    for (i in 0 until min(x.size, y.size)) if (x[i] != y[i]) return@Comparator x[i].compareTo(y[i])
    if (x.size != y.size) return@Comparator (if (x.isEmpty()) Int.MAX_VALUE else x.size).compareTo(if (y.isEmpty()) Int.MAX_VALUE else y.size)
    a.number.compareTo(b.number)
}

val AppDeckChrome: Dp get() = DECK_LINE + DECK_STRIP

@Immutable
class HoldAction(val label: String, val glyph: ImageVector, val run: () -> Unit)

@Immutable
class DeckHold(
    val app: (TvApp) -> List<HoldAction> = { emptyList() },
    val input: (TvApp) -> List<HoldAction> = { emptyList() },
    val channel: (TvChannel) -> List<HoldAction> = { emptyList() },
)

@Composable
internal fun HoldMenu(open: Boolean, actions: List<HoldAction>, onDismiss: () -> Unit) {
    ChoiceMenu(expanded = open, onDismiss = onDismiss) {
        actions.forEach { action ->
            ChoiceMenuItem(action.label, action.glyph, selected = false) {
                onDismiss()
                action.run()
            }
        }
    }
}

internal fun holdActions(actions: List<HoldAction>) = actions.map { action ->
    CustomAccessibilityAction(action.label) {
        action.run()
        true
    }
}

@Immutable
class PhoneSource(val id: String, val name: String, val description: String, val glyph: ImageVector, val active: Boolean, val onClick: () -> Unit)

private sealed interface DeckLine {
    data class Icons(val apps: List<TvApp>) : DeckLine
    data class Inputs(val inputs: List<TvApp>, val phone: List<PhoneSource>) : DeckLine
    data class Channels(val channels: List<TvChannel>) : DeckLine
}

private fun deckLines(items: DeckItems, cols: Int, phone: List<PhoneSource>, keyWidth: (String) -> Float, room: Float, gap: Float): List<DeckLine> {
    val inputs = items.inputs
    val widths = inputs.map { keyWidth(it.title) } + phone.map { keyWidth(it.name) }
    var from = 0
    val sources = packKeys(widths, room, gap).map { count ->
        val to = from + count
        DeckLine.Inputs(
            inputs.subList(from.coerceAtMost(inputs.size), to.coerceAtMost(inputs.size)),
            phone.subList((from - inputs.size).coerceIn(0, phone.size), (to - inputs.size).coerceIn(0, phone.size)),
        ).also { from = to }
    }
    return items.rest.chunked(cols).map { DeckLine.Icons(it) } +
        sources +
        listOfNotNull(items.channels.takeIf { it.isNotEmpty() }?.let { DeckLine.Channels(it) })
}

internal fun packKeys(widths: List<Float>, room: Float, gap: Float): List<Int> {
    val lines = mutableListOf<Int>()
    var count = 0
    var used = 0f
    widths.forEach { width ->
        val next = if (count == 0) width else used + gap + width
        if (count > 0 && next > room) {
            lines += count
            count = 1
            used = width
        } else {
            count++
            used = next
        }
    }
    if (count > 0) lines += count
    return lines
}

@Composable
private fun rememberKeyWidth(): (String) -> Float {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val chrome = with(LocalDensity.current) { (SOURCE_PAD * 2 + SOURCE_MARK + SOURCE_MARK_GAP).toPx() }
    return remember(measurer, style, chrome) {
        { name -> chrome + measurer.measure(name, style, maxLines = 1, softWrap = false).size.width }
    }
}

@Composable
fun AppDeck(
    all: List<TvApp>,
    inputs: List<TvApp>,
    channels: List<TvChannel>,
    activeId: String?,
    channelId: String?,
    use: DeckUse,
    enabled: Boolean,
    deck: AnchoredDraggableState<DeckValue>,
    onLaunch: (String) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    hold: DeckHold = DeckHold(),
    phone: List<PhoneSource> = emptyList(),
    below: (@Composable () -> Unit)? = null,
    peek: Dp = PAD_PEEK,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val colors = MaterialTheme.colorScheme
    val keyHeight = sourceKeyHeight()
    BoxWithConstraints(modifier) {
        val cols = ((maxWidth - 24.dp) / DECK_COLUMN_MIN).toInt().coerceIn(4, 7)
        val inset = 12.dp + ((maxWidth - 24.dp) / cols - DECK_ICON) / 2
        val live = remember(all, inputs, channels, activeId, use, cols) { deckItems(all, inputs, channels, activeId, use, cols) }
        var items by remember { mutableStateOf(live) }
        val shut by remember { derivedStateOf { deck.offset.let { it.isNaN() || it <= 0f } && deck.targetValue == DeckValue.Collapsed } }
        LaunchedEffect(live, shut) { if (shut) items = live }

        val keyWidth = rememberKeyWidth()
        val lineRoom = with(density) { (maxWidth - inset - 12.dp).toPx() }
        val gap = with(density) { SOURCE_GAP.toPx() }
        val lines = remember(items, cols, phone, keyWidth, lineRoom, gap) { deckLines(items, cols, phone, keyWidth, lineRoom, gap) }
        val sourceLine = SOURCE_TOP + keyHeight + DECK_UNDER
        val tops = remember(lines, sourceLine) {
            var top = 0.dp
            lines.map { line -> top.also { top += if (line is DeckLine.Icons) DECK_LINE else sourceLine } } to top
        }
        val content = tops.second
        val room = maxHeight - DECK_LINE - DECK_STRIP - if (below != null) peek else 0.dp
        val gridHeight = if (content <= room) {
            content
        } else {
            (tops.first.drop(1) + content).filter { it <= room }.maxOrNull() ?: room
        }.coerceAtLeast(0.dp)
        val full = with(density) { gridHeight.toPx() }
        LaunchedEffect(full) {
            deck.updateAnchors(
                DraggableAnchors {
                    DeckValue.Collapsed at 0f
                    if (full > 0f) DeckValue.Expanded at full
                },
            )
        }
        val open = { (deck.offset.takeIf { !it.isNaN() } ?: 0f).coerceAtLeast(0f) }
        val shown = { if (full > 0f) (open() / full).coerceIn(0f, 1f) else 0f }
        val opened by remember { derivedStateOf { shown() > 0f } }
        val dragging = remember { MutableInteractionSource() }
        val dragged by dragging.collectIsDraggedAsState()
        var flung by remember { mutableStateOf(false) }
        val fling = AnchoredDraggableDefaults.flingBehavior(deck, positionalThreshold = { it * THRESHOLD }, animationSpec = DECK_SPRING)
        val drag = Modifier.anchoredDraggable(
            state = deck,
            orientation = Orientation.Vertical,
            enabled = full > 0f,
            interactionSource = dragging,
            flingBehavior = remember(fling) { fling.watched { flung = it } },
        )
        LaunchedEffect(full) {
            if (full <= 0f) return@LaunchedEffect
            snapshotFlow { open() >= full * THRESHOLD }.drop(1).collect { if (dragged || flung) haptics.tick() }
        }
        val settle = { value: DeckValue -> scope.launch { deck.animateTo(value, DECK_SPRING) } }
        val launch = { id: String ->
            onLaunch(id)
            settle(DeckValue.Collapsed)
        }
        val gridScroll = rememberScrollState()
        val channelShown = channelId != null && items.channels.any { it.id == channelId }
        val handOff = { (1f - shown() / HAND_OFF).coerceIn(0f, 1f) }

        Layout(
            contents = listOf(
                {
                    Row(Modifier.fillMaxWidth().height(DECK_LINE).padding(horizontal = 12.dp).then(drag)) {
                        items.row.forEach { app ->
                            DeckIcon(
                                app,
                                active = app.id == activeId,
                                enabled = enabled,
                                onClick = { launch(app.id) },
                                dot = if (channelShown && app.id == activeId) handOff else FULL,
                                hold = hold.app(app),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(cols - items.row.size) { Spacer(Modifier.weight(1f)) }
                    }
                },
                {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = if (revealFade(open(), size.height, REVEAL_FADE.toPx()) > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            }
                            .drawWithContent {
                                drawContent()
                                val edge = open()
                                val fade = REVEAL_FADE.toPx()
                                val amount = revealFade(edge, size.height, fade)
                                if (amount > 0f) {
                                    drawRect(
                                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = edge - fade, endY = edge),
                                        topLeft = Offset(0f, edge - fade),
                                        size = Size(size.width, fade),
                                        alpha = amount,
                                        blendMode = BlendMode.DstOut,
                                    )
                                }
                            }
                            .then(if (content > room) Modifier.verticalScroll(gridScroll) else drag)
                            .then(if (opened) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                        lines.forEachIndexed { index, line ->
                            val top = tops.first[index]
                            when (line) {
                                is DeckLine.Icons -> Row(Modifier.fillMaxWidth().height(DECK_LINE).padding(horizontal = 12.dp)) {
                                    line.apps.forEachIndexed { column, app ->
                                        DeckIcon(
                                            app,
                                            active = app.id == activeId,
                                            enabled = enabled,
                                            onClick = { launch(app.id) },
                                            hold = hold.app(app),
                                            modifier = Modifier.weight(1f).deckReveal(open, gridScroll, top, DECK_LINE, column, scale = true),
                                        )
                                    }
                                    repeat(cols - line.apps.size) { Spacer(Modifier.weight(1f)) }
                                }
                                is DeckLine.Inputs -> SourceRow(sourceLine, inset) {
                                    line.inputs.forEachIndexed { column, input ->
                                        InputKey(
                                            input,
                                            active = input.id == activeId,
                                            enabled = enabled,
                                            keyHeight = keyHeight,
                                            onClick = { launch(input.id) },
                                            hold = hold.input(input),
                                            modifier = Modifier.deckReveal(open, gridScroll, top, sourceLine, column, scale = false),
                                        )
                                    }
                                    line.phone.forEachIndexed { column, source ->
                                        PhoneKey(
                                            source,
                                            enabled = enabled,
                                            keyHeight = keyHeight,
                                            onClick = {
                                                source.onClick()
                                                settle(DeckValue.Collapsed)
                                            },
                                            modifier = Modifier.deckReveal(open, gridScroll, top, sourceLine, line.inputs.size + column, scale = false),
                                        )
                                    }
                                }
                                is DeckLine.Channels -> SourceLine(sourceLine, inset) {
                                    itemsIndexed(line.channels, key = { _, it -> it.id }) { column, channel ->
                                        ChannelKey(
                                            channel,
                                            active = channel.id == channelId,
                                            enabled = enabled,
                                            keyHeight = keyHeight,
                                            onClick = {
                                                onChannel(channel.id)
                                                settle(DeckValue.Collapsed)
                                            },
                                            hold = hold.channel(channel),
                                            modifier = Modifier.deckReveal(open, gridScroll, top, sourceLine, column, scale = false),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                {
                    val expanded = deck.targetValue == DeckValue.Expanded
                    val state = stringResource(if (expanded) R.string.deck_expanded else R.string.deck_collapsed)
                    val label = stringResource(R.string.all_apps)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(drag)
                            .then(
                                if (full > 0f) {
                                    Modifier
                                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                            settle(if (deck.targetValue == DeckValue.Expanded) DeckValue.Collapsed else DeckValue.Expanded)
                                        }
                                        .semantics {
                                            contentDescription = label
                                            stateDescription = state
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (full > 0f) {
                            Icon(
                                Lucide.ChevronDown,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 180f * shown() },
                            )
                        }
                    }
                },
                {
                    if (below != null) {
                        Box(Modifier.then(if (opened) Modifier.clearAndSetSemantics {} else Modifier)) {
                            below()
                            if (opened) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .then(drag)
                                        .pointerInput(Unit) { detectTapGestures { settle(DeckValue.Collapsed) } },
                                )
                            }
                        }
                    }
                },
            ),
        ) { (line, grid, strip, pad), constraints ->
            val width = constraints.maxWidth
            val lineHeight = DECK_LINE.roundToPx()
            val stripHeight = DECK_STRIP.roundToPx()
            val first = line.first().measure(Constraints.fixed(width, lineHeight))
            val gridPlaceable = grid.first().measure(Constraints.fixed(width, gridHeight.roundToPx()))
            val chevron = strip.first().measure(Constraints.fixed(width, stripHeight))
            val padHeight = (constraints.maxHeight - lineHeight - stripHeight).coerceAtLeast(0)
            val padPlaceable = pad.firstOrNull()?.measure(Constraints.fixed(width, padHeight))
            layout(width, constraints.maxHeight) {
                val reveal = open().roundToInt()
                first.place(0, 0)
                gridPlaceable.placeWithLayer(0, lineHeight) {
                    clip = true
                    shape = RevealShape(reveal.toFloat())
                }
                chevron.place(0, lineHeight + reveal)
                padPlaceable?.placeWithLayer(0, lineHeight + stripHeight + reveal) {
                    alpha = 1f - PAD_DIM * shown()
                }
            }
        }
    }
}

private fun Modifier.deckReveal(open: () -> Float, scroll: ScrollState, top: Dp, height: Dp, column: Int, scale: Boolean) =
    graphicsLayer {
        val lag = COLUMN_LAG.toPx() * min(column, 4)
        val span = (height - COLUMN_LAG * 4).toPx()
        val p = ((open() + scroll.value - top.toPx() - lag) / span).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * DECK_RISE.toPx()
        if (scale) {
            scaleX = lerp(DECK_SCALE_FROM, 1f, p)
            scaleY = scaleX
        }
    }

private val REVEAL_FADE = 28.dp

private fun revealFade(edge: Float, height: Float, fade: Float): Float =
    if (edge <= 0f) 0f else ((height - edge) / fade).coerceIn(0f, 1f).let { if (it < 0.02f) 0f else it }

private class RevealShape(private val height: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width, height.coerceIn(0f, size.height)))
}

private fun FlingBehavior.watched(flinging: (Boolean) -> Unit) = object : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        flinging(true)
        return try {
            with(this@watched) { performFling(initialVelocity) }
        } finally {
            flinging(false)
        }
    }
}

@Composable
fun AppGrid(
    all: List<TvApp>,
    inputs: List<TvApp>,
    channels: List<TvChannel>,
    activeId: String?,
    channelId: String?,
    use: DeckUse,
    enabled: Boolean,
    onLaunch: (String) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    hold: DeckHold = DeckHold(),
    contentPadding: PaddingValues = PaddingValues(),
    phone: List<PhoneSource> = emptyList(),
) {
    val keyHeight = sourceKeyHeight()
    BoxWithConstraints(modifier) {
        val cols = ((maxWidth - 24.dp) / GRID_TILE).toInt().coerceIn(4, GRID_COLUMNS)
        val inset = 12.dp + ((maxWidth - 24.dp) / cols - DECK_ICON) / 2
        val sourceLine = SOURCE_TOP + keyHeight + DECK_UNDER
        val keyWidth = rememberKeyWidth()
        val density = LocalDensity.current
        val lineRoom = with(density) { (maxWidth - inset - 12.dp).toPx() }
        val gap = with(density) { SOURCE_GAP.toPx() }
        val lines = remember(all, inputs, channels, use, cols, phone, keyWidth, lineRoom, gap) {
            val items = deckItems(all, inputs, channels, activeId = null, use, cols)
            deckLines(items.copy(rest = items.row + items.rest), cols, phone, keyWidth, lineRoom, gap)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            lines.forEachIndexed { index, line ->
                item(key = index, contentType = line::class) {
                    when (line) {
                        is DeckLine.Icons -> Row(Modifier.fillMaxWidth().height(DECK_LINE).padding(horizontal = 12.dp)) {
                            line.apps.forEach { app ->
                                DeckIcon(app, active = app.id == activeId, enabled = enabled, onClick = { onLaunch(app.id) }, hold = hold.app(app), modifier = Modifier.weight(1f))
                            }
                            repeat(cols - line.apps.size) { Spacer(Modifier.weight(1f)) }
                        }
                        is DeckLine.Inputs -> SourceRow(sourceLine, inset) {
                            line.inputs.forEach { input ->
                                InputKey(input, active = input.id == activeId, enabled = enabled, keyHeight = keyHeight, onClick = { onLaunch(input.id) }, hold = hold.input(input))
                            }
                            line.phone.forEach { source ->
                                PhoneKey(source, enabled = enabled, keyHeight = keyHeight, onClick = source.onClick)
                            }
                        }
                        is DeckLine.Channels -> SourceLine(sourceLine, inset) {
                            itemsIndexed(line.channels, key = { _, it -> it.id }) { _, channel ->
                                ChannelKey(
                                    channel,
                                    active = channel.id == channelId,
                                    enabled = enabled,
                                    keyHeight = keyHeight,
                                    onClick = { onChannel(channel.id) },
                                    hold = hold.channel(channel),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GRID_TILE = 72.dp

private const val GRID_COLUMNS = 10

internal val FULL = { 1f }

private val DECK_COLUMN_MIN = 64.dp

internal val DECK_ICON = 48.dp

private val DECK_LINE = 72.dp

internal val DECK_TOP = 6.dp

internal val DECK_UNDER = 18.dp

private val DECK_STRIP = 24.dp

private val DECK_RISE = 8.dp

private const val DECK_SCALE_FROM = 0.92f

private val COLUMN_LAG = 6.dp

private const val THRESHOLD = 0.4f

private const val HAND_OFF = 0.3f

internal val PAD_PEEK = 96.dp

internal val DECK_TILE_TOP = DECK_TOP

private const val PAD_DIM = 0.62f

val DECK_SPRING = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
