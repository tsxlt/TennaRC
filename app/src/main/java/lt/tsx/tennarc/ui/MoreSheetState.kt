package lt.tsx.tennarc.ui

import androidx.activity.BackEventCompat
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import lt.tsx.tennarc.ui.components.DECK_SPRING
import lt.tsx.tennarc.ui.components.DeckGrow
import lt.tsx.tennarc.ui.components.Haptics
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.SheetDragHandle
import lt.tsx.tennarc.ui.components.grownTopPx

@Stable
internal class MoreSheetState(
    private val scope: CoroutineScope,
    private val density: () -> Density,
    private val haptics: () -> Haptics,
    private val onShow: () -> Unit,
) {
    val drag = AnchoredDraggableState(MoreSheetValue.Hidden)
    val full = mutableFloatStateOf(0f)
    val panelTop = mutableFloatStateOf(Float.NaN)
    private var settling: Job? = null
    private val crossing = MoreHold()
    private var dragStart: Float? = null

    val offset: Float get() = drag.offset.takeUnless { it.isNaN() } ?: 0f

    val revealed by derivedStateOf { offset > 0f }

    val share: Float get() = full.floatValue.let { if (it <= 0f) 0f else (offset / it).coerceIn(0f, 1f) }

    fun open() {
        onShow()
        settle(MoreSheetValue.Shown)
    }

    fun hide() = settle(MoreSheetValue.Hidden)

    fun settle(target: MoreSheetValue) {
        settling?.cancel()
        settling = scope.launch { drag.animateTo(target, DECK_SPRING) }
    }

    fun measured(height: Float) {
        drag.updateAnchors(DraggableAnchors { MoreSheetValue.Hidden at 0f; MoreSheetValue.Shown at height })
    }

    fun hold() {
        settling?.cancel()
        settling = null
        if (drag.isAnimationRunning) scope.launch(start = CoroutineStart.UNDISPATCHED) { drag.anchoredDrag(MutatePriority.UserInput) {} }
        crossing.take(offset, full.floatValue, with(density()) { MORE_SHUT_PULL.toPx() })
    }

    fun pull(delta: Float): Float {
        val taken = drag.dispatchRawDelta(delta)
        if (crossing.moved(offset)) haptics().tick()
        return taken
    }

    fun letGo(velocity: Float) {
        val opens = with(density()) { crossing.opens(offset, velocity, MORE_FLING_SPEED.toPx(), MORE_FLICK_PULL.toPx()) }
        if (crossing.flicked(opens)) haptics().tick()
        crossing.letGo()
        settle(if (opens) MoreSheetValue.Shown else MoreSheetValue.Hidden)
    }

    val swipeUp = SheetDragHandle(
        onDrag = { distance ->
            if (dragStart == null) hold()
            onShow()
            val all = full.floatValue
            if (all > 0f) {
                val current = offset
                val start = dragStart ?: current.also { dragStart = it }
                pull((start + distance).coerceIn(0f, all) - current)
            }
        },
        onRelease = { velocity ->
            dragStart = null
            letGo(velocity)
        },
        onCancel = {
            dragStart = null
            crossing.letGo()
            hide()
        },
    )

    val scroll = MoreSheetScroll(drag, full, with(density()) { MORE_FLING_SPEED.toPx() }, hold = ::hold, pull = ::pull, letGo = ::letGo)
}

@Composable
internal fun rememberMoreSheetState(sheet: () -> Sheet?, onSheet: (Sheet?) -> Unit, shown: MutableFloatState): MoreSheetState {
    val density by rememberUpdatedState(LocalDensity.current)
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val setSheet by rememberUpdatedState(onSheet)
    val scope = rememberCoroutineScope()
    val more = remember { MoreSheetState(scope, { density }, { haptics }) { setSheet(Sheet.MORE) } }
    LaunchedEffect(more) {
        snapshotFlow { more.drag.settledValue }.collect { if (it == MoreSheetValue.Hidden && sheet() == Sheet.MORE) setSheet(null) }
    }
    val current = sheet()
    LaunchedEffect(current) {
        if (current != Sheet.MORE && more.drag.targetValue != MoreSheetValue.Hidden) more.hide()
    }
    LaunchedEffect(more) {
        snapshotFlow { more.share }.collect { shown.floatValue = it }
    }
    return more
}

internal suspend fun <T> AnchoredDraggableState<T>.closeWithBack(swipe: Flow<BackEventCompat>, full: Float, open: T, shut: T, rescue: CoroutineScope) {
    try {
        anchoredDrag { _ -> swipe.collect { dragTo(full * (1f - it.progress)) } }
    } catch (e: CancellationException) {
        rescue.launch { animateTo(open, DECK_SPRING) }
        throw e
    }
    animateTo(shut, DECK_SPRING)
}

@Composable
internal fun GrownMoreOutside(more: MoreSheetState, grow: DeckGrow) {
    var heldFromAbove by remember { mutableStateOf(false) }
    val up = more.drag.settledValue == MoreSheetValue.Shown || more.drag.targetValue == MoreSheetValue.Shown
    if (!up && !heldFromAbove) return
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val top = grow.grownTopPx().takeUnless { it.isNaN() }?.roundToInt()?.coerceIn(0, constraints.maxHeight) ?: 0
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, top))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    var last = down.position.y
                    var dragging = false
                    var letGo = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            val moved = change.position - down.position
                            if (!dragging) {
                                if (moved.getDistance() <= viewConfiguration.touchSlop) continue
                                if (moved.y <= abs(moved.x)) return@awaitEachGesture
                                dragging = true
                                heldFromAbove = true
                                more.hold()
                                last = change.position.y
                            }
                            change.consume()
                            more.pull(-(change.position.y - last))
                            last = change.position.y
                        }
                        if (!dragging) {
                            more.hide()
                            return@awaitEachGesture
                        }
                        letGo = true
                        more.letGo(-velocity.calculateVelocity().y)
                    } finally {
                        heldFromAbove = false
                        if (dragging && !letGo) more.letGo(0f)
                    }
                }
            },
    )
}
