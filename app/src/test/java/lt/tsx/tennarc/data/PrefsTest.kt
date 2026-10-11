package lt.tsx.tennarc.data

import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {
    private val tenna = Prefs(tvName = "Tenna", host = "192.168.1.70", mac = "02:00:00:00:00:01")
    private val bedroom = SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, mac = "")

    @Test
    fun `reset puts the settings back and keeps the TVs`() {
        val changed = tenna.copy(
            otherTvs = listOf(bedroom),
            themeMode = ThemeMode.LIGHT,
            haptics = false,
            touchpadSpeed = 3f,
            searchEngine = SearchEngine.GOOGLE,
            findTitles = true,
            askedFindTitles = true,
            hiddenApps = setOf("youtube.leanback.v4"),
        )
        val reset = changed.resetSettings()
        assertEquals(Prefs().themeMode, reset.themeMode)
        assertEquals(Prefs().haptics, reset.haptics)
        assertEquals(Prefs().touchpadSpeed, reset.touchpadSpeed, 0f)
        assertEquals(Prefs().searchEngine, reset.searchEngine)
        assertEquals(Prefs().findTitles, reset.findTitles)
        assertEquals(Prefs().askedFindTitles, reset.askedFindTitles)
        assertEquals(changed.currentTv, reset.currentTv)
        assertEquals(changed.otherTvs, reset.otherTvs)
        assertEquals(changed.hiddenApps, reset.hiddenApps)
    }

    @Test
    fun `switching keeps the TV in use to switch back to`() {
        val switched = tenna.switchTo(bedroom)
        assertEquals(bedroom, switched.currentTv)
        assertEquals(listOf(tenna.currentTv), switched.otherTvs)
        val back = switched.switchTo(tenna.currentTv)
        assertEquals(tenna.currentTv, back.currentTv)
        assertEquals(listOf(bedroom), back.otherTvs)
    }

    @Test
    fun `switching to the TV in use changes nothing`() {
        assertSame(tenna, tenna.switchTo(tenna.currentTv))
    }

    @Test
    fun `a TV on a new address keeps its place in the list`() {
        val ordered = tenna.copy(otherTvs = listOf(bedroom), demoListed = true, tvOrder = listOf("demo", "192.168.1.70", "192.168.1.50"))
        val moved = ordered.movedTo("192.168.1.71")
        assertEquals("192.168.1.71", moved.host)
        assertEquals(listOf("demo", "192.168.1.71", "192.168.1.50"), moved.tvKeys)
    }

    @Test
    fun `forgetting drops only that TV`() {
        assertEquals(emptyList<SavedTv>(), tenna.switchTo(bedroom).forgetTv(tenna.currentTv).otherTvs)
    }

    @Test
    fun `each TV keeps its own pad, and one new to it starts from the last used`() {
        val both = tenna.copy(otherTvs = listOf(bedroom))
            .withPad(tenna.host, touchpad = true, app = null)
            .withPad(bedroom.host, touchpad = false, app = null)
        assertTrue(both.touchpadOn(tenna.host))
        assertFalse(both.touchpadOn(bedroom.host))
        assertFalse(both.touchpadOn("192.168.1.99"))
        assertTrue(both.withPad(tenna.host, touchpad = true, app = null).touchpadOn("192.168.1.99"))
    }

    @Test
    fun `each app on a TV keeps its own pad, and one new to it goes by its kind`() {
        val left = tenna.withPad(tenna.host, touchpad = true, app = TvApps.YOUTUBE)
            .withPad(tenna.host, touchpad = false, app = TvApps.BROWSER)
        assertEquals(true, left.touchpadIn(tenna.host, TvApps.YOUTUBE))
        assertEquals(false, left.touchpadIn(tenna.host, TvApps.BROWSER))
        assertEquals(false, left.touchpadIn(tenna.host, TvApps.NETFLIX))
        assertEquals(null, left.touchpadIn(tenna.host, "com.example.game"))
        assertEquals(false, left.touchpadIn(bedroom.host, TvApps.YOUTUBE))
    }

    @Test
    fun `keeping the pad as it is changes nothing`() {
        val kept = tenna.withPad(tenna.host, touchpad = true, app = TvApps.BROWSER)
        assertSame(kept, kept.withPad(tenna.host, touchpad = true, app = TvApps.BROWSER))
    }

    @Test
    fun `with no TV yet only the last used pad is kept`() {
        val none = Prefs(host = "").withPad("", touchpad = true, app = TvApps.BROWSER)
        assertTrue(none.touchpadMode)
        assertTrue(none.touchpadModes.isEmpty())
    }

    @Test
    fun `the demo keeps a pad of its own`() {
        val demo = tenna.withPad(tenna.host, touchpad = false, app = null).useDemo()
        assertEquals(DEMO_KEY, demo.tvKey)
        val touchpad = demo.withPad(demo.tvKey, touchpad = true, app = null)
        assertTrue(touchpad.touchpadOn(DEMO_KEY))
        assertFalse(touchpad.touchpadOn(tenna.host))
    }

    @Test
    fun `forgetting a TV forgets its pad`() {
        val both = tenna.switchTo(bedroom)
            .withPad(tenna.host, touchpad = true, app = TvApps.YOUTUBE)
            .withPad(bedroom.host, touchpad = false, app = TvApps.BROWSER)
        val forgotten = both.forgetTv(tenna.currentTv)
        assertTrue(forgotten.touchpadModes.keys.all { it.startsWith(bedroom.host) })
        assertEquals(false, forgotten.touchpadIn(bedroom.host, TvApps.BROWSER))
        assertEquals(false, forgotten.touchpadIn(tenna.host, TvApps.YOUTUBE))
    }

    @Test
    fun `the TV behind the demo keeps its pad`() {
        val behind = tenna.withPad(tenna.host, touchpad = true, app = null).useDemo()
        assertEquals(behind.touchpadModes, behind.forgetTv(tenna.currentTv).touchpadModes)
    }

    @Test
    fun `removing the demo forgets its pad`() {
        val demo = tenna.useDemo().withPad(DEMO_KEY, touchpad = true, app = TvApps.YOUTUBE).withPad(tenna.host, touchpad = false, app = null)
        val removed = demo.removeDemo()
        assertFalse(removed.demoTv || removed.demoListed)
        assertEquals(mapOf(tenna.host to false), removed.touchpadModes)
    }

    @Test
    fun `a TV on a new address keeps its pad`() {
        val moved = tenna.withPad(tenna.host, touchpad = true, app = TvApps.YOUTUBE).movedTo("192.168.1.71")
        assertTrue(moved.touchpadOn("192.168.1.71"))
        assertEquals(true, moved.touchpadIn("192.168.1.71", TvApps.YOUTUBE))
        assertTrue(moved.touchpadModes.keys.none { it.startsWith(tenna.host) })
    }
}
