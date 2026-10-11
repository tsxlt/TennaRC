package lt.tsx.tennarc.tv

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.rethrowIfCancelled
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.w3c.dom.Element
import org.xml.sax.SAXException

private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
private const val CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1"
private const val DEVICE_NS = "urn:schemas-upnp-org:device-1-0"
private const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
private const val CONTROL_NS = "urn:schemas-upnp-org:control-1-0"
internal const val DLNA_FLAGS = "DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"

class DlnaException(message: String) : IOException(message)

data class SsdpReply(val location: String, val server: String, val usn: String = "") {
    val uuid: String? get() = usn.substringAfter("uuid:", "").substringBefore("::").ifEmpty { null }
}

object Ssdp {
    fun search(target: String, timeoutMs: Long = 2_500): List<SsdpReply> {
        val query = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $target\r\n\r\n")
        }.toByteArray()
        val replies = LinkedHashMap<String, SsdpReply>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 250
            val group = InetSocketAddress(SSDP_ADDRESS, SSDP_PORT)
            repeat(2) { socket.send(DatagramPacket(query, query.size, group)) }
            val buffer = ByteArray(8 * 1024)
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (System.nanoTime() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val headers = parseHeaders(String(packet.data, 0, packet.length))
                val location = headers["location"] ?: continue
                replies.putIfAbsent(location, SsdpReply(location, headers["server"].orEmpty(), headers["usn"].orEmpty()))
            }
        }
        return replies.values.toList()
    }

    internal fun parseHeaders(response: String): Map<String, String> = response
        .lineSequence()
        .drop(1)
        .mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }
        .toMap()
}

data class UpnpDevice(val location: String, val name: String, val services: Map<String, String>) {
    val host: String? get() = hostOf(location)
    val avTransport: String? get() = services[AV_TRANSPORT]
    val connectionManager: String? get() = services[CONNECTION_MANAGER]
}

data class TransportStatus(val state: String, val positionSeconds: Int, val durationSeconds: Int)

class DlnaClient(private val http: OkHttpClient) {

    suspend fun findRenderer(host: String): UpnpDevice? = withContext(Dispatchers.IO) {
        Ssdp.search(MEDIA_RENDERER)
            .filter { hostOf(it.location) == host }
            .firstNotNullOfOrNull { reply -> catching { describe(reply.location) }.logged("Dlna.findRenderer").getOrNull()?.takeIf { it.avTransport != null } }
    }

    fun describe(location: String): UpnpDevice {
        val body = http.newCall(Request.Builder().url(location).build()).execute().use { response ->
            if (!response.isSuccessful) throw DlnaException("${response.code} from $location")
            response.body.boundedString()
        }
        val document = parseXml(body)
        val name = document.getElementsByTagNameNS(DEVICE_NS, "friendlyName").item(0)?.textContent?.trim().orEmpty()
        val services = mutableMapOf<String, String>()
        val nodes = document.getElementsByTagNameNS(DEVICE_NS, "service")
        for (i in 0 until nodes.length) {
            val service = nodes.item(i) as Element
            val type = service.child("serviceType") ?: continue
            val control = service.child("controlURL") ?: continue
            services[type] = URI(location).resolve(control).toString()
        }
        return UpnpDevice(location, name, services)
    }

    suspend fun play(renderer: UpnpDevice, url: String, title: String, mime: String) = withContext(Dispatchers.IO) {
        catching { call(renderer, "Stop") }.logged("Dlna.play")
        call(renderer, "SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to didl(url, title, mime))
        call(renderer, "Play", "Speed" to "1")
    }

    suspend fun pause(renderer: UpnpDevice) = withContext(Dispatchers.IO) { call(renderer, "Pause") }
    suspend fun resume(renderer: UpnpDevice) = withContext(Dispatchers.IO) { call(renderer, "Play", "Speed" to "1") }
    suspend fun stop(renderer: UpnpDevice) = withContext(Dispatchers.IO) { call(renderer, "Stop") }

    suspend fun seek(renderer: UpnpDevice, seconds: Int) = withContext(Dispatchers.IO) {
        call(renderer, "Seek", "Unit" to "REL_TIME", "Target" to formatTime(seconds))
    }

    suspend fun status(renderer: UpnpDevice): TransportStatus = withContext(Dispatchers.IO) {
        val transport = call(renderer, "GetTransportInfo")
        val position = call(renderer, "GetPositionInfo")
        TransportStatus(
            state = transport["CurrentTransportState"].orEmpty(),
            positionSeconds = parseTime(position["RelTime"]),
            durationSeconds = parseTime(position["TrackDuration"]),
        )
    }

    suspend fun sinkProtocols(renderer: UpnpDevice): List<String> = withContext(Dispatchers.IO) {
        val control = renderer.connectionManager ?: return@withContext emptyList()
        try {
            soap(control, CONNECTION_MANAGER, "GetProtocolInfo", emptyList())["Sink"].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            logFailure("Dlna.sinkProtocols", e)
            emptyList()
        }
    }

    fun call(renderer: UpnpDevice, action: String, vararg arguments: Pair<String, String>): Map<String, String> {
        val control = renderer.avTransport ?: throw DlnaException("${renderer.name} has no AVTransport")
        return soap(control, AV_TRANSPORT, action, listOf("InstanceID" to "0") + arguments)
    }

    private fun soap(control: String, service: String, action: String, arguments: List<Pair<String, String>>): Map<String, String> {
        val fields = arguments.joinToString("") { (key, value) ->
            "<$key>${escapeXml(value)}</$key>"
        }
        val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="$SOAP_NS" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$service">$fields</u:$action></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(control)
            .header("SOAPAction", "\"$service#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body.boundedString()
            if (!response.isSuccessful) throw DlnaException(soapFault(action, body) ?: "$action failed: HTTP ${response.code}")
            val envelopeBody = parseXml(body).getElementsByTagNameNS(SOAP_NS, "Body").item(0) as? Element
                ?: return emptyMap()
            val result = envelopeBody.elements().firstOrNull() ?: return emptyMap()
            return result.elements().associate { it.localName to it.textContent.orEmpty() }
        }
    }

    private fun soapFault(action: String, body: String): String? {
        val document = try {
            parseXml(body)
        } catch (_: SAXException) {
            return null
        } catch (_: IOException) {
            return null
        }
        val code = document.getElementsByTagNameNS(CONTROL_NS, "errorCode").item(0)?.textContent
        val description = document.getElementsByTagNameNS(CONTROL_NS, "errorDescription").item(0)?.textContent
        return "$action refused: ${listOfNotNull(code, description).joinToString(" ")}"
    }
}

internal fun didl(url: String, title: String, mime: String): String {
    val upnpClass = when {
        mime.startsWith("audio/") -> "object.item.audioItem.musicTrack"
        mime.startsWith("image/") -> "object.item.imageItem.photo"
        else -> "object.item.videoItem"
    }
    return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
        """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
        """<item id="0" parentID="-1" restricted="1">""" +
        "<dc:title>${escapeXml(title)}</dc:title>" +
        "<upnp:class>$upnpClass</upnp:class>" +
        """<res protocolInfo="http-get:*:$mime:$DLNA_FLAGS">${escapeXml(url)}</res>""" +
        "</item></DIDL-Lite>"
}

internal fun acceptsType(sinks: List<String>, mime: String): Boolean = sinks.any { entry ->
    val fields = entry.split(':')
    fields.size >= 3 && fields[0] == "http-get" && (fields[2] == "*" || fields[2].equals(mime, ignoreCase = true))
}

internal fun escapeXml(text: String): String = buildString(text.length) {
    for (c in text) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
    }
}

internal fun parseTime(text: String?): Int {
    if (text.isNullOrBlank() || text == "NOT_IMPLEMENTED") return 0
    return text.split(":").fold(0.0) { total, part -> total * 60 + (part.toDoubleOrNull() ?: 0.0) }.toInt()
}

internal fun formatTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
}

internal fun parseXml(text: String): org.w3c.dom.Document {
    if (text.contains("<!DOCTYPE", ignoreCase = true)) throw IOException("XML with a DOCTYPE refused")
    return DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            catching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        .newDocumentBuilder()
        .parse(text.byteInputStream())
}

private fun Element.elements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

private fun Element.child(name: String): String? =
    elements().firstOrNull { it.localName == name }?.textContent?.trim()

private const val MAX_XML_BYTES = 256L * 1024

internal fun ResponseBody.boundedString(): String {
    val source = source()
    if (source.request(MAX_XML_BYTES + 1)) throw DlnaException("Reply larger than $MAX_XML_BYTES bytes")
    return source.buffer.readUtf8()
}
