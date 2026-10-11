package lt.tsx.tennarc.tv

import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ConnectionKeeper(
    private val scope: CoroutineScope,
    private val idleMs: Long = IDLE_DISCONNECT_MS,
    private val busy: suspend () -> Unit = {},
    private val run: suspend (allowPairing: Boolean) -> Unit,
) {
    private var job: Job? = null
    private var idleJob: Job? = null
    private var holders = 0

    var visibleScreens = 0
        private set

    val active: Boolean get() = job?.isActive == true

    fun connect(allowPairing: Boolean = false) {
        job?.cancel()
        job = scope.launch { run(allowPairing) }
    }

    fun ensureConnected() {
        if (!active) connect()
    }

    fun cancel() {
        job?.cancel()
    }

    fun hold() {
        holders++
        idleJob?.cancel()
        ensureConnected()
    }

    fun release(): Boolean {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return false
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMs)
            busy()
            job?.cancel()
        }
        return true
    }

    fun screenStarted() {
        visibleScreens++
    }

    fun screenStopped() {
        visibleScreens = (visibleScreens - 1).coerceAtLeast(0)
    }

    fun reset() {
        holders = 0
    }
}

internal fun retryDelay(failures: Int, maxMs: Long): Long = min(1_000L shl min(failures - 1, 4), maxMs) + retryJitter()

internal fun retryJitter(): Long = Random.nextLong(RETRY_JITTER_MS)

internal fun TvState.afterFailures(
    failures: Int,
    connecting: TvState.() -> TvState = { copy(connection = Connection.Connecting) },
    offline: TvState.() -> TvState,
): TvState = when {
    failures >= 2 -> offline()
    connection != Connection.Offline -> connecting()
    else -> this
}

internal const val IDLE_DISCONNECT_MS = 60_000L
private const val RETRY_JITTER_MS = 700L
