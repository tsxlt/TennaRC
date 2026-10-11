package lt.tsx.tennarc.tv.samsung

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

internal class FakeSamsungTv(
    private val onOpen: (WebSocket) -> Unit = { it.send(CONNECT) },
    private val onEmit: (WebSocket, JSONObject) -> Unit = { _, _ -> },
) : WebSocketListener() {
    private val received = LinkedBlockingQueue<JSONObject>()
    private val opened = CountDownLatch(1)
    val closed = CountDownLatch(1)

    @Volatile private var socket: WebSocket? = null

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        opened.countDown()
        onOpen(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = JSONObject(text)
        received.add(message)
        if (message.optString("method") == "ms.channel.emit") onEmit(webSocket, message.getJSONObject("params"))
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        closed.countDown()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed.countDown()

    fun send(text: String) {
        check(opened.await(5, TimeUnit.SECONDS)) { "the app never connected" }
        socket!!.send(text)
    }

    fun next(matches: (JSONObject) -> Boolean = { true }): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            val left = deadline - System.nanoTime()
            val message = received.poll(left.coerceAtLeast(0), TimeUnit.NANOSECONDS) ?: error("the app sent nothing like that")
            if (matches(message)) return message
        }
    }

    fun nextRemote(): JSONObject = next { it.optString("method") == "ms.remote.control" }.getJSONObject("params")

    fun nextEmit(event: String): JSONObject =
        next { it.optString("method") == "ms.channel.emit" && it.getJSONObject("params").optString("event") == event }

    fun drain(): List<JSONObject> = buildList { received.drainTo(this) }

    companion object {
        const val CONNECT = """{"data":{"token":123456789},"event":"ms.channel.connect","from":"host"}"""
        const val CONNECT_WITHOUT_TOKEN = """{"data":{},"event":"ms.channel.connect","from":"host"}"""
        const val UNAUTHORIZED = """{"event":"ms.channel.unauthorized"}"""

        const val INSTALLED_APPS = """{"event":"ed.installedApp.get","from":"host","data":{"data":[
            {"appId":"111299001912","app_type":2,"icon":"/opt/share/webappservice/apps_icon/FirstScreen/111299001912/250x250.png","is_lock":0,"name":"YouTube"},
            {"appId":"3201608010191","app_type":2,"icon":"/opt/share/webappservice/apps_icon/FirstScreen/3201608010191/250x250.png","is_lock":0,"name":"Deezer"},
            {"appId":"3201606009684","app_type":2,"icon":"/opt/share/webappservice/apps_icon/FirstScreen/3201606009684/250x250.png","is_lock":0,"name":"Spotify - Music and Podcasts"}
        ]}}"""

        fun connect(token: String) = """{"data":{"token":"$token"},"event":"ms.channel.connect","from":"host"}"""

        fun ime(event: String, text: String? = null): String {
            val message = JSONObject().put("event", "ms.remote.$event").put("from", "host")
            if (text != null) message.put("data", base64(text))
            return message.toString()
        }

        fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

        fun sameJson(a: Any?, b: Any?): Boolean = when {
            a is JSONObject && b is JSONObject ->
                a.length() == b.length() && a.keys().asSequence().all { b.has(it) && sameJson(a.get(it), b.get(it)) }
            a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameJson(a.get(it), b.get(it)) }
            else -> a == b
        }

        fun tls(): SSLSocketFactory {
            val password = "fake-tv".toCharArray()
            val keys = KeyStore.getInstance("PKCS12")
            FakeSamsungTv::class.java.getResourceAsStream("fake-tv.p12")!!.use { keys.load(it, password) }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
            return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }.socketFactory
        }
    }
}
