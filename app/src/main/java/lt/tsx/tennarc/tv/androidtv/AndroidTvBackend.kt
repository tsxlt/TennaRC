package lt.tsx.tennarc.tv.androidtv

import android.util.Log
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.ConnectionKeeper
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.KnownApps
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvBackend
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.tv.afterFailures
import lt.tsx.tennarc.tv.firetv.AdbShell
import lt.tsx.tennarc.tv.hdmiInputs
import lt.tsx.tennarc.tv.philips.JointSpacePorts
import lt.tsx.tennarc.tv.philips.PhilipsAmbilight
import lt.tsx.tennarc.tv.retryDelay

class AndroidTvBackend(
    private val settings: Settings,
    identity: ClientIdentity = KeystoreIdentity(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val remotePort: Int = REMOTE_PORT,
    private val pairingPort: Int = PAIRING_PORT,
    jointSpacePorts: JointSpacePorts = JointSpacePorts(),
    listsDir: File? = null,
    adbShells: ((host: String, port: Int) -> AdbShell)? = null,
    adbPorts: AdbPorts = AdbPorts.None,
) : TvBackend {

    override val kind = TvKind.ANDROID_TV
    override val features = ANDROID_TV_FEATURES

    private val mutableState = MutableStateFlow(TvState(host = settings.prefs.value.host, kind = kind, features = features))
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    override val iconLoader = IconLoader { null }

    private val tls = TvTls(identity)
    private val codes = Channel<String>(Channel.CONFLATED)

    private var session: RemoteSession? = null
    private var volumeJob: Job? = null

    @Volatile private var lastVolume: RemoteEvent.Volume? = null

    private val ambilight = PhilipsAmbilight(settings, scope, jointSpacePorts, notify = { mutableNotices.tryEmit(it) })

    private val adbLink = adbShells?.let { AdbLink(settings, settings.prefs.value.host, scope, it, adbPorts) }
    private val adb = adbLink?.let { link ->
        AndroidTvAdb(link, scope, settings.prefs.value.host, listsDir?.let { KnownApps(it, scope) }, notify = { mutableNotices.tryEmit(it) })
    }

    private var keyInputs: List<TvApp> = emptyList()

    init {
        scope.launch {
            combine(ambilight.state, adb?.state ?: flowOf(null)) { light, extras -> light to extras }.collect { (light, extras) ->
                mutableState.update { it.withExtras(light, extras) }
            }
        }
    }

    private fun TvState.withExtras(light: Ambilight?, extras: AndroidTvAdb.Reading?): TvState = copy(
        ambilight = light,
        features = ANDROID_TV_FEATURES + (if (light == null) emptySet() else setOf(Feature.AMBILIGHT)) + extras?.features.orEmpty(),
        devLink = extras?.link,
        apps = if (apps.isEmpty()) apps else extras?.apps ?: AndroidTvApps.curated,
        inputs = extras?.inputs ?: keyInputs,
        picture = extras?.picture,
    )

    private val keeper = ConnectionKeeper(scope, busy = { mutableState.first { it.connection !is Connection.AwaitingCode } }) { allowPairing -> stayConnected(allowPairing) }

    override fun close() {
        keeper.reset()
        scope.coroutineContext.cancelChildren()
        session?.close()
        session = null
        adbLink?.close()
    }

    override fun onScreenStarted() {
        keeper.screenStarted()
        hold()
        ambilight.refresh()
        adbLink?.watch(true)
        adb?.refresh()
    }

    override fun onScreenStopped() {
        keeper.screenStopped()
        if (keeper.visibleScreens == 0) adbLink?.watch(false)
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

    override fun submitPairingCode(code: String) {
        codes.trySend(code.trim().uppercase())
    }

    override fun retry() {
        mutableState.update { it.copy(connection = Connection.Connecting) }
        connect(allowPairing = false)
    }

    override fun forgetPairing() {
        settings.setClientKey(mutableState.value.host, null)
        ambilight.forget(mutableState.value.host)
        adbLink?.forget(mutableState.value.host)
        keeper.cancel()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    private fun connect(allowPairing: Boolean) = keeper.connect(allowPairing)

    private suspend fun stayConnected(allowPairing: Boolean) {
        var failures = 0
        var rejections = 0
        while (true) {
            val host = settings.prefs.value.host
            mutableState.update { if (it.connection == Connection.Offline) it else it.copy(connection = Connection.Connecting) }
            try {
                val pin = TvPin.decode(settings.clientKey(host)) ?: run {
                    if (!allowPairing) {
                        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
                        return
                    }
                    pairWith(host)?.also { settings.setClientKey(host, it.encode()) } ?: return
                }
                val opened = RemoteSession.open(tls, host, remotePort, pin, ::onEvent)
                if (pin.remote == null) settings.setClientKey(host, pin.copy(remote = opened.fingerprint).encode())
                session = opened
                failures = 0
                rejections = 0
                Log.i(TAG, "connected to Android TV at $host")
                mutableState.update { state ->
                    state.copy(connection = Connection.Connected, apps = state.apps.ifEmpty { AndroidTvApps.curated })
                        .withExtras(ambilight.state.value, adb?.state?.value)
                }
                ambilight.connected(host)
                adbLink?.remoteUp(host)
                opened.awaitClosed()
            } catch (e: ClientRejectedException) {
                Log.w(TAG, "$host turned the phone's certificate away", e)
                if (++rejections >= 2) {
                    forgotten(host)
                    return
                }
            } catch (_: TvChangedException) {
                Log.w(TAG, "$host shows a different certificate than the TV that was paired")
                forgotten(host)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connection to $host failed or dropped", e)
            } finally {
                session?.close()
                session = null
                volumeJob?.cancel()
                adbLink?.remoteDown()
                mutableState.update { it.copy(power = null, foregroundAppId = null, keyboard = null) }
            }
            failures++
            mutableState.update { it.afterFailures(failures) { copy(connection = Connection.Offline, volume = null, muted = false) } }
            delay(retryDelay(failures, MAX_RETRY_DELAY_MS))
        }
    }

    private fun forgotten(host: String) {
        settings.setClientKey(host, null)
        mutableState.update { it.copy(connection = Connection.NeedsPairing(forgotten = true)) }
    }

    private suspend fun pairWith(host: String): TvPin? {
        var wrong = false
        while (true) {
            codes.tryReceive()
            val pairing = try {
                PairingSession.open(tls, host, pairingPort)
            } catch (e: PairingRejectedException) {
                Log.w(TAG, "$host won't pair: ${e.message}")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(declined = true)) }
                return null
            }
            try {
                mutableState.update { it.copy(connection = Connection.AwaitingCode(wrong)) }
                while (true) {
                    val code = select<String?> {
                        codes.onReceive { it }
                        pairing.hungUp.onAwait { null }
                    }
                    if (code == null) {
                        mutableState.update { it.copy(connection = Connection.NeedsPairing(declined = true)) }
                        return null
                    }
                    if (pairing.finish(code)) return TvPin(pairing.fingerprint)
                    mutableState.update { it.copy(connection = Connection.AwaitingCode(wrong = true)) }
                }
            } catch (e: PairingRejectedException) {
                Log.w(TAG, "$host refused the code: ${e.message}")
                wrong = true
            } finally {
                pairing.close()
            }
        }
    }

    private fun onEvent(event: RemoteEvent) {
        when (event) {
            is RemoteEvent.Started -> mutableState.update { it.copy(power = if (event.on) TvPower.ON else TvPower.STANDBY) }
            is RemoteEvent.Volume -> {
                lastVolume = event
                mutableState.update { it.copy(volume = volumePercent(event), muted = event.muted) }
            }
            is RemoteEvent.AppInfo -> if (event.packageName.isNotEmpty()) mutableState.update {
                if (it.foregroundAppId == event.packageName) it else it.copy(foregroundAppId = event.packageName, playing = true, keyboard = null)
            }
            is RemoteEvent.ImeShow -> mutableState.update { it.copy(keyboard = TvKeyboard(event.text.length)) }
            is RemoteEvent.Configure -> {
                Log.i(TAG, "remote service on ${event.vendor} ${event.model}, features ${event.features}")
                keyInputs = if (hasInputs(event.vendor, event.model)) hdmiInputs { n -> "$INPUT_PREFIX$n" } else emptyList()
                mutableState.update { it.copy(inputs = adb?.state?.value?.inputs ?: keyInputs) }
            }
            RemoteEvent.Error -> Log.w(TAG, "the TV reported an error")
            else -> Unit
        }
    }

    override fun press(button: TvButton) {
        when (button) {
            TvButton.PLAY -> mutableState.update { it.copy(playing = true) }
            TvButton.PAUSE, TvButton.STOP -> mutableState.update { it.copy(playing = false) }
            else -> Unit
        }
        key(button.keyCode)
    }

    override fun playPause() {
        mutableState.update { it.copy(playing = !it.playing) }
        key(KeyCode.MEDIA_PLAY_PAUSE)
    }

    override fun pointerMove(dx: Float, dy: Float) = Unit

    override fun pointerScroll(dx: Float, dy: Float) = Unit

    override fun pointerClick() = Unit

    override fun launch(appId: String) {
        inputKey(appId)?.let { return key(it) }
        if (isTvInput(appId)) return switchInput(appId)
        command(Notice.LAUNCH_FAILED) { it.openLink(launchLinkFor(appId)) }
    }

    private fun switchInput(id: String) {
        val adb = adb ?: return notConnected()
        scope.launch {
            when (adb.switchInput(id)) {
                true -> Unit
                false -> mutableNotices.tryEmit(Notice.COMMAND_FAILED)
                null -> {
                    val inputId = inputIdOf(id)
                    if (inputId != null && adb.onlyHandler) command { it.openLink(passthroughUri(inputId)) } else mutableNotices.tryEmit(Notice.COMMAND_FAILED)
                }
            }
        }
    }

    override fun volumeUp() = key(KeyCode.VOLUME_UP)

    override fun volumeDown() = key(KeyCode.VOLUME_DOWN)

    override fun setVolume(level: Int) {
        val current = session ?: return notConnected()
        val volume = lastVolume ?: return
        val steps = volumeSteps(volume, level)
        volumeJob?.cancel()
        volumeJob = scope.launch {
            repeat(abs(steps)) {
                current.key(if (steps > 0) KeyCode.VOLUME_UP else KeyCode.VOLUME_DOWN)
                delay(VOLUME_STEP_MS)
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        if (session == null) return notConnected()
        if (mutableState.value.muted == muted) return
        mutableState.update { it.copy(muted = muted) }
        key(KeyCode.VOLUME_MUTE)
    }

    override fun type(edit: TextEdit) = command { session ->
        val sent = (0 until edit.delete).all { session.key(KeyCode.DEL) } && (edit.insert.isEmpty() || session.type(edit.insert))
        if (sent) mutableState.update { state ->
            val keyboard = state.keyboard ?: return@update state
            state.copy(keyboard = TvKeyboard((keyboard.textLength - edit.delete).coerceAtLeast(0) + edit.insert.length))
        }
        sent
    }

    override fun enter() = command { session ->
        session.key(KeyCode.ENTER).also { sent -> if (sent) mutableState.update { it.copy(keyboard = null) } }
    }

    override fun screenOff() = Unit

    override fun screenOn() = Unit

    override fun powerOff() {
        if (mutableState.value.power != TvPower.STANDBY) key(KeyCode.POWER)
    }

    override fun wake() {
        val current = session
        if (current != null) {
            if (mutableState.value.power == TvPower.STANDBY) current.key(KeyCode.POWER)
            return
        }
        val prefs = settings.prefs.value
        scope.launch {
            try {
                WakeOnLan.wake(prefs.mac, prefs.host)
                mutableNotices.tryEmit(Notice.WAKE_SENT)
                retry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("AndroidTvBackend.wake", e)
                mutableNotices.tryEmit(Notice.WAKE_FAILED)
            }
        }
    }

    override fun castPlayPause() = Unit

    override fun castStop() = Unit

    override fun castSeek(seconds: Int) = Unit

    override fun setUpDevLink() {
        adbLink?.setUp(mutableState.value.host)
    }

    override fun turnOffDevLink() {
        adbLink?.turnOff()
    }

    override fun setBacklight(level: Int) {
        adb?.setBacklight(level)
    }

    override fun setAmbilight(style: AmbilightStyle) = ambilight.set(style)

    override fun pairAmbilight() = ambilight.pair()

    override fun submitAmbilightPin(pin: String) = ambilight.submitPin(pin)

    override fun actionsFor(target: LinkTarget): List<LinkAction> = androidTvActions(target)

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean =
        scope.async { openNow(target, action) }.await()

    private suspend fun openNow(target: LinkTarget, action: LinkAction): Boolean {
        val link = appLinkFor(target).takeIf { action == LinkAction.APP } ?: run {
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            return false
        }
        return sendLink(link)
    }

    override suspend fun search(query: String, target: SearchTarget): Boolean =
        scope.async {
            val link = searchLinkFor(query, target) ?: run {
                mutableNotices.tryEmit(Notice.OPEN_FAILED)
                return@async false
            }
            sendLink(link)
        }.await()

    private suspend fun sendLink(link: String): Boolean {
        val current = awaitSession() ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return false
        }
        if (!current.openLink(link)) {
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            return false
        }
        return true
    }

    private suspend fun awaitSession(): RemoteSession? {
        session?.let { return it }
        keeper.ensureConnected()
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            state.first { it.connection == Connection.Connected || it.connection is Connection.NeedsPairing }
            session
        }
    }

    private fun key(keyCode: Int) = command { it.key(keyCode) }

    private fun command(failure: Notice = Notice.COMMAND_FAILED, block: (RemoteSession) -> Boolean) {
        val current = session ?: return notConnected()
        if (!block(current)) mutableNotices.tryEmit(failure)
    }

    private fun notConnected() {
        mutableNotices.tryEmit(Notice.NOT_CONNECTED)
    }

    private companion object {
        const val TAG = "Tenna"
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467
        const val MAX_RETRY_DELAY_MS = 10_000L
        const val CONNECT_WAIT_MS = 10_000L
        const val VOLUME_STEP_MS = 100L
    }
}
