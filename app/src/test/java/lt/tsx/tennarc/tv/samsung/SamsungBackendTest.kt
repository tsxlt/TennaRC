package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.samsung.FakeSamsungTv.Companion.base64
import lt.tsx.tennarc.tv.samsung.FakeSamsungTv.Companion.ime
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SamsungBackendTest {
    private val channel = MockWebServer()
    private val lan = MockWebServer()
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val quick = SamsungSession.Timing(promptAfterMs = 300, answerMs = 800, pairingMs = 5_000, appsMs = 300)

    private lateinit var settings: Settings
    private lateinit var backend: SamsungBackend
    private val host get() = lan.hostName

    @Volatile private var tv = FakeSamsungTv()
    @Volatile private var power: String? = "on"
    @Volatile private var dialCode = 201
    private val restApps = ConcurrentHashMap<String, String>()
    private val lanRequests = CopyOnWriteArrayList<Pair<RecordedRequest, String>>()
    private var volume = 17

    @Before
    fun start() {
        channel.useHttps(FakeSamsungTv.tls())
        channel.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().webSocketUpgrade(tv).build()
        }
        channel.start()
        lan.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body?.utf8().orEmpty()
                lanRequests += request to body
                val target = request.target
                return when {
                    target == "/api/v2/" -> power?.let { MockResponse(body = device(it)) } ?: MockResponse(code = 404)
                    target.startsWith("/api/v2/applications/") ->
                        restApps[target.substringAfterLast('/')]?.let { MockResponse(body = it) } ?: MockResponse(code = 404)
                    target == "/ws/app/YouTube" -> MockResponse(code = dialCode)
                    target == RENDERER -> soap(request.headers["SOAPAction"].orEmpty(), body)
                    else -> MockResponse(code = 404)
                }
            }
        }
        lan.start()
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.update { it.copy(kind = TvKind.SAMSUNG, host = host) }
        backend = backend()
    }

    @After
    fun stop() {
        backend.close()
        channel.close()
        lan.close()
        executor.shutdownNow()
    }

    private fun backend(renderer: String? = "http://$host:${lan.port}$RENDERER") = SamsungBackend(
        settings,
        tvHttp = TvHttp.tvClient(),
        lanHttp = OkHttpClient(),
        scope = scope,
        ports = SamsungPorts(secure = channel.port, plain = closedPort(), rest = lan.port, dial = lan.port),
        findRenderer = { renderer },
        timing = quick,
    )

    private fun await(condition: (TvState) -> Boolean): TvState = runBlocking { withTimeout(10_000) { backend.state.first(condition) } }

    private fun connected(): TvState {
        settings.setClientKey(host, "123456789")
        backend.hold()
        return await { it.connection == Connection.Connected }
    }

    @Test
    fun `pairing waits on the TV's prompt and keeps the token it hands out`() {
        tv = FakeSamsungTv(onOpen = {})
        backend.pair()
        await { it.connection == Connection.AwaitingApproval }
        tv.send(FakeSamsungTv.connect("55555555"))
        await { it.connection == Connection.Connected }
        assertEquals("55555555", settings.clientKey(host))
        val request = channel.takeRequest()
        assertNull(request.url.queryParameter("token"))
        assertEquals(base64("Tenna RC"), request.url.queryParameter("name"))
    }

    @Test
    fun `fills in the MAC the TV reports`() {
        connected()
        assertEquals("aa:bb:cc:dd:ee:ff", settings.prefs.value.mac)
    }

    @Test
    fun `keeps a MAC the user typed`() {
        settings.update { it.copy(mac = "02:00:00:00:00:01") }
        connected()
        assertEquals("02:00:00:00:00:01", settings.prefs.value.mac)
    }

    @Test
    fun `a stored token goes with the connection and needs no prompt`() {
        val state = connected()
        assertEquals("123456789", channel.takeRequest().url.queryParameter("token"))
        assertEquals(TvKind.SAMSUNG, state.kind)
        assertEquals(SAMSUNG_FEATURES, state.features)
        assertEquals(TvPower.ON, state.power)
    }

    @Test
    fun `Deny on the TV asks to pair again`() {
        tv = FakeSamsungTv(onOpen = { it.send(FakeSamsungTv.UNAUTHORIZED) })
        backend.pair()
        assertEquals(Connection.NeedsPairing(declined = true), await { it.connection is Connection.NeedsPairing }.connection)
        assertNull(settings.clientKey(host))
    }

    @Test
    fun `without a token nothing connects until asked to pair`() {
        backend.hold()
        assertEquals(Connection.NeedsPairing(), await { it.connection is Connection.NeedsPairing }.connection)
        assertEquals(0, channel.requestCount)
    }

    @Test
    fun `a TV in standby is left alone, as the remote channel would switch it on`() {
        power = "standby"
        settings.setClientKey(host, "123456789")
        backend.hold()
        await { it.connection == Connection.Connected && it.power == TvPower.STANDBY }
        Thread.sleep(500)
        assertEquals(0, channel.requestCount)
        assertFalse(backend.state.value.isUsable)
    }

    @Test
    fun `keys, typing and the TV's keyboard go over the channel`() {
        connected()
        backend.press(TvButton.BLUE)
        assertEquals("KEY_CYAN", tv.nextRemote().getString("DataOfCmd"))

        tv.send(ime("imeStart"))
        await { it.keyboard == TvKeyboard(0) }
        backend.type(TextEdit(delete = 0, insert = "news"))
        await { it.keyboard == TvKeyboard(4) }
        assertEquals(base64("news"), tv.nextRemote().getString("Cmd"))
        backend.enter()
        assertEquals("SendInputEnd", tv.nextRemote().getString("TypeOfRemote"))
        tv.send(ime("imeEnd"))
        await { it.keyboard == null }
    }

    @Test
    fun `the TV's app list opens apps the way their type says`() {
        tv = FakeSamsungTv(onEmit = { ws, params ->
            if (params.getString("event") == "ed.installedApp.get") {
                ws.send("""{"event":"ed.installedApp.get","data":{"data":[
                    {"appId":"111299001912","app_type":2,"name":"YouTube"},{"appId":"org.tizen.browser","app_type":4,"name":"Internet"}]}}""")
            }
        })
        val state = connected().let { await { it.apps.isNotEmpty() } }
        assertEquals(listOf(TvApp("111299001912", "YouTube"), TvApp("org.tizen.browser", "Internet")), state.apps)

        backend.launch("org.tizen.browser")
        val launch = tv.nextEmit("ed.apps.launch").getJSONObject("params").getJSONObject("data")
        assertEquals("NATIVE_LAUNCH", launch.getString("action_type"))
        assertEquals("org.tizen.browser", launch.getString("appId"))
    }

    @Test
    fun `newer firmware's missing app list is made up from the well-known apps it has`() {
        restApps["111299001912"] = """{"id":"111299001912","name":"YouTube","running":false,"version":"2.1.1","visible":false}"""
        restApps["3201907018807"] = """{"id":"3201907018807","name":"Netflix","running":false,"visible":false}"""
        connected()
        val apps = await { it.apps.isNotEmpty() }.apps
        assertEquals(setOf(TvApp("111299001912", "YouTube"), TvApp("3201907018807", "Netflix")), apps.toSet())
    }

    @Test
    fun `only YouTube links open, and only when the TV may have YouTube`() {
        val youTube = LinkTarget.YouTube("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ")
        assertEquals(listOf(LinkAction.APP), backend.actionsFor(youTube))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkTarget.Netflix("https://www.netflix.com/title/80100172", "80100172")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkTarget.Web("https://example.com/")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(LinkTarget.Media("http://nas.local/film.mp4", "video/mp4", "film")))
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(StreamingService.DISNEY_PLUS.sampleLink))

        tv = FakeSamsungTv(onEmit = { ws, params ->
            if (params.getString("event") == "ed.installedApp.get") {
                ws.send("""{"event":"ed.installedApp.get","data":{"data":[{"appId":"3201606009684","app_type":2,"name":"Spotify"}]}}""")
            }
        })
        connected()
        await { it.apps.isNotEmpty() }
        assertEquals(emptyList<LinkAction>(), backend.actionsFor(youTube))
    }

    @Test
    fun `a YouTube link goes to the TV's DIAL server`() = runBlocking {
        connected()
        assertTrue(backend.open(LinkTarget.YouTube("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"), LinkAction.APP))
        val (request, body) = lanRequests.first { it.first.target == "/ws/app/YouTube" }
        assertEquals("POST", request.method)
        assertEquals("v=dQw4w9WgXcQ", body)
        assertTrue(tv.drain().none { it.toString().contains("ed.apps.launch") })
    }

    @Test
    fun `without DIAL the YouTube app gets the start query as its meta tag`() = runBlocking {
        dialCode = 404
        connected()
        assertTrue(backend.open(LinkTarget.YouTube("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"), LinkAction.APP))
        val launch = tv.nextEmit("ed.apps.launch").getJSONObject("params").getJSONObject("data")
        assertEquals("DEEP_LINK", launch.getString("action_type"))
        assertEquals("111299001912", launch.getString("appId"))
        assertEquals("v=dQw4w9WgXcQ", launch.getString("metaTag"))
    }

    @Test
    fun `volume comes from the UPnP renderer and is set there`() {
        settings.setClientKey(host, "123456789")
        backend.onScreenStarted()
        val state = await { it.volume == 17 }
        assertFalse(state.muted)

        backend.setVolume(30)
        await { it.volume == 30 }
        val (_, body) = waitFor { lanRequests.firstOrNull { it.second.contains("<u:SetVolume") } }
        assertTrue(body, body.contains("<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>30</DesiredVolume>"))
        backend.onScreenStopped()
    }

    @Test
    fun `mute is a toggle, pressed only when the state has to change`() {
        backend = backend(renderer = null)
        connected()
        backend.setMuted(true)
        assertEquals("KEY_MUTE", tv.nextRemote().getString("DataOfCmd"))
        assertTrue(backend.state.value.muted)
        backend.setMuted(true)
        backend.press(TvButton.HOME)
        assertEquals("KEY_HOME", tv.nextRemote().getString("DataOfCmd"))
        assertNull(backend.state.value.volume)
    }

    private fun soap(action: String, body: String): MockResponse {
        val reply = when {
            action.endsWith("#GetVolume\"") -> "<CurrentVolume>$volume</CurrentVolume>"
            action.endsWith("#GetMute\"") -> "<CurrentMute>0</CurrentMute>"
            action.endsWith("#SetVolume\"") -> {
                volume = Regex("<DesiredVolume>(\\d+)</DesiredVolume>").find(body)!!.groupValues[1].toInt()
                ""
            }
            else -> return MockResponse(code = 500)
        }
        val name = action.substringAfterLast('#').removeSuffix("\"")
        return MockResponse(
            body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""" +
                """<u:${name}Response xmlns:u="${RenderingControl.SERVICE}">$reply</u:${name}Response></s:Body></s:Envelope>""",
        )
    }

    private fun <T : Any> waitFor(find: () -> T?): T {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            find()?.let { return it }
            Thread.sleep(20)
        }
        error("timed out")
    }

    private companion object {
        const val RENDERER = "/upnp/control/RenderingControl1"

        fun device(power: String) = """{"device":{"FrameTVSupport":"false","PowerState":"$power","TokenAuthSupport":"true",
            "modelName":"QE55Q80TATXXC","name":"[TV] Living Room","type":"Samsung SmartTV","wifiMac":"aa:bb:cc:dd:ee:ff"}}"""

        fun closedPort(): Int = MockWebServer().use { it.start(); it.port }
    }
}
