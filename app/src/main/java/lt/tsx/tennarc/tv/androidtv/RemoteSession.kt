package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import lt.tsx.tennarc.logFailure

internal class ClientRejectedException(cause: Throwable) : IOException("The TV doesn't know this phone", cause)

internal class TvChangedException : IOException("The TV's certificate changed")

internal fun Throwable.isRejection(): Boolean {
    val causes = generateSequence(this) { it.cause }.toList()
    if (causes.any { it is SocketTimeoutException || it is ConnectException || it is NoRouteToHostException }) return false
    return causes.any { it is SSLException || it is EOFException || it is SocketException && it.message.orEmpty().contains("reset", ignoreCase = true) }
}

internal class RemoteSession private constructor(
    private val socket: TvSocket,
    private val onEvent: (RemoteEvent) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)
    private val started = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()

    @Volatile private var failure: Throwable? = null
    @Volatile private var heardFromTv = false
    @Volatile private var lastActivity = now()
    @Volatile private var features = RemoteProtocol.REQUESTED
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0

    val fingerprint: String = socket.peerCertificate.fingerprint()
    val isOpen: Boolean get() = !closed.isCompleted

    suspend fun awaitClosed() = closed.await()

    fun key(keyCode: Int): Boolean = send(RemoteProtocol.keyInject(keyCode))

    fun openLink(link: String): Boolean = send(RemoteProtocol.appLink(link))

    fun type(text: String): Boolean = send(RemoteProtocol.imeText(imeCounter, fieldCounter, text))

    fun close() = finish(IOException("Closed"))

    private fun send(frame: ByteArray): Boolean = outbox.trySend(frame).isSuccess

    private fun start() {
        scope.launch { read() }
        scope.launch { write() }
        scope.launch { watchIdle() }
    }

    private fun read() {
        try {
            while (true) {
                val frame = socket.input.readFrame()
                heardFromTv = true
                lastActivity = now()
                val event = try {
                    RemoteProtocol.decode(frame)
                } catch (_: ProtocolException) {
                    continue
                }
                handle(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("RemoteSession.read", e)
            finish(e)
        }
    }

    private fun handle(event: RemoteEvent) {
        when (event) {
            is RemoteEvent.Configure -> {
                features = RemoteProtocol.REQUESTED and event.features
                send(RemoteProtocol.configure(features))
            }
            RemoteEvent.SetActive -> send(RemoteProtocol.setActive(features))
            is RemoteEvent.Ping -> send(RemoteProtocol.pingResponse(event.value))
            is RemoteEvent.ImeCounters -> {
                imeCounter = event.ime
                fieldCounter = event.field
            }
            is RemoteEvent.Started -> started.complete(Unit)
            else -> Unit
        }
        onEvent(event)
    }

    private suspend fun write() {
        try {
            for (frame in outbox) {
                socket.output.writeFrame(frame)
                lastActivity = now()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("RemoteSession.write", e)
            finish(e)
        }
    }

    private suspend fun watchIdle() {
        while (true) {
            delay(IDLE_CHECK_MS)
            if (now() - lastActivity > IDLE_LIMIT_MS) finish(SocketTimeoutException("The TV went quiet"))
        }
    }

    @Synchronized
    private fun finish(cause: Throwable) {
        if (closed.isCompleted) return
        failure = cause
        outbox.close()
        socket.close()
        closed.complete(Unit)
        scope.cancel()
    }

    private fun openFailure(): IOException {
        val cause = failure ?: EOFException("Closed")
        if (!heardFromTv && cause.isRejection()) return ClientRejectedException(cause)
        return cause as? IOException ?: IOException(cause)
    }

    companion object {
        private const val START_TIMEOUT_MS = 10_000L
        private const val IDLE_CHECK_MS = 2_000L
        private const val IDLE_LIMIT_MS = 16_000L

        private fun now() = System.nanoTime() / 1_000_000

        suspend fun open(tls: TvTls, host: String, port: Int, pin: TvPin, onEvent: (RemoteEvent) -> Unit): RemoteSession {
            val socket = try {
                tls.connect(host, port)
            } catch (e: IOException) {
                throw if (e.isRejection()) ClientRejectedException(e) else e
            }
            val session = try {
                RemoteSession(socket, onEvent)
            } catch (e: Exception) {
                socket.close()
                throw e
            }
            try {
                if (!pin.admits(session.fingerprint)) throw TvChangedException()
                socket.readTimeoutMs = 0
                session.start()
                val ready = withTimeoutOrNull(START_TIMEOUT_MS) {
                    select<Boolean> {
                        session.started.onAwait { true }
                        session.closed.onAwait { false }
                    }
                } ?: throw SocketTimeoutException("The TV never said the remote was ready")
                if (!ready) throw session.openFailure()
                return session
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
    }
}
