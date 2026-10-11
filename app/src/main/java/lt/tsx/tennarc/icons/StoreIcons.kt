package lt.tsx.tennarc.icons
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.logFailure
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import java.util.Collections

object StoreIcons {
    private const val PREFIX = "store://"

    fun url(packageName: String): String = PREFIX + packageName

    fun isStoreUrl(url: String): Boolean = url.startsWith(PREFIX)

    internal fun sourceOf(url: String): IconSource? {
        val app = url.removePrefix(PREFIX).takeIf { isStoreUrl(url) && PACKAGE.matches(it) } ?: return null
        return MARKS[app] ?: IconSource.Listing(app)
    }

    internal fun iconIn(page: CharSequence, size: Int): String? = OG_IMAGE.find(page)?.let { "${it.groupValues[1]}=s$size" }

    private val MARKS: Map<String, IconSource> = mapOf(
        "com.netflix.ninja" to IconSource.Listing("com.netflix.mediaclient"),
        "com.google.android.youtube.tv" to IconSource.Site("youtube.com"),
        "com.amazon.firetv.youtube" to IconSource.Site("youtube.com"),
        "com.spotify.tv.android" to IconSource.Listing("com.spotify.music"),
        "com.amazon.amazonvideo.livingroom" to IconSource.Listing("com.amazon.avod.thirdpartyclient"),
        "com.amazon.firebat" to IconSource.Listing("com.amazon.avod.thirdpartyclient"),
        "com.amazon.avod" to IconSource.Listing("com.amazon.avod.thirdpartyclient"),
        "com.plexapp.android" to IconSource.Site("plex.tv"),
    )

    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    private val OG_IMAGE = Regex("""<meta property="og:image" content="(https://play-lh\.googleusercontent\.com/[A-Za-z0-9_-]+)""")
}

internal sealed interface IconSource {
    data class Listing(val app: String) : IconSource

    data class Site(val domain: String) : IconSource
}

class StoreIconLoader(private val http: OkHttpClient) : IconLoader {
    private val cache = LruCache<String, ImageBitmap>(48)

    private val unlisted = Collections.synchronizedSet(HashSet<IconSource>())

    override fun cached(url: String): ImageBitmap? = cache.get(url)

    override suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val source = StoreIcons.sourceOf(url) ?: return null
        if (source in unlisted) return null
        return withContext(Dispatchers.IO) {
            try {
                runInterruptible {
                    val icon = when (source) {
                        is IconSource.Site -> SITE_ICON + source.domain + "&size=$SITE_PX"
                        is IconSource.Listing -> {
                            val page = Request.Builder().url("https://play.google.com/store/apps/details?id=${source.app}&hl=en").build()
                            http.newCall(page).execute().use { response ->
                                if (response.code == 404) unlisted += source
                                if (!response.isSuccessful) null else findIcon(response.body.source())
                            } ?: return@runInterruptible null
                        }
                    }
                    http.newCall(Request.Builder().url(icon).build()).execute().use { response ->
                        if (!response.isSuccessful) null
                        else BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("StoreIcons.load", e)
                null
            }
        }?.also { cache.put(url, it) }
    }

    private fun findIcon(source: BufferedSource): String? {
        val window = StringBuilder()
        val chunk = Buffer()
        while (source.read(chunk, CHUNK) != -1L) {
            window.append(chunk.readUtf8())
            StoreIcons.iconIn(window, ICON_PX)?.let { return it }
            if (window.length > OVERLAP) window.delete(0, window.length - OVERLAP)
        }
        return null
    }

    private companion object {
        const val CHUNK = 64L * 1024
        const val OVERLAP = 1024

        const val ICON_PX = 192

        const val SITE_PX = 128
        const val SITE_ICON = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://"
    }
}
