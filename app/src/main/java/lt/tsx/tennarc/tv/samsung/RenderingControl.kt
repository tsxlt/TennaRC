package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.DlnaClient
import lt.tsx.tennarc.tv.Ssdp
import lt.tsx.tennarc.tv.escapeXml
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RenderingControl(private val http: OkHttpClient, private val controlUrl: String) {
    suspend fun volume(): Int =
        call("GetVolume", "Channel" to "Master")["CurrentVolume"]?.toIntOrNull() ?: throw SamsungException("No volume in the reply")

    suspend fun muted(): Boolean = when (call("GetMute", "Channel" to "Master")["CurrentMute"]) {
        "1", "true" -> true
        "0", "false" -> false
        else -> throw SamsungException("No mute state in the reply")
    }

    suspend fun setVolume(level: Int) {
        call("SetVolume", "Channel" to "Master", "DesiredVolume" to level.toString())
    }

    private suspend fun call(action: String, vararg arguments: Pair<String, String>): Map<String, String> = withContext(Dispatchers.IO) {
        val fields = (listOf("InstanceID" to "0") + arguments).joinToString("") { (key, value) -> "<$key>${escapeXml(value)}</$key>" }
        val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$SERVICE">$fields</u:$action></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPAction", "\"$SERVICE#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SamsungException("$action failed: HTTP ${response.code}")
            ARGUMENT.findAll(response.body.bounded()).associate { it.groupValues[1] to it.groupValues[2].trim() }
        }
    }

    companion object {
        const val SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"
        private val ARGUMENT = Regex("""<([A-Za-z]+)>([^<]*)</\1>""")

        suspend fun find(http: OkHttpClient, host: String): String? = withContext(Dispatchers.IO) {
            val dlna = DlnaClient(http)
            Ssdp.search(SERVICE)
                .filter { hostOf(it.location) == host }
                .firstNotNullOfOrNull { reply -> catching { dlna.describe(reply.location).services[SERVICE] }.logged("RenderingControl.find").getOrNull() }
        }
    }
}
