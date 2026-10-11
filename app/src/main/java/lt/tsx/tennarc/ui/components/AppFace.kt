package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.icons.LocalIconLoader
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.ui.theme.animateColorBetween
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.glass

fun sortedApps(all: List<TvApp>, inputs: List<TvApp> = emptyList()): List<TvApp> {
    val installed = all.filterNot { app -> inputs.any { it.id == app.id } }
    val favorites = TvApps.favorites.mapNotNull { id -> installed.firstOrNull { it.id == id } }.ifEmpty { installed.take(6) }
    return favorites + installed.filterNot { app -> favorites.any { it.id == app.id } }.sortedBy { it.label.lowercase() } +
        inputs.filter { it.plugged != false }
}

internal val TvApp.label: String get() = TvApps.shortTitles[id] ?: title

private const val LIVE_TV = "com.webos.app.livetv"

internal fun appGlyph(app: TvApp): ImageVector? {
    when (app.id) {
        TvApps.BROWSER -> return Lucide.Globe
        TvApps.PHOTO_VIDEO -> return Lucide.Images
        "com.webos.app.music" -> return Lucide.Music
        "com.webos.app.homeconnect" -> return Lucide.House
        "com.webos.app.discovery" -> return Lucide.ShoppingBag
        LIVE_TV -> return Lucide.Tv
    }
    val words = app.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toSet()
    return when {
        "live" in words && "tv" in words -> Lucide.Tv
        words.any { it.startsWith("cast") || it.endsWith("cast") || it.endsWith("caster") } -> Lucide.Cast
        "browser" in words || "web" in words -> Lucide.Globe
        words.any { it in setOf("photo", "photos", "gallery", "image", "images", "pictures") } -> Lucide.Images
        words.any { it in setOf("music", "audio", "radio") } -> Lucide.Music
        words.any { it in setOf("store", "market") } -> Lucide.ShoppingBag
        else -> null
    }
}

internal fun inputGlyph(input: TvApp): ImageVector {
    val words = input.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
    return if ("pc" in words || "computer" in words) Lucide.Monitor else Lucide.LogIn
}

@Composable
fun AppFace(app: TvApp, modifier: Modifier = Modifier, size: Dp = DECK_ICON, pressed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val loader = LocalIconLoader.current
    val glyph = remember(app.id, app.title, app.input) { if (app.input) inputGlyph(app) else appGlyph(app) }
    val url = app.iconUrl.takeUnless { app.input }
    val key = url?.let { (hostOf(it) ?: "") + "/" + app.id }
    val image by produceState(url?.let { loader.cached(it, key!!) }, url, key, loader) { value = url?.let { loader.load(it, key!!) } }
    val shape = IconShape
    val tint = animateColorBetween(pressed, colors.surfaceContainerHigh, colors.surfaceContainerHighest, tween(if (pressed) 40 else 180), label = "faceFill")
    val face = clearGlass(tint)
    val fill = face.fill
    val bitmap = image
    val mark = remember(bitmap) { bitmap != null && isMark(bitmap) }
    if (bitmap != null && mark) {
        Box(modifier.size(size).glass(face, shape).clip(shape), contentAlignment = Alignment.Center) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * MARK_SCALE))
        }
        return
    }
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(fill).border(1.dp, colors.onSurface.copy(alpha = RIM_ALPHA), shape),
        )
        return
    }
    val ink = if (pressed) colors.onSurface else colors.onSurfaceVariant
    Box(modifier.size(size).glass(face, shape).clip(shape), contentAlignment = Alignment.Center) {
        if (glyph != null) {
            Icon(glyph, contentDescription = null, tint = ink, modifier = Modifier.size(size / 2))
        } else {
            Text(
                app.label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = if (size < 32.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                color = ink,
            )
        }
    }
}

internal fun isMark(icon: ImageBitmap): Boolean {
    val w = icon.width
    val h = icon.height
    if (w < 2 || h < 2) return false
    val row = IntArray(w)
    val column = IntArray(h)
    var clear = 0
    fun count(pixels: IntArray) = pixels.forEach { if (it ushr 24 < CLEAR_ALPHA) clear++ }
    icon.readPixels(row, startX = 0, startY = 0, width = w, height = 1)
    count(row)
    icon.readPixels(row, startX = 0, startY = h - 1, width = w, height = 1)
    count(row)
    icon.readPixels(column, startX = 0, startY = 0, width = 1, height = h)
    count(column)
    icon.readPixels(column, startX = w - 1, startY = 0, width = 1, height = h)
    count(column)
    return clear * 2 > (w + h) * 2
}

private const val MARK_SCALE = 0.6f

private const val CLEAR_ALPHA = 32

private const val RIM_ALPHA = 0.08f
