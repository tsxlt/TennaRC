package lt.tsx.tennarc.link

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Size
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.RemoteService
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.rethrowIfCancelled
import lt.tsx.tennarc.tv.MediaServer
import lt.tsx.tennarc.tv.MediaSource
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvHub
import lt.tsx.tennarc.tv.localAddressFor
import lt.tsx.tennarc.tv.skipFully

val LinkTarget.isPhoneFile: Boolean get() = this is LinkTarget.Media && url.startsWith("content:")

class PhoneFileException(val notice: Notice) : IOException(notice.name)

class LocalMedia(private val context: Context, private val hub: () -> TvHub) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private var server: MediaServer? = null
    private var watch: Job? = null
    private val current = MutableStateFlow<Share?>(null)
    private val mutableServing = MutableStateFlow(false)

    val serving: StateFlow<Boolean> = mutableServing.asStateFlow()

    private val wakeLock: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TennaRC:serving")
            ?.apply { setReferenceCounted(false) }
    }

    @Suppress("DEPRECATION")
    private val wifiLock: WifiManager.WifiLock? by lazy {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TennaRC serving")
            ?.apply { setReferenceCounted(false) }
    }

    private class Share(val uri: Uri, val path: String, val url: String)

    suspend fun serve(file: LinkTarget.Media): LinkTarget.Media = lock.withLock {
        val uri = Uri.parse(file.url)
        val running = server ?: start()
        catching { context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.logged("LocalMedia.serve")
        val source = withContext(Dispatchers.IO) {
            try {
                ContentSource.of(context.contentResolver, uri, file.mime)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                logFailure("LocalMedia.serve", e)
                null
            }
        }
        val before = current.value
        if (source == null) {
            if (before?.uri != uri) revoke(uri)
            throw PhoneFileException(Notice.FILE_UNREADABLE)
        }
        before?.let {
            running.remove(it.path)
            if (it.uri != uri) revoke(it.uri)
        }
        val path = running.add(source, MimeTypeMap.getSingleton().getExtensionFromMimeType(file.mime))
        val share = Share(uri, path, running.url(path))
        current.value = share
        file.copy(url = share.url)
    }

    private suspend fun start(): MediaServer {
        val host = hub().state.value.host
        val started = withContext(Dispatchers.IO) {
            val tv = catching { InetAddress.getByName(host) }.logged("LocalMedia.tvAddress").getOrNull() as? Inet4Address
            val local = localAddressFor(tv) ?: return@withContext null
            catching { MediaServer.start(local) { peer -> tv == null || peer == tv } }.logged("LocalMedia.start").getOrNull()
        } ?: throw PhoneFileException(Notice.NOT_ON_TV_NETWORK)
        server = started
        mutableServing.value = true
        wakeLock?.acquire(MAX_SERVING_MS)
        wifiLock?.acquire()
        hub().hold()
        RemoteService.start(context)
        watch = scope.launch {
            combine(current, hub().state.map { it.cast?.url }) { share, casting -> share to (share != null && share.url == casting) }
                .distinctUntilChanged()
                .collectLatest { (share, playing) ->
                    if (playing) return@collectLatest
                    delay(STOP_AFTER_MS)
                    lock.withLock { if (current.value === share) stop() }
                }
        }
        return started
    }

    private fun stop() {
        val running = server ?: return
        server = null
        watch?.cancel()
        current.value?.let { revoke(it.uri) }
        current.value = null
        running.close()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        hub().release()
        mutableServing.value = false
    }

    private fun revoke(uri: Uri) {
        catching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.logged("LocalMedia.revoke")
    }

    private companion object {
        const val STOP_AFTER_MS = 60_000L
        const val MAX_SERVING_MS = 6 * 60 * 60 * 1000L
    }
}

private class ContentSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val mime: String,
    override val length: Long?,
) : MediaSource {
    override fun open(offset: Long): InputStream {
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        try {
            stream.skipFully(offset)
        } catch (e: IOException) {
            stream.close()
            throw e
        }
        return stream
    }

    companion object {
        fun of(resolver: ContentResolver, uri: Uri, mime: String): ContentSource {
            val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
            val length = descriptor.use { it.length }.takeIf { it >= 0 } ?: sizeColumn(resolver, uri)
            return ContentSource(resolver, uri, mime, length)
        }

        private fun sizeColumn(resolver: ContentResolver, uri: Uri): Long? = catching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.logged("LocalMedia.size").getOrNull()?.takeIf { it >= 0 }
    }
}

private val MEDIA_KINDS = setOf("video", "image", "audio")

private val MIME_SPELLINGS = mapOf("image/jpg" to "image/jpeg", "audio/mp3" to "audio/mpeg", "audio/x-mp3" to "audio/mpeg")

fun describePhoneFile(resolver: ContentResolver, uri: Uri, type: String?): LinkTarget.Media? {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
    val name = catching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.logged("LocalMedia.name").getOrNull() ?: uri.lastPathSegment.orEmpty()
    val byName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
    val mime = listOf(catching { resolver.getType(uri) }.logged("LocalMedia.type").getOrNull(), type, byName)
        .mapNotNull { it?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) }
        .map { MIME_SPELLINGS[it] ?: it }
        .firstOrNull { '*' !in it && it.substringBefore('/') in MEDIA_KINDS }
        ?: return null
    return LinkTarget.Media(uri.toString(), mime, name.substringBeforeLast('.').ifBlank { name })
}

fun phoneFileThumbnail(context: Context, uri: Uri, mime: String, size: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            return context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            logFailure("LocalMedia.phoneFileThumbnail", e)
        }
    }
    return catching {
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: retriever.frameAtTime
                frame?.let {
                    val scale = size.toFloat() / minOf(it.width, it.height)
                    if (scale < 1f) Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true) else it
                }
            } finally {
                retriever.release()
            }
        }
    }.logged("LocalMedia.phoneFileThumbnail").getOrNull()
}
