package lt.tsx.tennarc.tv.roku

import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.tv.TvApp

object RokuChannels {
    const val YOUTUBE = "837"
    const val NETFLIX = "12"

    const val MEDIA_ASSISTANT = "782875"
}

internal data class RokuLaunch(val appId: String, val params: List<Pair<String, String>>)

internal fun rokuActions(target: LinkTarget, apps: List<TvApp>): List<LinkAction> {
    fun has(appId: String) = apps.any { it.id == appId }
    fun mayHave(appId: String) = apps.isEmpty() || has(appId)
    return when (target) {
        is LinkTarget.YouTube -> listOfNotNull(LinkAction.APP.takeIf { mayHave(RokuChannels.YOUTUBE) })
        is LinkTarget.Netflix -> listOfNotNull(LinkAction.APP.takeIf { mayHave(RokuChannels.NETFLIX) })
        is LinkTarget.Media ->
            listOfNotNull(LinkAction.PLAY_MEDIA.takeIf { has(RokuChannels.MEDIA_ASSISTANT) && mediaFormat(target.mime) != null })
        is LinkTarget.Streaming, is LinkTarget.Web -> emptyList()
    }
}

internal fun rokuLaunch(target: LinkTarget, action: LinkAction): RokuLaunch? = when {
    action == LinkAction.APP && target is LinkTarget.YouTube ->
        RokuLaunch(RokuChannels.YOUTUBE, listOf("contentID" to target.videoId, "mediaType" to "live"))
    action == LinkAction.APP && target is LinkTarget.Netflix ->
        RokuLaunch(RokuChannels.NETFLIX, listOf("contentID" to target.titleId, "mediaType" to "movie"))
    action == LinkAction.PLAY_MEDIA && target is LinkTarget.Media -> mediaFormat(target.mime)?.let { (audio, format) ->
        val params = if (audio) {
            listOf("u" to target.url, "t" to "a", "songName" to target.title, "songFormat" to format)
        } else {
            listOf("u" to target.url, "t" to "v", "videoName" to target.title, "videoFormat" to format)
        }
        RokuLaunch(RokuChannels.MEDIA_ASSISTANT, params)
    }
    else -> null
}

private fun mediaFormat(mime: String): Pair<Boolean, String>? = when (mime) {
    "video/mp4" -> false to "mp4"
    "video/x-matroska" -> false to "mkv"
    "audio/mpeg" -> true to "mp3"
    else -> null
}
