package lt.tsx.tennarc.tv.philips

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.Notice
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhilipsAmbilightTest {
    private val tv = FakePhilipsTv(unknownStyles = setOf("Lounge light"))
    private val notices = CopyOnWriteArrayList<Notice>()
    private lateinit var settings: Settings
    private lateinit var light: PhilipsAmbilight

    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var scope: CoroutineScope

    @Before
    fun start() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + main)
        settings = Settings(RuntimeEnvironment.getApplication())
        light = PhilipsAmbilight(settings, scope, tv.ports, notify = { notices += it }) { OkHttpClient() }
    }

    @After
    fun stop() {
        scope.cancel()
        main.close()
        tv.close()
    }

    @Test
    fun `pairs with the PIN on screen, then drives the light and keeps the login`() {
        on { light.connected(tv.host) }
        awaitLight { it?.access == AmbilightAccess.NeedsPairing }

        on { light.pair() }
        awaitLight { it?.access == AmbilightAccess.AwaitingPin() }
        on { light.submitPin("0000") }
        awaitLight { it?.access == AmbilightAccess.AwaitingPin(wrong = true) }
        on { light.submitPin(" ${FakePhilipsTv.PIN} ") }

        val paired = awaitLight { it?.access == AmbilightAccess.Paired && it.on != null }
        assertEquals(Ambilight(AmbilightAccess.Paired, on = true, style = AmbilightStyle.FOLLOW_VIDEO), paired)
        val login = JointSpaceLogin.decode(settings.clientKey(loginKey(tv.host)))!!
        assertEquals(FakePhilipsTv.AUTH_KEY, tv.logins[login.user])

        on { light.set(AmbilightStyle.LOUNGE) }
        eventually { tv.config.getString("styleName") == "FOLLOW_COLOR" }
        assertEquals("HOT_LAVA", tv.config.getString("menuSetting"))
        awaitLight { it?.style == AmbilightStyle.LOUNGE && it.current == AmbilightStyle.LOUNGE }

        on { light.set(AmbilightStyle.OFF) }
        eventually { tv.power == "Off" }
        assertEquals(AmbilightStyle.LOUNGE, awaitLight { it?.on == false }?.style)

        tv.config = JSONObject("""{"styleName":"FOLLOW_VIDEO","isExpert":false,"menuSetting":"VIVID"}""")
        on { light.refresh() }
        awaitLight { it?.style == AmbilightStyle.FOLLOW_VIDEO }
        on { light.set(AmbilightStyle.LOUNGE) }
        eventually { tv.config.getString("styleName") == "FOLLOW_COLOR" }
        on { light.set(AmbilightStyle.FOLLOW_VIDEO) }
        eventually { tv.config.getString("styleName") == "FOLLOW_VIDEO" && tv.power == "On" }
        assertEquals("VIVID", tv.config.getString("menuSetting"))
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `a stored login is used at once, and one the TV has dropped goes back to pairing`() {
        tv.logins["Known0123456789A"] = FakePhilipsTv.AUTH_KEY
        settings.setClientKey(loginKey(tv.host), JointSpaceLogin("Known0123456789A", FakePhilipsTv.AUTH_KEY).encode())
        tv.power = "Off"

        on { light.connected(tv.host) }
        assertEquals(AmbilightStyle.OFF, awaitLight { it?.on != null }?.current)

        tv.logins.clear()
        on { light.refresh() }
        awaitLight { it?.access == AmbilightAccess.NeedsPairing }
        assertNull(settings.clientKey(loginKey(tv.host)))
    }

    @Test
    fun `a TV without JointSPACE never shows the light`() {
        val elsewhere = PhilipsAmbilight(settings, scope, JointSpacePorts(FakePhilipsTv.closedPort(), FakePhilipsTv.closedPort())) { OkHttpClient() }
        on { elsewhere.connected(tv.host) }
        on { elsewhere.forget(tv.host) }
        Thread.sleep(500)
        assertNull(elsewhere.state.value)
        on { elsewhere.set(AmbilightStyle.FOLLOW_VIDEO) }
        assertNull(elsewhere.state.value)
    }

    private fun on(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun awaitLight(predicate: (Ambilight?) -> Boolean): Ambilight? =
        runBlocking { withTimeout(10_000) { light.state.first(predicate) } }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "The TV never got there" }
            Thread.sleep(20)
        }
    }
}
