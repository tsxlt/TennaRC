package lt.tsx.tennarc.tv.webos
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.Connection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

open class WebOsException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PairingDeclinedException(message: String) : WebOsException(message)

class PairingTimeoutException : WebOsException("Nobody answered the prompt on the TV")

class PairingForgottenException : WebOsException("The TV has forgotten this phone")

class WebOsSession private constructor(
    private val http: OkHttpClient,
    private val clientKey: String?,
    private val deviceName: String,
    val signed: Boolean,
    private val onPairingPrompt: () -> Unit,
) : WebSocketListener() {

    @Volatile private var socket: WebSocket? = null
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val subscriptions = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val registration = CompletableDeferred<String>()
    private val closed = CompletableDeferred<Unit>()
    private val pointerLock = Mutex()
    @Volatile private var pointer: PointerSocket? = null

    lateinit var key: String
        private set

    val isOpen: Boolean get() = !closed.isCompleted

    suspend fun awaitClosed() = closed.await()

    suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject {
        val id = "r${ids.getAndIncrement()}"
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = reply
        try {
            if (socket?.send(frame("request", id, uri, payload)) != true) throw WebOsException("Not connected")
            return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { reply.await() }
                ?: throw WebOsException("$uri timed out")
        } finally {
            pending.remove(id)
        }
    }

    fun subscribe(uri: String, payload: JSONObject = JSONObject()): Flow<JSONObject> = callbackFlow {
        val id = "s${ids.getAndIncrement()}"
        subscriptions[id] = { message ->
            if (message.optString("type") == "error") {
                close(WebOsException(message.optString("error").ifEmpty { "$uri refused" }))
            } else {
                trySend(message.optJSONObject("payload") ?: JSONObject())
            }
        }
        val onClosed = closed.invokeOnCompletion { close() }
        if (socket?.send(frame("subscribe", id, uri, payload)) != true) close(WebOsException("Not connected"))
        awaitClose {
            subscriptions.remove(id)
            onClosed.dispose()
            if (isOpen) socket?.send(frame("unsubscribe", id, uri, JSONObject()))
        }
    }

    suspend fun pointer(): PointerSocket = pointerLock.withLock {
        pointer?.takeIf { it.isOpen }?.let { return it }
        val path = request(POINTER_SOCKET).optString("socketPath")
        if (path.isEmpty()) throw WebOsException("The TV gave no pointer socket")
        PointerSocket.open(http, path).also { pointer = it }
    }

    fun close() {
        socket?.close(1000, null)
        shutDown(WebOsException("Closed"))
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", WebOsManifest.json(deviceName, signed))
        clientKey?.let { payload.put("client-key", it) }
        webSocket.send(JSONObject().put("type", "register").put("id", REGISTER_ID).put("payload", payload).toString())
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = catching { JSONObject(text) }.logged("WebOsSession.onMessage").getOrNull() ?: return
        val id = message.optString("id")
        val type = message.optString("type")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        if (id == REGISTER_ID) {
            when (type) {
                "response" -> if (payload.optString("pairingType") == "PROMPT") onPairingPrompt()
                "registered" -> {
                    val key = payload.optString("client-key")
                    if (key.isNotEmpty()) registration.complete(key)
                    else registration.completeExceptionally(WebOsException("The TV sent no client key"))
                }
                "error" -> registration.completeExceptionally(
                    PairingDeclinedException(message.optString("error").ifEmpty { "Pairing refused" }),
                )
            }
            return
        }

        subscriptions[id]?.let { deliver ->
            deliver(message)
            return
        }

        val reply = pending[id] ?: return
        when {
            type == "error" ->
                reply.completeExceptionally(WebOsException(message.optString("error").ifEmpty { "Request refused" }))
            payload.has("returnValue") && !payload.optBoolean("returnValue") ->
                reply.completeExceptionally(WebOsException(payload.optString("errorText").ifEmpty { "Request failed" }))
            else -> reply.complete(payload)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        shutDown(WebOsException("The TV closed the connection"))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
        shutDown(WebOsException("The TV closed the connection"))

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
        shutDown(t as? IOException ?: WebOsException("Connection failed", t))

    private fun shutDown(cause: IOException) {
        registration.completeExceptionally(cause)
        pending.values.forEach { it.completeExceptionally(cause) }
        pointer?.close()
        closed.complete(Unit)
    }

    private fun frame(type: String, id: String, uri: String, payload: JSONObject) =
        JSONObject().put("type", type).put("id", id).put("uri", uri).put("payload", payload).toString()

    companion object {
        const val REGISTER_ID = "register_0"
        private const val POINTER_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket"
        private const val REQUEST_TIMEOUT_MS = 8_000L

        private const val PAIRING_TIMEOUT_MS = 90_000L
        private const val REGISTER_TIMEOUT_MS = 10_000L

        suspend fun open(
            http: OkHttpClient,
            url: String,
            clientKey: String?,
            deviceName: String,
            allowPrompt: Boolean = true,
            onPairingPrompt: () -> Unit = {},
        ): WebOsSession = try {
            openOnce(http, url, clientKey, deviceName, signed = true, allowPrompt, onPairingPrompt)
        } catch (e: PairingDeclinedException) {
            if (e.message?.contains("blacklist", ignoreCase = true) != true) throw e
            openOnce(http, url, clientKey, deviceName, signed = false, allowPrompt, onPairingPrompt)
        }

        private suspend fun openOnce(
            http: OkHttpClient,
            url: String,
            clientKey: String?,
            deviceName: String,
            signed: Boolean,
            allowPrompt: Boolean,
            onPairingPrompt: () -> Unit,
        ): WebOsSession {
            lateinit var session: WebOsSession
            val prompted = AtomicBoolean(false)
            session = WebOsSession(http, clientKey, deviceName, signed) {
                prompted.set(true)
                if (allowPrompt) onPairingPrompt() else session.registration.completeExceptionally(PairingForgottenException())
            }
            session.socket = http.newWebSocket(Request.Builder().url(url).build(), session)
            try {
                session.key = withTimeoutOrNull(REGISTER_TIMEOUT_MS) { session.registration.await() }
                    ?: if (!prompted.get()) {
                        throw WebOsException("The TV didn't answer the registration")
                    } else {
                        withTimeoutOrNull(PAIRING_TIMEOUT_MS - REGISTER_TIMEOUT_MS) { session.registration.await() }
                            ?: throw PairingTimeoutException()
                    }
                return session
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
    }
}

class PointerSocket private constructor() : WebSocketListener() {
    private val opened = CompletableDeferred<Unit>()
    @Volatile private var socket: WebSocket? = null

    @Volatile var isOpen = false
        private set

    val backlog: Long get() = socket?.queueSize() ?: 0L

    fun button(name: String) = send("type:button\nname:$name\n\n")
    fun move(dx: Int, dy: Int) = send("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    fun click() = send("type:click\n\n")
    fun scroll(dx: Int, dy: Int) = send("type:scroll\ndx:$dx\ndy:$dy\n\n")

    fun close() {
        isOpen = false
        socket?.close(1000, null)
    }

    private fun send(frame: String) {
        if (socket?.send(frame) != true) throw WebOsException("Pointer socket closed")
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        isOpen = true
        opened.complete(Unit)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        isOpen = false
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isOpen = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isOpen = false
        opened.completeExceptionally(t)
    }

    companion object {
        suspend fun open(http: OkHttpClient, url: String): PointerSocket {
            val pointer = PointerSocket()
            val socket = http.newWebSocket(Request.Builder().url(url).build(), pointer)
            try {
                withTimeoutOrNull(5_000) { pointer.opened.await() }
                    ?: throw WebOsException("The pointer socket did not open")
            } catch (e: Throwable) {
                socket.cancel()
                throw e
            }
            return pointer
        }
    }
}
