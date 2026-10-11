package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.jsonOrNull
import lt.tsx.tennarc.tv.TvPower
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

data class SamsungDevice(
    val name: String,
    val model: String?,
    val power: TvPower?,
    val tokenAuth: Boolean?,
    val frame: Boolean,
    val mac: String?,
    val id: String?,
    val isTv: Boolean,
) {
    companion object {
        fun parse(text: String): SamsungDevice? {
            val root = jsonOrNull(text) ?: return null
            val device = root.optJSONObject("device") ?: return null
            fun flag(name: String) = when (device.optString(name)) {
                "true" -> true
                "false" -> false
                else -> null
            }
            val type = device.optString("type").ifEmpty { root.optString("type") }
            return SamsungDevice(
                name = device.optString("name").ifEmpty { root.optString("name") }.removePrefix("[TV]").trim().ifEmpty { "Samsung TV" },
                model = device.optString("modelName").ifEmpty { null },
                power = when (device.optString("PowerState")) {
                    "on" -> TvPower.ON
                    "standby" -> TvPower.STANDBY
                    else -> null
                },
                tokenAuth = flag("TokenAuthSupport"),
                frame = flag("FrameTVSupport") == true,
                mac = device.optString("wifiMac").ifEmpty { null },
                id = device.optString("duid").ifEmpty { device.optString("id") }.ifEmpty { root.optString("id") }
                    .removePrefix("uuid:").ifEmpty { null },
                isTv = type.contains("TV", ignoreCase = true),
            )
        }
    }
}

object SamsungApps {
    const val YOUTUBE = "111299001912"

    const val DEEP_LINK_TYPE = 2
    const val NATIVE_TYPE = 4

    val youTubeIds = setOf(YOUTUBE, "9Ur5IzDKqV.TizenYouTube")

    val wellKnown = listOf(
        YOUTUBE,
        "3201907018807", "11101200001",
        "3201910019365", "3201512006785",
        "3201901017640", "3202009021709", "3202204027038",
        "3201807016597",
        "3201606009684",
        "3201512006963",
        "3202301029760",
        "3202208028071",
        "3202010022079", "3201907018784",
    )

    fun actionType(type: Int?) = if (type == NATIVE_TYPE) "NATIVE_LAUNCH" else "DEEP_LINK"
}

class SamsungRest(private val http: OkHttpClient, private val host: String, private val port: Int = 8001) {
    suspend fun device(): SamsungDevice = withContext(Dispatchers.IO) {
        val body = get("/api/v2/") ?: throw SamsungException("$host has no Samsung API")
        SamsungDevice.parse(body) ?: throw SamsungException("$host isn't a Samsung TV")
    }

    suspend fun app(id: String): SamsungApp? = withContext(Dispatchers.IO) {
        val body = get("/api/v2/applications/$id") ?: return@withContext null
        val json = jsonOrNull(body) ?: return@withContext null
        val app = json.optJSONObject("app") ?: json
        val name = app.optString("name").ifEmpty { return@withContext null }
        SamsungApp(app.optString("id").ifEmpty { id }, name, SamsungApps.DEEP_LINK_TYPE)
    }

    private fun get(path: String): String? =
        http.newCall(Request.Builder().url("http://$host:$port$path").build()).execute().use { response ->
            if (response.isSuccessful) response.body.bounded() else null
        }
}

private const val MAX_REPLY_BYTES = 64L * 1024

internal fun ResponseBody.bounded(): String {
    val source = source()
    if (source.request(MAX_REPLY_BYTES + 1)) throw IOException("Reply larger than $MAX_REPLY_BYTES bytes")
    return source.buffer.readUtf8()
}
