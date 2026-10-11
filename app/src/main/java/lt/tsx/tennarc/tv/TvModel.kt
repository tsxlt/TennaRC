package lt.tsx.tennarc.tv

import androidx.annotation.StringRes
import lt.tsx.tennarc.R
import lt.tsx.tennarc.link.queryParam

enum class TvButton(val wire: String) {
    UP("UP"), DOWN("DOWN"), LEFT("LEFT"), RIGHT("RIGHT"), ENTER("ENTER"),
    BACK("BACK"), EXIT("EXIT"), HOME("HOME"), MENU("MENU"), QMENU("QMENU"), INFO("INFO"),
    INPUT_HUB("INPUT_HUB"),
    PLAY("PLAY"), PAUSE("PAUSE"), STOP("STOP"), REWIND("REWIND"), FAST_FORWARD("FASTFORWARD"),
    CHANNEL_UP("CHANNELUP"), CHANNEL_DOWN("CHANNELDOWN"),
    RED("RED"), GREEN("GREEN"), YELLOW("YELLOW"), BLUE("BLUE"),
    DIGIT_0("0"), DIGIT_1("1"), DIGIT_2("2"), DIGIT_3("3"), DIGIT_4("4"),
    DIGIT_5("5"), DIGIT_6("6"), DIGIT_7("7"), DIGIT_8("8"), DIGIT_9("9"),
    CAPTIONS("CC"), AUDIO_TRACK("SAP"),
    GUIDE("GUIDE"), CHANNEL_LIST("LIST"),
}

object TvApps {
    const val BROWSER = "com.webos.app.browser"
    const val YOUTUBE = "youtube.leanback.v4"
    const val NETFLIX = "netflix"
    const val MEDIA_STATION_X = "de.benzac.msx"
    const val PHOTO_VIDEO = "com.webos.app.photovideo"
    const val WEB_VIDEO_CASTER = "com.instantbits.cast.webvideo"

    fun searchTarget(appId: String, query: String): String? = when (appId) {
        YOUTUBE -> "q=" + queryParam(query)
        else -> null
    }

    fun isSearchable(appId: String) = searchTarget(appId, "") != null

    val favorites = listOf(YOUTUBE, NETFLIX, BROWSER, MEDIA_STATION_X, PHOTO_VIDEO, WEB_VIDEO_CASTER)

    val shortTitles = mapOf(
        BROWSER to "Browser",
        MEDIA_STATION_X to "MSX",
        PHOTO_VIDEO to "Photos",
        WEB_VIDEO_CASTER to "Caster",
        "com.webos.app.homeconnect" to "Home",
        "com.webos.app.discovery" to "Store",
    )

    val knownTitles = mapOf(
        YOUTUBE to "YouTube",
        NETFLIX to "Netflix",
        BROWSER to "Web Browser",
        MEDIA_STATION_X to "Media Station X",
        PHOTO_VIDEO to "Photo & Video",
        WEB_VIDEO_CASTER to "Web Video Caster",
        "com.webos.app.music" to "Music",
        "com.webos.app.homeconnect" to "Home Dashboard",
        "com.webos.app.discovery" to "LG Content Store",
        "com.webos.app.livetv" to "Live TV",
    )
}

data class TvApp(
    val id: String,
    val title: String,
    val iconUrl: String? = null,
    val color: Long? = null,
    val searchable: Boolean = false,
    val input: Boolean = false,
    val plugged: Boolean? = null,
)

sealed interface Connection {
    data class NeedsPairing(val declined: Boolean = false, val timedOut: Boolean = false, val forgotten: Boolean = false) : Connection
    data object Connecting : Connection
    data object AwaitingApproval : Connection

    data class AwaitingCode(val wrong: Boolean = false) : Connection
    data object Connected : Connection
    data object Offline : Connection
}

enum class TvPower { ON, SCREEN_OFF, STANDBY }

data class TvKeyboard(val textLength: Int)

enum class CastPhase { LOADING, PLAYING, PAUSED }

data class CastState(
    val title: String,
    val phase: CastPhase,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val stalled: Boolean = false,
    val url: String? = null,
)

data class TvState(
    val host: String,
    val kind: TvKind = TvKind.WEBOS,
    val features: Set<Feature> = WEBOS_FEATURES,
    val connection: Connection = Connection.Connecting,
    val power: TvPower? = null,
    val foregroundAppId: String? = null,
    val volume: Int? = null,
    val muted: Boolean = false,
    val apps: List<TvApp> = emptyList(),
    val inputs: List<TvApp> = emptyList(),
    val channels: List<TvChannel> = emptyList(),
    val appsFromMemory: Boolean = false,
    val channelId: String? = null,
    val programme: TvProgramme? = null,
    val soundOutput: String? = null,
    val picture: TvPicture? = null,
    val keyboard: TvKeyboard? = null,
    val cast: CastState? = null,
    val link: LinkReport? = null,
    val ambilight: Ambilight? = null,
    val devLink: DevLink? = null,
    val playing: Boolean = true,
) {
    val isConnected: Boolean get() = connection == Connection.Connected

    val isUsable: Boolean get() = isConnected && power != TvPower.STANDBY

    fun appTitle(id: String?): String? =
        id?.let { (apps + inputs).firstOrNull { app -> app.id == id }?.title ?: TvApps.knownTitles[id] }
}

data class FoundTv(val host: String, val name: String, val id: String? = null, val kind: TvKind = TvKind.WEBOS, val model: String? = null)

enum class Notice(@StringRes val text: Int) {
    NOT_CONNECTED(R.string.notice_not_connected),
    COMMAND_FAILED(R.string.notice_command_failed),
    LAUNCH_FAILED(R.string.notice_launch_failed),
    OPEN_FAILED(R.string.notice_open_failed),
    NO_RENDERER(R.string.notice_no_renderer),
    CAST_FAILED(R.string.notice_cast_failed),
    WAKE_SENT(R.string.notice_wake_sent),
    ROKU_LIMITED(R.string.notice_roku_limited),
    ADB_OFF(R.string.notice_adb_off),
    WAKE_FAILED(R.string.notice_wake_failed),
    TV_MOVED(R.string.notice_tv_moved),
    CAST_UNSUPPORTED(R.string.notice_cast_unsupported),
    NOT_ON_TV_NETWORK(R.string.notice_not_on_tv_network),
    FILE_UNREADABLE(R.string.notice_file_unreadable),
}

val WEBOS_FEATURES: Set<Feature> = Feature.entries.toSet() - Feature.AMBILIGHT

fun hdmiInputs(idFor: (Int) -> String): List<TvApp> = (1..4).map { TvApp(idFor(it), "HDMI $it", input = true) }

data class TvChannel(val id: String, val number: String, val name: String)

data class TvProgramme(val title: String, val startMs: Long? = null, val endMs: Long? = null)

data class TvPicture(val mode: String?, val backlight: Int?)

enum class AmbilightStyle {
    OFF,
    FOLLOW_VIDEO,
    FOLLOW_AUDIO,

    LOUNGE,
}

sealed interface AmbilightAccess {
    data object NeedsPairing : AmbilightAccess

    data class AwaitingPin(val wrong: Boolean = false) : AmbilightAccess
    data object Paired : AmbilightAccess
}

data class Ambilight(
    val access: AmbilightAccess = AmbilightAccess.Paired,
    val on: Boolean? = null,
    val style: AmbilightStyle? = null,
) {
    val current: AmbilightStyle?
        get() = if (on == false) AmbilightStyle.OFF else style
}

sealed interface DevLink {
    data object Off : DevLink

    data object Connecting : DevLink

    data object AwaitingApproval : DevLink
    data object Up : DevLink

    data class Down(val problem: DevProblem? = null) : DevLink

    data class Failed(val problem: DevProblem) : DevLink
}

enum class DevProblem {
    DEBUGGING_OFF,

    WIRELESS_ONLY,

    NOT_ALLOWED,

    UNREACHABLE,
}
