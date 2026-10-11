package lt.tsx.tennarc.tv.firetv

import dadb.AdbShellPacket
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.coroutines.resumeWithException
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.rethrowIfCancelled

interface AdbShell {
    suspend fun trusted(): Boolean

    suspend fun connect()

    suspend fun shell(command: String): String

    fun close()
}

class AdbPortClosedException(host: String, cause: Throwable? = null) : IOException("$host refuses ADB connections", cause)

class AdbTlsRequiredException : IOException("ADB here wants TLS (Wireless debugging)")

class DadbShell(private val host: String, private val keys: AdbKeys, private val port: Int = ADB_PORT) : AdbShell {
    @Volatile private var dadb: Dadb? = null
    @Volatile private var closed = false
    @Volatile private var shellV2 = true
    private val idle = ArrayDeque<Session>()

    override suspend fun trusted(): Boolean {
        withContext(Dispatchers.IO) { keys.pair() }
        val socket = Socket()
        return blocking(abort = socket::close) {
            socket.use {
                try {
                    it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                } catch (e: IOException) {
                    throw e.asPortClosed()
                }
                it.soTimeout = PROBE_TIMEOUT_MS
                AdbAuthProbe.trusted(it.getInputStream(), it.getOutputStream(), keys::sign)
            }
        }
    }

    override suspend fun connect() {
        val pair = withContext(Dispatchers.IO) { keys.pair() }
        val created = Dadb.create(host, port, pair, CONNECT_TIMEOUT_MS, PROMPT_TIMEOUT_MS)
        val v2 = suspendCancellableCoroutine { cont ->
            thread(name = "adb-handshake", isDaemon = true) {
                try {
                    val supported = created.supportsFeature(SHELL_V2)
                    cont.resume(supported) { _, _, _ -> created.close() }
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    logFailure("AdbShell.connect", e)
                    cont.resumeWithException(e.asPortClosed())
                }
            }
        }
        val kept = synchronized(this) {
            if (!closed) dadb = created
            !closed
        }
        if (!kept) {
            created.close()
            throw IOException("Closed while connecting to $host")
        }
        shellV2 = v2
    }

    override suspend fun shell(command: String): String {
        live()
        if (!shellV2) return timed { blocking { live().open("shell:$command").use { it.source.readUtf8() } } }
        val session = synchronized(idle) { idle.removeFirstOrNull() } ?: blocking { Session(live().openShell()) }
        var healthy = false
        try {
            return timed { blocking { session.run(command) } }.also { healthy = true }
        } finally {
            val kept = healthy && !closed && synchronized(idle) {
                (idle.size < MAX_IDLE_SESSIONS).also { room -> if (room) idle.addLast(session) }
            }
            if (!kept) session.close()
        }
    }

    override fun close() {
        val open = synchronized(this) {
            closed = true
            dadb.also { dadb = null }
        }
        synchronized(idle) { idle.toList().also { idle.clear() } }.forEach { it.close() }
        open?.close()
    }

    private fun live(): Dadb = synchronized(this) { dadb.takeUnless { closed } } ?: throw IOException("Not connected to $host")

    private suspend fun <T> timed(block: suspend () -> T): T = try {
        withTimeout(COMMAND_TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw IOException("$host stopped answering", e)
    }

    private suspend fun <T> blocking(abort: () -> Unit = ::close, block: () -> T): T =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { abort() }
            IO.execute { cont.resumeWith(runCatching(block)) }
        }

    private fun IOException.asPortClosed(): IOException = if (isRefused()) AdbPortClosedException(host, this) else this

    private fun Exception.asPortClosed(): Exception = if (this is IOException) asPortClosed() else this

    private class Session(private val stream: AdbShellStream) {
        private var commands = 0

        fun run(command: String): String {
            val id = ++commands
            stream.write(ShellFraming.line(id, command))
            val received = ByteArrayOutputStream()
            while (true) {
                when (val packet = stream.read()) {
                    is AdbShellPacket.StdOut, is AdbShellPacket.StdError -> received.write(packet.payload)
                    is AdbShellPacket.Exit -> throw IOException("The TV's shell exited")
                }
                ShellFraming.output(id, String(received.toByteArray(), Charsets.UTF_8))?.let { return it }
            }
        }

        fun close() {
            catching { stream.close() }
        }
    }

    private companion object {
        const val ADB_PORT = 5555
        const val SHELL_V2 = "shell_v2"
        const val CONNECT_TIMEOUT_MS = 3_000
        const val PROBE_TIMEOUT_MS = 5_000
        const val PROMPT_TIMEOUT_MS = 90_000
        const val COMMAND_TIMEOUT_MS = 15_000L
        const val MAX_IDLE_SESSIONS = 2
        val IO = Dispatchers.IO.asExecutor()
    }
}

private fun Throwable.isRefused(): Boolean = generateSequence(this) { it.cause }.any {
    it is ConnectException &&
        (it.message?.contains("ECONNREFUSED") == true || it.message?.contains("Connection refused", ignoreCase = true) == true)
}

internal object ShellFraming {
    private fun begin(id: Int) = "tenna:begin:$id:"
    private fun end(id: Int) = "tenna:end:$id:"

    fun line(id: Int, command: String) = "echo ${begin(id)}; {\n$command\n} </dev/null 2>&1; echo ${end(id)}\n"

    fun output(id: Int, received: String): String? {
        val end = received.indexOf(end(id))
        if (end < 0) return null
        val begin = received.lastIndexOf(begin(id), end).let { if (it < 0) 0 else it + begin(id).length }
        return received.substring(begin, end).removePrefix("\n").removeSuffix("\n")
    }
}
