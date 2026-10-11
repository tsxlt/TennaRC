package lt.tsx.tennarc.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

inline fun DrawScope.drawFrost(strength: Float = 1f, draw: DrawScope.(grain: Brush, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) -> Unit) {
    if (strength <= 0f) return
    val computed = frostComputed()
    val alpha = strength.coerceAtMost(1f)
    draw(frostLift(computed), alpha, null, BlendMode.Plus)
    draw(frostShade(computed), alpha, FrostShadeOverWhite, BlendMode.Modulate)
}

@PublishedApi
internal fun DrawScope.frostComputed(): Boolean = Build.VERSION.SDK_INT >= 33 && drawContext.canvas.nativeCanvas.isHardwareAccelerated

@PublishedApi internal fun frostLift(computed: Boolean): Brush = if (computed && Build.VERSION.SDK_INT >= 33) Frost.computed.first else Frost.tiled.first

@PublishedApi internal fun frostShade(computed: Boolean): Brush = if (computed && Build.VERSION.SDK_INT >= 33) Frost.computed.second else Frost.tiled.second

@PublishedApi
internal val FrostShadeOverWhite: ColorFilter = ColorFilter.tint(Color.White, BlendMode.DstOver)

fun grainToRestore(alpha: Float): Float {
    val through = 1f - alpha.coerceIn(0f, 1f)
    return sqrt(1f - through * through)
}

fun Modifier.frostBehind(strength: Float = 1f): Modifier = this then FrostBehindElement(strength)

private data class FrostBehindElement(val strength: Float) : ModifierNodeElement<FrostBehindNode>() {
    override fun create() = FrostBehindNode(strength)

    override fun update(node: FrostBehindNode) {
        if (node.strength == strength) return
        node.strength = strength
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "frostBehind"
        properties["strength"] = strength
    }
}

private class FrostBehindNode(var strength: Float) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawFrost(strength) { grain, alpha, filter, blend -> drawRect(grain, alpha = alpha, colorFilter = filter, blendMode = blend) }
        drawContent()
    }
}

@Immutable
data class Glass(val fill: Color, val sheen: Color, val rim: Color, val rimLow: Color, val grain: Float = 1f)

@Composable
@ReadOnlyComposable
fun glass(
    fill: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    floating: Boolean = false,
    sheen: Float = if (floating) FLOATING_SHEEN else 1f,
    grain: Float = 1f,
): Glass {
    val dark = MaterialTheme.colorScheme.darkness
    val rim = androidx.compose.ui.graphics.lerp(LIGHT_RIM, DARK_RIM, dark)
    return Glass(
        fill = fill,
        sheen = Color.White.copy(alpha = lerp(0f, DARK_SHEEN, dark) * sheen),
        rim = rim,
        rimLow = if (floating) androidx.compose.ui.graphics.lerp(LIGHT_RIM_FOOT, DARK_RIM_FOOT, dark) else rim.copy(alpha = 0f),
        grain = grain,
    )
}

@Composable
@ReadOnlyComposable
fun clearGlass(fill: Color, ground: Color = ground(), floating: Boolean = true, sheen: Float = if (floating) FLOATING_SHEEN else 1f): Glass {
    val tint = seeThrough(fill, ground)
    val grain = if (tint.alpha < FAINT_TINT) 0f else grainToRestore(tint.alpha)
    return glass(tint, floating, sheen, grain)
}

val LocalGround = compositionLocalOf { Color.Unspecified }

@Composable
@ReadOnlyComposable
fun ground(): Color = LocalGround.current.takeOrElse { MaterialTheme.colorScheme.background }

@Composable
fun ProvideGround(ground: Color, content: @Composable () -> Unit) = CompositionLocalProvider(LocalGround provides ground, content = content)

@Composable
@ReadOnlyComposable
fun seeThrough(fill: Color): Color = seeThrough(fill, ground())

fun seeThrough(fill: Color, ground: Color): Color {
    val over = fill.compositeOver(ground)
    val alpha = maxOf(reach(over.red, ground.red), reach(over.green, ground.green), reach(over.blue, ground.blue))
    if (alpha <= SEE_THROUGH_NONE) return Color.Transparent
    if (alpha >= 1f) return over
    return Color(
        red = ground.red + (over.red - ground.red) / alpha,
        green = ground.green + (over.green - ground.green) / alpha,
        blue = ground.blue + (over.blue - ground.blue) / alpha,
        alpha = alpha,
    )
}

private fun reach(target: Float, ground: Float): Float = when {
    target > ground -> (target - ground) / (1f - ground)
    target < ground -> (ground - target) / ground
    else -> 0f
}

val ColorScheme.glassPressed: Color get() = onSurface.copy(alpha = lerp(LIGHT_PRESS, DARK_PRESS, darkness))

fun Glass.rimBrush(top: Float, bottom: Float): Brush = Brush.verticalGradient(listOf(rim, rimLow), startY = top, endY = bottom)

fun Glass.sheenBrush(top: Float, bottom: Float): Brush = Brush.verticalGradient(listOf(sheen, sheen.copy(alpha = 0f)), startY = top, endY = bottom)

fun Modifier.glass(glass: Glass, shape: Shape): Modifier = this then GlassElement(glass, shape)

private data class GlassElement(val glass: Glass, val shape: Shape) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(glass, shape)

    override fun update(node: GlassNode) = node.update(glass, shape)

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["glass"] = glass
        properties["shape"] = shape
    }
}

private class GlassNode(private var glass: Glass, private var shape: Shape) : Modifier.Node(), DrawModifierNode {
    private var builtFor = Size.Unspecified
    private var builtDirection: LayoutDirection? = null
    private var builtDensity = 0f
    private var outline: Outline? = null
    private var rimOutline: Outline? = null
    private var sheen: Brush? = null
    private var rim: Brush? = null
    private var stroke: Stroke? = null

    fun update(glass: Glass, shape: Shape) {
        if (glass == this.glass && shape == this.shape) return
        val rebuild = shape != this.shape || glass.sheen != this.glass.sheen || glass.rim != this.glass.rim || glass.rimLow != this.glass.rimLow
        this.glass = glass
        this.shape = shape
        if (rebuild) builtFor = Size.Unspecified
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (size != builtFor || layoutDirection != builtDirection || density != builtDensity) {
            val hairline = 1.dp.toPx()
            outline = shape.createOutline(size, layoutDirection, this)
            rimOutline = shape.createOutline(Size(size.width - hairline, size.height - hairline), layoutDirection, this)
            sheen = glass.sheenBrush(0f, min(size.height, SHEEN_DEPTH.toPx()))
            rim = glass.rimBrush(0f, if (glass.rimLow.alpha > 0f) size.height else min(size.height, RIM_FADE.toPx()))
            stroke = Stroke(hairline)
            builtFor = size
            builtDirection = layoutDirection
            builtDensity = density
        }
        val outline = outline!!
        val stroke = stroke!!
        if (glass.fill.alpha > 0f) drawOutline(outline, glass.fill)
        if (glass.sheen.alpha > 0f) drawOutline(outline, sheen!!)
        drawFrost(glass.grain) { grain, alpha, filter, blend -> drawOutline(outline, grain, alpha, colorFilter = filter, blendMode = blend) }
        translate(stroke.width / 2, stroke.width / 2) { drawOutline(rimOutline!!, rim!!, style = stroke) }
        drawContent()
    }
}

private object Frost {
    @get:RequiresApi(33)
    val computed by lazy { shaders() }
    val tiled by lazy { tiles() }

    @RequiresApi(33)
    private fun shaders(): Pair<Brush, Brush> {
        fun layer(shade: Boolean) = ShaderBrush(
            RuntimeShader(FROST_AGSL).apply {
                setFloatUniform("seed", FROST_SEED * 61f, FROST_SEED * 37f)
                setFloatUniform("value", FROST_VALUE * GRAIN)
                setFloatUniform("chroma", FROST_CHROMA * GRAIN)
                setFloatUniform("chromaFloor", FROST_CHROMA_FLOOR)
                setFloatUniform("darken", FROST_SHADE)
                setFloatUniform("shade", if (shade) 1f else 0f)
            },
        )
        return layer(shade = false) to layer(shade = true)
    }

    private fun tiles(): Pair<Brush, Brush> {
        val random = Random(FROST_SEED)
        val count = FROST_TILE * FROST_TILE
        val lift = IntArray(count)
        val shade = IntArray(count)
        val offset = FloatArray(3)
        for (i in 0 until count) {
            val hue = random.nextFloat()
            val saturation = random.nextFloat()
            frostOffset(hue, saturation * saturation, (random.nextFloat() + random.nextFloat() - 1f) * JITTER_SPREAD, offset)
            val up = max(offset[0], max(offset[1], offset[2])).coerceAtLeast(0f)
            lift[i] = if (up <= 0f) {
                0
            } else {
                val alpha = (up * 255f).roundToInt().coerceIn(1, 255)
                (alpha shl 24) or
                    (channel(offset[0], up) shl 16) or
                    (channel(offset[1], up) shl 8) or
                    channel(offset[2], up)
            }
            shade[i] = (0xFF shl 24) or
                (keep(offset[0]) shl 16) or
                (keep(offset[1]) shl 8) or
                keep(offset[2])
        }
        return brush(lift) to brush(shade)
    }

    private fun channel(offset: Float, up: Float): Int = (offset.coerceAtLeast(0f) / up * 255f).roundToInt().coerceIn(0, 255)

    private fun keep(offset: Float): Int = ((1f + min(offset, 0f) * FROST_SHADE) * 255f).roundToInt().coerceIn(0, 255)

    private fun brush(pixels: IntArray) = ShaderBrush(
        ImageShader(
            android.graphics.Bitmap.createBitmap(pixels, FROST_TILE, FROST_TILE, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(),
            TileMode.Repeated,
            TileMode.Repeated,
        ),
    )

}

internal fun frostOffset(hue: Float, saturation: Float, jitter: Float, out: FloatArray) {
    val value = jitter * FROST_VALUE * GRAIN
    val chroma = (abs(jitter) + FROST_CHROMA_FLOOR) * FROST_CHROMA * GRAIN
    var sum = 0f
    for (c in 0..2) {
        val pure = (abs(fract(hue + HUE_PHASE[c]) * 6f - 3f) - 1f).coerceIn(0f, 1f)
        out[c] = 1f + (pure - 1f) * saturation
        sum += out[c]
    }
    val grey = sum / 3f
    for (c in 0..2) out[c] = value + (out[c] - grey) * chroma
}

private fun fract(x: Float) = x - floor(x)

private val HUE_PHASE = floatArrayOf(1f, 2f / 3f, 1f / 3f)

// Dave Hoskins' "Hash without Sine" (MIT).
private const val FROST_AGSL = """
uniform float2 seed;
uniform float value;
uniform float chroma;
uniform float chromaFloor;
uniform float darken;
uniform float shade;

float4 hash(float2 p) {
    float4 q = fract(p.xyxy * float4(0.1031, 0.1030, 0.0973, 0.1099));
    q += dot(q, q.wzxy + 33.33);
    return fract((q.xxyz + q.yzzw) * q.zywx);
}

half4 main(float2 coord) {
    float4 h = hash(floor(coord) + seed);
    float3 pure = clamp(abs(fract(h.x + float3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    float3 tint = mix(float3(1.0), pure, h.y * h.y);
    float jitter = (h.z + h.w - 1.0) * 2.4494897;
    float3 offset = jitter * value + (tint - (tint.r + tint.g + tint.b) / 3.0) * (abs(jitter) + chromaFloor) * chroma;
    if (shade > 0.5) {
        return half4(half3(1.0 + min(offset, 0.0) * darken), 1.0);
    }
    float3 up = max(offset, 0.0);
    return half4(half3(up), half(max(up.r, max(up.g, up.b))));
}
"""

internal fun frostTile(seed: Int = FROST_SEED, size: Int = WIDGET_TILE): android.graphics.Bitmap {
    val random = Random(seed)
    val pixels = IntArray(size * size) {
        val speck = kotlin.math.sqrt(-2 * kotlin.math.ln(random.nextDouble().coerceAtLeast(1e-9))) * kotlin.math.cos(2 * Math.PI * random.nextDouble())
        val alpha = (abs(speck) * WIDGET_SPREAD).coerceAtMost(255.0).roundToInt()
        if (speck < 0) {
            alpha shl 24
        } else {
            val hue = floatArrayOf(random.nextFloat() * 360f, WIDGET_TINT, 1f)
            (alpha shl 24) or (android.graphics.Color.HSVToColor(hue) and 0xFFFFFF)
        }
    }
    return android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}

const val FROST_STRENGTH = 0.55f
private const val WIDGET_TILE = 128
private const val WIDGET_SPREAD = 22.0
private const val WIDGET_TINT = 0.35f

private const val FROST_TILE = 512
private const val FROST_SEED = 7
private val JITTER_SPREAD = sqrt(6f)
private const val SEE_THROUGH_NONE = 0.002f
private const val FAINT_TINT = 0.2f
const val GRAIN = 0.5f

private const val FROST_VALUE = 0.04f
private const val FROST_CHROMA = 0.05f
private const val FROST_CHROMA_FLOOR = 0.35f
private const val FROST_SHADE = 0.8f

private const val DARK_SHEEN = 0.055f
private val SHEEN_DEPTH = 112.dp
private const val FLOATING_SHEEN = 0.6f
private val DARK_RIM = Color.White.copy(alpha = 0.14f)
private val DARK_RIM_FOOT = Color.White.copy(alpha = 0.05f)
private val LIGHT_RIM = Color.Black.copy(alpha = 0.09f)
private val LIGHT_RIM_FOOT = Color.Black.copy(alpha = 0.14f)
private val RIM_FADE = 40.dp
private const val DARK_PRESS = 0.10f
private const val LIGHT_PRESS = 0.12f
