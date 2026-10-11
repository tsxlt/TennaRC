package lt.tsx.tennarc.tv.firetv

import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.afterFailures
import lt.tsx.tennarc.tv.hdmiInputs
import lt.tsx.tennarc.tv.retryDelay

class FireTvBackend(
    private val settings: Settings,
    filesDir: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val keys: AdbKeys = AdbKeys(File(filesDir, "adb")),
    private val shells: (host: String, keys: AdbKeys) -> AdbShell = { host, keys -> DadbShell(host, keys) },
) : TvBackend {

    override val kind = TvKind.FIRE_TV

    private val mutableState = MutableStateFlow(
        TvState(host = settings.prefs.value.host, kind = TvKind.FIRE_TV, features = fireTvFeatures(emptyList())),
    )
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    override val features: Set<Feature> get() = mutableState.value.features

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    override val iconLoader = IconLoader { null }

    private var shell: AdbShell? = null
    private var input: Channel<InputOp>? = null
    private var lost: CompletableDeferred<Unit>? = null
    private var launchers: Map<String, Launcher> = emptyMap()
    private val typed = TypedText()
    private val queuedKeys = AtomicInteger()

    private val keeper = ConnectionKeeper(scope, busy = { mutableState.first { it.connection != Connection.AwaitingApproval } }) { allowPairing -> stayConnected(allowPairing) }

    override fun close() {
        keeper.reset()
        scope.coroutineContext.cancelChildren()
        shell?.close()
        shell = null
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
        keys.forget()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    private fun connect(allowPairing: Boolean) = keeper.connect(allowPairing)

    private suspend fun stayConnected(allowPairing: Boolean) {
        var failures = 0
        var refused = false
        var toldAdbOff = false
        while (true) {
            val host = settings.prefs.value.host
            if (settings.clientKey(host) == null && !allowPairing) {
                mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
                return
            }
            mutableState.update { if (it.connection == Connection.Offline) it else it.copy(connection = Connection.Connecting) }
            val opened = shells(host, keys)
            try {
                val trusted = opened.trusted()
                if (!trusted) {
                    if (!allowPairing) {
                        Log.w(TAG, "$host no longer trusts this phone")
                        settings.setClientKey(host, null)
                        mutableState.update { it.copy(connection = Connection.NeedsPairing(forgotten = true)) }
                        return
                    }
                    mutableState.update { it.copy(connection = Connection.AwaitingApproval) }
                }
                withTimeoutOrNull(if (trusted) HANDSHAKE_TIMEOUT_MS else APPROVAL_TIMEOUT_MS) { opened.connect() } ?: run {
                    if (trusted) throw IOException("$host didn't finish connecting")
                    Log.w(TAG, "nobody allowed this phone on $host")
                    mutableState.update { it.copy(connection = Connection.NeedsPairing(timedOut = true)) }
                    return
                }
                settings.setClientKey(host, PAIRED)
                failures = 0
                refused = false
                toldAdbOff = false
                Log.i(TAG, "connected to $host")
                val queue = Channel<InputOp>(Channel.UNLIMITED)
                val dropped = CompletableDeferred<Unit>()
                shell = opened
                input = queue
                lost = dropped
                mutableState.update { it.copy(connection = Connection.Connected) }
                coroutineScope {
                    launch { sendInput(opened, queue) }
                    launch { loadApps(opened) }
                    launch { watch(opened) }
                    dropped.await()
                    coroutineContext.cancelChildren()
                }
                Log.w(TAG, "$host stopped answering")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbPortClosedException) {
                refused = true
                Log.w(TAG, "$host refuses ADB: debugging is off", e)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                refused = false
                Log.w(TAG, "connection to $host failed or dropped", e)
            } finally {
                opened.close()
                if (shell === opened) {
                    shell = null
                    input?.close()
                    input = null
                    lost = null
                }
                queuedKeys.set(0)
                mutableState.update { it.copy(power = null, foregroundAppId = null) }
            }
            failures++
            mutableState.update { it.afterFailures(failures) { copy(connection = Connection.Offline) } }
            if (failures >= 2 && refused && !toldAdbOff) {
                toldAdbOff = true
                mutableNotices.tryEmit(Notice.ADB_OFF)
            }
            delay(retryDelay(failures, MAX_RETRY_DELAY_MS))
        }
    }

    private suspend fun watch(shell: AdbShell) {
        while (true) {
            if (keeper.visibleScreens > 0) {
                val output = shell.shell(STATUS_COMMAND)
                val power = parseWakefulness(output)
                val app = parseFocusedApp(output)
                mutableState.update { state ->
                    val moved = app != null && app != state.foregroundAppId
                    state.copy(
                        power = power ?: state.power,
                        foregroundAppId = app ?: state.foregroundAppId,
                        playing = if (moved) true else state.playing,
                    )
                }
            }
            delay(POLL_MS + Random.nextLong(POLL_JITTER_MS))
        }
    }

    private suspend fun loadApps(shell: AdbShell) {
        try {
            val list = parseApps(shell.shell(APPS_COMMAND))
            launchers = list.launchers
            mutableState.update { it.copy(apps = list.apps, features = fireTvFeatures(list.apps)) }
            if (TV_SCREEN_FEATURE in shell.shell("pm list features")) {
                mutableState.update { it.copy(inputs = hdmiInputs { n -> "$INPUT_PREFIX$n" }) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "app list failed: ${e.message}")
        }
    }

    private suspend fun sendInput(shell: AdbShell, queue: ReceiveChannel<InputOp>) {
        for (first in queue) {
            val batch = mutableListOf(first)
            while (true) batch += queue.tryReceive().getOrNull() ?: break
            queuedKeys.addAndGet(-batch.count { it is InputOp.Key })
            val line = inputCommand(batch, typed)
            if (line.isEmpty()) continue
            val output = try {
                shell.shell(line)
            } catch (e: IOException) {
                mutableNotices.tryEmit(Notice.COMMAND_FAILED)
                throw e
            }
            if (!inputAccepted(output)) {
                Log.w(TAG, "input failed: $output")
                mutableNotices.tryEmit(Notice.COMMAND_FAILED)
            }
        }
    }

    private fun send(op: InputOp) {
        val queue = input ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return
        }
        if (op is InputOp.Key && queuedKeys.incrementAndGet() > MAX_QUEUED_KEYS) {
            queuedKeys.decrementAndGet()
            return
        }
        queue.trySend(op)
    }

    override fun press(button: TvButton) {
        when (button) {
            TvButton.PLAY -> mutableState.update { it.copy(playing = true) }
            TvButton.PAUSE, TvButton.STOP -> mutableState.update { it.copy(playing = false) }
            else -> Unit
        }
        send(InputOp.Key(keyCode(button)))
    }

    override fun playPause() {
        mutableState.update { it.copy(playing = !it.playing) }
        send(InputOp.Key(KEY_PLAY_PAUSE))
    }

    override fun type(edit: TextEdit) = send(InputOp.Text(edit))

    override fun enter() = send(InputOp.Key(KEY_ENTER))

    override fun volumeUp() = send(InputOp.Key(KEY_VOLUME_UP))

    override fun volumeDown() = send(InputOp.Key(KEY_VOLUME_DOWN))

    override fun setMuted(muted: Boolean) = send(InputOp.Key(KEY_MUTE))

    override fun powerOff() = send(InputOp.Key(KEY_SLEEP))

    override fun launch(appId: String) {
        inputKey(appId)?.let { return send(InputOp.Key(it)) }
        launchApp(appId)
    }

    private fun launchApp(appId: String) = command(Notice.LAUNCH_FAILED) { shell ->
        val attempts = launchCommands(appId, launchers[appId])
        if (attempts.none { started(shell.shell(it)) }) throw IllegalStateException("$appId didn't start")
    }

    override fun wake() {
        scope.launch {
            val current = awaitShell() ?: run {
                mutableNotices.tryEmit(Notice.NOT_CONNECTED)
                return@launch
            }
            try {
                current.shell("input keyevent $KEY_WAKE")
                mutableNotices.tryEmit(Notice.WAKE_SENT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "wake failed", e)
                mutableNotices.tryEmit(Notice.COMMAND_FAILED)
                if (e is IOException) dropped(current)
            }
        }
    }

    override fun pointerMove(dx: Float, dy: Float) = Unit
    override fun pointerScroll(dx: Float, dy: Float) = Unit
    override fun pointerClick() = Unit
    override fun setVolume(level: Int) = Unit
    override fun screenOff() = Unit
    override fun screenOn() = Unit
    override fun castPlayPause() = Unit
    override fun castStop() = Unit
    override fun castSeek(seconds: Int) = Unit

    override fun actionsFor(target: LinkTarget): List<LinkAction> {
        val browser = installed(FireTvApps.browsers) != null
        return when (target) {
            is LinkTarget.YouTube -> listOfNotNull(
                LinkAction.APP.takeIf { installed(FireTvApps.youTubes) != null },
                LinkAction.BROWSER.takeIf { browser },
            )
            is LinkTarget.Netflix -> listOfNotNull(LinkAction.APP.takeIf { installed(listOf(FireTvApps.NETFLIX)) != null })
            is LinkTarget.Streaming -> listOfNotNull(LinkAction.APP.takeIf { installed(FireTvApps.forService[target.service].orEmpty()) != null })
            is LinkTarget.Web -> listOfNotNull(LinkAction.BROWSER.takeIf { browser })
            is LinkTarget.Media -> emptyList()
        }
    }

    private fun installed(appIds: List<String>): String? {
        val apps = mutableState.value.apps
        if (apps.isEmpty()) return appIds.firstOrNull()
        return appIds.firstOrNull { id -> apps.any { it.id == id } }
    }

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean =
        scope.async { start(Notice.OPEN_FAILED) { openCommand(target, action) } }.await()

    private fun openCommand(target: LinkTarget, action: LinkAction): String = when {
        action == LinkAction.BROWSER && target !is LinkTarget.Media ->
            viewCommand(target.url, installed(FireTvApps.browsers) ?: throw IllegalStateException("No browser"))
        action == LinkAction.APP && target is LinkTarget.YouTube ->
            viewCommand(youTubeWatchUrl(target.videoId), installed(FireTvApps.youTubes) ?: throw IllegalStateException("No YouTube app"), browsable = true)
        action == LinkAction.APP && target is LinkTarget.Netflix -> netflixCommand(target.titleId)
        action == LinkAction.APP && target is LinkTarget.Streaming ->
            viewCommand(target.url, installed(FireTvApps.forService[target.service].orEmpty()) ?: throw IllegalStateException("No app for ${target.service}"))
        else -> throw IllegalArgumentException("$action can't open $target")
    }

    override suspend fun search(query: String, target: SearchTarget): Boolean =
        scope.async { start(Notice.OPEN_FAILED) { searchCommand(query, target) } }.await()

    private fun searchCommand(query: String, target: SearchTarget): String = when (target) {
        is SearchTarget.InApp ->
            if (target.appId in FireTvApps.youTubes) viewCommand(youTubeSearchUrl(query), target.appId, browsable = true)
            else throw IllegalArgumentException("${target.appId} takes no search")
        is SearchTarget.Web ->
            viewCommand(target.engine.url(query), installed(FireTvApps.browsers) ?: throw IllegalStateException("No browser"))
    }

    private suspend fun start(failure: Notice, command: () -> String): Boolean {
        val current = awaitShell() ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return false
        }
        return try {
            val output = current.shell(command())
            started(output).also { if (!it) Log.w(TAG, "the TV didn't start that: $output") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "open failed", e)
            if (e is IOException) dropped(current)
            false
        }.also { if (!it) mutableNotices.tryEmit(failure) }
    }

    private suspend fun awaitShell(): AdbShell? {
        shell?.let { return it }
        keeper.ensureConnected()
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            state.first { it.connection == Connection.Connected || it.connection is Connection.NeedsPairing }
            shell
        }
    }

    private fun command(failure: Notice = Notice.COMMAND_FAILED, block: suspend (AdbShell) -> Unit) {
        val current = shell ?: run {
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
                if (e is IOException) dropped(current)
            }
        }
    }

    private fun dropped(failed: AdbShell) {
        if (shell === failed) lost?.complete(Unit)
    }

    private companion object {
        const val TAG = "Tenna"

        const val PAIRED = "adb"
        const val APPROVAL_TIMEOUT_MS = 60_000L

        const val HANDSHAKE_TIMEOUT_MS = 15_000L
        const val MAX_RETRY_DELAY_MS = 10_000L
        const val CONNECT_WAIT_MS = 10_000L

        const val POLL_MS = 3_000L
        const val POLL_JITTER_MS = 500L
        const val MAX_QUEUED_KEYS = 12
    }
}
