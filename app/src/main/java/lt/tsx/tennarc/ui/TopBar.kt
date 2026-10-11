package lt.tsx.tennarc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.DEMO_KEY
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.currentTv
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.data.tvKeys
import lt.tsx.tennarc.data.useDemo
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ChoiceMenu
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.MENU_INSET
import lt.tsx.tennarc.ui.components.PILL_CORNER
import lt.tsx.tennarc.ui.components.SquircleCache
import lt.tsx.tennarc.ui.components.mark
import lt.tsx.tennarc.ui.components.menuFadeEdges
import lt.tsx.tennarc.ui.components.menuLitFill
import lt.tsx.tennarc.ui.components.rememberPressWash
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.ui.theme.warning

@Composable
internal fun TopControls(
    searching: Boolean,
    shown: () -> Float,
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    onSheet: (Sheet) -> Unit,
    searchField: @Composable (Modifier) -> Unit,
    link: LinkNote,
    onIconsWidth: (Int) -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().layout { measurable, constraints ->
            val start = 16.dp.roundToPx()
            val end = lerp(4.dp, 16.dp, shown()).roundToPx()
            val row = measurable.measure(constraints.offset(horizontal = -(start + end)))
            layout(constraints.maxWidth, row.height) { row.place(start, 0) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        searchField(Modifier.weight(1f))
        Box(
            Modifier
                .graphicsLayer {
                    alpha = 1f - shown()
                    clip = true
                }
                .layout { measurable, constraints ->
                    val icons = measurable.measure(constraints.copy(minWidth = 0))
                    onIconsWidth(icons.width)
                    layout((icons.width * (1f - shown())).roundToInt(), icons.height) { icons.place(0, 0) }
                }
                .then(if (searching) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true), verticalAlignment = Alignment.CenterVertically) {
                val linkTitle = stringResource(R.string.link_banner_title)
                AnimatedVisibility(link.verdict != null, enter = TOP_ICON_IN, exit = TOP_ICON_OUT) {
                    IconButton(onClick = link.onHelp, modifier = Modifier.semantics { contentDescription = linkTitle }, shape = IconShape) {
                        val warning = MaterialTheme.colorScheme.warning
                        Icon(Lucide.WifiOuter, contentDescription = null, tint = warning.copy(alpha = WEAK_BARS_ALPHA))
                        Icon(Lucide.WifiLow, contentDescription = null, tint = warning)
                    }
                }
                AnimatedVisibility(prefs.otherTvs.isNotEmpty() || prefs.demoListed, enter = TOP_ICON_IN, exit = TOP_ICON_OUT) {
                    TvPicker(state, prefs, actions, onAdd = { onSheet(Sheet.ADD_TV) })
                }
                IconButton(onClick = { onSheet(Sheet.POWER) }, shape = IconShape) {
                    val sleeping = prefs.sleepAt > System.currentTimeMillis()
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = stringResource(R.string.power),
                        tint = if (sleeping) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { onSheet(Sheet.SETTINGS) }, shape = IconShape) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.settings))
                }
            }
        }
    }
}

@Composable
private fun TvPicker(state: TvState, prefs: Prefs, actions: RemoteActions, onAdd: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val reachable = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        prefs.otherTvs.forEach { tv -> launch { reachable[tv.host] = actions.isReachable(tv.host, tv.kind) } }
    }
    var chosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prefs.inUseKey) { chosen = null }
    val using = chosen ?: prefs.inUseKey
    fun choose(key: String, switch: (Prefs) -> Prefs) {
        if (key == using) {
            open = false
            return
        }
        chosen = key
        scope.launch {
            delay(SWITCH_BEAT_MS)
            open = false
            actions.updatePrefs(switch)
        }
    }
    val haptics = LocalHaptics.current
    Box {
        IconButton(
            onClick = {
                haptics.key()
                open = true
            },
            shape = IconShape,
        ) {
            Crossfade(if (prefs.demoTv) Lucide.Tv else prefs.kind.mark, animationSpec = tween(SWITCH_FADE_MS), label = "tvPickerMark") { mark ->
                Icon(mark, contentDescription = stringResource(R.string.tv_picker, prefs.tvName), modifier = Modifier.size(22.dp))
            }
        }
        ChoiceMenu(expanded = open, onDismiss = { open = false }) {
            val entries: List<SavedTv?> = prefs.tvKeys.mapNotNull { key ->
                when (key) {
                    DEMO_KEY -> DEMO_ENTRY
                    prefs.host -> prefs.currentTv
                    else -> prefs.otherTvs.firstOrNull { it.host == key }
                }
            } + null
            val addName = stringResource(R.string.tv_picker_add_short)
            val demoName = stringResource(R.string.settings_demo_tv)
            val measurer = rememberTextMeasurer()
            val nameStyle = MaterialTheme.typography.labelLarge
            val names = entries.map { tv -> if (tv == null) addName else if (tv === DEMO_ENTRY) demoName else tv.name }
            val widths = remember(names, nameStyle, density) {
                names.map { name ->
                    val text = measurer.measure(name, nameStyle, maxLines = 1, softWrap = false).size.width
                    with(density) { (maxOf(text.toDp(), TV_MARK_SIZE) + TV_TILE_PAD * 2).coerceAtMost(TV_TILE_MAX) }
                }
            }
            val scroll = rememberScrollState()
            val tiles = remember { mutableStateMapOf<String, Rect>() }
            val grid = remember { arrayOfNulls<LayoutCoordinates>(1) }
            val pill = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
            var pillFor by remember { mutableStateOf<String?>(null) }
            val target = tiles[using]
            LaunchedEffect(target, using) {
                val at = target ?: return@LaunchedEffect
                when {
                    pillFor == null -> pill.snapTo(at)
                    pill.targetValue != at -> pill.animateTo(at, TILE_SLIDE)
                }
                pillFor = using
            }
            val menuScope = rememberCoroutineScope()
            fun lightUp(key: String) {
                val at = tiles[key] ?: return
                pillFor = key
                menuScope.launch { pill.animateTo(at, TILE_SLIDE) }
                val inset = with(density) { MENU_INSET.toPx() }
                val left = (at.left - inset).roundToInt()
                val right = (at.right + inset - scroll.viewportSize).roundToInt()
                val to = scroll.value.coerceIn(right.coerceAtLeast(0), left.coerceAtLeast(0))
                if (to != scroll.value) menuScope.launch { scroll.animateScrollTo(to, TILE_SLIDE_SCROLL) }
            }
            val lit = menuLitFill()
            val litPath = remember { SquircleCache() }
            LaunchedEffect(target != null) {
                val at = target ?: return@LaunchedEffect
                scroll.scrollTo((at.left - with(density) { MENU_INSET.toPx() }).roundToInt().coerceAtLeast(0))
            }
            Row(
                Modifier
                    .menuFadeEdges(scroll)
                    .horizontalScroll(scroll)
                    .padding(horizontal = MENU_INSET)
                    .onPlaced { grid[0] = it }
                    .drawBehind {
                        if (target == null || pillFor == null) return@drawBehind
                        val at = pill.value
                        translate(at.left, at.top) { drawPath(litPath.of(at.size, PILL_CORNER.toPx()), lit) }
                    },
                horizontalArrangement = Arrangement.spacedBy(TV_TILE_GAP),
            ) {
                entries.forEachIndexed { index, tv ->
                    val key = when {
                        tv == null -> null
                        tv === DEMO_ENTRY -> DEMO_KEY
                        else -> tv.host
                    }
                    val placed = Modifier
                        .width(widths[index])
                        .onGloballyPositioned { tile ->
                            val from = grid[0]?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                            val at = Rect(from.localPositionOf(tile, Offset.Zero), tile.size.toSize())
                            if (key != null && tiles[key] != at) tiles[key] = at
                        }
                    if (tv == null) {
                        TvTile(Icons.Rounded.Add, addName, selected = false, dim = false, placed) {
                            open = false
                            onAdd()
                        }
                    } else if (tv === DEMO_ENTRY) {
                        TvTile(Lucide.Tv, demoName, selected = using == DEMO_KEY, dim = false, placed) {
                            if (using != DEMO_KEY) lightUp(DEMO_KEY)
                            choose(DEMO_KEY) { it.useDemo() }
                        }
                    } else {
                        TvTile(tv.kind.mark, tv.name, selected = using == tv.host, dim = tv.host != prefs.host && reachable[tv.host] != true, placed) {
                            if (using != tv.host) lightUp(tv.host)
                            choose(tv.host) { it.switchTo(tv) }
                        }
                    }
                }
            }
        }
    }
}

internal val Prefs.inUseKey: String get() = if (demoTv) DEMO_KEY else host

private val TOP_ICON_IN = expandHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f), expandFrom = Alignment.Start) +
    fadeIn(tween(220, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.5f)

private val TOP_ICON_OUT = shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 700f), shrinkTowards = Alignment.Start) +
    fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.6f)

private const val WEAK_BARS_ALPHA = 0.3f

private const val SWITCH_BEAT_MS = 110L

private const val SWITCH_FADE_MS = 150

private val TILE_SLIDE = spring(dampingRatio = 0.86f, stiffness = 900f, visibilityThreshold = Rect.VisibilityThreshold)

private val TILE_SLIDE_SCROLL = spring(dampingRatio = 0.86f, stiffness = 900f, visibilityThreshold = 1f)

internal fun Modifier.arrival(key: String): Modifier = composed {
    val arrive = remember { Animatable(1f) }
    val last = remember { arrayOf(key) }
    LaunchedEffect(key) {
        if (last[0] == key) return@LaunchedEffect
        last[0] = key
        arrive.snapTo(0f)
        arrive.animateTo(1f, spring(dampingRatio = 1f, stiffness = 700f))
    }
    graphicsLayer {
        alpha = ARRIVE_FROM + (1f - ARRIVE_FROM) * arrive.value
        translationY = (1f - arrive.value) * ARRIVE_RISE.toPx()
    }
}

private const val ARRIVE_FROM = 0.4f

private val ARRIVE_RISE = 8.dp

private val DEMO_ENTRY = SavedTv("Demo TV", "demo", TvKind.WEBOS, "")

@Composable
private fun TvTile(glyph: ImageVector, name: String, selected: Boolean, dim: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val fade = animateFloatAsState(if (dim) 0.5f else 1f, label = "tvTileDim")
    Column(
        modifier
            .clickable(interactionSource = null, indication = rememberPressWash(PILL_CORNER), role = Role.Button) {
                haptics.key()
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(horizontal = TV_TILE_PAD, vertical = 14.dp)
            .graphicsLayer { alpha = fade.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(glyph, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(TV_MARK_SIZE))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TV_TILE_MAX = 176.dp

private val TV_TILE_PAD = 16.dp

private val TV_TILE_GAP = 4.dp

private val TV_MARK_SIZE = 28.dp
