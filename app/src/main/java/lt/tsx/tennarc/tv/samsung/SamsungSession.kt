package lt.tsx.tennarc.tv.samsung

import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.logged
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

open class SamsungException(message: String, cause: Throwable? = null) : IOException(message, cause)

class SamsungDeclinedException : SamsungException("The TV refused this phone")

class SamsungPromptTimeoutException : SamsungException("Nobody answered the prompt on the TV")

class SamsungForgottenException : SamsungException("The TV didn't take this phone's token")

private class ChannelTimeoutException : SamsungException("The TV timed the connection out")

data class SamsungApp(val id: String, val name: String, val type: Int)

class SamsungSession private constructor(private val timing: Timing) : WebSocketListener() {
    @Volatile private var socket: WebSocket? = null
    private val connected = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val imeLock = Any()
    private val mutableKeyboard = MutableStateFlow<String?>(null)
    private val sentTexts = ArrayDeque<String>()
    private var textAnnounced = false

    @Volatile var token: String? = null
        private set

    val keyboard: StateFlow<String?> = mutableKeyboard.asStateFlow()

    val isOpen: Boolean get() = !closed.isCompleted

    suspend fun awaitClosed() = closed.await()

    fun key(code: String, cmd: String = "Click") = remote(
        JSONObject().put("Cmd", cmd).put("DataOfCmd", code).put("Option", "false").put("TypeOfRemote", "SendRemoteKey"),
    )

    fun type(edit: TextEdit) = synchronized(imeLock) {
        val text = mutableKeyboard.value.orEmpty().dropLast(edit.delete) + edit.insert
        if (!textAnnounced) {
            emit("custom.remote.textReceived", to = "broadcast")
            textAnnounced = true
        }
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
        remote(JSONObject().put("Cmd", encoded).put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString"))
        sentTexts.addLast(text)
        while (sentTexts.size > MAX_SENT_TEXTS) sentTexts.removeFirst()
        if (mutableKeyboard.value != null) mutableKeyboard.value = text
    }

    fun endText() = synchronized(imeLock) {
        remote(JSONObject().put("TypeOfRemote", "SendInputEnd"))
        textAnnounced = false
    }

    fun launch(appId: String, actionType: String, metaTag: String = "") = emit(
        "ed.apps.launch",
        data = JSONObject().put("action_type", actionType).put("appId", appId).put("metaTag", metaTag),
    )

    suspend fun installedApps(): List<SamsungApp>? {
        val reply = CompletableDeferred<JSONObject>()
        waiting.put(INSTALLED_APPS, reply)?.cancel()
        try {
            emit(INSTALLED_APPS)
            val message = withTimeoutOrNull(timing.appsMs) { reply.await() } ?: return null
            return parseInstalledApps(message)
        } finally {
            waiting.remove(INSTALLED_APPS, reply)
        }
    }

    fun close() {
        socket?.close(1000, null)
        shutDown(SamsungException("Closed"))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = catching { JSONObject(text) }.logged("SamsungSession.onMessage").getOrNull() ?: return
        when (val event = message.optString("event")) {
            "ms.channel.connect" -> {
                token = message.optJSONObject("data")?.optString("token")?.ifEmpty { null }
                connected.complete(Unit)
            }
            "ms.channel.unauthorized" -> connected.completeExceptionally(SamsungDeclinedException())
            "ms.channel.timeOut" -> connected.completeExceptionally(ChannelTimeoutException())
            "ms.error" -> connected.completeExceptionally(
                SamsungException(message.optJSONObject("data")?.optString("message")?.ifEmpty { null } ?: "The TV refused"),
            )
            "ms.remote.imeStart" -> synchronized(imeLock) {
                sentTexts.clear()
                textAnnounced = false
                mutableKeyboard.value = decodeText(message.opt("data")).orEmpty()
            }
            "ms.remote.imeUpdate" -> synchronized(imeLock) {
                val text = decodeText(message.opt("data")) ?: return
                val echo = sentTexts.indexOf(text)
                if (echo >= 0) repeat(echo + 1) { sentTexts.removeFirst() } else mutableKeyboard.value = text
            }
            "ms.remote.imeEnd" -> synchronized(imeLock) {
                sentTexts.clear()
                textAnnounced = false
                mutableKeyboard.value = null
            }
            else -> waiting.remove(event)?.complete(message)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        shutDown(SamsungException("The TV closed the connection"))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
        shutDown(SamsungException("The TV closed the connection"))

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
        shutDown(t as? IOException ?: SamsungException("Connection failed", t))

    private fun shutDown(cause: IOException) {
        connected.completeExceptionally(cause)
        waiting.values.forEach { it.completeExceptionally(cause) }
        mutableKeyboard.value = null
        closed.complete(Unit)
    }

    private fun emit(event: String, to: String = "host", data: JSONObject? = null) {
        val params = JSONObject().put("event", event).put("to", to)
        if (data != null) params.put("data", data)
        send(JSONObject().put("method", "ms.channel.emit").put("params", params))
    }

    private fun remote(params: JSONObject) = send(JSONObject().put("method", "ms.remote.control").put("params", params))

    private fun send(message: JSONObject) {
        if (socket?.send(message.toString()) != true) throw SamsungException("Not connected")
    }

    data class Timing(
        val promptAfterMs: Long = 1_500,
        val answerMs: Long = 6_000,
        val pairingMs: Long = 90_000,
        val appsMs: Long = 4_000,
    )

    companion object {
        private const val INSTALLED_APPS = "ed.installedApp.get"
        private const val MAX_SENT_TEXTS = 32

        suspend fun open(
            http: OkHttpClient,
            url: String,
            allowPrompt: Boolean,
            onPrompt: () -> Unit = {},
            timing: Timing = Timing(),
        ): SamsungSession {
            val session = SamsungSession(timing)
            session.socket = http.newWebSocket(Request.Builder().url(url).build(), session)
            try {
                val answered = withTimeoutOrNull(timing.promptAfterMs) { session.connected.await() } != null
                when {
                    answered -> Unit
                    !allowPrompt -> withTimeoutOrNull(timing.answerMs - timing.promptAfterMs) { session.connected.await() }
                        ?: throw SamsungForgottenException()
                    else -> {
                        onPrompt()
                        try {
                            withTimeoutOrNull(timing.pairingMs) { session.connected.await() } ?: throw SamsungPromptTimeoutException()
                        } catch (_: ChannelTimeoutException) {
                            throw SamsungPromptTimeoutException()
                        }
                    }
                }
                return session
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }

        internal fun parseInstalledApps(message: JSONObject): List<SamsungApp> {
            val list = message.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
            return (0 until list.length()).mapNotNull { index ->
                val app = list.optJSONObject(index) ?: return@mapNotNull null
                val id = app.optString("appId").ifEmpty { return@mapNotNull null }
                SamsungApp(id, app.optString("name").ifEmpty { id }, app.optInt("app_type", SamsungApps.DEEP_LINK_TYPE))
            }
        }

        private fun decodeText(data: Any?): String? {
            val encoded = data as? String ?: return null
            return try {
                String(Base64.getDecoder().decode(encoded))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
