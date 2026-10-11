package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.glassPressed

class PressWash(
    private val color: Color,
    private val radius: Dp,
    private val inset: Dp = 0.dp,
    private val insetY: Dp = 0.dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressWashNode(interactionSource, color, radius, inset, insetY)

    override fun equals(other: Any?): Boolean =
        other is PressWash && other.color == color && other.radius == radius && other.inset == inset && other.insetY == insetY

    override fun hashCode(): Int = ((color.hashCode() * 31 + radius.hashCode()) * 31 + inset.hashCode()) * 31 + insetY.hashCode()
}

@Composable
fun rememberPressWash(radius: Dp = CornerSmall, inset: Dp = 0.dp, insetY: Dp = 0.dp): PressWash {
    val ink = MaterialTheme.colorScheme.glassPressed
    return remember(ink, radius, inset, insetY) { PressWash(ink, radius, inset, insetY) }
}

private class PressWashNode(
    private val source: InteractionSource,
    private val color: Color,
    private val radius: Dp,
    private val inset: Dp,
    private val insetY: Dp,
) : Modifier.Node(), DrawModifierNode {
    private val wash = Animatable(0f)
    private val outline = SquircleCache()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { wash.animateTo(1f, PRESS_IN) }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch { wash.animateTo(0f, PRESS_OUT) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val alpha = wash.value
        if (alpha > 0f) {
            val x = inset.toPx()
            val y = insetY.toPx()
            translate(x, y) {
                drawPath(outline.of(Size(size.width - 2f * x, size.height - 2f * y), radius.toPx()), color, alpha = alpha)
            }
        }
        drawContent()
    }
}

class SquircleCache {
    private val path = Path()
    private var left = Float.NaN
    private var top = Float.NaN
    private var width = Float.NaN
    private var height = Float.NaN
    private var radius = Float.NaN

    fun of(size: Size, radius: Float): Path = of(0f, 0f, size.width, size.height, radius)

    fun of(left: Float, top: Float, width: Float, height: Float, radius: Float): Path {
        if (left != this.left || top != this.top || width != this.width || height != this.height || radius != this.radius) {
            this.left = left
            this.top = top
            this.width = width
            this.height = height
            this.radius = radius
            path.rewind()
            if (width > 0f && height > 0f) path.addSquircle(Rect(Offset(left, top), Size(width, height)), radius)
        }
        return path
    }
}

const val IN_USE_INK = 0.08f

private val PRESS_IN = tween<Float>(40)
private val PRESS_OUT = tween<Float>(180)
