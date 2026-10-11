package lt.tsx.tennarc.data

import android.content.Context
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PadStoreTest {
    private val app = RuntimeEnvironment.getApplication()
    private val store = app.getSharedPreferences("tenna", Context.MODE_PRIVATE)
    private val lounge = SavedTv("Lounge", "192.168.1.70", TvKind.WEBOS, mac = "")
    private val bedroom = SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, mac = "")

    private fun twoTvs(prefs: Prefs) = prefs.copy(tvName = lounge.name, host = lounge.host, otherTvs = listOf(bedroom))
        .withPad(lounge.host, touchpad = true, app = TvApps.YOUTUBE)
        .withPad(bedroom.host, touchpad = false, app = TvApps.BROWSER)

    @Test
    fun eachTvsPadComesBack() {
        Settings(app).update(::twoTvs)
        val read = Settings(app).prefs.value
        assertTrue(read.touchpadOn(lounge.host))
        assertFalse(read.touchpadOn(bedroom.host))
        assertEquals(true, read.touchpadIn(lounge.host, TvApps.YOUTUBE))
        assertEquals(false, read.touchpadIn(bedroom.host, TvApps.BROWSER))
        assertFalse(read.touchpadMode)
    }

    @Test
    fun theOnePadFromBeforeIsTheTvInUsesAndWhereEveryOtherStarts() {
        store.edit()
            .putString("host", lounge.host)
            .putString("tv_name", lounge.name)
            .putBoolean("touchpad_mode", true)
            .putString("pad_follows_app", TvApps.BROWSER)
            .commit()
        val settings = Settings(app)
        val read = settings.prefs.value
        assertTrue(read.touchpadOn(lounge.host))
        assertEquals(true, read.touchpadIn(lounge.host, TvApps.BROWSER))
        assertTrue(read.touchpadMode)
        assertTrue(read.touchpadOn(bedroom.host))

        settings.update { it }
        assertFalse(store.contains("pad_follows_app"))
        assertEquals(read, Settings(app).prefs.value)
    }

    @Test
    fun theDemoInUseTakesThePadFromBefore() {
        store.edit()
            .putString("host", lounge.host)
            .putBoolean("demo_tv", true)
            .putBoolean("touchpad_mode", true)
            .commit()
        val read = Settings(app).prefs.value
        assertEquals(mapOf(DEMO_KEY to true), read.touchpadModes)
        assertTrue(read.padFollowsApps.isEmpty())
        assertTrue(read.touchpadOn(lounge.host))
    }

    @Test
    fun aNewInstallHasNoPadsYet() {
        val read = Settings(app).prefs.value
        assertTrue(read.touchpadModes.isEmpty())
        assertTrue(read.padFollowsApps.isEmpty())
        assertFalse(read.touchpadMode)
    }

    @Test
    fun aForgottenTvsPadIsGoneForGood() {
        Settings(app).update(::twoTvs)
        Settings(app).update { it.forgetTv(bedroom) }
        val read = Settings(app).prefs.value
        assertEquals(true, read.touchpadIn(lounge.host, TvApps.YOUTUBE))
        assertTrue(read.touchpadModes.keys.none { it.startsWith(bedroom.host) })
    }

    @Test
    fun aRemovedDemosPadIsGoneForGood() {
        Settings(app).update { twoTvs(it).useDemo().withPad(DEMO_KEY, touchpad = true, app = TvApps.NETFLIX) }
        assertEquals(true, Settings(app).prefs.value.touchpadIn(DEMO_KEY, TvApps.NETFLIX))
        Settings(app).update { it.removeDemo() }
        val read = Settings(app).prefs.value
        assertTrue(read.touchpadModes.keys.none { it.startsWith(DEMO_KEY) })
        assertEquals(true, read.touchpadIn(lounge.host, TvApps.YOUTUBE))
    }
}
