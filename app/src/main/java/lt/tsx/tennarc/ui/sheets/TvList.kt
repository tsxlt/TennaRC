package lt.tsx.tennarc.ui.sheets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.DEMO_KEY
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.currentTv
import lt.tsx.tennarc.data.tvKeys
import lt.tsx.tennarc.ui.components.ChoiceMenu
import lt.tsx.tennarc.ui.components.ChoiceMenuItem
import lt.tsx.tennarc.ui.components.ConfirmMenuItem
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.LABEL_LINES
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.IN_USE_INK
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.MenuDivider
import lt.tsx.tennarc.ui.components.SquircleCache
import lt.tsx.tennarc.ui.components.mark
import lt.tsx.tennarc.ui.components.rememberPressWash
import lt.tsx.tennarc.ui.onboarding.label
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.darkness

@Composable
internal fun TvList(
    prefs: Prefs,
    onUse: (SavedTv?) -> Unit,
    onReorder: (List<String>) -> Unit,
    actionsFor: (tv: SavedTv?, inUse: Boolean) -> List<TvAction>,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val reorder by rememberUpdatedState(onReorder)
    val moveUp = stringResource(R.string.settings_move_up)
    val moveDown = stringResource(R.string.settings_move_down)
    val useLabel = stringResource(R.string.settings_use_tv)
    val inUseLabel = stringResource(R.string.settings_in_use)
    val keys = prefs.tvKeys
    val saved by rememberUpdatedState(keys)
    val drag = remember { TvDrag() }
    var moving by remember { mutableStateOf<List<String>?>(null) }
    val order = moving?.takeIf { it.size == keys.size && it.containsAll(keys) } ?: keys
    LaunchedEffect(keys, drag.key) { if (drag.key == null && order == keys) moving = null }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val using = if (prefs.demoTv) DEMO_KEY else prefs.host
    val spots = remember { mutableStateMapOf<String, RowSpot>() }
    val pill = remember { LitPill(spots, drag) }
    pill.moveTo(using)
    LaunchedEffect(using) { pill.slide() }
    val pillInk = colors.onSurface.copy(alpha = IN_USE_INK)
    val pillPath = remember { SquircleCache() }
    Column(
        Modifier.drawBehind {
            pill.locate()
            if (pill.top.isNaN()) return@drawBehind
            val x = TV_PILL_INSET.toPx()
            val y = TV_PILL_GAP.toPx() / 2f
            translate(x, pill.top + y) {
                drawPath(pillPath.of(Size(size.width - 2f * x, pill.height - 2f * y), CornerSmall.toPx()), pillInk)
            }
        },
    ) {
        order.forEach { key ->
            key(key) {
                val demo = key == DEMO_KEY
                val tv = when {
                    demo -> null
                    key == prefs.host -> prefs.currentTv
                    else -> prefs.otherTvs.firstOrNull { it.host == key }
                }
                if (demo || tv != null) {
                    val spot = remember { RowSpot() }
                    DisposableEffect(Unit) {
                        spots[key] = spot
                        onDispose { spots.remove(key) }
                    }
                    val inUse = key == using
                    val index = order.indexOf(key)
                    val lifted = drag.key == key
                    val menuOpen = menuFor == key
                    val lift = animateFloatAsState(if (lifted && drag.held) 1f else 0f, LIFT, label = "tvLift")
                    val press = remember { MutableInteractionSource() }
                    val actions = actionsFor(tv, inUse)
                    Box(
                        Modifier
                            .onPlaced { spot.placed(it.positionInParent().y, it.size.height.toFloat(), index, glides = !lifted, scope) }
                            .zIndex(if (lifted || menuOpen) 1f else 0f)
                            .graphicsLayer { translationY = spot.glide + if (drag.key == key) drag.y else 0f },
                    ) {
                        TvRow(
                            mark = if (demo) Lucide.Tv else tv!!.kind.mark,
                            markSize = if (demo) TV_GLYPH else TV_MARK,
                            name = if (demo) stringResource(R.string.settings_demo_tv) else tv!!.name,
                            summary = if (demo) stringResource(R.string.settings_demo_tv_body) else tv!!.kind.label,
                            press = press,
                            inUse = inUse,
                            lift = lift,
                            modifier = Modifier
                                .clickable(press, indication = null, role = Role.Button) { if (inUse) menuFor = key else onUse(tv) }
                                .semantics {
                                    if (inUse) stateDescription = inUseLabel
                                    fun move(to: Int): Boolean {
                                        val moved = order.toMutableList().apply { add(to, removeAt(index)) }
                                        moving = moved
                                        reorder(moved)
                                        return true
                                    }
                                    customActions = listOfNotNull(
                                        CustomAccessibilityAction(moveUp) { move(index - 1) }.takeIf { index > 0 },
                                        CustomAccessibilityAction(moveDown) { move(index + 1) }.takeIf { index < order.lastIndex },
                                    ) + actions.filter { it.label != useLabel }.map { action ->
                                        CustomAccessibilityAction(action.label) {
                                            action.run()
                                            true
                                        }
                                    }
                                }
                                .pointerInput(key) {
                                    var finger = 0f
                                    var travel = 0f
                                    fun drop(menu: Boolean) {
                                        drag.held = false
                                        val now = moving
                                        if (travel > viewConfiguration.touchSlop) {
                                            if (now != null && now != saved) reorder(now)
                                        } else if (menu) {
                                            menuFor = key
                                        }
                                        val from = drag.y
                                        drag.settling = scope.launch {
                                            animate(from, 0f, animationSpec = GLIDE) { value, _ -> drag.y = value }
                                            drag.key = null
                                        }
                                    }
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.tick()
                                            drag.settling?.cancel()
                                            moving = moving?.takeIf { it.size == saved.size && it.containsAll(saved) } ?: saved
                                            drag.key = key
                                            drag.held = true
                                            drag.y = 0f
                                            finger = 0f
                                            travel = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            travel += abs(amount.y)
                                            finger += amount.y
                                            var now = moving ?: saved
                                            var at = now.indexOf(key)
                                            while (true) {
                                                val next = now.getOrNull(at + 1)?.let { spots[it]?.height }
                                                val previous = now.getOrNull(at - 1)?.let { spots[it]?.height }
                                                if (next != null && finger > next / 2f) {
                                                    now = now.toMutableList().apply { add(at + 1, removeAt(at)) }
                                                    finger -= next
                                                    at++
                                                } else if (previous != null && finger < -previous / 2f) {
                                                    now = now.toMutableList().apply { add(at - 1, removeAt(at)) }
                                                    finger += previous
                                                    at--
                                                } else {
                                                    break
                                                }
                                                haptics.faintTick()
                                            }
                                            moving = now
                                            var above = 0f
                                            var all = 0f
                                            for (i in now.indices) {
                                                val height = spots[now[i]]?.height ?: 0f
                                                if (i < at) above += height
                                                all += height
                                            }
                                            val own = spots[key]?.height ?: 0f
                                            drag.y = finger.coerceIn(-above, (all - above - own).coerceAtLeast(-above))
                                        },
                                        onDragEnd = { drop(menu = true) },
                                        onDragCancel = { drop(menu = false) },
                                    )
                                },
                        )
                        TvMenu(open = menuFor == key, actions = actions, onDismiss = { menuFor = null })
                    }
                }
            }
        }
    }
}

private class RowSpot {
    var top by mutableFloatStateOf(Float.NaN)
    var height by mutableFloatStateOf(0f)
    var glide by mutableFloatStateOf(0f)
    private var index = -1
    private var speed = 0f
    private var gliding: Job? = null

    fun placed(at: Float, size: Float, index: Int, glides: Boolean, scope: CoroutineScope) {
        val before = top
        val moved = this.index >= 0 && this.index != index
        top = at
        height = size
        this.index = index
        if (!moved || !glides || before.isNaN() || before == at) return
        gliding?.cancel()
        glide += before - at
        gliding = scope.launch {
            animate(glide, 0f, speed, GLIDE) { value, velocity ->
                glide = value
                speed = velocity
            }
            speed = 0f
        }
    }
}

private class TvDrag {
    var key by mutableStateOf<String?>(null)
    var held by mutableStateOf(false)
    var y by mutableFloatStateOf(0f)
    var settling: Job? = null
}

private class LitPill(private val spots: Map<String, RowSpot>, private val drag: TvDrag) {
    private val progress = Animatable(1f)
    private var key by mutableStateOf<String?>(null)
    private var fromTop = Float.NaN
    private var fromHeight = 0f
    private var underWay = false

    var top = Float.NaN
        private set
    var height = 0f
        private set

    fun locate() {
        val to = key?.let { spots[it] }
        if (to == null || to.top.isNaN()) {
            top = Float.NaN
            return
        }
        val toTop = to.top + to.glide + if (drag.key == key) drag.y else 0f
        val moved = progress.value
        if (fromTop.isNaN()) {
            top = toTop
            height = to.height
        } else {
            val t = if (underWay) moved else 0f
            top = lerp(fromTop, toTop, t)
            height = lerp(fromHeight, to.height, t)
        }
    }

    fun moveTo(using: String) = Snapshot.withoutReadObservation {
        if (key == using) return@withoutReadObservation
        if (key != null) {
            locate()
            if (!top.isNaN()) {
                fromTop = top
                fromHeight = height
                underWay = false
            }
        }
        key = using
    }

    suspend fun slide() {
        if (fromTop.isNaN()) return
        progress.snapTo(0f)
        underWay = true
        progress.animateTo(1f, PILL_SLIDE)
        fromTop = Float.NaN
        underWay = false
    }
}

@Composable
internal fun TvRow(
    mark: ImageVector,
    name: String,
    summary: String?,
    press: InteractionSource,
    modifier: Modifier = Modifier,
    markSize: Dp = TV_MARK,
    inUse: Boolean = false,
    bare: Boolean = false,
    busy: Boolean = false,
    lift: State<Float>? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface
    val lit = animateFloatAsState(if (inUse) 1f else 0f, PILL_SLIDE, label = "tvInUse")
    val wash = rememberPressWash(CornerSmall)
    val pill = MaterialTheme.shapes.small
    val tile = MaterialTheme.shapes.extraSmall
    val platter = lerp(colors.surfaceContainerLowest, ink.copy(alpha = LIFTED_INK).compositeOver(colors.surfaceContainerHigh), colors.darkness)
    val turn = animateFloatAsState(if (busy) 1f else 0f, tween(BUSY_TURN_MS, easing = FastOutSlowInEasing), label = "tvBusy")
    val outline = remember { SquircleCache() }
    Box(modifier.fillMaxWidth().padding(horizontal = TV_PILL_INSET, vertical = TV_PILL_GAP / 2)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (lift == null) {
                        Modifier
                    } else {
                        Modifier.graphicsLayer {
                            val up = lift.value
                            scaleX = 1f + LIFT_SCALE * up
                            scaleY = scaleX
                            shadowElevation = LIFT_SHADOW.toPx() * up
                            shape = pill
                        }
                    },
                )
                .drawBehind {
                    val up = lift?.value ?: 0f
                    if (up > 0f) drawPath(outline.of(size, CornerSmall.toPx()), platter, alpha = up)
                }
                .indication(press, wash)
                .heightIn(min = TV_ROW_MIN)
                .padding(horizontal = TV_ROW_PAD, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(TV_MARK_TILE)
                    .then(if (bare) Modifier else Modifier.background(ink.copy(alpha = TILE_INK), tile)),
                contentAlignment = Alignment.Center,
            ) {
                if (turn.value < 1f) {
                    Icon(
                        mark,
                        contentDescription = null,
                        tint = if (bare) colors.onSurfaceVariant else ink,
                        modifier = Modifier
                            .size(markSize)
                            .graphicsLayer {
                                val t = turn.value
                                alpha = (if (bare) 1f else MARK_DIM + (1f - MARK_DIM) * lit.value) * (1f - t)
                                scaleX = 1f - BUSY_SHRINK * t
                                scaleY = scaleX
                                rotationZ = 90f * t
                            },
                    )
                }
                if (turn.value > 0f) {
                    CircularProgressIndicator(
                        Modifier
                            .size(TV_GLYPH - 4.dp)
                            .graphicsLayer {
                                val t = turn.value
                                alpha = t
                                scaleX = 1f - BUSY_SHRINK * (1f - t)
                                scaleY = scaleX
                            },
                        color = colors.onSurfaceVariant,
                        strokeWidth = 2.dp,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Crossfade(name, animationSpec = tween(BUSY_TURN_MS), label = "tvName") { shown ->
                    Text(shown, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

private val TV_PILL_INSET = PANEL_INSET

private val TV_PILL_GAP = PANEL_ROW_GAP

private val TV_ROW_PAD = 10.dp

private const val BUSY_TURN_MS = 260

private const val BUSY_SHRINK = 0.4f

private val TV_ROW_MIN = 60.dp

private val TV_MARK_TILE = 40.dp

private val TV_MARK = 28.dp

internal val TV_GLYPH = 22.dp

private const val TILE_INK = 0.05f

private const val MARK_DIM = 0.6f

private const val LIFTED_INK = 0.12f

private const val LIFT_SCALE = 0.02f

private val LIFT_SHADOW = 12.dp

private val LIFT = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)

private val GLIDE = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)

private val PILL_SLIDE = spring<Float>(dampingRatio = 0.86f, stiffness = 900f)

internal class TvAction(val label: String, val glyph: ImageVector, val confirm: String? = null, val detail: String? = null, val run: () -> Unit)

@Composable
private fun TvMenu(open: Boolean, actions: List<TvAction>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    LaunchedEffect(open) { if (open) haptics.key() }
    val (undoing, doing) = actions.partition { it.confirm != null }
    ChoiceMenu(expanded = open, onDismiss = onDismiss, offset = DpOffset(TV_PILL_INSET, 0.dp)) {
        doing.forEach { action ->
            ChoiceMenuItem(
                text = {
                    Column {
                        Text(action.label, maxLines = LABEL_LINES, overflow = TextOverflow.Ellipsis)
                        if (action.detail != null) {
                            Text(action.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = LABEL_LINES, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                glyph = action.glyph,
                selected = false,
                onClick = {
                    onDismiss()
                    action.run()
                },
            )
        }
        if (doing.isNotEmpty() && undoing.isNotEmpty()) MenuDivider()
        undoing.forEach { action ->
            key(action.label) {
                ConfirmMenuItem(action.label, action.confirm.orEmpty(), action.glyph) {
                    onDismiss()
                    action.run()
                }
            }
        }
    }
}
