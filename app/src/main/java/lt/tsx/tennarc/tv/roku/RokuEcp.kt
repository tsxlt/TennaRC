package lt.tsx.tennarc.tv.roku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.tv.boundedString
import lt.tsx.tennarc.tv.parseXml
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.w3c.dom.Element
import java.io.IOException

open class EcpException(val code: Int, message: String) : IOException(message)

class EcpRefusedException(message: String) : EcpException(403, message)

data class RokuDeviceInfo(
    val name: String,
    val power: TvPower?,
    val isTv: Boolean,
    val hasTuner: Boolean,
    val controlsPower: Boolean,
    val macs: List<String>,
    val ecpMode: String?,
    val model: String? = null,
) {
    val restricted: Boolean get() = ecpMode == "limited" || ecpMode == "disabled"
}

class RokuEcp(private val http: OkHttpClient, host: String, port: Int = PORT) {
    private val base: HttpUrl by lazy { HttpUrl.Builder().scheme("http").host(host).port(port).build() }

    suspend fun deviceInfo(): RokuDeviceInfo = parseDeviceInfo(get("query/device-info"))

    suspend fun apps(): List<TvApp> = parseApps(get("query/apps")).map { (id, title) -> TvApp(id, title, iconUrl(id)) }

    suspend fun activeApp(): String? = parseActiveApp(get("query/active-app"))

    suspend fun channels(): List<TvChannel> = parseChannels(get("query/tv-channels"))

    suspend fun activeChannel(): String? = parseActiveChannel(get("query/tv-active-channel"))

    suspend fun playing(): Boolean? = parsePlaying(get("query/media-player"))

    suspend fun keypress(key: String) = post(base.newBuilder().addPathSegment("keypress").addEncodedPathSegment(key).build())

    suspend fun launch(appId: String, params: List<Pair<String, String>> = emptyList()) {
        val url = base.newBuilder().addPathSegment("launch").addPathSegment(appId)
        params.forEach { (name, value) -> url.addQueryParameter(name, value) }
        post(url.build())
    }

    fun iconUrl(appId: String): String =
        base.newBuilder().addPathSegments("query/icon").addPathSegment(appId).build().toString()

    private suspend fun get(path: String): String =
        call(Request.Builder().url(base.newBuilder().addPathSegments(path).build()).build()) { it.boundedString() }

    private suspend fun post(url: HttpUrl) = call(Request.Builder().url(url).post(EMPTY).build()) { }

    private suspend fun <T> call(request: Request, read: (ResponseBody) -> T): T = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            when {
                response.code == 403 -> throw EcpRefusedException("${request.url.encodedPath} refused")
                !response.isSuccessful -> throw EcpException(response.code, "HTTP ${response.code} from ${request.url.encodedPath}")
                else -> read(response.body)
            }
        }
    }

    companion object {
        const val PORT = 8060
        private val EMPTY = ByteArray(0).toRequestBody()
    }
}

internal fun rokuPower(mode: String): TvPower? = when (mode) {
    "" -> null
    "PowerOn" -> TvPower.ON
    else -> TvPower.STANDBY
}

internal fun parseDeviceInfo(xml: String): RokuDeviceInfo {
    val root = parseXml(xml).documentElement
    if (root.tagName != "device-info") throw IOException("Not a Roku's device-info: <${root.tagName}>")
    fun field(name: String) = root.child(name).orEmpty()
    fun flag(name: String) = field(name) == "true"
    val name = listOf("user-device-name", "friendly-device-name", "default-device-name", "friendly-model-name", "model-name")
        .firstNotNullOfOrNull { field(it).ifEmpty { null } }
        ?: "Roku"
    val wifi = field("wifi-mac")
    val ethernet = field("ethernet-mac")
    val interfaces = if (field("network-type") == "ethernet") listOf(ethernet, wifi) else listOf(wifi, ethernet)
    return RokuDeviceInfo(
        name = name,
        power = rokuPower(field("power-mode")),
        isTv = flag("is-tv"),
        hasTuner = flag("is-tv") && field("tuner-type").isNotEmpty(),
        controlsPower = flag("is-tv") || flag("supports-tv-power-control"),
        macs = interfaces.filter(WakeOnLan::isValidMac),
        ecpMode = field("ecp-setting-mode").lowercase().ifEmpty { null },
        model = field("friendly-model-name").ifEmpty { field("model-name") }.ifEmpty { null },
    )
}

internal fun parseApps(xml: String): List<Pair<String, String>> =
    parseXml(xml).documentElement.children("app").mapNotNull { app ->
        val id = app.getAttribute("id").ifEmpty { return@mapNotNull null }
        id to app.textContent.trim().ifEmpty { id }
    }

internal fun parseActiveApp(xml: String): String? =
    parseXml(xml).documentElement.children("app").firstOrNull()?.getAttribute("id")?.ifEmpty { null }

internal fun parsePlaying(xml: String): Boolean? = when (parseXml(xml).documentElement.getAttribute("state")) {
    "play" -> true
    "pause" -> false
    else -> null
}

private fun Element.children(name: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == name }

private fun Element.child(name: String): String? = children(name).firstOrNull()?.textContent?.trim()

internal fun parseChannels(xml: String): List<TvChannel> =
    parseXml(xml).documentElement.children("channel").mapNotNull { channel ->
        val number = channel.child("number")?.ifEmpty { null } ?: return@mapNotNull null
        if (channel.child("user-hidden") == "true") return@mapNotNull null
        TvChannel(number, number, channel.child("name").orEmpty())
    }.distinctBy { it.id }

internal fun parseActiveChannel(xml: String): String? =
    parseXml(xml).documentElement.children("channel").firstOrNull()?.child("number")?.ifEmpty { null }
