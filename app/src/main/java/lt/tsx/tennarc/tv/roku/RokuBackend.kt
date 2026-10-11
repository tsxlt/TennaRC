package lt.tsx.tennarc.tv.roku

import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.rethrowIfCancelled
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.ConnectionKeeper
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvBackend
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.icons.TvIconLoader
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.tv.afterFailures
import lt.tsx.tennarc.tv.learnMacs
import lt.tsx.tennarc.tv.retryDelay
import okhttp3.OkHttpClient

internal val ROKU_FEATURES: Set<Feature> =
    setOf(Feature.TEXT_INPUT, Feature.APP_LIST, Feature.WAKE, Feature.YOUTUBE_LINKS, Feature.NETFLIX_LINKS)

internal val RokuDeviceInfo.features: Set<Feature>
    get() = ROKU_FEATURES - Feature.WAKE +
        listOfNotNull(Feature.WAKE.takeIf { controlsPower }, Feature.LIVE_TV.takeIf { hasTuner })

class RokuBackend(
    private val settings: Settings,
    private val http: OkHttpClient = TvHttp.lanClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    port: Int = RokuEcp.PORT,
) : TvBackend {

    override val kind = TvKind.ROKU

    private val host = settings.prefs.value.host
    private val ecp = RokuEcp(http, host, port)

    private val mutableState = MutableStateFlow(TvState(host = host, kind = TvKind.ROKU, features = ROKU_FEATURES))
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    override val features: Set<Feature> get() = mutableState.value.features

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    override val iconLoader: IconLoader = TvIconLoader(http) { host }

    private val nudges = Channel<Unit>(Channel.CONFLATED)

    private val sendLock = Mutex()

    private val pending = AtomicInteger()

    private var macs: List<String> = emptyList()

    private val keeper = ConnectionKeeper(scope) { poll() }

    override fun close() {
        keeper.reset()
        scope.coroutineContext.cancelChildren()
    }

    override fun onScreenStarted() {
        keeper.screenStarted()
        val polling = keeper.active
        hold()
        if (polling) nudges.trySend(Unit)
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

    override fun pair() = retry()

    override fun cancelPairing() = Unit

    override fun forgetPairing() = Unit

    override fun retry() {
        mutableState.update { it.copy(connection = Connection.Connecting) }
        connect()
    }

    private fun connect() = keeper.connect()

    private suspend fun poll() {
        var failures = 0
        var fresh = true
        while (true) {
            val answered = try {
                look(fresh)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$host didn't answer device-info", e)
                false
            }
            if (answered) {
                failures = 0
                fresh = false
                withTimeoutOrNull(pollInterval() + Random.nextLong(POLL_JITTER_MS)) { nudges.receive() }
                continue
            }
            failures++
            fresh = true
            mutableState.update { it.afterFailures(failures) { copy(connection = Connection.Offline, power = null, foregroundAppId = null) } }
            delay(retryDelay(failures, MAX_RETRY_DELAY_MS))
        }
    }

    private fun pollInterval() = if (keeper.visibleScreens > 0) POLL_VISIBLE_MS else POLL_HIDDEN_MS

    private suspend fun look(fresh: Boolean) {
        val info = try {
            ecp.deviceInfo()
        } catch (_: EcpRefusedException) {
            if (fresh) mutableNotices.tryEmit(Notice.ROKU_LIMITED)
            mutableState.update { it.copy(connection = Connection.Connected, power = null) }
            return
        }
        learnMacs(info.macs)
        mutableState.update { it.copy(connection = Connection.Connected, power = info.power, features = info.features) }
        if (fresh) {
            if (info.restricted) mutableNotices.tryEmit(Notice.ROKU_LIMITED)
            quietly {
                val (inputs, apps) = ecp.apps().partition { it.id.startsWith("tvinput.") }
                mutableState.update { it.copy(apps = apps, inputs = inputs.map { input -> input.copy(input = true, iconUrl = null) }) }
            }
            if (info.hasTuner) quietly {
                val channels = ecp.channels()
                mutableState.update { it.copy(channels = channels) }
            }
        }
        if (info.power == TvPower.ON) quietly { lookAtForegroundApp() } else mutableState.update { it.copy(foregroundAppId = null) }
    }

    private suspend fun lookAtForegroundApp() {
        val appId = ecp.activeApp()
        mutableState.update { if (it.foregroundAppId == appId) it else it.copy(foregroundAppId = appId, playing = true) }
        val channel = if (appId == TUNER) ecp.activeChannel() else null
        mutableState.update { if (it.channelId == channel) it else it.copy(channelId = channel) }
        if (appId == null || appId.startsWith("tvinput.")) return
        ecp.playing()?.let { playing -> mutableState.update { it.copy(playing = playing) } }
    }

    private fun learnMacs(reported: List<String>) {
        if (reported.isEmpty()) return
        macs = reported
        settings.learnMacs(host, reported)
    }

    override fun press(button: TvButton) {
        val key = button.ecpKey ?: return
        if (key == "Play") mutableState.update { it.copy(playing = !it.playing) }
        command(repeatable = true) { it.keypress(key) }
    }

    override fun playPause() = press(TvButton.PLAY)

    override fun pointerMove(dx: Float, dy: Float) = Unit

    override fun pointerScroll(dx: Float, dy: Float) = Unit

    override fun pointerClick() = Unit

    override fun launch(appId: String) = command(Notice.LAUNCH_FAILED) { it.launch(appId) }

    override fun openChannel(id: String) = command(Notice.LAUNCH_FAILED) { it.launch(TUNER, listOf("ch" to id)) }

    override fun volumeUp() = command(repeatable = true) { it.keypress("VolumeUp") }

    override fun volumeDown() = command(repeatable = true) { it.keypress("VolumeDown") }

    override fun setVolume(level: Int) = Unit

    override fun setMuted(muted: Boolean) = command { it.keypress("VolumeMute") }

    override fun type(edit: TextEdit) = command { ecp -> edit.ecpKeys().forEach { ecp.keypress(it) } }

    override fun enter() = command { it.keypress("Enter") }

    override fun screenOff() = Unit

    override fun screenOn() = Unit

    override fun powerOff() = command { it.keypress("PowerOff") }

    override fun wake() {
        scope.launch {
            try {
                if (!powerOn()) {
                    wakeOnLan()
                    retry()
                }
                mutableNotices.tryEmit(Notice.WAKE_SENT)
            } catch (e: CancellationException) {
                throw e
            } catch (_: EcpRefusedException) {
                mutableNotices.tryEmit(Notice.ROKU_LIMITED)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                Log.w(TAG, "couldn't wake $host", e)
                mutableNotices.tryEmit(Notice.WAKE_FAILED)
            }
        }
    }

    private suspend fun powerOn(): Boolean = try {
        sendLock.withLock { ecp.keypress("PowerOn") }
        true
    } catch (e: EcpException) {
        throw e
    } catch (_: IOException) {
        false
    }

    private suspend fun wakeOnLan() {
        val setting = if (macs.isNotEmpty()) WakeOnLan.setting(macs) else settings.prefs.value.mac
        if (setting.isBlank()) throw IOException("No MAC address known for $host")
        WakeOnLan.wake(setting, host)
    }

    override fun actionsFor(target: LinkTarget): List<LinkAction> = rokuActions(target, mutableState.value.apps)

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean =
        scope.async { openNow(target, action) }.await()

    private suspend fun openNow(target: LinkTarget, action: LinkAction): Boolean {
        val launch = rokuLaunch(target, action) ?: run {
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            return false
        }
        if (!awaitConnected()) {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return false
        }
        return try {
            sendLock.withLock { ecp.launch(launch.appId, launch.params) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: EcpRefusedException) {
            mutableNotices.tryEmit(Notice.ROKU_LIMITED)
            false
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Log.w(TAG, "couldn't open $target", e)
            mutableNotices.tryEmit(if (action == LinkAction.PLAY_MEDIA) Notice.CAST_FAILED else Notice.OPEN_FAILED)
            false
        }
    }

    override suspend fun search(query: String, target: SearchTarget): Boolean {
        mutableNotices.tryEmit(Notice.OPEN_FAILED)
        return false
    }

    private suspend fun awaitConnected(): Boolean {
        if (mutableState.value.isConnected) return true
        keeper.ensureConnected()
        return withTimeoutOrNull(CONNECT_WAIT_MS) { state.first { it.isConnected } } != null
    }

    override fun castPlayPause() = Unit

    override fun castStop() = Unit

    override fun castSeek(seconds: Int) = Unit

    private fun command(failure: Notice = Notice.COMMAND_FAILED, repeatable: Boolean = false, block: suspend (RokuEcp) -> Unit) {
        if (!mutableState.value.isConnected) {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return
        }
        if (repeatable && pending.get() >= MAX_PENDING) return
        pending.incrementAndGet()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                sendLock.withLock { block(ecp) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: EcpRefusedException) {
                mutableNotices.tryEmit(Notice.ROKU_LIMITED)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                Log.w(TAG, "command failed", e)
                mutableNotices.tryEmit(failure)
            } finally {
                pending.decrementAndGet()
            }
        }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "optional request failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "Tenna"

        const val TUNER = "tvinput.dtv"
        const val POLL_VISIBLE_MS = 4_000L
        const val POLL_HIDDEN_MS = 20_000L
        const val POLL_JITTER_MS = 500L
        const val MAX_RETRY_DELAY_MS = 10_000L
        const val CONNECT_WAIT_MS = 10_000L
        const val MAX_PENDING = 8
    }
}
