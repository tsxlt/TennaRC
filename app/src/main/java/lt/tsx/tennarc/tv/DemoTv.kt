package lt.tsx.tennarc.tv

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit

class DemoTv : TvBackend {
    override val kind = TvKind.WEBOS
    override val features = WEBOS_FEATURES + Feature.AMBILIGHT
    override val notices: SharedFlow<Notice> = MutableSharedFlow()
    override val iconLoader = IconLoader { null }

    private val mutableState = MutableStateFlow(
        TvState(
            host = HOST,
            features = features,
            connection = Connection.Connected,
            power = TvPower.ON,
            foregroundAppId = TvApps.YOUTUBE,
            volume = 14,
            apps = (TvApps.knownTitles.keys + "com.webos.app.livetv").distinct().map { id ->
                TvApp(id, TvApps.knownTitles.getValue(id), searchable = TvApps.isSearchable(id))
            },
            inputs = listOf(
                TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true),
                TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true),
                TvApp("com.webos.app.hdmi3", "HDMI 3", input = true, plugged = false),
            ),
            channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius"), TvChannel("3", "3", "LRT Lituanica")),
            soundOutput = "tv_speaker",
            picture = TvPicture("normal", 80),
            ambilight = Ambilight(on = true, style = AmbilightStyle.FOLLOW_VIDEO),
        ),
    )
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    private var typed = 0

    override fun onScreenStarted() = Unit
    override fun onScreenStopped() = Unit
    override fun hold() = Unit
    override fun release() = Unit
    override fun close() = Unit

    override fun pair() = Unit
    override fun cancelPairing() = Unit
    override fun retry() = Unit
    override fun forgetPairing() = Unit
    override fun wake() = mutableState.update { it.copy(power = TvPower.ON) }

    override fun press(button: TvButton) = Unit
    override fun pointerMove(dx: Float, dy: Float) = Unit
    override fun pointerScroll(dx: Float, dy: Float) = Unit
    override fun pointerClick() = Unit

    override fun launch(appId: String) = mutableState.update { it.copy(foregroundAppId = appId, playing = true, keyboard = null) }
    override fun openChannel(id: String) = mutableState.update { it.copy(channelId = id, foregroundAppId = "com.webos.app.livetv") }
    override fun setSoundOutput(output: String) = mutableState.update { it.copy(soundOutput = output) }
    override fun setPictureMode(mode: String) = mutableState.update { it.copy(picture = TvPicture(mode, it.picture?.backlight)) }
    override fun setBacklight(level: Int) = mutableState.update { it.copy(picture = TvPicture(it.picture?.mode, level)) }
    override fun setAmbilight(style: AmbilightStyle) = mutableState.update { state ->
        val light = state.ambilight ?: Ambilight()
        state.copy(ambilight = if (style == AmbilightStyle.OFF) light.copy(on = false) else light.copy(on = true, style = style))
    }

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean {
        launch(if (action == LinkAction.BROWSER) TvApps.BROWSER else TvApps.YOUTUBE)
        return true
    }

    override suspend fun search(query: String, target: SearchTarget): Boolean {
        launch((target as? SearchTarget.InApp)?.appId ?: TvApps.BROWSER)
        return true
    }

    override fun playPause() = mutableState.update { it.copy(playing = !it.playing) }

    override fun volumeUp() = setVolume((state.value.volume ?: 0) + 1)
    override fun volumeDown() = setVolume((state.value.volume ?: 0) - 1)
    override fun setVolume(level: Int) = mutableState.update { it.copy(volume = level.coerceIn(0, 100), muted = false) }
    override fun setMuted(muted: Boolean) = mutableState.update { it.copy(muted = muted) }

    override fun type(edit: TextEdit) {
        typed = (typed - edit.delete).coerceAtLeast(0) + edit.insert.length
        mutableState.update { it.copy(keyboard = TvKeyboard(typed)) }
    }

    override fun enter() {
        typed = 0
        mutableState.update { it.copy(keyboard = null) }
    }

    override fun castPlayPause() = Unit
    override fun castStop() = Unit
    override fun castSeek(seconds: Int) = Unit

    override fun screenOff() = mutableState.update { it.copy(power = TvPower.SCREEN_OFF) }
    override fun screenOn() = mutableState.update { it.copy(power = TvPower.ON) }
    override fun powerOff() = mutableState.update { it.copy(power = TvPower.STANDBY) }

    companion object {
        const val HOST = "demo"
    }
}
