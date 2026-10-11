package lt.tsx.tennarc.tv.webos
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebOsSessionTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val url get() = "ws://${server.hostName}:${server.port}/"

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private class FakeTv(
        val onRegister: (WebSocket, JSONObject) -> Unit = { ws, _ -> ws.send(registered("key-1")) },
        val onRequest: (WebSocket, JSONObject) -> Unit = { ws, msg -> ws.send(reply(msg, JSONObject().put("returnValue", true))) },
    ) : WebSocketListener() {
        val received = LinkedBlockingQueue<JSONObject>()

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            received.add(message)
            if (message.getString("type") == "register") onRegister(webSocket, message) else onRequest(webSocket, message)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        fun next(): JSONObject = received.poll(5, TimeUnit.SECONDS) ?: error("the app sent nothing")
    }

    private fun serve(tv: FakeTv) = server.enqueue(MockResponse.Builder().webSocketUpgrade(tv).build())

    @Test
    fun `a stored key registers without a prompt`() = runBlocking {
        val tv = FakeTv().also(::serve)
        var prompted = false
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC") { prompted = true }

        assertEquals("key-1", session.key)
        assertFalse(prompted)
        val payload = tv.next().getJSONObject("payload")
        assertEquals("key-1", payload.getString("client-key"))
        assertEquals("PROMPT", payload.getString("pairingType"))
        assertEquals("Tenna RC", payload.getJSONObject("manifest").getString("deviceName"))
        assertEquals("com.lge.test", payload.getJSONObject("manifest").getJSONObject("signed").getString("appId"))
        session.close()
    }

    @Test
    fun `a new phone sees the prompt and then gets a key`() = runBlocking {
        val tv = FakeTv(onRegister = { ws, _ ->
            ws.send("""{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT","returnValue":true}}""")
            ws.send(registered("fresh-key"))
        }).also(::serve)
        var prompted = false
        val session = WebOsSession.open(http, url, null, "Tenna RC") { prompted = true }

        assertTrue(prompted)
        assertEquals("fresh-key", session.key)
        assertFalse(tv.next().getJSONObject("payload").has("client-key"))
        session.close()
    }

    @Test
    fun `a declined prompt is reported as such`() = runBlocking {
        serve(FakeTv(onRegister = { ws, _ ->
            ws.send("""{"type":"error","id":"register_0","error":"403 User denied access","payload":{}}""")
        }))
        try {
            WebOsSession.open(http, url, null, "Tenna RC")
            fail("expected the pairing to be declined")
        } catch (e: PairingDeclinedException) {
            assertEquals("403 User denied access", e.message)
        }
    }

    @Test
    fun `webOS 26 rejecting the signed manifest gets the unsigned one`() = runBlocking {
        val register: (WebSocket, JSONObject) -> Unit = { ws, msg ->
            val manifest = msg.getJSONObject("payload").getJSONObject("manifest")
            if (manifest.has("signatures")) {
                ws.send("""{"type":"error","id":"register_0","error":"403 Pairing rejected: blacklisted certificate detected","payload":{}}""")
            } else {
                ws.send(registered("unsigned-key"))
            }
        }
        serve(FakeTv(onRegister = register))
        serve(FakeTv(onRegister = register))
        val session = WebOsSession.open(http, url, null, "Tenna RC")
        assertEquals("unsigned-key", session.key)
        session.close()
    }

    @Test
    fun `a forgotten key fails instead of prompting when prompts aren't allowed`() = runBlocking {
        serve(FakeTv(onRegister = { ws, _ ->
            ws.send("""{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT","returnValue":true}}""")
        }))
        var prompted = false
        try {
            WebOsSession.open(http, url, "stale-key", "Tenna RC", allowPrompt = false) { prompted = true }
            fail("expected the stale key to be reported")
        } catch (_: PairingForgottenException) {
            assertFalse(prompted)
        }
    }

    @Test
    fun `requests get their own replies`() = runBlocking {
        val tv = FakeTv(onRequest = { ws, msg ->
            val volume = if (msg.getString("uri").endsWith("getVolume")) 14 else 0
            ws.send(reply(msg, JSONObject().put("returnValue", true).put("volume", volume)))
        }).also(::serve)
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC")
        tv.next()

        val payload = session.request("ssap://audio/getVolume")
        assertEquals(14, payload.getInt("volume"))
        assertEquals("ssap://audio/getVolume", tv.next().getString("uri"))
        session.close()
    }

    @Test
    fun `returnValue false fails the request`() = runBlocking {
        serve(FakeTv(onRequest = { ws, msg ->
            ws.send(reply(msg, JSONObject().put("returnValue", false).put("errorText", "app not found")))
        }))
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC")
        try {
            session.request("ssap://system.launcher/launch", JSONObject().put("id", "nope"))
            fail("expected the launch to fail")
        } catch (e: WebOsException) {
            assertEquals("app not found", e.message)
        }
        session.close()
    }

    @Test
    fun `subscriptions deliver every update`() = runBlocking {
        serve(FakeTv(onRequest = { ws, msg ->
            if (msg.getString("type") == "subscribe") {
                listOf("youtube.leanback.v4", "netflix").forEach { app ->
                    ws.send(reply(msg, JSONObject().put("returnValue", true).put("appId", app)))
                }
            }
        }))
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC")
        val apps = withTimeout(5_000) {
            session.subscribe("ssap://com.webos.applicationManager/getForegroundAppInfo").take(2).toList()
        }
        assertEquals(listOf("youtube.leanback.v4", "netflix"), apps.map { it.getString("appId") })
        session.close()
    }

    @Test
    fun `buttons and pointer moves go over the pointer socket`() = runBlocking {
        val pointerServer = MockWebServer().apply { start() }
        val frames = LinkedBlockingQueue<String>()
        pointerServer.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    frames.add(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }).build(),
        )
        serve(FakeTv(onRequest = { ws, msg ->
            val path = "ws://${pointerServer.hostName}:${pointerServer.port}/resources/pointer"
            ws.send(reply(msg, JSONObject().put("returnValue", true).put("socketPath", path)))
        }))
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC")

        session.pointer().button("HOME")
        session.pointer().move(12, -3)
        session.pointer().click()

        assertEquals("type:button\nname:HOME\n\n", frames.poll(5, TimeUnit.SECONDS))
        assertEquals("type:move\ndx:12\ndy:-3\ndown:0\n\n", frames.poll(5, TimeUnit.SECONDS))
        assertEquals("type:click\n\n", frames.poll(5, TimeUnit.SECONDS))
        session.close()
        pointerServer.close()
    }

    @Test
    fun `a dropped connection fails what's still waiting`() = runBlocking {
        serve(FakeTv(onRequest = { ws, _ -> ws.close(1001, "going away") }))
        val session = WebOsSession.open(http, url, "key-1", "Tenna RC")
        try {
            session.request("ssap://audio/volumeUp")
            fail("expected the request to fail")
        } catch (_: WebOsException) {
        }
        withTimeout(5_000) { session.awaitClosed() }
        assertFalse(session.isOpen)
    }

    private companion object {
        fun registered(key: String) = """{"type":"registered","id":"register_0","payload":{"client-key":"$key"}}"""

        fun reply(request: JSONObject, payload: JSONObject) =
            JSONObject().put("type", "response").put("id", request.getString("id")).put("payload", payload).toString()
    }
}
