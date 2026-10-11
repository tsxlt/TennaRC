package lt.tsx.tennarc.tv.philips

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.json.JSONObject
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class FakePhilipsTv(
    private val system: String = SYSTEM_ANDROID.replace("\"secured_transport\": \"true\", ", ""),
    private val unknownStyles: Set<String> = emptySet(),
) : Dispatcher(), Closeable {
    private val server = MockWebServer().also {
        it.dispatcher = this
        it.start(InetAddress.getLoopbackAddress(), 0)
    }
    val host: String get() = server.hostName
    val port: Int get() = server.port

    val ports = JointSpacePorts(secure = closedPort(), plain = server.port)

    val logins = ConcurrentHashMap<String, String>()

    @Volatile var power = "On"

    @Volatile var config = JSONObject("""{"styleName":"FOLLOW_VIDEO","isExpert":false,"menuSetting":"STANDARD"}""")

    val posts = LinkedBlockingQueue<Pair<String, JSONObject>>()

    val challenges = AtomicInteger()

    @Volatile private var offered: String? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val body = request.body?.utf8()?.takeIf { it.isNotEmpty() }?.let(::JSONObject)
        if (request.method == "POST" && body != null) posts.add(path to body)
        return when (path) {
            "/6/system" -> MockResponse(body = system)
            "/6/pair/request" -> {
                val device = body!!.getJSONObject("device")
                check(body.getJSONObject("access").getJSONArray("scope").length() == 3)
                offered = device.getString("id")
                MockResponse(body = """{"error_id":"SUCCESS","error_text":"Authorization required","auth_key":"$AUTH_KEY","timestamp":$TIMESTAMP,"timeout":60}""")
            }
            "/6/pair/grant" -> {
                val user = offered ?: return MockResponse(code = 400)
                if (!signedBy(request, user, AUTH_KEY)) return challenge()
                val auth = body!!.getJSONObject("auth")
                val right = auth.getString("pin") == PIN && auth.getString("auth_signature") == pairingSignature(TIMESTAMP, PIN)
                if (!right) return MockResponse(body = """{"error_id":"INVALID_PIN","error_text":"Invalid pin"}""")
                logins[user] = AUTH_KEY
                MockResponse(body = """{"error_id":"SUCCESS","error_text":"Pairing completed"}""")
            }
            else -> {
                if (logins.none { (user, password) -> signedBy(request, user, password) }) return challenge()
                when {
                    path == "/6/ambilight/power" && body == null -> MockResponse(body = """{"power":"$power"}""")
                    path == "/6/ambilight/power" -> {
                        power = body!!.getString("power")
                        MockResponse()
                    }
                    path == "/6/ambilight/currentconfiguration" && body == null -> MockResponse(body = config.toString())
                    path == "/6/ambilight/currentconfiguration" -> {
                        if (body!!.getString("styleName") in unknownStyles) return MockResponse(code = 400)
                        config = body
                        MockResponse()
                    }
                    else -> MockResponse(code = 404)
                }
            }
        }
    }

    fun nextPost(): Pair<String, JSONObject> = posts.poll(5, TimeUnit.SECONDS) ?: error("The phone posted nothing")

    override fun close() = server.close()

    private fun challenge(): MockResponse {
        challenges.incrementAndGet()
        return MockResponse(
            code = 401,
            headers = headersOf("WWW-Authenticate", """Digest realm="XTV", qop="auth,auth-int", nonce="$NONCE", opaque="$OPAQUE""""),
        )
    }

    private fun signedBy(request: RecordedRequest, user: String, password: String): Boolean {
        val header = request.headers["Authorization"]?.removePrefix("Digest ") ?: return false
        val params = Regex("""(\w+)=(?:"([^"]*)"|([^\s,]+))""").findAll(header)
            .associate { it.groupValues[1] to it.groups[2]?.value.orEmpty().ifEmpty { it.groupValues[3] } }
        if (params["username"] != user || params["nonce"] != NONCE || params["opaque"] != OPAQUE) return false
        val ha1 = md5("$user:XTV:$password")
        val ha2 = md5("${request.method}:${params["uri"]}")
        val expected = md5("$ha1:$NONCE:${params["nc"]}:${params["cnonce"]}:${params["qop"]}:$ha2")
        return params["uri"] == request.target && params["response"] == expected
    }

    private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val PIN = "4271"
        const val TIMESTAMP = 1696183223L
        const val AUTH_KEY = "2f3c9e1d8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d"
        const val NONCE = "MTY5NjE4MzIyMzo4ZmQ0"
        const val OPAQUE = "c2VjcmV0X29wYXF1ZQ"

        const val SYSTEM_ANDROID = """{
            "notifyChange": "http", "menulanguage": "Swedish", "name": "65OLED855/12", "country": "Sweden",
            "nettvversion": "9.0.0", "epgsource": "broadcast",
            "api_version": {"Major": 6, "Minor": 4, "Patch": 0},
            "featuring": {
                "jsonfeatures": {
                    "editfavorites": ["TVChannels", "SatChannels"], "recordings": ["List", "Schedule", "Manage"],
                    "ambilight": ["LoungeLight", "Hue", "Ambilight", "HueStreaming"], "menuitems": ["Setup_Menu"],
                    "textentry": ["not_available"], "applications": ["TV_Apps", "TV_Games", "TV_Settings"],
                    "pointer": ["not_available"], "inputkey": ["key"], "activities": ["intent"],
                    "channels": ["preset_string"], "mappings": ["server_mapping"]
                },
                "systemfeatures": {
                    "tvtype": "consumer", "content": ["dmr", "pvr"], "tvsearch": "intent",
                    "pairing_type": "digest_auth_pairing", "secured_transport": "true", "companion_screen": "true"
                }
            },
            "os_type": "MSAF_2019_P"
        }"""

        fun closedPort(): Int = ServerSocket(0).use { it.localPort }
    }
}
