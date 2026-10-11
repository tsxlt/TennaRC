package lt.tsx.tennarc.tv.androidtv

internal sealed interface RemoteEvent {
    data class Configure(val features: Int, val model: String, val vendor: String) : RemoteEvent

    data object SetActive : RemoteEvent

    data class Ping(val value: Int) : RemoteEvent

    data class Started(val on: Boolean) : RemoteEvent

    data class Volume(val level: Int, val max: Int, val muted: Boolean) : RemoteEvent

    data class AppInfo(val packageName: String) : RemoteEvent

    data class ImeCounters(val ime: Int, val field: Int) : RemoteEvent

    data class ImeShow(val text: String) : RemoteEvent

    data object Error : RemoteEvent

    data object Other : RemoteEvent
}

internal object RemoteProtocol {
    const val PING = 1
    const val KEY = 2
    const val IME = 4
    const val POWER = 32
    const val VOLUME = 64
    const val APP_LINK = 512

    const val REQUESTED = PING or KEY or IME or POWER or VOLUME or APP_LINK

    private const val SHORT = 3

    private const val CONFIGURE = 1
    private const val SET_ACTIVE = 2
    private const val ERROR = 3
    private const val PING_REQUEST = 8
    private const val PING_RESPONSE = 9
    private const val KEY_INJECT = 10
    private const val IME_KEY_INJECT = 20
    private const val IME_BATCH_EDIT = 21
    private const val IME_SHOW_REQUEST = 22
    private const val START = 40
    private const val SET_VOLUME_LEVEL = 50
    private const val APP_LINK_LAUNCH_REQUEST = 90

    fun configure(features: Int) = proto {
        message(CONFIGURE) {
            int(1, features)
            message(2) {
                int(3, 1)
                string(4, "1")
                string(5, "atvremote")
                string(6, "1.0.0")
            }
        }
    }

    fun setActive(features: Int) = proto { message(SET_ACTIVE) { int(1, features) } }

    fun pingResponse(value: Int) = proto { message(PING_RESPONSE) { int(1, value) } }

    fun keyInject(keyCode: Int) = proto {
        message(KEY_INJECT) {
            int(1, keyCode)
            int(2, SHORT)
        }
    }

    fun appLink(link: String) = proto { message(APP_LINK_LAUNCH_REQUEST) { string(1, link) } }

    fun imeText(imeCounter: Int, fieldCounter: Int, text: String) = proto {
        message(IME_BATCH_EDIT) {
            int(1, imeCounter)
            int(2, fieldCounter)
            message(3) {
                int(1, 1)
                message(2) {
                    int(1, text.length - 1)
                    int(2, text.length - 1)
                    string(3, text)
                }
            }
        }
    }

    fun decode(bytes: ByteArray): RemoteEvent {
        val message = ProtoMessage(bytes)
        message.message(CONFIGURE)?.let { configure ->
            val device = configure.message(2)
            return RemoteEvent.Configure(configure.int(1), model = device?.string(1).orEmpty(), vendor = device?.string(2).orEmpty())
        }
        if (message.has(SET_ACTIVE)) return RemoteEvent.SetActive
        message.message(PING_REQUEST)?.let { return RemoteEvent.Ping(it.int(1)) }
        message.message(START)?.let { return RemoteEvent.Started(it.bool(1)) }
        message.message(SET_VOLUME_LEVEL)?.let { return RemoteEvent.Volume(level = it.int(7), max = it.int(6), muted = it.bool(8)) }
        message.message(IME_KEY_INJECT)?.let { return RemoteEvent.AppInfo(it.message(1)?.string(12).orEmpty()) }
        message.message(IME_BATCH_EDIT)?.let { return RemoteEvent.ImeCounters(ime = it.int(1), field = it.int(2)) }
        message.message(IME_SHOW_REQUEST)?.let { return RemoteEvent.ImeShow(it.message(2)?.string(2).orEmpty()) }
        if (message.has(ERROR)) return RemoteEvent.Error
        return RemoteEvent.Other
    }
}

internal object KeyCode {
    const val HOME = 3
    const val BACK = 4
    const val DIGIT_0 = 7
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val VOLUME_MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val SETTINGS = 176
    const val TV_INPUT = 178
    const val TV_INPUT_HDMI_1 = 243
    const val CAPTIONS = 175
    const val MEDIA_AUDIO_TRACK = 222
    const val GUIDE = 172
    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186
}
