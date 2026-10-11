package lt.tsx.tennarc.icons

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.USER_AGENT
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logFailure
import okhttp3.OkHttpClient
import okhttp3.Request

class PosterLoader(private val http: OkHttpClient) : IconLoader {
    private val cache = LruCache<String, ImageBitmap>(40)

    override fun cached(url: String): ImageBitmap? = cache.get(url)

    override suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        if (hostOf(url) !in HOSTS) return null
        return withContext(Dispatchers.IO) {
            try {
                runInterruptible {
                    http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
                        if (!response.isSuccessful) null
                        else BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("PosterLoader.load", e)
                null
            }
        }?.also { cache.put(url, it) }
    }

    companion object {
        private val HOSTS = setOf("images.justwatch.com", "i.ytimg.com")

        fun youTubeStill(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    }
}
