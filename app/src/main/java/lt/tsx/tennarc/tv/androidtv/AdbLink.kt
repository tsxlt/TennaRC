package lt.tsx.tennarc.tv.androidtv

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.rethrowIfCancelled
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.tv.firetv.AdbPortClosedException
import lt.tsx.tennarc.tv.firetv.AdbShell
import lt.tsx.tennarc.tv.firetv.AdbTlsRequiredException
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

internal class AdbLink(
    private val settings: Settings,
    private var host: String?,
    private val scope: CoroutineScope,
    private val shells: (host: String, port: Int) -> AdbShell,
    private val ports: AdbPorts = AdbPorts.None,
    private val random: Random = Random,
) {
    private val mutableStatus = MutableStateFlow(host?.let { if (port(it) != null) DevLink.Down() else null } ?: DevLink.Off)
    val status: StateFlow<DevLink> = mutableStatus.asStateFlow()

    private val mutableConnections = MutableStateFlow(0)
    val connections: StateFlow<Int> = mutableConnections.asStateFlow()

    private val remote = MutableStateFlow(false)

    private val watched = MutableStateFlow(false)
    private val current = MutableStateFlow<AdbShell?>(null)
    private var lost: CompletableDeferred<Unit>? = null
    private val nudges = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var lingerJob: Job? = null

    private var settingUp = false

    fun remoteUp(host: String) {
        lingerJob?.cancel()
        if (this.host != host) {
            stop()
            this.host = host
        }
        remote.value = true
        if (port(host) == null) {
            if (!settingUp) mutableStatus.value = linkOff()
            return
        }
        if (job?.isActive == true) return nudge()
        if (mutableStatus.value !is DevLink.Up) mutableStatus.value = DevLink.Connecting
        job = scope.launch {
            delay(START_DELAY_MS)
            stayConnected(host, asked = false)
        }
    }

    fun remoteDown() {
        remote.value = false
        lingerJob?.cancel()
        lingerJob = scope.launch {
            delay(LINGER_MS)
            if (!settingUp) stop()
        }
    }

    fun watch(watching: Boolean) {
        watched.value = watching
        if (watching) nudge()
    }

    fun setUp(host: String) {
        lingerJob?.cancel()
        stop()
        this.host = host
        settingUp = true
        mutableStatus.value = DevLink.Connecting
        job = scope.launch { stayConnected(host, asked = true) }
    }

    fun turnOff() {
        host?.let { settings.setClientKey(adbKey(it), null) }
        stop()
        mutableStatus.value = DevLink.Off
    }

    fun forget(host: String) {
        settings.setClientKey(adbKey(host), null)
        if (this.host == host) {
            stop()
            mutableStatus.value = DevLink.Off
        }
    }

    fun close() {
        lingerJob?.cancel()
        stop()
    }

    suspend fun call(command: String, timeoutMs: Long = CALL_TIMEOUT_MS): String? {
        val shell = current.value ?: awaitShell() ?: return null
        return run(shell, command, timeoutMs)
    }

    private suspend fun awaitShell(): AdbShell? {
        if (job?.isActive != true) return null
        nudge()
        return withTimeoutOrNull(CONNECT_WAIT_MS) { current.filterNotNull().first() }
    }

    private suspend fun run(shell: AdbShell, command: String, timeoutMs: Long): String? {
        val output = try {
            withTimeoutOrNull(timeoutMs) { shell.shell(command) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "adb call failed: ${e.message}")
            null
        }
        if (output == null) dropped(shell)
        return output
    }

    private fun nudge() {
        nudges.trySend(Unit)
    }

    private fun stop() {
        job?.cancel()
        job = null
        settingUp = false
        current.value?.close()
        current.value = null
        lost = null
        host?.let { if (port(it) != null && mutableStatus.value != DevLink.Off) mutableStatus.value = DevLink.Down() }
    }

    private fun linkOff(): DevLink = mutableStatus.value.takeIf { it is DevLink.Failed } ?: DevLink.Off

    private suspend fun stayConnected(host: String, asked: Boolean) {
        var asking = asked
        var failures = 0
        var looked = false
        var port = port(host) ?: ADB_PORT
        while (true) {
            if (!asking) remote.first { it }
            val opened = shells(host, port)
            var problem: DevProblem? = DevProblem.UNREACHABLE
            try {
                val trusted = withTimeoutOrNull(PROBE_TIMEOUT_MS) { opened.trusted() } ?: throw IOException("$host didn't answer on $port")
                if (!trusted) {
                    if (!asking) {
                        Log.w(TAG, "$host no longer allows this phone's ADB key")
                        mutableStatus.value = DevLink.Down(DevProblem.NOT_ALLOWED)
                        return
                    }
                    mutableStatus.value = DevLink.AwaitingApproval
                }
                withTimeoutOrNull(if (trusted) HANDSHAKE_TIMEOUT_MS else APPROVAL_TIMEOUT_MS) { opened.connect() } ?: run {
                    if (trusted) throw IOException("$host didn't finish connecting")
                    Log.w(TAG, "nobody allowed this phone's ADB on $host")
                    settingUp = false
                    mutableStatus.value = DevLink.Failed(DevProblem.NOT_ALLOWED)
                    return
                }
                settings.setClientKey(adbKey(host), port.toString())
                asking = false
                settingUp = false
                failures = 0
                looked = false
                Log.i(TAG, "developer connection to $host:$port")
                val dropped = CompletableDeferred<Unit>()
                lost = dropped
                current.value = opened
                mutableStatus.value = DevLink.Up
                mutableConnections.value++
                coroutineScope {
                    launch { keepAlive(opened) }
                    dropped.await()
                    coroutineContext.cancelChildren()
                }
                Log.d(TAG, "developer connection to $host dropped")
                problem = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: AdbTlsRequiredException) {
                problem = DevProblem.WIRELESS_ONLY
            } catch (e: AdbPortClosedException) {
                Log.d(TAG, "$host refuses ADB on $port: ${e.message}")
                problem = DevProblem.DEBUGGING_OFF
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                Log.d(TAG, "developer connection to $host failed: ${e.message}")
            } finally {
                opened.close()
                if (current.value === opened) {
                    current.value = null
                    lost = null
                }
            }
            if (problem == DevProblem.DEBUGGING_OFF && !looked) {
                looked = true
                val announced = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { ports.find(host) } ?: AdbPorts.Announced()
                val moved = announced.plain?.takeIf { it != port }
                if (moved != null) {
                    Log.i(TAG, "$host announces ADB on $moved now")
                    port = moved
                    continue
                }
                if (announced.tls != null && announced.plain == null) problem = DevProblem.WIRELESS_ONLY
            }
            if (asking) {
                settingUp = false
                mutableStatus.value = DevLink.Failed(problem ?: DevProblem.UNREACHABLE)
                return
            }
            failures++
            if (failures >= 2 || mutableStatus.value !is DevLink.Up) {
                mutableStatus.value = DevLink.Down(problem.takeIf { it != DevProblem.UNREACHABLE })
            }
            pause(retryDelay(failures))
        }
    }

    private suspend fun keepAlive(shell: AdbShell) {
        while (true) {
            watched.first { it }
            delay(PING_MS + random.nextLong(PING_JITTER_MS))
            if (watched.value && run(shell, PING, PING_TIMEOUT_MS) == null) return
        }
    }

    private fun dropped(shell: AdbShell) {
        if (current.value === shell) lost?.complete(Unit)
    }

    private suspend fun pause(millis: Long) {
        nudges.tryReceive()
        withTimeoutOrNull(millis) { nudges.receive() }
    }

    private fun retryDelay(failures: Int): Long =
        min(FIRST_RETRY_MS shl min(failures - 1, 10), MAX_RETRY_MS) + random.nextLong(RETRY_JITTER_MS)

    private fun port(host: String): Int? = settings.clientKey(adbKey(host))?.toIntOrNull()

    companion object {
        private const val TAG = "Tenna"
        const val ADB_PORT = 5555

        const val START_DELAY_MS = 1_500L

        const val LINGER_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 5_000L
        const val HANDSHAKE_TIMEOUT_MS = 10_000L

        const val APPROVAL_TIMEOUT_MS = 60_000L
        const val CALL_TIMEOUT_MS = 6_000L

        const val CONNECT_WAIT_MS = 5_000L
        const val FIRST_RETRY_MS = 2_000L

        const val MAX_RETRY_MS = 120_000L
        const val RETRY_JITTER_MS = 1_000L
        const val PING_MS = 45_000L
        const val PING_JITTER_MS = 5_000L
        const val PING_TIMEOUT_MS = 4_000L
        const val LOOKUP_TIMEOUT_MS = 6_000L
        private const val PING = "echo"
    }
}

internal fun adbKey(host: String) = "adb@$host"
