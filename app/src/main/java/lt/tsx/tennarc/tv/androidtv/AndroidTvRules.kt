package lt.tsx.tennarc.tv.androidtv

import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.queryParam
import lt.tsx.tennarc.icons.StoreIcons
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton

val ANDROID_TV_FEATURES: Set<Feature> = setOf(
    Feature.TEXT_INPUT,
    Feature.CAPTIONS,
    Feature.VOLUME_LEVEL,
    Feature.MUTE_STATE,
    Feature.WAKE,
    Feature.LIVE_TV,
    Feature.YOUTUBE_LINKS,
    Feature.NETFLIX_LINKS,
)

object AndroidTvApps {
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val NETFLIX = "com.netflix.ninja"
    const val PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
    const val DISNEY_PLUS = "com.disney.disneyplus"
    const val HBO_MAX = "com.wbd.stream"
    const val APPLE_TV = "com.apple.atve.androidtv.appletv"
    const val PLEX = "com.plexapp.android"
    const val SPOTIFY = "com.spotify.tv.android"

    val curated = listOf(
        TvApp(YOUTUBE, "YouTube", iconUrl = StoreIcons.url(YOUTUBE), color = 0xFFE62117, searchable = true),
        TvApp(NETFLIX, "Netflix", iconUrl = StoreIcons.url(NETFLIX), color = 0xFF141414),
        TvApp(PRIME_VIDEO, "Prime Video", iconUrl = StoreIcons.url(PRIME_VIDEO), color = 0xFF00A8E1),
        TvApp(DISNEY_PLUS, "Disney+", iconUrl = StoreIcons.url(DISNEY_PLUS), color = 0xFF0E1A5C),
        TvApp(HBO_MAX, "HBO Max", iconUrl = StoreIcons.url(HBO_MAX), color = 0xFF002BE7),
        TvApp(PLEX, "Plex", iconUrl = StoreIcons.url(PLEX), color = 0xFF282A2D),
        TvApp(APPLE_TV, "Apple TV", iconUrl = StoreIcons.url(APPLE_TV), color = 0xFF000000),
        TvApp(SPOTIFY, "Spotify", iconUrl = StoreIcons.url(SPOTIFY), color = 0xFF1DB954),
    )
}

internal val TvButton.keyCode: Int
    get() = when (this) {
        TvButton.UP -> KeyCode.DPAD_UP
        TvButton.DOWN -> KeyCode.DPAD_DOWN
        TvButton.LEFT -> KeyCode.DPAD_LEFT
        TvButton.RIGHT -> KeyCode.DPAD_RIGHT
        TvButton.ENTER -> KeyCode.DPAD_CENTER
        TvButton.BACK -> KeyCode.BACK
        TvButton.EXIT, TvButton.HOME -> KeyCode.HOME
        TvButton.MENU -> KeyCode.SETTINGS
        TvButton.QMENU -> KeyCode.MENU
        TvButton.INFO -> KeyCode.INFO
        TvButton.INPUT_HUB -> KeyCode.TV_INPUT
        TvButton.CAPTIONS -> KeyCode.CAPTIONS
        TvButton.AUDIO_TRACK -> KeyCode.MEDIA_AUDIO_TRACK
        TvButton.GUIDE, TvButton.CHANNEL_LIST -> KeyCode.GUIDE
        TvButton.PLAY -> KeyCode.MEDIA_PLAY
        TvButton.PAUSE -> KeyCode.MEDIA_PAUSE
        TvButton.STOP -> KeyCode.MEDIA_STOP
        TvButton.REWIND -> KeyCode.MEDIA_REWIND
        TvButton.FAST_FORWARD -> KeyCode.MEDIA_FAST_FORWARD
        TvButton.CHANNEL_UP -> KeyCode.CHANNEL_UP
        TvButton.CHANNEL_DOWN -> KeyCode.CHANNEL_DOWN
        TvButton.RED -> KeyCode.PROG_RED
        TvButton.GREEN -> KeyCode.PROG_GREEN
        TvButton.YELLOW -> KeyCode.PROG_YELLOW
        TvButton.BLUE -> KeyCode.PROG_BLUE
        TvButton.DIGIT_0, TvButton.DIGIT_1, TvButton.DIGIT_2, TvButton.DIGIT_3, TvButton.DIGIT_4,
        TvButton.DIGIT_5, TvButton.DIGIT_6, TvButton.DIGIT_7, TvButton.DIGIT_8, TvButton.DIGIT_9,
        -> KeyCode.DIGIT_0 + wire.toInt()
    }

internal fun androidTvActions(target: LinkTarget): List<LinkAction> = when (target) {
    is LinkTarget.YouTube, is LinkTarget.Netflix, is LinkTarget.Streaming -> listOf(LinkAction.APP)
    is LinkTarget.Media, is LinkTarget.Web -> emptyList()
}

internal fun appLinkFor(target: LinkTarget): String? = when (target) {
    is LinkTarget.YouTube -> "https://www.youtube.com/watch?v=${target.videoId}"
    is LinkTarget.Netflix -> "https://www.netflix.com/title/${target.titleId}"
    is LinkTarget.Streaming -> target.url
    is LinkTarget.Media, is LinkTarget.Web -> null
}

internal fun searchLinkFor(query: String, target: SearchTarget): String? = when {
    target is SearchTarget.InApp && target.appId == AndroidTvApps.YOUTUBE ->
        "https://www.youtube.com/results?search_query=" + queryParam(query)
    else -> null
}

internal fun launchLinkFor(appId: String): String = if ("://" in appId) appId else "market://launch?id=$appId"

internal fun volumePercent(volume: RemoteEvent.Volume): Int? =
    if (volume.max <= 0) null else ((volume.level * 100 + volume.max / 2) / volume.max).coerceIn(0, 100)

internal fun volumeSteps(volume: RemoteEvent.Volume, percent: Int): Int {
    if (volume.max <= 0) return 0
    val target = (percent.coerceIn(0, 100) * volume.max + 50) / 100
    return target - volume.level
}

internal fun hasInputs(vendor: String, model: String): Boolean {
    val name = "$vendor $model".lowercase()
    return STREAMERS.none { it in name }
}

private val STREAMERS = listOf("google", "chromecast", "nvidia", "shield", "streamer", "stick", "box", "onn", "walmart", "amazon")

internal const val INPUT_PREFIX = "input:hdmi"

internal fun inputKey(appId: String): Int? = appId.removePrefix(INPUT_PREFIX).takeIf { appId.startsWith(INPUT_PREFIX) }
    ?.toIntOrNull()?.takeIf { it in 1..4 }?.let { KeyCode.TV_INPUT_HDMI_1 + it - 1 }
