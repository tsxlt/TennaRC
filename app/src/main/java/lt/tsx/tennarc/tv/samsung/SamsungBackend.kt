package lt.tsx.tennarc.tv.samsung

import android.util.Log
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.ConnectionKeeper
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvBackend
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.icons.TvIconLoader
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.tv.afterFailures
import lt.tsx.tennarc.tv.hdmiInputs
import lt.tsx.tennarc.tv.learnMacs
import lt.tsx.tennarc.tv.retryDelay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

val SAMSUNG_FEATURES: Set<Feature> = setOf(
    Feature.TEXT_INPUT,
    Feature.CAPTIONS,
    Feature.KEYBOARD_STATE,
    Feature.APP_LIST,
    Feature.VOLUME_LEVEL,
    Feature.MUTE_STATE,
    Feature.WAKE,
    Feature.LIVE_TV,
    Feature.YOUTUBE_LINKS,
)

data class SamsungPorts(
    val secure: Int = 8002,
    val plain: Int = 8001,
    val rest: Int = 8001,
    val dial: Int = 8080,
)

internal fun remoteUrl(secure: Boolean, host: String, port: Int, token: String?): String {
    val name = URLEncoder.encode(Base64.getEncoder().encodeToString(DEVICE_NAME.toByteArray()), "UTF-8")
    val url = "${if (secure) "wss" else "ws"}://$host:$port/api/v2/channels/samsung.remote.control?name=$name"
    return if (secure && !token.isNullOrEmpty()) url + "&token=" + URLEncoder.encode(token, "UTF-8") else url
}

internal fun samsungKey(button: TvButton): String = when (button) {
    TvButton.UP -> "KEY_UP"
    TvButton.DOWN -> "KEY_DOWN"
    TvButton.LEFT -> "KEY_LEFT"
    TvButton.RIGHT -> "KEY_RIGHT"
    TvButton.ENTER -> "KEY_ENTER"
    TvButton.BACK -> "KEY_RETURN"
    TvButton.EXIT -> "KEY_EXIT"
    TvButton.HOME -> "KEY_HOME"
    TvButton.MENU -> "KEY_MENU"
    TvButton.QMENU -> "KEY_TOOLS"
    TvButton.INFO -> "KEY_INFO"
    TvButton.INPUT_HUB -> "KEY_SOURCE"
    TvButton.CAPTIONS -> "KEY_CAPTION"
    TvButton.AUDIO_TRACK -> "KEY_MTS"
    TvButton.GUIDE -> "KEY_GUIDE"
    TvButton.CHANNEL_LIST -> "KEY_CH_LIST"
    TvButton.PLAY -> "KEY_PLAY"
    TvButton.PAUSE -> "KEY_PAUSE"
    TvButton.STOP -> "KEY_STOP"
    TvButton.REWIND -> "KEY_REWIND"
    TvButton.FAST_FORWARD -> "KEY_FF"
    TvButton.CHANNEL_UP -> "KEY_CHUP"
    TvButton.CHANNEL_DOWN -> "KEY_CHDOWN"
    TvButton.RED -> "KEY_RED"
    TvButton.GREEN -> "KEY_GREEN"
    TvButton.YELLOW -> "KEY_YELLOW"
    TvButton.BLUE -> "KEY_CYAN"
    TvButton.DIGIT_0, TvButton.DIGIT_1, TvButton.DIGIT_2, TvButton.DIGIT_3, TvButton.DIGIT_4,
    TvButton.DIGIT_5, TvButton.DIGIT_6, TvButton.DIGIT_7, TvButton.DIGIT_8, TvButton.DIGIT_9,
    -> "KEY_${button.wire}"
}

class SamsungBackend(
    private val settings: Settings,
    private val tvHttp: OkHttpClient = TvHttp.tvClient(),
    private val lanHttp: OkHttpClient = TvHttp.lanClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ports: SamsungPorts = SamsungPorts(),
    private val findRenderer: suspend (host: String) -> String? = { host -> RenderingControl.find(lanHttp, host) },
    private val timing: SamsungSession.Timing = SamsungSession.Timing(),
) : TvBackend {

    override val kind = TvKind.SAMSUNG
    override val features = SAMSUNG_FEATURES

    private val mutableState = MutableStateFlow(TvState(host = settings.prefs.value.host, kind = kind, features = features))
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    override val iconLoader: IconLoader = TvIconLoader(tvHttp) { mutableState.value.host }

    @Volatile private var session: SamsungSession? = null
    @Volatile private var device: SamsungDevice? = null
    @Volatile private var renderer: RenderingControl? = null
    @Volatile private var appTypes: Map<String, Int> = emptyMap()

    private val keeper = ConnectionKeeper(scope, busy = { mutableState.first { it.connection != Connection.AwaitingApproval } }) { allowPairing -> stayConnected(allowPairing) }

    override fun close() {
        keeper.reset()
        scope.coroutineContext.cancelChildren()
        session?.close()
        session = null
    }

    override fun onScreenStarted() {
        keeper.screenStarted()
        hold()
    }

    override fun onScreenStopped() {
        keeper.screenStopped()
        release()
    }

    override fun hold() {
        keeper.hold()
    }

    override fun release() {
        keeper.release()
    }

    override fun pair() = connect(allowPairing = true)

    override fun cancelPairing() {
        keeper.cancel()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    override fun retry() {
        mutableState.update { it.copy(connection = Connection.Connecting) }
        connect(allowPairing = false)
    }

    override fun forgetPairing() {
        settings.setClientKey(mutableState.value.host, null)
        keeper.cancel()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    private fun connect(allowPairing: Boolean) = keeper.connect(allowPairing)

    private suspend fun stayConnected(allowPairing: Boolean) {
        var failures = 0
        while (true) {
            val host = settings.prefs.value.host
            val token = settings.clientKey(host)
            if (token == null && !allowPairing) {
                mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
                return
            }
            mutableState.update {
                if (it.connection == Connection.Offline || it.power == TvPower.STANDBY) it else it.copy(connection = Connection.Connecting)
            }
            var standby = false
            try {
                val info = orNull { rest(host).device() }
                device = info
                info?.mac?.let { settings.learnMacs(host, listOf(it), onlyIfUnset = true) }
                if (info?.power == TvPower.STANDBY) {
                    failures = 0
                    mutableState.update { it.copy(connection = Connection.Connected, power = TvPower.STANDBY) }
                    awaitPowerOn(host)
                    continue
                }
                val opened = openSession(host, token, info, allowPrompt = allowPairing)
                settings.setClientKey(host, opened.token ?: token ?: NO_TOKEN)
                session = opened
                failures = 0
                Log.i(TAG, "connected to $host")
                mutableState.update { it.copy(connection = Connection.Connected, power = info?.power) }
                coroutineScope {
                    launch {
                        opened.keyboard.collect { text -> mutableState.update { it.copy(keyboard = text?.let { field -> TvKeyboard(field.length) }) } }
                    }
                    launch { loadApps(opened, host) }
                    launch { watchVolume(host) }
                    if (info?.power != null) {
                        launch {
                            awaitStandby(host)
                            standby = true
                            mutableState.update { it.copy(power = TvPower.STANDBY) }
                            opened.close()
                        }
                    }
                    opened.awaitClosed()
                    coroutineContext.cancelChildren()
                }
                if (standby) continue
            } catch (_: SamsungForgottenException) {
                Log.w(TAG, "$host didn't take the stored token")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(forgotten = true)) }
                return
            } catch (_: SamsungPromptTimeoutException) {
                Log.w(TAG, "nobody answered the prompt on $host")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(timedOut = true)) }
                return
            } catch (_: SamsungDeclinedException) {
                Log.w(TAG, "$host refused this phone")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(declined = true)) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connection to $host failed or dropped", e)
            } finally {
                session?.close()
                session = null
                renderer = null
                mutableState.update { it.copy(keyboard = null) }
            }
            failures++
            mutableState.update {
                it.afterFailures(failures, connecting = { copy(connection = Connection.Connecting, power = null) }) {
                    copy(connection = Connection.Offline, power = null, volume = null, muted = false)
                }
            }
            delay(retryDelay(failures, MAX_RETRY_DELAY_MS))
        }
    }

    private suspend fun openSession(host: String, token: String?, info: SamsungDevice?, allowPrompt: Boolean): SamsungSession {
        try {
            return openChannel(remoteUrl(secure = true, host, ports.secure, token), allowPrompt)
        } catch (e: IOException) {
            if (e is SamsungException || info?.tokenAuth == true || !e.isRefused()) throw e
        }
        Log.i(TAG, "$host refuses :${ports.secure}, trying :${ports.plain}")
        return openChannel(remoteUrl(secure = false, host, ports.plain, token = null), allowPrompt)
    }

    private suspend fun openChannel(url: String, allowPrompt: Boolean) =
        SamsungSession.open(tvHttp, url, allowPrompt, onPrompt = { mutableState.update { it.copy(connection = Connection.AwaitingApproval) } }, timing)

    private fun Throwable.isRefused(): Boolean = generateSequence(this) { it.cause }.any {
        it.message?.contains("ECONNREFUSED") == true || it.message?.contains("Connection refused", ignoreCase = true) == true
    }

    private suspend fun awaitPowerOn(host: String) {
        while (true) {
            delay(POWER_POLL_MS)
            val info = orNull { rest(host).device() } ?: throw SamsungException("$host stopped answering in standby")
            device = info
            if (info.power != TvPower.STANDBY) return
        }
    }

    private suspend fun awaitStandby(host: String) {
        while (true) {
            delay(POWER_POLL_MS)
            if (orNull { rest(host).device() }?.power == TvPower.STANDBY) return
        }
    }

    private suspend fun loadApps(session: SamsungSession, host: String) {
        val apps = orNull { session.installedApps() }?.takeIf { it.isNotEmpty() } ?: probeApps(host)
        appTypes = apps.associate { it.id to it.type }
        mutableState.update { state -> state.copy(apps = apps.map { TvApp(it.id, it.name) }, inputs = hdmiInputs { n -> "KEY_HDMI$n" }) }
    }

    private suspend fun probeApps(host: String): List<SamsungApp> = coroutineScope {
        val rest = rest(host)
        SamsungApps.wellKnown
            .map { id -> async { orNull { rest.app(id) } } }
            .awaitAll()
            .filterNotNull()
            .distinctBy { it.name }
    }

    private suspend fun watchVolume(host: String) {
        val control = RenderingControl(lanHttp, orNull { findRenderer(host) } ?: return)
        renderer = control
        while (true) {
            orNull { refreshVolume(control) }
            delay(if (keeper.visibleScreens > 0) VOLUME_POLL_MS else VOLUME_IDLE_POLL_MS)
        }
    }

    private suspend fun refreshVolume(control: RenderingControl) {
        val volume = control.volume()
        val muted = control.muted()
        mutableState.update { it.copy(volume = volume, muted = muted) }
    }

    private suspend fun refreshVolumeSoon() {
        val control = renderer ?: return
        delay(VOLUME_SETTLE_MS)
        orNull { refreshVolume(control) }
    }

    private fun rest(host: String) = SamsungRest(lanHttp, host, ports.rest)

    override fun press(button: TvButton) {
        when (button) {
            TvButton.PLAY -> mutableState.update { it.copy(playing = true) }
            TvButton.PAUSE, TvButton.STOP -> mutableState.update { it.copy(playing = false) }
            else -> Unit
        }
        command { it.key(samsungKey(button)) }
    }

    override fun playPause() = press(if (mutableState.value.playing) TvButton.PAUSE else TvButton.PLAY)

    override fun pointerMove(dx: Float, dy: Float) = Unit
    override fun pointerScroll(dx: Float, dy: Float) = Unit
    override fun pointerClick() = Unit

    override fun launch(appId: String) = command(Notice.LAUNCH_FAILED) {
        if (appId.startsWith("KEY_HDMI")) it.key(appId) else it.launch(appId, SamsungApps.actionType(appTypes[appId]))
    }

    override fun volumeUp() = command { it.key("KEY_VOLUP"); refreshVolumeSoon() }

    override fun volumeDown() = command { it.key("KEY_VOLDOWN"); refreshVolumeSoon() }

    override fun setVolume(level: Int) {
        val control = renderer ?: run {
            mutableNotices.tryEmit(if (session == null) Notice.NOT_CONNECTED else Notice.COMMAND_FAILED)
            return
        }
        val clamped = level.coerceIn(0, 100)
        mutableState.update { it.copy(volume = clamped) }
        command { control.setVolume(clamped) }
    }

    override fun setMuted(muted: Boolean) {
        if (session != null && muted == mutableState.value.muted) return
        if (session != null) mutableState.update { it.copy(muted = muted) }
        command { it.key("KEY_MUTE"); refreshVolumeSoon() }
    }

    override fun type(edit: TextEdit) = command { it.type(edit) }

    override fun enter() = command { it.endText() }

    override fun screenOff() = Unit
    override fun screenOn() = Unit
    override fun castPlayPause() = Unit
    override fun castStop() = Unit
    override fun castSeek(seconds: Int) = Unit

    override fun powerOff() = command { session ->
        if (device?.frame == true) {
            session.key(POWER, "Press")
            delay(FRAME_POWER_HOLD_MS)
            session.key(POWER, "Release")
        } else {
            session.key(POWER)
        }
    }

    override fun wake() {
        val prefs = settings.prefs.value
        scope.launch {
            try {
                WakeOnLan.wake(prefs.mac, prefs.host)
                mutableNotices.tryEmit(Notice.WAKE_SENT)
                if (!mutableState.value.isConnected) retry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("SamsungBackend.wake", e)
                mutableNotices.tryEmit(Notice.WAKE_FAILED)
            }
        }
    }

    override fun actionsFor(target: LinkTarget): List<LinkAction> {
        val apps = mutableState.value.apps
        val hasYouTube = apps.isEmpty() || youTube(apps) != null
        return if (target is LinkTarget.YouTube && hasYouTube) listOf(LinkAction.APP) else emptyList()
    }

    private fun youTube(apps: List<TvApp>) =
        apps.firstOrNull { it.id in SamsungApps.youTubeIds || it.title.equals("YouTube", ignoreCase = true) }

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean =
        scope.async { openNow(target, action) }.await()

    private suspend fun openNow(target: LinkTarget, action: LinkAction): Boolean {
        if (target !is LinkTarget.YouTube || action != LinkAction.APP) {
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            return false
        }
        val session = awaitSession() ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return false
        }
        return try {
            if (!dialYouTube(mutableState.value.host, target.videoId)) {
                val appId = youTube(mutableState.value.apps)?.id ?: SamsungApps.YOUTUBE
                session.launch(appId, "DEEP_LINK", "v=${target.videoId}")
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("SamsungBackend.openNow", e)
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            false
        }
    }

    private suspend fun dialYouTube(host: String, videoId: String): Boolean = orNull {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://$host:${ports.dial}/ws/app/YouTube")
                .post("v=$videoId".toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()
            lanHttp.newCall(request).execute().use { it.isSuccessful }
        }
    } == true

    override suspend fun search(query: String, target: SearchTarget): Boolean {
        mutableNotices.tryEmit(Notice.OPEN_FAILED)
        return false
    }

    private suspend fun awaitSession(): SamsungSession? {
        session?.let { return it }
        keeper.ensureConnected()
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            state.first { it.connection == Connection.Connected || it.connection is Connection.NeedsPairing }
            session
        }
    }

    private fun command(failure: Notice = Notice.COMMAND_FAILED, block: suspend (SamsungSession) -> Unit) {
        val current = session ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return
        }
        scope.launch {
            try {
                block(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "command failed", e)
                mutableNotices.tryEmit(failure)
            }
        }
    }

    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(TAG, "optional request failed: ${e.message}")
        null
    }

    private companion object {
        const val TAG = "Tenna"
        const val POWER = "KEY_POWER"

        const val NO_TOKEN = ""

        const val MAX_RETRY_DELAY_MS = 10_000L
        const val CONNECT_WAIT_MS = 10_000L
        const val POWER_POLL_MS = 3_000L
        const val VOLUME_POLL_MS = 2_000L
        const val VOLUME_IDLE_POLL_MS = 10_000L
        const val VOLUME_SETTLE_MS = 300L

        const val FRAME_POWER_HOLD_MS = 3_000L
    }
}

private const val DEVICE_NAME = "Tenna RC"
