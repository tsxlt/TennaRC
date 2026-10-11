package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import lt.tsx.tennarc.ui.theme.PillShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import lt.tsx.tennarc.ui.theme.Glass
import lt.tsx.tennarc.ui.theme.LocalGround
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.drawFrost
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.ground
import lt.tsx.tennarc.ui.theme.rimBrush
import lt.tsx.tennarc.ui.theme.sheenBrush

@Immutable
internal data class DeckMetrics(val row: Dp, val well: Dp, val capsule: Dp, val capsuleWell: Dp, val handleGap: Dp) {
    companion object {
        fun forHeight(height: Dp, width: Dp = FULL_WIDTH): DeckMetrics {
            val wide = ((width - NARROW_WIDTH) / (FULL_WIDTH - NARROW_WIDTH)).coerceIn(0f, 1f)
            val tall = ((height - SHORT_HEIGHT) / (TALL_HEIGHT - SHORT_HEIGHT)).coerceIn(0f, 1f).coerceAtMost(wide)
            return DeckMetrics(
                row = lerp(52.dp, 64.dp, tall),
                well = lerp(44.dp, 52.dp, tall),
                capsule = lerp(44.dp, 52.dp, tall),
                capsuleWell = lerp(40.dp, 48.dp, tall),
                handleGap = lerp(4.dp, 6.dp, tall),
            )
        }
    }
}

private val SHORT_HEIGHT = 640.dp

private val TALL_HEIGHT = 880.dp

private val NARROW_WIDTH = 336.dp

private val FULL_WIDTH = 360.dp

internal val LocalDeckMetrics = staticCompositionLocalOf { DeckMetrics.forHeight(0.dp) }

private val PANEL_SHEEN_DEPTH = 112.dp

private val PANEL_RIM_FADE = 40.dp

@Immutable
data class SheetDragHandle(
    val onDrag: (pullPx: Float) -> Unit,
    val onRelease: (velocityPxPerSec: Float) -> Unit,
    val onCancel: () -> Unit = {},
)

internal const val DECK_HANDLE_TAG = "deckHandle"

@Immutable
data class DeckGrow(
    val revealedPx: () -> Float,
    val fullPx: MutableFloatState,
    val onMeasured: (fullPx: Float) -> Unit,
    val revealed: () -> Boolean,
    val scrollConnection: NestedScrollConnection,
    val content: @Composable () -> Unit,
    val panelTopPx: MutableFloatState,
    val ceilingPx: () -> Float = { Float.NaN },
)

fun DeckGrow.grownTopPx(): Float = panelTopPx.floatValue - revealedPx().coerceAtLeast(0f)

val DeckShape: Shape
    @Composable get() = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

@Composable
fun DeckPanel(
    swipeUp: SheetDragHandle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    room: DpSize? = null,
    grow: DeckGrow? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fill = colors.surfaceContainerLow
    val glass = clearGlass(fill, floating = false)
    val shape = DeckShape
    val drag by rememberUpdatedState(swipeUp)
    val usable by rememberUpdatedState(enabled)
    val growing by rememberUpdatedState(grow)
    val dim by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, tween(DIM_MS), label = "deckDim")
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val metrics = remember(window, room, density) {
        val size = room ?: with(density) { DpSize(window.width.toDp(), window.height.toDp()) }
        DeckMetrics.forHeight(size.height, size.width)
    }
    val gesture = Modifier
        .fillMaxWidth()
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val open = growing?.let { it.revealedPx() > 0f } == true
                if (!usable && !open) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, Offset.Zero)
                var moved = Offset.Zero
                val alreadyOpen = open
                var began = false
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (change.isConsumed || !change.pressed) return@awaitEachGesture
                        moved += change.positionChange()
                        if (moved.getDistance() <= slop) continue
                        val vertical = if (alreadyOpen) abs(moved.y) > abs(moved.x) else -moved.y > abs(moved.x)
                        if (!vertical) return@awaitEachGesture
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, moved)
                        began = true
                        drag.onDrag(-moved.y)
                        break
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        moved += change.positionChange()
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, moved)
                        if (!change.pressed) break
                        drag.onDrag(-moved.y)
                    }
                    drag.onRelease(-velocity.calculateVelocity().y)
                    began = false
                } finally {
                    if (began) drag.onCancel()
                }
            }
        }
    val insetAndPadding = Modifier
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
    val handle = @Composable {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = metrics.handleGap)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(PillShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
                    .testTag(DECK_HANDLE_TAG)
                    .clearAndSetSemantics {},
            )
        }
    }
    if (grow == null) {
        Column(
            modifier
                .then(gesture)
                .glass(glass, shape)
                .then(insetAndPadding),
        ) {
            handle()
            CompositionLocalProvider(LocalDeckMetrics provides metrics, LocalGround provides fill) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }, content = content)
            }
        }
    } else {
        GrowingDeckPanel(modifier.then(gesture), gesture, insetAndPadding, glass, shape, dim, metrics, grow, handle, content)
    }
}

@Composable
private fun GrowingDeckPanel(
    modifier: Modifier,
    gesture: Modifier,
    insetAndPadding: Modifier,
    glass: Glass,
    panelShape: Shape,
    dim: Float,
    metrics: DeckMetrics,
    grow: DeckGrow,
    handle: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val under = ground()
    val laidOn = glass.fill.compositeOver(under.copy(alpha = 1f))
    val paint = remember { GrowingPaint() }
    Layout(
        contents = listOf(
            { Box(gesture) { handle() } },
            {
                Box(
                    (if (grow.revealed()) Modifier else Modifier.clearAndSetSemantics {})
                        .nestedScroll(grow.scrollConnection),
                ) {
                    CompositionLocalProvider(LocalDeckMetrics provides metrics, LocalGround provides laidOn) { grow.content() }
                }
            },
            {
                CompositionLocalProvider(LocalDeckMetrics provides metrics, LocalGround provides laidOn) {
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }, content = content)
                }
            },
        ),
        modifier = modifier
            .onGloballyPositioned { grow.panelTopPx.floatValue = it.positionInRoot().y }
            .drawBehind {
                val reveal = grow.revealedPx().coerceAtLeast(0f)
                val top = -reveal
                val height = size.height - top
                val hairline = 1.dp.toPx()
                val half = hairline / 2
                paint.update(this, panelShape, glass, height)
                val outline = paint.outline!!
                translate(0f, top) {
                    drawOutline(outline, under.copy(alpha = 1f))
                    drawFrost { grain, alpha, filter, blend -> drawOutline(outline, grain, alpha, colorFilter = filter, blendMode = blend) }
                    if (glass.fill.alpha > 0f) drawOutline(outline, glass.fill)
                    if (glass.sheen.alpha > 0f) drawOutline(outline, paint.sheen!!)
                    drawFrost(glass.grain) { grain, alpha, filter, blend -> drawOutline(outline, grain, alpha, colorFilter = filter, blendMode = blend) }
                    translate(half, half) { drawOutline(paint.rimOutline!!, paint.rim!!, style = paint.stroke!!) }
                }
            }
            .then(insetAndPadding),
    ) { (handleM, moreM, peekM), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val handleP = handleM.first().measure(loose)
        val peekP = peekM.first().measure(loose)
        val ceiling = grow.ceilingPx()
        val top = if (ceiling.isNaN()) Float.NaN else grow.panelTopPx.floatValue
        val room = if (ceiling.isNaN() || top.isNaN()) Constraints.Infinity else ((top - ceiling).roundToInt() + peekP.height).coerceAtLeast(peekP.height)
        val moreP = moreM.first().measure(Constraints(maxWidth = width, maxHeight = room))
        val growMax = (moreP.height - peekP.height).coerceAtLeast(1)
        grow.fullPx.floatValue = growMax.toFloat()
        grow.onMeasured(growMax.toFloat())
        val reportedHeight = handleP.height + peekP.height
        layout(width, reportedHeight) {
            val grown = grow.revealedPx().roundToInt().coerceIn(0, growMax)
            val progress = grown.toFloat() / growMax
            handleP.place(0, -grown)
            val rowsShown = (1f - progress / CROSSFADE_SPAN).coerceIn(0f, 1f)
            if (rowsShown > 0f) {
                peekP.placeWithLayer(0, handleP.height - grown) {
                    alpha = rowsShown
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            }
            val shows = (grown + peekP.height).coerceIn(0, moreP.height)
            if (grown > 0) {
                moreP.placeWithLayer(0, handleP.height - grown) {
                    clip = true
                    shape = RevealFromTop(shows.toFloat())
                    alpha = (progress / CROSSFADE_SPAN).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            }
        }
    }
}

private const val CROSSFADE_SPAN = 0.6f

private class GrowingPaint {
    var outline: Outline? = null
    var rimOutline: Outline? = null
    var sheen: Brush? = null
    var rim: Brush? = null
    var stroke: Stroke? = null
    private var builtShape: Shape? = null
    private var builtGlass: Glass? = null
    private var builtDirection: LayoutDirection? = null
    private var builtDensity = 0f
    private var builtWidth = Float.NaN
    private var builtHeight = Float.NaN
    private var builtSheenTo = Float.NaN
    private var builtRimTo = Float.NaN

    fun update(scope: DrawScope, shape: Shape, glass: Glass, height: Float) {
        val width = scope.size.width
        val hairline = with(scope) { 1.dp.toPx() }
        val look = shape != builtShape || glass != builtGlass || scope.layoutDirection != builtDirection || scope.density != builtDensity
        if (look || width != builtWidth || height != builtHeight) {
            outline = shape.createOutline(Size(width, height), scope.layoutDirection, scope)
            rimOutline = shape.createOutline(Size(width - hairline, height - hairline), scope.layoutDirection, scope)
            builtWidth = width
            builtHeight = height
        }
        val sheenTo = min(height, with(scope) { PANEL_SHEEN_DEPTH.toPx() })
        if (look || sheenTo != builtSheenTo) {
            sheen = glass.sheenBrush(0f, sheenTo)
            builtSheenTo = sheenTo
        }
        val rimTo = if (glass.rimLow.alpha > 0f) height else min(height, with(scope) { PANEL_RIM_FADE.toPx() })
        if (look || rimTo != builtRimTo) {
            rim = glass.rimBrush(0f, rimTo)
            builtRimTo = rimTo
        }
        if (look || stroke == null) stroke = Stroke(hairline)
        builtShape = shape
        builtGlass = glass
        builtDirection = scope.layoutDirection
        builtDensity = scope.density
    }
}

private class RevealFromTop(private val height: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width, height.coerceIn(0f, size.height)))
}
