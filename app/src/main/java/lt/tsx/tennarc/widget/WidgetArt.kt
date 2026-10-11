package lt.tsx.tennarc.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.appGlyph
import lt.tsx.tennarc.ui.components.inputGlyph
import lt.tsx.tennarc.ui.components.isMark
import lt.tsx.tennarc.ui.theme.addSquircle

internal class WidgetArt(private val icons: IconLoader, private val density: Float) {
    fun now(model: WidgetModel, size: Dp, palette: WidgetPalette): Bitmap = when {
        model.app != null -> app(model.app, size, palette)
        model.casting -> glyph(Lucide.Cast, "cast", size, palette)
        else -> glyph(Lucide.Tv, "tv", size, palette)
    }

    fun app(app: WidgetApp, size: Dp, palette: WidgetPalette): Bitmap {
        val tv = TvApp(app.id, app.title, app.iconUrl, input = app.input)
        val glyph = if (app.input) inputGlyph(tv) else appGlyph(tv)
        val own = app.input || (glyph != null && app.id.startsWith("com.webos.app."))
        val url = app.iconUrl.takeUnless { own }
        val image = url?.let { catching { icons.cached(it, iconKey(it, app.id)) }.logged("WidgetArt.icon").getOrNull() }
        val key = "app|${app.id}|$url|${size.value}|${palette.dark}|${image?.let(System::identityHashCode)}"
        return remembered(key) {
            val px = pixels(size)
            when {
                image != null && catching { isMark(image) }.getOrDefault(false) -> face(px, palette) { canvas -> canvas.drawFit(image, px * MARK_SCALE, px) }
                image != null -> icon(image, px, palette)
                glyph != null -> face(px, palette) { canvas -> canvas.drawVector(glyph, palette.inkDim.toArgb(), px) }
                else -> face(px, palette) { canvas -> canvas.drawLetter(letter(app), palette.inkDim.toArgb(), px) }
            }
        }
    }

    private fun glyph(vector: ImageVector, name: String, size: Dp, palette: WidgetPalette): Bitmap =
        remembered("glyph|$name|${size.value}|${palette.dark}") {
            val px = pixels(size)
            face(px, palette) { canvas -> canvas.drawVector(vector, palette.inkDim.toArgb(), px) }
        }

    private fun pixels(size: Dp) = max(1, (size.value * density.coerceAtMost(MAX_SCALE)).roundToInt())

    private fun remembered(key: String, draw: () -> Bitmap): Bitmap = drawn.get(key) ?: draw().also { drawn.put(key, it) }

    private fun face(px: Int, palette: WidgetPalette, mark: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.key.toArgb() }
        canvas.drawPath(squircle(px), paint)
        mark(canvas)
        return bitmap
    }

    private fun icon(image: ImageBitmap, px: Int, palette: WidgetPalette): Bitmap {
        val source = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = squircle(px)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = palette.key.toArgb()
        canvas.drawPath(outline, paint)
        val scale = max(px.toFloat() / source.width, px.toFloat() / source.height)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate((px - source.width * scale) / 2f, (px - source.height * scale) / 2f)
        })
        paint.shader = shader
        canvas.drawPath(outline, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        val line = density.coerceAtMost(MAX_SCALE)
        paint.strokeWidth = line
        paint.color = palette.ink.copy(alpha = RIM_ALPHA).toArgb()
        canvas.drawPath(squircle(px, inset = line / 2), paint)
        return bitmap
    }

    private fun squircle(px: Int, inset: Float = 0f) = Path().apply {
        addSquircle(Rect(Offset(inset, inset), Size(px - 2 * inset, px - 2 * inset)), px / 3f - inset)
    }.asAndroidPath()

    private fun Canvas.drawFit(image: ImageBitmap, box: Float, px: Int) {
        val source = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val scale = minOf(box / source.width, box / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((px - source.width * scale) / 2f, (px - source.height * scale) / 2f)
        }
        drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun Canvas.drawVector(vector: ImageVector, color: Int, px: Int) {
        val size = px / 2f
        val k = size / vector.viewportWidth
        save()
        translate((px - size) / 2f, (px - size) / 2f)
        scale(k, k)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun walk(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorGroup -> walk(node)
                    is VectorPath -> {
                        val path = node.pathData.toPath().asAndroidPath()
                        if (node.fill != null) {
                            paint.style = Paint.Style.FILL
                            drawPath(path, paint)
                        }
                        if (node.stroke != null) {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = node.strokeLineWidth
                            paint.strokeCap = when (node.strokeLineCap) {
                                StrokeCap.Round -> Paint.Cap.ROUND
                                StrokeCap.Square -> Paint.Cap.SQUARE
                                else -> Paint.Cap.BUTT
                            }
                            paint.strokeJoin = when (node.strokeLineJoin) {
                                StrokeJoin.Round -> Paint.Join.ROUND
                                StrokeJoin.Bevel -> Paint.Join.BEVEL
                                else -> Paint.Join.MITER
                            }
                            drawPath(path, paint)
                        }
                    }
                }
            }
        }
        walk(vector.root)
        restore()
    }

    private fun Canvas.drawLetter(letter: String, color: Int, px: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            textSize = px / 3f
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
        }
        val metrics = paint.fontMetrics
        drawText(letter, px / 2f, px / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
    }

    companion object {
        private val drawn = LruCache<String, Bitmap>(24)

        private const val MAX_SCALE = 2.5f
        private const val MARK_SCALE = 0.6f
        private const val RIM_ALPHA = 0.08f

        fun iconKey(url: String, appId: String): String = (hostOf(url) ?: "") + "/" + appId

        fun letter(app: WidgetApp): String =
            (TvApps.shortTitles[app.id] ?: app.title).firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    }
}

internal val LocalWidgetArt = staticCompositionLocalOf { WidgetArt(IconLoader { null }, 2f) }
