package lt.tsx.tennarc.link

import java.net.URLDecoder
import lt.tsx.tennarc.uriOrNull

sealed interface LinkTarget {
    val url: String

    data class YouTube(override val url: String, val videoId: String) : LinkTarget
    data class Netflix(override val url: String, val titleId: String) : LinkTarget
    data class Media(override val url: String, val mime: String, val title: String) : LinkTarget
    data class Web(override val url: String) : LinkTarget

    data class Streaming(override val url: String, val service: StreamingService) : LinkTarget
}

val LinkTarget.service: StreamingService?
    get() = when (this) {
        is LinkTarget.YouTube -> StreamingService.YOUTUBE
        is LinkTarget.Netflix -> StreamingService.NETFLIX
        is LinkTarget.Streaming -> service
        is LinkTarget.Media, is LinkTarget.Web -> null
    }

enum class LinkAction { APP, PLAY_MEDIA, BROWSER }

val LinkTarget.possibleActions: List<LinkAction>
    get() = when (this) {
        is LinkTarget.YouTube -> listOf(LinkAction.APP, LinkAction.BROWSER)
        is LinkTarget.Netflix, is LinkTarget.Streaming -> listOf(LinkAction.APP)
        is LinkTarget.Media -> listOf(LinkAction.PLAY_MEDIA, LinkAction.BROWSER)
        is LinkTarget.Web -> listOf(LinkAction.BROWSER)
    }

object LinkRouter {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val BARE_DOMAIN = Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/\S*)?$""")
    private val LAN_HOST = Regex("""^(?:\d{1,3}(?:\.\d{1,3}){3}|[\w.-]+\.local)(?:[:/]|$)""")
    private val YOUTUBE_ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val NETFLIX_PATH = Regex("""^/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:watch|title)/(\d+)""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>\"'»”’…"

    private val MEDIA_TYPES = mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "ts" to "video/mp2t",
        "m2ts" to "video/mp2t",
        "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg",
        "avi" to "video/x-msvideo",
        "wmv" to "video/x-ms-wmv",
        "mp3" to "audio/mpeg",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
    )

    fun extract(text: String): String? {
        URL_IN_TEXT.find(text)?.let { return trimTrailing(it.value) }
        val word = text.trim()
        if (!BARE_DOMAIN.matches(word)) return null
        val local = LAN_HOST.containsMatchIn(word)
        return if (local) "http://$word" else "https://$word"
    }

    private fun trimTrailing(url: String): String {
        var end = url.length
        while (end > 0) {
            val last = url[end - 1]
            if (last !in TRAILING_PUNCTUATION) break
            if (last == ')') {
                val head = url.subSequence(0, end)
                if (head.count { it == '(' } >= head.count { it == ')' }) break
            }
            end--
        }
        return url.substring(0, end)
    }

    fun classify(url: String): LinkTarget {
        val uri = uriOrNull(url) ?: return LinkTarget.Web(url)
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return LinkTarget.Web(url)
        val path = uri.rawPath.orEmpty()

        youTubeId(host, path, uri.rawQuery)?.let { return LinkTarget.YouTube(url, it) }

        if (host == "netflix.com") {
            NETFLIX_PATH.find(path)?.let { return LinkTarget.Netflix(url, it.groupValues[1]) }
        }
        StreamingService.byHost(host)
            ?.takeIf { it != StreamingService.NETFLIX && it != StreamingService.YOUTUBE && path.trim('/').isNotEmpty() }
            ?.let { return LinkTarget.Streaming(url, it) }

        val fileName = decode(path.substringAfterLast('/'))
        val extension = fileName.substringAfterLast('.', "").lowercase()
        MEDIA_TYPES[extension]?.let { mime ->
            return LinkTarget.Media(url, mime, fileName.substringBeforeLast('.').ifBlank { fileName })
        }
        return LinkTarget.Web(url)
    }

    private fun youTubeId(host: String, path: String, query: String?): String? {
        val candidate = when (host) {
            "youtu.be" -> path.removePrefix("/").substringBefore('/')
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when {
                path == "/watch" -> queryParam(query, "v")
                else -> listOf("/shorts/", "/live/", "/embed/", "/v/")
                    .firstOrNull { path.startsWith(it) }
                    ?.let { path.removePrefix(it).substringBefore('/') }
            }
            else -> null
        }
        return candidate?.takeIf { YOUTUBE_ID.matches(it) }
    }

    private fun queryParam(query: String?, name: String): String? = query
        ?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it.first() == name }
        ?.getOrNull(1)
        ?.let(::decode)

    private fun decode(text: String) = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (_: IllegalArgumentException) {
        text
    }
}
