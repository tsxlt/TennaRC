package lt.tsx.tennarc.tv.philips

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.jsonOrNull
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.TvHttp
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class JointSpacePorts(val secure: Int = 1926, val plain: Int = 1925)

data class JointSpaceSystem(
    val name: String?,
    val apiVersion: Int,
    val osType: String?,
    val ambilight: Boolean,
    val needsPairing: Boolean,
    val secure: Boolean,
    val featuring: String?,
) {
    companion object {
        fun parse(text: String): JointSpaceSystem? {
            val root = jsonOrNull(text) ?: return null
            val version = root.optJSONObject("api_version")
            val featuring = root.optJSONObject("featuring")
            if (version == null && featuring == null) return null
            val json = featuring?.optJSONObject("jsonfeatures")
            val system = featuring?.optJSONObject("systemfeatures")
            return JointSpaceSystem(
                name = root.optString("name").ifBlank { null },
                apiVersion = version?.optInt("Major")?.takeIf { it > 0 } ?: PROBE_VERSION,
                osType = root.optString("os_type").ifEmpty { system?.optString("os_type").orEmpty() }.ifEmpty { null },
                ambilight = json?.optJSONArray("ambilight")?.contains("Ambilight") == true,
                needsPairing = system?.optString("pairing_type") == "digest_auth_pairing",
                secure = system?.optString("secured_transport") == "true",
                featuring = featuring?.toString(),
            )
        }
    }
}

data class AmbilightConfig(val styleName: String, val menuSetting: String?, val expert: Boolean = false)

data class AmbilightReading(val on: Boolean?, val config: AmbilightConfig?)

data class JointSpaceLogin(val user: String, val password: String) {
    fun encode(): String = "$user $password"

    companion object {
        fun decode(text: String?): JointSpaceLogin? {
            val parts = text?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
            return if (parts.size == 2) JointSpaceLogin(parts[0], parts[1]) else null
        }
    }
}

data class JointSpaceDevice(val id: String, val name: String)

data class PairingOffer(val authKey: String, val timestamp: Long, val timeoutSeconds: Int)

class JointSpaceUnauthorized : IOException("The TV refused the JointSPACE login")

class JointSpacePairingRefused(reason: String) : IOException("The TV won't pair: $reason")

class JointSpace(
    private val http: OkHttpClient,
    val base: HttpUrl,
    val system: JointSpaceSystem? = null,
    login: JointSpaceLogin? = null,
) {
    private var auth: DigestAuth? = login?.let { DigestAuth(it.user, it.password) }

    var login: JointSpaceLogin? = login
        set(value) {
            field = value
            auth = value?.let { DigestAuth(it.user, it.password) }
        }

    suspend fun system(): JointSpaceSystem? = io {
        val reply = call("system")
        if (reply.ok) JointSpaceSystem.parse(reply.body) else null
    }

    suspend fun ambilight(): AmbilightReading = io {
        val power = call("ambilight/power").checked()
        val config = call("ambilight/currentconfiguration").checked()
        AmbilightReading(power.takeIf { it.ok }?.let { parseAmbilightPower(it.body) }, config.takeIf { it.ok }?.let { parseAmbilightConfig(it.body) })
    }

    suspend fun setAmbilightPower(on: Boolean): Boolean = post("ambilight/power", JSONObject().put("power", if (on) "On" else "Off"))

    suspend fun setAmbilightConfig(config: AmbilightConfig): Boolean = post(
        "ambilight/currentconfiguration",
        JSONObject().put("styleName", config.styleName).put("isExpert", config.expert).apply {
            config.menuSetting?.let { put("menuSetting", it) }
        },
    )

    suspend fun pairRequest(device: JointSpaceDevice): PairingOffer = io {
        val access = JSONObject().put("scope", JSONArray(listOf("read", "write", "control")))
        system?.featuring?.let { featuring -> jsonOrNull(featuring)?.let { access.put("featuring", it) } }
        val reply = call("pair/request", JSONObject().put("access", access).put("device", device.json()), auth = null)
        val json = jsonOrNull(reply.body)
        val error = json?.optString("error_id").orEmpty()
        if (json == null || error != "SUCCESS") throw JointSpacePairingRefused(error.ifEmpty { "HTTP ${reply.code}" })
        PairingOffer(
            authKey = json.optString("auth_key").ifEmpty { throw JointSpacePairingRefused("no auth_key") },
            timestamp = json.optLong("timestamp"),
            timeoutSeconds = json.optInt("timeout", DEFAULT_PIN_TIMEOUT_S),
        )
    }

    suspend fun pairGrant(device: JointSpaceDevice, offer: PairingOffer, pin: String): JointSpaceLogin? = io {
        val login = JointSpaceLogin(device.id, offer.authKey)
        val auth = JSONObject()
            .put("auth_appId", "1")
            .put("auth_timestamp", offer.timestamp)
            .put("auth_signature", pairingSignature(offer.timestamp, pin))
            .put("pin", pin)
        val reply = call("pair/grant", JSONObject().put("auth", auth).put("device", device.json()), DigestAuth(login.user, login.password))
        val error = jsonOrNull(reply.body)?.optString("error_id")
        login.takeIf { reply.ok && error == "SUCCESS" }
    }

    private suspend fun post(path: String, body: JSONObject): Boolean = io { call(path, body).checked().ok }

    private class Reply(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    private fun Reply.checked(): Reply = if (code == 401) throw JointSpaceUnauthorized() else this

    private fun call(path: String, body: JSONObject? = null, auth: DigestAuth? = this.auth): Reply {
        val url = base.resolve(path) ?: throw IOException("No JointSPACE path $path")
        val method = if (body == null) "GET" else "POST"
        val uri = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        fun send() = http.newCall(
            Request.Builder().url(url).apply {
                if (body != null) post(body.toString().toRequestBody(JSON))
                auth?.header(method, uri)?.let { header("Authorization", it) }
            }.build(),
        ).execute()

        send().use { response ->
            val challenge = response.headers("WWW-Authenticate").firstNotNullOfOrNull(DigestChallenge::parse)
            if (response.code != 401 || auth == null || challenge == null) return Reply(response.code, response.body.bounded())
            auth.answer(challenge)
        }
        return send().use { Reply(it.code, it.body.bounded()) }
    }

    companion object {
        suspend fun probe(http: OkHttpClient, host: String, ports: JointSpacePorts = JointSpacePorts(), login: JointSpaceLogin? = null): JointSpace? {
            for ((scheme, port) in listOf("https" to ports.secure, "http" to ports.plain)) {
                val system = try {
                    JointSpace(http, baseUrl(scheme, host, port, PROBE_VERSION), login = login).system()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logFailure("JointSpace.probe", e)
                    null
                } ?: continue
                val secure = scheme == "https" || system.secure
                val base = if (secure) baseUrl("https", host, ports.secure, system.apiVersion) else baseUrl("http", host, port, system.apiVersion)
                return JointSpace(http, base, system, login)
            }
            return null
        }

        fun client(): OkHttpClient = TvHttp.tvClient().newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .pingInterval(0, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        internal fun baseUrl(scheme: String, host: String, port: Int, version: Int): HttpUrl =
            HttpUrl.Builder().scheme(scheme).host(host).port(port).addPathSegment(version.toString()).addPathSegment("").build()
    }
}

internal fun ambilightStyle(config: AmbilightConfig?): AmbilightStyle? = when (config?.styleName) {
    "FOLLOW_VIDEO" -> AmbilightStyle.FOLLOW_VIDEO
    "FOLLOW_AUDIO" -> AmbilightStyle.FOLLOW_AUDIO
    "Lounge light", "FOLLOW_COLOR" -> AmbilightStyle.LOUNGE
    "OFF" -> AmbilightStyle.OFF
    else -> null
}

internal fun ambilightConfigs(style: AmbilightStyle): List<AmbilightConfig> = when (style) {
    AmbilightStyle.FOLLOW_VIDEO -> listOf(AmbilightConfig("FOLLOW_VIDEO", "STANDARD"))
    AmbilightStyle.FOLLOW_AUDIO -> listOf(AmbilightConfig("FOLLOW_AUDIO", "ENERGY_ADAPTIVE_BRIGHTNESS"))
    AmbilightStyle.LOUNGE -> listOf(AmbilightConfig("Lounge light", "HOT_LAVA"), AmbilightConfig("FOLLOW_COLOR", "HOT_LAVA"))
    AmbilightStyle.OFF -> emptyList()
}

internal fun parseAmbilightPower(text: String): Boolean? = when (jsonOrNull(text)?.optString("power")) {
    "On" -> true
    "Off" -> false
    else -> null
}

internal fun parseAmbilightConfig(text: String): AmbilightConfig? {
    val json = jsonOrNull(text) ?: return null
    val style = json.optString("styleName").ifEmpty { return null }
    return AmbilightConfig(style, json.optString("menuSetting").ifEmpty { null }, json.optBoolean("isExpert"))
}

internal fun pairingSignature(timestamp: Long, pin: String): String {
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(PAIRING_SECRET, "HmacSHA1")) }
    mac.update(timestamp.toString().toByteArray())
    mac.update(pin.toByteArray())
    return Base64.getEncoder().encodeToString(mac.doFinal())
}

private fun JointSpaceDevice.json() = JSONObject()
    .put("device_name", name)
    .put("device_os", "Android")
    .put("app_name", APP_NAME)
    .put("app_id", APP_ID)
    .put("type", "native")
    .put("id", id)

private fun JSONArray.contains(value: String) = (0 until length()).any { optString(it) == value }

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private fun ResponseBody.bounded(): String {
    val source = source()
    if (source.request(MAX_REPLY_BYTES + 1)) throw IOException("Reply larger than $MAX_REPLY_BYTES bytes")
    return source.buffer.readUtf8()
}

private val JSON = "application/json".toMediaType()
private const val MAX_REPLY_BYTES = 64L * 1024
private const val APP_NAME = "Tenna RC"
private const val APP_ID = "lt.tsx.tennarc"
internal const val PROBE_VERSION = 6
private const val DEFAULT_PIN_TIMEOUT_S = 60

private val PAIRING_SECRET: ByteArray =
    Base64.getDecoder().decode("ZmVay1EQVFOaZhwQ4Kv81ypLAZNczV9sG4KkseXWn1NEk6cXmPKO/MCa9sryslvLCFMnNe4Z4CPXzToowvhHvA==")
