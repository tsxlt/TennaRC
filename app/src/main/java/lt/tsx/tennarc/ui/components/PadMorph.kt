package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import lt.tsx.tennarc.ui.theme.CornerExtraLarge
import lt.tsx.tennarc.ui.theme.Glass
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.drawFrost
import lt.tsx.tennarc.ui.theme.rimBrush
import lt.tsx.tennarc.ui.theme.sheenBrush
import kotlin.math.min
import kotlin.math.sign

private const val FADE_OUT_END = 0.4f

private const val SCALE_FROM = 0.96f

private val MORPH = spring<Float>(dampingRatio = 1f, stiffness = 200f)

private val SHEEN_DEPTH = 112.dp
private val RIM_FADE = 40.dp

private class MorphPane(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val inset: Float,
    private val disc: Float,
    private val corner: Float,
) : Shape {
    val fill = Path()
    val rim = Path()

    var top = 0f
        private set
    var bottom = 0f
        private set

    private var fillKey = BuildKey()
    private var rimKey = BuildKey()

    private class BuildKey {
        var progress = Float.NaN
        var size = Size.Unspecified
        var shrink = -1f
    }

    fun build(out: Path, size: Size, shrink: Float) {
        val p = progress.value
        val half = disc / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val left = lerp(inset, cx - half, p)
        val right = lerp(size.width - inset, cx + half, p)
        top = lerp(0f, cy - half, p)
        bottom = lerp(size.height, cy + half, p)
        val key = if (out === fill) fillKey else rimKey
        if (key.progress == p && key.size == size && key.shrink == shrink) return
        key.progress = p
        key.size = size
        key.shrink = shrink
        val radius = lerp(corner, half, p)
        out.reset()
        out.addSquircle(Rect(left + shrink, top + shrink, right - shrink, bottom - shrink), (radius - shrink).coerceAtLeast(0f))
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        build(fill, size, 0f)
        return Outline.Generic(fill)
    }
}

private class Crossfade(startDisc: Boolean) {
    private var toDisc = startDisc
    private var from = if (startDisc) 1f else 0f
    private var to = from
    private var touchAlpha = if (startDisc) 0f else 1f
    private var touchScale = 1f
    private var discAlpha = if (startDisc) 1f else 0f
    private var discScale = 1f

    private fun fraction(p: Float): Float = if (to == from) 1f else ((p - from) / (to - from)).coerceIn(0f, 1f)

    fun alpha(disc: Boolean, p: Float): Float {
        val start = if (disc) discAlpha else touchAlpha
        val u = fraction(p)
        if (disc != toDisc) return start * (1f - (u / FADE_OUT_END).coerceIn(0f, 1f))
        val f = ((u - FADE_OUT_END) / (1f - FADE_OUT_END)).coerceIn(0f, 1f)
        return start + (1f - start) * f
    }

    fun scale(disc: Boolean, p: Float): Float {
        val start = if (disc) discScale else touchScale
        if (disc != toDisc) return start
        val f = ((fraction(p) - FADE_OUT_END) / (1f - FADE_OUT_END)).coerceIn(0f, 1f)
        return start + (1f - start) * f
    }

    fun turn(p: Float, towardDisc: Boolean) {
        val touchA = alpha(disc = false, p)
        val touchS = scale(disc = false, p)
        val discA = alpha(disc = true, p)
        val discS = scale(disc = true, p)
        touchAlpha = touchA
        discAlpha = discA
        touchScale = if (!towardDisc && touchA <= 0f) SCALE_FROM else touchS
        discScale = if (towardDisc && discA <= 0f) SCALE_FROM else discS
        toDisc = towardDisc
        from = p
        to = if (towardDisc) 1f else 0f
    }
}

private fun glassAt(touch: Glass, disc: Glass, p: Float): Glass = when {
    p <= 0f -> touch
    p >= 1f -> disc
    else -> Glass(
        fill = lerp(touch.fill, disc.fill, p),
        sheen = lerp(touch.sheen, disc.sheen, p),
        rim = lerp(touch.rim, disc.rim, p),
        rimLow = lerp(touch.rimLow, disc.rimLow, p),
        grain = lerp(touch.grain, disc.grain, p),
    )
}

private class PaneBrushes(private val sheenDepth: Float, private val rimFade: Float) {
    private var glass: Glass? = null
    private var top = Float.NaN
    private var bottom = Float.NaN
    var sheen: Brush? = null
        private set
    var rim: Brush? = null
        private set

    fun at(glass: Glass, top: Float, bottom: Float) {
        if (glass == this.glass && top == this.top && bottom == this.bottom) return
        this.glass = glass
        this.top = top
        this.bottom = bottom
        sheen = glass.sheenBrush(top, top + min(bottom - top, sheenDepth))
        val rimBottom = if (glass.rimLow.alpha > 0f) bottom else min(bottom, top + rimFade)
        rim = glass.rimBrush(top, rimBottom)
    }
}

@Composable
fun PadMorph(
    toDisc: Boolean,
    disc: Dp,
    inset: Dp,
    touchpad: @Composable (Modifier) -> Unit,
    discPad: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val touchGlass = touchpadPane(colors)
    val discGlass = dpadGlass(colors)
    val density = LocalDensity.current
    val insetPx = with(density) { inset.toPx() }
    val discPx = with(density) { disc.toPx() }
    val cornerPx = with(density) { CornerExtraLarge.toPx() }
    val hairline = with(density) { 1.dp.toPx() }
    val sheenDepth = with(density) { SHEEN_DEPTH.toPx() }
    val rimFade = with(density) { RIM_FADE.toPx() }

    val progress = remember { Animatable(if (toDisc) 1f else 0f) }
    val fade = remember { Crossfade(toDisc) }
    LaunchedEffect(toDisc) {
        val p = progress.value
        val target = if (toDisc) 1f else 0f
        fade.turn(p, toDisc)
        val v = progress.velocity
        progress.animateTo(target, MORPH, initialVelocity = if (sign(v) == sign(target - p)) v else 0f)
    }
    val pane = remember(progress, insetPx, discPx, cornerPx) { MorphPane(progress, insetPx, discPx, cornerPx) }
    val rimStroke = remember(hairline) { Stroke(hairline) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val brushes = PaneBrushes(sheenDepth, rimFade)
                onDrawBehind {
                    val glass = glassAt(touchGlass, discGlass, progress.value)
                    pane.build(pane.fill, size, 0f)
                    pane.build(pane.rim, size, hairline / 2)
                    brushes.at(glass, pane.top, pane.bottom)
                    if (glass.fill.alpha > 0f) drawPath(pane.fill, glass.fill)
                    if (glass.sheen.alpha > 0f) drawPath(pane.fill, brushes.sheen!!)
                    drawFrost(glass.grain) { grain, alpha, filter, blend -> drawPath(pane.fill, grain, alpha, colorFilter = filter, blendMode = blend) }
                    drawPath(pane.rim, brushes.rim!!, style = rimStroke)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val touchZ = if (toDisc) 0f else 2f
        val discZ = if (toDisc) 2f else 0f
        Box(
            Modifier
                .zIndex(touchZ)
                .fillMaxSize()
                .then(if (toDisc) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer {
                    val p = progress.value
                    alpha = fade.alpha(disc = false, p)
                    val s = fade.scale(disc = false, p)
                    scaleX = s
                    scaleY = s
                    clip = p > 0f && p < 1f
                    shape = pane
                },
        ) { touchpad(Modifier.fillMaxSize()) }
        Box(
            Modifier
                .zIndex(1f)
                .then(if (toDisc) Modifier.fillMaxSize() else Modifier.size(disc))
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                },
        )
        Box(
            Modifier
                .zIndex(discZ)
                .fillMaxSize()
                .then(if (toDisc) Modifier else Modifier.clearAndSetSemantics {})
                .graphicsLayer {
                    val p = progress.value
                    alpha = fade.alpha(disc = true, p)
                    val s = fade.scale(disc = true, p)
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) { discPad(Modifier.size(disc)) }
    }
}
