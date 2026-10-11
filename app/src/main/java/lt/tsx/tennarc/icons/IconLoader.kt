package lt.tsx.tennarc.icons
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logged
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface IconLoader {
    suspend fun load(url: String): ImageBitmap?

    fun cached(url: String): ImageBitmap? = null

    suspend fun load(url: String, key: String): ImageBitmap? = load(url)

    fun cached(url: String, key: String): ImageBitmap? = cached(url)
}

val LocalIconLoader = staticCompositionLocalOf { IconLoader { null } }

val LocalPosterLoader = staticCompositionLocalOf { IconLoader { null } }

class TvIconLoader(private val http: OkHttpClient, private val tvHost: () -> String) : IconLoader {
    private val cache = LruCache<String, ImageBitmap>(48)

    override fun cached(url: String): ImageBitmap? = cache.get(url)

    override suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        if (hostOf(url) != tvHost()) return null
        return withContext(Dispatchers.IO) {
            catching {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) null
                    else BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                }
            }.logged("IconLoader.load").getOrNull()
        }?.also { cache.put(url, it) }
    }
}

class DiskIconCache(private val dir: File, private val source: () -> IconLoader) : IconLoader {
    private val memory = LruCache<String, ImageBitmap>(96)

    override fun cached(url: String): ImageBitmap? = cached(url, url)

    override suspend fun load(url: String): ImageBitmap? = load(url, url)

    override fun cached(url: String, key: String): ImageBitmap? {
        val name = name(key)
        return memory.get(name) ?: source().cached(url) ?: read(File(dir, name))?.also { memory.put(name, it) }
    }

    override suspend fun load(url: String, key: String): ImageBitmap? {
        val name = name(key)
        memory.get(name)?.let { return it }
        val file = File(dir, name)
        val kept = withContext(Dispatchers.IO) { read(file) }
        if (kept != null && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return kept.also { memory.put(name, it) }
        val fresh = source().load(url) ?: return kept
        memory.put(name, fresh)
        withContext(Dispatchers.IO) { write(file, fresh) }
        return fresh
    }

    private fun read(file: File): ImageBitmap? =
        if (!file.isFile) null else catching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.logged("IconLoader.read").getOrNull()

    private fun write(file: File, bitmap: ImageBitmap) = catching {
        dir.mkdirs()
        val partial = File(dir, file.name + ".part")
        partial.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        partial.renameTo(file)
    }.logged("IconLoader.write")

    private fun name(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32) + ".png"

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
