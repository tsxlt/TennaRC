package lt.tsx.tennarc.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Shape
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = SQUIRCLE_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp, smoothing: Float = SQUIRCLE_SMOOTHING) :
        this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Generic(
            Path().apply {
                addSquircle(
                    Rect(Offset.Zero, size),
                    topLeft = if (ltr) topStart else topEnd,
                    topRight = if (ltr) topEnd else topStart,
                    bottomRight = if (ltr) bottomEnd else bottomStart,
                    bottomLeft = if (ltr) bottomStart else bottomEnd,
                    smoothing = smoothing,
                )
            },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing

    override fun hashCode(): Int = ((((topStart.hashCode() * 31) + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()) * 31 + smoothing.hashCode()

    override fun toString(): String = "SquircleShape($topStart, $topEnd, $bottomEnd, $bottomStart, $smoothing)"
}

const val SQUIRCLE_SMOOTHING = 1f

val CornerExtraSmall = 10.dp
val CornerSmall = 14.dp
val CornerMedium = 18.dp
val CornerLarge = 22.dp
val CornerExtraLarge = 30.dp

fun Path.addSquircle(rect: Rect, radius: Float, smoothing: Float = SQUIRCLE_SMOOTHING) =
    addSquircle(rect, radius, radius, radius, radius, smoothing)

fun Path.addSquircle(rect: Rect, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, smoothing: Float = SQUIRCLE_SMOOTHING) {
    val w = rect.width
    val h = rect.height
    val tl = Corner(topLeft, smoothing, w / 2f, h / 2f)
    val tr = Corner(topRight, smoothing, w / 2f, h / 2f)
    val br = Corner(bottomRight, smoothing, w / 2f, h / 2f)
    val bl = Corner(bottomLeft, smoothing, w / 2f, h / 2f)
    val x = rect.left
    val y = rect.top

    moveTo(x + w - tr.across.p, y)
    tr.draw(this, x + w - tr.r, y + tr.r, from = -90f, first = tr.across, second = tr.down,
        into = { a, b, c, d -> relativeCubicTo(a, 0f, a + b, 0f, a + b + c, d) },
        out = { a, b, c, d -> relativeCubicTo(d, c, d, b + c, d, a + b + c) })
    lineTo(x + w, y + h - br.down.p)
    br.draw(this, x + w - br.r, y + h - br.r, from = 0f, first = br.down, second = br.across,
        into = { a, b, c, d -> relativeCubicTo(0f, a, 0f, a + b, -d, a + b + c) },
        out = { a, b, c, d -> relativeCubicTo(-c, d, -(b + c), d, -(a + b + c), d) })
    lineTo(x + bl.across.p, y + h)
    bl.draw(this, x + bl.r, y + h - bl.r, from = 90f, first = bl.across, second = bl.down,
        into = { a, b, c, d -> relativeCubicTo(-a, 0f, -(a + b), 0f, -(a + b + c), -d) },
        out = { a, b, c, d -> relativeCubicTo(-d, -c, -d, -(b + c), -d, -(a + b + c)) })
    lineTo(x, y + tl.down.p)
    tl.draw(this, x + tl.r, y + tl.r, from = 180f, first = tl.down, second = tl.across,
        into = { a, b, c, d -> relativeCubicTo(0f, -a, 0f, -(a + b), d, -(a + b + c)) },
        out = { a, b, c, d -> relativeCubicTo(c, -d, b + c, -d, a + b + c, -d) })
    close()
}

private class Corner(radius: Float, smoothing: Float, alongWidth: Float, alongHeight: Float) {
    val r = min(radius, min(alongWidth, alongHeight)).coerceAtLeast(0f)
    val across = Half(r, smoothing, alongWidth)
    val down = Half(r, smoothing, alongHeight)

    fun draw(
        path: Path,
        cx: Float,
        cy: Float,
        from: Float,
        first: Half,
        second: Half,
        into: Path.(Float, Float, Float, Float) -> Unit,
        out: Path.(Float, Float, Float, Float) -> Unit,
    ) {
        if (r <= 0f) return
        path.into(first.a, first.b, first.c, first.d)
        path.arcTo(Rect(Offset(cx, cy), r), from + 45f * first.s, 90f - 45f * (first.s + second.s), false)
        path.out(second.a, second.b, second.c, second.d)
    }
}

private class Half(r: Float, smoothing: Float, budget: Float) {
    val s = if (r > 0f) min(smoothing, (budget / r - 1f).coerceAtLeast(0f)) else 0f
    val p = if (r > 0f) min((1f + s) * r, budget) else 0f
    val a: Float
    val b: Float
    val c: Float
    val d: Float

    init {
        val given = 45f * s
        c = r * tan(rad(given / 2f)) * cos(rad(given))
        d = c * tan(rad(given))
        b = ((p - r * (1f - sin(rad(given))) - c) / 3f).coerceAtLeast(0f)
        a = 2f * b
    }
}

private fun rad(degrees: Float): Float = (degrees * Math.PI / 180.0).toFloat()

val PillShape: CornerBasedShape = SquircleShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50))

const val ICON_CORNER = 0.30f
const val ICON_SMOOTHING = 0.66f

val IconShape: Shape = SuperellipseShape(ICON_ROUNDNESS)

const val ICON_ROUNDNESS = 3f

class SuperellipseShape(private val n: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addSuperellipse(Rect(Offset.Zero, size), n) })
}

fun Path.addSuperellipse(rect: Rect, n: Float) {
    val steps = 4 * SUPERELLIPSE_STEPS
    val e = 2f / n
    for (i in 0 until steps) {
        val t = i.toFloat() / steps * 2f * PI.toFloat()
        val c = cos(t)
        val s = sin(t)
        val x = rect.center.x + rect.width / 2 * sign(c) * abs(c).pow(e)
        val y = rect.center.y + rect.height / 2 * sign(s) * abs(s).pow(e)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val SUPERELLIPSE_STEPS = 48
