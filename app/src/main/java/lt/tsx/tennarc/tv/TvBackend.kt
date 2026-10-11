package lt.tsx.tennarc.tv

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import lt.tsx.tennarc.icons.IconLoader

enum class TvKind(val label: String, val controlPort: Int) {
    WEBOS("LG webOS", 3001),
    ROKU("Roku", 8060),
    SAMSUNG("Samsung", 8002),
    ANDROID_TV("Android TV", 6466),
    FIRE_TV("Fire TV", 5555),
}

enum class Feature {
    POINTER,

    TEXT_INPUT,

    KEYBOARD_STATE,

    APP_LIST,

    VOLUME_LEVEL,

    MUTE_STATE,

    SCREEN_OFF,

    WAKE,

    MEDIA_CAST,

    LIVE_TV,

    YOUTUBE_LINKS,

    NETFLIX_LINKS,

    WEB_LINKS,

    CAPTIONS,

    PICTURE,

    SOUND_OUTPUT,

    AMBILIGHT,
}

interface AmbilightCommands {
    fun setAmbilight(style: AmbilightStyle) = Unit

    fun pairAmbilight() = Unit
    fun submitAmbilightPin(pin: String) = Unit
}

interface TvBackend : TvCommands, AmbilightCommands {
    val kind: TvKind
    val features: Set<Feature>
    val state: StateFlow<TvState>
    val notices: SharedFlow<Notice>
    val iconLoader: IconLoader

    fun onScreenStarted()
    fun onScreenStopped()

    fun hold()
    fun release()

    fun setVolume(level: Int)

    fun close()
}
