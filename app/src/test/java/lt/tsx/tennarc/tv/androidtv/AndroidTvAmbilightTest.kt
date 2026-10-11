package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.philips.FakePhilipsTv
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTvAmbilightTest {
    private val tv = FakeAndroidTv()
    private val philips = FakePhilipsTv()
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val settings = Settings(RuntimeEnvironment.getApplication()).apply {
        update { it.copy(host = "127.0.0.1", kind = TvKind.ANDROID_TV) }
    }
    private val backend = AndroidTvBackend(
        settings,
        TestIdentity("Tenna RC"),
        CoroutineScope(SupervisorJob() + main),
        tv.remotePort,
        tv.pairingPort,
        philips.ports,
    )

    @After
    fun stop() {
        on { backend.close() }
        tv.close()
        philips.close()
        main.close()
    }

    @Test
    fun `the TV gains Ambilight once JointSPACE answers, with no second TV to add`() {
        assertTrue(Feature.AMBILIGHT !in backend.state.value.features)
        on { backend.pair() }
        val code = checkNotNull(tv.codes.poll(5, TimeUnit.SECONDS)) { "The TV showed no code" }
        on { backend.submitPairingCode(code) }

        val found = awaitState { Feature.AMBILIGHT in it.features }
        assertEquals(AmbilightAccess.NeedsPairing, found.ambilight?.access)
        assertTrue(found.features.containsAll(ANDROID_TV_FEATURES))

        on { backend.pairAmbilight() }
        awaitState { it.ambilight?.access is AmbilightAccess.AwaitingPin }
        on { backend.submitAmbilightPin(FakePhilipsTv.PIN) }
        val paired = awaitState { it.ambilight?.on != null }
        assertEquals(true, paired.ambilight?.on)
        assertTrue("the remote is still up", paired.isConnected)
    }

    private fun on(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun awaitState(predicate: (TvState) -> Boolean): TvState =
        runBlocking { withTimeout(10_000) { backend.state.first(predicate) } }
}
