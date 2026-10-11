package lt.tsx.tennarc.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.rethrowIfCancelled

interface MediaSource {
    val mime: String

    val length: Long?

    fun open(offset: Long): InputStream
}

class MediaServer private constructor(
    private val socket: ServerSocket,
    private val allow: (InetAddress) -> Boolean,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val files = ConcurrentHashMap<String, MediaSource>()
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    val port: Int get() = socket.localPort

    init {
        scope.launch { acceptAll() }
    }

    fun add(source: MediaSource, extension: String? = null): String {
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val ending = extension?.lowercase(Locale.ROOT)?.takeIf { EXTENSION.matches(it) }?.let { ".$it" }.orEmpty()
        return "/$token$ending".also { files[it] = source }
    }

    fun remove(path: String) {
        files.remove(path)
    }

    fun url(path: String): String {
        val host = socket.inetAddress.hostAddress.orEmpty()
        return "http://${if (':' in host) "[$host]" else host}:$port$path"
    }

    override fun close() {
        catching { socket.close() }
        scope.cancel()
        clients.forEach { catching { it.close() } }
    }

    private fun acceptAll() {
        while (true) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                return
            }
            if (!allow(client.inetAddress) || clients.size >= MAX_CLIENTS) {
                catching { client.close() }
                continue
            }
            clients += client
            if (!scope.isActive) {
                catching { client.close() }
                return
            }
            scope.launch {
                try {
                    client.use(::converse)
                } catch (_: IOException) {
                } finally {
                    clients -= client
                }
            }
        }
    }

    private fun converse(client: Socket) {
        client.soTimeout = IDLE_TIMEOUT_MS
        client.tcpNoDelay = true
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream(), BUFFER_BYTES)
        while (true) {
            val request = try {
                readRequest(input) ?: return
            } catch (_: BadRequestException) {
                head(output, 400, "Bad Request", emptyList(), close = true)
                output.flush()
                return
            }
            val again = answer(request, output)
            output.flush()
            if (!again) return
        }
    }

    private fun answer(request: Request, output: OutputStream): Boolean {
        if (request.method != "GET" && request.method != "HEAD") {
            head(output, 405, "Method Not Allowed", listOf("Allow" to "GET, HEAD"), close = true)
            return false
        }
        val source = files[request.path] ?: run {
            head(output, 404, "Not Found", emptyList(), close = !request.keepAlive)
            return request.keepAlive
        }
        val length = source.length
        val range = length?.let { parseRange(request.headers["range"], it) }
        if (range == LongRange.EMPTY) {
            head(output, 416, "Range Not Satisfiable", listOf("Content-Range" to "bytes */$length"), close = !request.keepAlive)
            return request.keepAlive
        }
        val start = range?.first ?: 0L
        val count = if (range != null) range.last - range.first + 1 else length
        val body = if (request.method == "GET") {
            try {
                source.open(start)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                logFailure("MediaServer.answer", e)
                val missing = e is FileNotFoundException || e is SecurityException
                head(output, if (missing) 404 else 500, if (missing) "Not Found" else "Internal Server Error", emptyList(), close = true)
                return false
            }
        } else {
            null
        }
        val again = request.keepAlive && count != null
        val headers = buildList {
            add("Content-Type" to source.mime)
            if (count != null) add("Content-Length" to count.toString())
            if (length != null) add("Accept-Ranges" to "bytes")
            if (range != null) add("Content-Range" to "bytes ${range.first}-${range.last}/$length")
            add(TRANSFER_MODE to transferMode(request.headers[TRANSFER_MODE.lowercase(Locale.ROOT)], source.mime))
            add(CONTENT_FEATURES to DLNA_FLAGS)
        }
        if (body == null) {
            head(output, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK", headers, close = !again)
            return again
        }
        body.use { stream ->
            head(output, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK", headers, close = !again)
            val buffer = ByteArray(BUFFER_BYTES)
            var left = count ?: Long.MAX_VALUE
            while (left > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                left -= read
            }
            if (count != null && left > 0) return false
        }
        return again
    }

    private fun head(output: OutputStream, status: Int, reason: String, headers: List<Pair<String, String>>, close: Boolean) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            for ((name, value) in headers) append(name).append(": ").append(value).append("\r\n")
            if (status >= 400) append("Content-Length: 0\r\n")
            append("Connection: ").append(if (close) "close" else "keep-alive").append("\r\n\r\n")
        }
        output.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val keepAlive: Boolean)

    private class BadRequestException : IOException("Bad request")

    private fun readRequest(input: InputStream): Request? {
        var budget = MAX_HEAD_BYTES
        fun line(): String? {
            val text = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) return null
                if (--budget < 0) throw BadRequestException()
                if (byte == '\n'.code) return text.toString().removeSuffix("\r")
                text.append(byte.toChar())
            }
        }
        var first = line() ?: return null
        while (first.isEmpty()) first = line() ?: return null
        val parts = first.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) throw BadRequestException()
        val headers = HashMap<String, String>()
        while (true) {
            val header = line() ?: return null
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon <= 0) throw BadRequestException()
            headers[header.substring(0, colon).trim().lowercase(Locale.ROOT)] = header.substring(colon + 1).trim()
        }
        val connection = headers["connection"].orEmpty().lowercase(Locale.ROOT)
        val body = headers["content-length"].let { it != null && it != "0" } || "transfer-encoding" in headers
        val keepAlive = !body && if (parts[2] == "HTTP/1.0") "keep-alive" in connection else "close" !in connection
        return Request(parts[0], pathOf(parts[1]), headers, keepAlive)
    }

    companion object {
        fun start(address: InetAddress, allow: (InetAddress) -> Boolean = { true }): MediaServer =
            MediaServer(ServerSocket(0, BACKLOG, address), allow)

        private val random = SecureRandom()
        private val EXTENSION = Regex("[a-z0-9]{1,5}")
        private const val TOKEN_BYTES = 16
        private const val BACKLOG = 16
        private const val MAX_CLIENTS = 16
        private const val MAX_HEAD_BYTES = 16 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val IDLE_TIMEOUT_MS = 30_000
        private const val TRANSFER_MODE = "transferMode.dlna.org"
        private const val CONTENT_FEATURES = "contentFeatures.dlna.org"
    }
}

private fun pathOf(target: String): String {
    val path = if (target.startsWith("http://", ignoreCase = true)) "/" + target.substringAfter("://").substringAfter('/', "") else target
    return path.substringBefore('?').substringBefore('#')
}

private fun transferMode(asked: String?, mime: String): String = when {
    asked != null && asked in listOf("Streaming", "Interactive", "Background") -> asked
    mime.startsWith("image/") -> "Interactive"
    else -> "Streaming"
}

internal fun parseRange(header: String?, length: Long): LongRange? {
    val spec = header?.trim() ?: return null
    if (!spec.startsWith("bytes=", ignoreCase = true)) return null
    val ranges = spec.substring("bytes=".length).trim()
    if (',' in ranges) return null
    val dash = ranges.indexOf('-')
    if (dash < 0) return null
    val first = ranges.substring(0, dash).trim()
    val last = ranges.substring(dash + 1).trim()
    if (first.isEmpty()) {
        val suffix = last.digits() ?: return null
        if (suffix == 0L || length == 0L) return LongRange.EMPTY
        return maxOf(0L, length - suffix) until length
    }
    val start = first.digits() ?: return null
    val end = if (last.isEmpty()) null else last.digits() ?: return null
    if (end != null && end < start) return null
    if (start >= length) return LongRange.EMPTY
    return start..minOf(end ?: length, length - 1)
}

private fun String.digits(): Long? = takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull()

internal fun InputStream.skipFully(count: Long) {
    var left = count
    var buffer: ByteArray? = null
    while (left > 0) {
        val skipped = try {
            skip(left)
        } catch (_: IOException) {
            0L
        }
        if (skipped > 0) {
            left -= skipped
            continue
        }
        val chunk = buffer ?: ByteArray(64 * 1024).also { buffer = it }
        val read = read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
        if (read < 0) throw EOFException("$left bytes short")
        left -= read
    }
}

internal fun localAddressFor(tv: Inet4Address?): Inet4Address? {
    val interfaces = catching { NetworkInterface.getNetworkInterfaces()?.toList() }.logged("localAddressFor").getOrNull().orEmpty()
        .filter {
            try {
                it.isUp && !it.isLoopback
            } catch (_: SocketException) {
                false
            }
        }
        .sortedByDescending { it.name.startsWith("wlan") }
    val addresses = interfaces.flatMap { it.interfaceAddresses }.filter { it.address is Inet4Address }
    if (tv == null) return addresses.map { it.address as Inet4Address }.firstOrNull { it.isSiteLocalAddress }
    addresses.firstOrNull { sameSubnet(it.address as Inet4Address, tv, it.networkPrefixLength.toInt()) }
        ?.let { return it.address as Inet4Address }
    return catching { DatagramSocket().use { it.connect(tv, 9); it.localAddress as? Inet4Address } }
        .logged("localAddressFor").getOrNull()
        ?.takeIf { it.isSiteLocalAddress }
}

internal fun sameSubnet(a: Inet4Address, b: Inet4Address, prefix: Int): Boolean {
    if (prefix !in 1..32) return false
    val mask = -1 shl (32 - prefix)
    return (a.bits() and mask) == (b.bits() and mask)
}

private fun Inet4Address.bits(): Int = address.fold(0) { bits, byte -> (bits shl 8) or (byte.toInt() and 0xff) }
