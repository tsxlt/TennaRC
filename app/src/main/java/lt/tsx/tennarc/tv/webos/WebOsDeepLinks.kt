package lt.tsx.tennarc.tv.webos
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.uriOrNull

internal data class WebOsLaunch(val appId: String, val contentTarget: String)

internal object WebOsDeepLinks {
    private class Format(val appId: String, val contentTarget: (String) -> String?)

    private val formats: Map<StreamingService, Format> = mapOf(
        StreamingService.PRIME_VIDEO to Format("amazon", ::primeVideo),
        StreamingService.APPLE_TV to Format("com.apple.appletv", ::appleTv),
    )

    fun launchFor(target: LinkTarget): WebOsLaunch? = when (target) {
        is LinkTarget.YouTube -> WebOsLaunch(TvApps.YOUTUBE, "v=${target.videoId}")
        is LinkTarget.Netflix -> WebOsLaunch(TvApps.NETFLIX, "m=https://www.netflix.com/watch/${target.titleId}&source_type=4")
        is LinkTarget.Streaming -> formats[target.service]?.let { format ->
            format.contentTarget(target.url)?.let { WebOsLaunch(format.appId, it) }
        }
        is LinkTarget.Media, is LinkTarget.Web -> null
    }

    private val GTI = Regex("""amzn1\.dv\.gti\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""", RegexOption.IGNORE_CASE)

    private fun primeVideo(url: String): String? = GTI.find(url)?.value

    private val APPLE_TV_KEEP = listOf("showId", "playableId")

    private fun appleTv(url: String): String? {
        val uri = uriOrNull(url) ?: return null
        if (uri.host?.lowercase() != "tv.apple.com") return null
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (!path.substringAfterLast('/').startsWith("umc.")) return null
        val query = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        val kept = APPLE_TV_KEEP.mapNotNull { name -> query.firstOrNull { it.size == 2 && it[0] == name }?.let { "$name=${it[1]}" } }
        return "https://tv.apple.com$path" + if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")
    }
}
