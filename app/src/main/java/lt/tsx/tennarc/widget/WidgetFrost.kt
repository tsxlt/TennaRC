package lt.tsx.tennarc.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import kotlin.math.roundToInt
import kotlin.math.sqrt
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.FROST_STRENGTH
import lt.tsx.tennarc.ui.theme.GRAIN
import lt.tsx.tennarc.ui.theme.TennaColors
import lt.tsx.tennarc.ui.theme.TennaLight
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.frostTile

@Immutable
internal class WidgetPalette(
    val dark: Boolean,
    val surface: Color,
    val key: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    val danger: Color,
    val dangerContainer: Color,
    val hairline: Color,
    val rim: Color,
) {
    val inkProvider get() = ColorProvider(ink)
    val inkDimProvider get() = ColorProvider(inkDim)

    companion object {
        val Dark = WidgetPalette(
            dark = true,
            surface = TennaColors.Panel,
            key = TennaColors.RaisedHigh,
            ink = TennaColors.Text,
            inkDim = TennaColors.TextDim,
            accent = TennaColors.Ember,
            danger = TennaColors.Coral,
            dangerContainer = TennaColors.CoralDeep,
            hairline = Color.White.copy(alpha = 0.12f),
            rim = Color.White.copy(alpha = 0.07f),
        )

        val Light = WidgetPalette(
            dark = false,
            surface = TennaLight.Panel,
            key = TennaLight.RaisedHigh,
            ink = TennaLight.Text,
            inkDim = TennaLight.TextDim,
            accent = TennaLight.Ember,
            danger = TennaLight.Coral,
            dangerContainer = TennaLight.CoralDeep,
            hairline = Color.Black.copy(alpha = 0.12f),
            rim = Color.Black.copy(alpha = 0.07f),
        )

        fun of(dark: Boolean) = if (dark) Dark else Light

        fun of(context: Context, dark: Boolean): WidgetPalette {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return of(dark)
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val base = of(dark)
            return WidgetPalette(
                dark = dark,
                surface = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLow,
                key = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                ink = scheme.onSurface,
                inkDim = scheme.onSurfaceVariant,
                accent = scheme.primary,
                danger = scheme.error,
                dangerContainer = scheme.errorContainer,
                hairline = base.hairline,
                rim = base.rim,
            )
        }
    }
}

internal val LocalWidgetPalette = staticCompositionLocalOf { WidgetPalette.Dark }

internal fun widgetRadius(context: Context): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return CornerLarge
    val resources = context.resources
    return catching { (resources.getDimension(android.R.dimen.system_app_widget_background_radius) / resources.displayMetrics.density).dp }
        .getOrDefault(CornerLarge)
}

internal object WidgetFrost {
    private data class Key(val width: Int, val height: Int, val radius: Float, val dark: Boolean, val surface: Int)

    private val drawn = LruCache<Key, Bitmap>(4)
    private val grain by lazy { frostTile() }

    private const val MAX_SCALE = 2f

    private const val MAX_PIXELS = 300_000f

    fun backdrop(size: DpSize, density: Float, radius: Dp, palette: WidgetPalette): Bitmap {
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val scale = minOf(density, MAX_SCALE, sqrt(MAX_PIXELS / (w * h)))
        val key = Key((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), radius.value * scale, palette.dark, palette.surface.toArgb())
        drawn.get(key)?.let { return it }
        return draw(key, scale, palette).also { drawn.put(key, it) }
    }

    private fun draw(key: Key, scale: Float, palette: WidgetPalette): Bitmap {
        val bitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Path().apply { addSquircle(Rect(Offset.Zero, Size(key.width.toFloat(), key.height.toFloat())), key.radius) }.asAndroidPath()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.surface.toArgb()
        canvas.drawPath(outline, paint)
        if (palette.dark) {
            paint.shader = LinearGradient(0f, 0f, 0f, key.height * 0.66f, Color.White.copy(alpha = SHEEN).toArgb(), 0, Shader.TileMode.CLAMP)
            canvas.drawPath(outline, paint)
        }
        paint.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = (FROST_STRENGTH * GRAIN * 255).roundToInt().coerceIn(0, 255)
        canvas.drawPath(outline, paint)
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale
        paint.color = palette.rim.toArgb()
        val inset = Path().apply {
            addSquircle(Rect(Offset(scale / 2, scale / 2), Size(key.width - scale, key.height - scale)), key.radius - scale / 2)
        }.asAndroidPath()
        canvas.drawPath(inset, paint)
        return bitmap
    }

    private const val SHEEN = 0.035f
}
