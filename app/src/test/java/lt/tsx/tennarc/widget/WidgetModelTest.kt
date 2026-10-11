package lt.tsx.tennarc.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.hideApp
import lt.tsx.tennarc.data.tvKeys
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.WEBOS_FEATURES
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val prefs = Prefs(tvName = "Tenna", mac = "02:00:00:00:00:01")
    private val on = TvState(host = "h", connection = Connection.Connected, power = TvPower.ON)

    @Test
    fun `an app in front is what's on`() {
        val model = widgetModel(on.copy(foregroundAppId = TvApps.NETFLIX), prefs)
        assertEquals(WidgetMode.READY, model.mode)
        assertEquals("Tenna", model.tvName)
        assertEquals("Netflix", model.title)
        assertTrue(model.playing)
        assertTrue(model.canWake)
    }

    @Test
    fun `a cast's title beats the app playing it`() {
        val cast = CastState("Big Buck Bunny", CastPhase.PAUSED)
        val model = widgetModel(on.copy(foregroundAppId = TvApps.PHOTO_VIDEO, cast = cast), prefs)
        assertEquals("Big Buck Bunny", model.title)
        assertFalse(model.playing)
    }

    @Test
    fun `nothing in front leaves the name alone`() {
        val model = widgetModel(on, prefs)
        assertNull(model.title)
        assertEquals(WidgetLine.NONE, widgetLine(model, armed = false, waking = false))
    }

    @Test
    fun `standby and out of reach both read as off`() {
        assertEquals(WidgetMode.OFF, widgetModel(on.copy(power = TvPower.STANDBY, foregroundAppId = TvApps.NETFLIX), prefs).mode)
        assertNull(widgetModel(on.copy(power = TvPower.STANDBY, foregroundAppId = TvApps.NETFLIX), prefs).title)
        assertEquals(WidgetMode.OFF, widgetModel(TvState(host = "h", connection = Connection.Offline), prefs).mode)
    }

    @Test
    fun `screen off with the sound on is still on`() {
        assertEquals(WidgetMode.READY, widgetModel(on.copy(power = TvPower.SCREEN_OFF), prefs).mode)
    }

    @Test
    fun `a fresh process knows nothing, and offers the keys`() {
        val model = widgetModel(TvState(host = "h"), prefs)
        assertEquals(WidgetMode.READY, model.mode)
        assertNull(model.title)
        assertFalse(model.playing)
        assertFalse(model.muted)
    }

    @Test
    fun `an unpaired TV only says so`() {
        for (connection in listOf(Connection.NeedsPairing(), Connection.AwaitingApproval, Connection.AwaitingCode())) {
            val model = widgetModel(TvState(host = "h", connection = connection), prefs)
            assertEquals(WidgetMode.SETUP, model.mode)
            assertEquals(WidgetLine.SETUP, widgetLine(model, armed = false, waking = false))
        }
    }

    @Test
    fun `waking needs the feature and a MAC`() {
        assertFalse(widgetModel(on, prefs.copy(mac = " ")).canWake)
        assertFalse(widgetModel(on.copy(features = setOf(Feature.APP_LIST)), prefs).canWake)
    }

    @Test
    fun `what the widget doesn't draw doesn't change it`() {
        val before = widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE, volume = 10), prefs)
        val after = widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE, volume = 11, apps = emptyList(), channelId = "7"), prefs)
        assertEquals(before, after)
    }

    @Test
    fun `the line says the most pressing thing`() {
        val playing = widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), prefs)
        val off = widgetModel(on.copy(power = TvPower.STANDBY), prefs)
        assertEquals(WidgetLine.ARMED, widgetLine(playing, armed = true, waking = false))
        assertEquals(WidgetLine.TITLE, widgetLine(playing, armed = false, waking = true))
        assertEquals(WidgetLine.OFF, widgetLine(off, armed = true, waking = false))
        assertEquals(WidgetLine.WAKING, widgetLine(off, armed = false, waking = true))
        assertEquals(WidgetLine.WAKING, widgetLine(widgetModel(TvState(host = "h"), prefs), armed = false, waking = true))
    }

    @Test
    fun `the shape goes by width and height both`() {
        assertEquals(WidgetLayout.COMPACT, widgetLayout(DpSize(168.dp, 92.dp)))
        assertEquals(WidgetLayout.COMPACT, widgetLayout(DpSize(199.dp, 99.dp)))
        assertEquals(WidgetLayout.SQUARE, widgetLayout(DpSize(176.dp, 100.dp)))
        assertEquals(WidgetLayout.SQUARE, widgetLayout(DpSize(199.dp, 400.dp)))
        assertEquals(WidgetLayout.ROW, widgetLayout(DpSize(200.dp, 92.dp)))
        assertEquals(WidgetLayout.ROW, widgetLayout(DpSize(600.dp, 99.dp)))
        assertEquals(WidgetLayout.CARD, widgetLayout(DpSize(338.dp, 100.dp)))
        assertEquals(WidgetLayout.CARD, widgetLayout(DpSize(338.dp, 189.dp)))
        assertEquals(WidgetLayout.HERO, widgetLayout(DpSize(338.dp, 190.dp)))
        assertEquals(WidgetLayout.HERO, widgetLayout(DpSize(338.dp, 260.dp)))
        assertEquals(WidgetLayout.COMPACT, widgetLayout(DpSize(130.dp, 40.dp)))
    }

    private val apps = listOf(
        TvApp(TvApps.YOUTUBE, "YouTube", "http://h/yt.png"),
        TvApp(TvApps.NETFLIX, "Netflix", "http://h/nf.png"),
        TvApp("amazon", "Prime Video", "http://h/pv.png"),
        TvApp("disney", "Disney+", "http://h/dp.png"),
        TvApp("spotify", "Spotify", "http://h/sp.png"),
        TvApp(TvApps.BROWSER, "Web Browser"),
    )
    private val listed = on.copy(apps = apps)

    @Test
    fun `favourites go by use, then the remote's own order`() {
        val fresh = widgetModel(listed, prefs).favorites.map { it.id }
        assertEquals(listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, "disney"), fresh)
        val habit = prefs.copy(appUse = mapOf("spotify" to 30f, "disney" to 12f, TvApps.NETFLIX to 3f))
        assertEquals(listOf("spotify", "disney", TvApps.NETFLIX, TvApps.YOUTUBE), widgetModel(listed, habit).favorites.map { it.id })
    }

    @Test
    fun `the app on screen isn't among the favourites, it's what's on`() {
        val model = widgetModel(listed.copy(foregroundAppId = TvApps.YOUTUBE), prefs)
        assertEquals(listOf(TvApps.NETFLIX, TvApps.BROWSER, "disney", "amazon"), model.favorites.map { it.id })
        assertEquals(WidgetApp(TvApps.YOUTUBE, "YouTube", "http://h/yt.png"), model.app)
        assertFalse(model.casting)
    }

    @Test
    fun `no favourites from a TV that can't list its apps, or hasn't yet`() {
        assertTrue(widgetModel(listed.copy(features = WEBOS_FEATURES - Feature.APP_LIST), prefs).favorites.isEmpty())
        assertTrue(widgetModel(on, prefs).favorites.isEmpty())
    }

    @Test
    fun `apps hidden from the remote are hidden here too`() {
        val hidden = prefs.copy(host = "h").hideApp(TvApps.NETFLIX, "Netflix")
        assertFalse(widgetModel(listed, hidden).favorites.any { it.id == TvApps.NETFLIX })
    }

    @Test
    fun `a cast has no app's icon`() {
        val model = widgetModel(listed.copy(foregroundAppId = TvApps.PHOTO_VIDEO, cast = CastState("Big Buck Bunny", CastPhase.PLAYING)), prefs)
        assertNull(model.app)
        assertTrue(model.casting)
        assertNull(widgetModel(listed, prefs).app)
    }

    private val bedroom = SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, mac = "")
    private val kitchen = SavedTv("Kitchen", "192.168.1.60", TvKind.SAMSUNG, mac = "")
    private val three = prefs.copy(host = "192.168.1.10", otherTvs = listOf(bedroom, kitchen))

    @Test
    fun `the next TV goes round them all, the demo too`() {
        assertNull(nextTv(prefs))
        assertEquals(NextTv("Bedroom"), nextTv(three))
        val second = three.switchToNext()
        assertEquals("192.168.1.50", second.host)
        assertEquals(NextTv("Kitchen"), nextTv(second))
        val third = second.switchToNext()
        assertEquals("192.168.1.60", third.host)
        val back = third.switchToNext()
        assertEquals("192.168.1.10", back.host)
        assertEquals("Tenna", back.tvName)
        assertEquals(three.tvKeys, back.tvKeys)

        val demo = prefs.copy(host = "192.168.1.10", demoListed = true)
        assertEquals(NextTv("", demo = true), nextTv(demo))
        val onDemo = demo.switchToNext()
        assertTrue(onDemo.demoTv)
        assertEquals(NextTv("Tenna"), nextTv(onDemo))
        assertFalse(onDemo.switchToNext().demoTv)
    }

    @Test
    fun `one TV has nowhere to switch to`() {
        assertEquals(prefs, prefs.switchToNext())
    }

    private val ready = widgetModel(listed.copy(foregroundAppId = TvApps.YOUTUBE), prefs)

    @Test
    fun `a short card is two rows, the room around them capped`() {
        val stack = widgetStack(WidgetLayout.CARD, DpSize(338.dp, 150.dp), ready, columns = 4)
        assertEquals(WidgetStack(WidgetLayout.CARD, margin = MAX_MARGIN), stack)
        assertEquals(2, stack.rows)
    }

    @Test
    fun `the card fills its height with favourites, not a gap`() {
        val card = widgetStack(WidgetLayout.CARD, DpSize(338.dp, 180.dp), ready, columns = 4)
        assertEquals(4, card.favorites)
        assertFalse(card.art)
        for (height in listOf(196.dp, 210.dp)) {
            val stack = widgetStack(WidgetLayout.HERO, DpSize(338.dp, height), ready, columns = 4)
            assertEquals(4, stack.favorites)
            assertEquals(3, stack.rows)
            assertTrue(gapBetweenRows(stack, height) <= MAX_GAP)
        }
    }

    @Test
    fun `taller, what's on gets a big icon, and the rows never drift apart`() {
        val stack = widgetStack(WidgetLayout.HERO, DpSize(338.dp, 260.dp), ready, columns = 4)
        assertTrue(stack.art)
        assertEquals(listOf(HEADER, ART, KEY, KEY), stack.heights)
        for (height in listOf(176, 196, 220, 260, 300, 340, 480).map { it.dp }) {
            val tall = widgetStack(WidgetLayout.HERO, DpSize(338.dp, height), ready, columns = 4)
            assertTrue("$height: ${gapBetweenRows(tall, height)}", gapBetweenRows(tall, height) <= MAX_GAP)
            assertEquals(height.value, (tall.height + tall.margin * 2 + gapBetweenRows(tall, height) * (tall.rows - 1)).value, 0.01f)
        }
        val huge = widgetStack(WidgetLayout.HERO, DpSize(338.dp, 480.dp), ready, columns = 4)
        assertEquals(MAX_GAP, huge.gap)
        assertTrue(huge.margin > MAX_MARGIN)
    }

    @Test
    fun `with other TVs, the line over the big icon offers the next`() {
        val model = ready.copy(nextTv = NextTv("Bedroom"))
        assertFalse(widgetStack(WidgetLayout.HERO, DpSize(338.dp, 196.dp), model, columns = 4).chip)
        val stack = widgetStack(WidgetLayout.HERO, DpSize(338.dp, 260.dp), model, columns = 4)
        assertTrue(stack.chip)
        assertTrue(stack.art)
        assertFalse(widgetStack(WidgetLayout.HERO, DpSize(338.dp, 260.dp), ready, columns = 4).chip)
    }

    @Test
    fun `without favourites the card shows what's on big instead`() {
        val bare = ready.copy(favorites = emptyList())
        val stack = widgetStack(WidgetLayout.HERO, DpSize(338.dp, 196.dp), bare, columns = 4)
        assertTrue(stack.art)
        assertEquals(0, stack.favorites)
    }

    @Test
    fun `the square adds its big icon, then two favourites`() {
        val short = widgetStack(WidgetLayout.SQUARE, DpSize(176.dp, 120.dp), ready, columns = 2)
        assertFalse(short.art)
        assertEquals(2, short.rows)
        val square = widgetStack(WidgetLayout.SQUARE, DpSize(176.dp, 196.dp), ready, columns = 2)
        assertTrue(square.art)
        assertEquals(0, square.favorites)
        val tall = widgetStack(WidgetLayout.SQUARE, DpSize(176.dp, 260.dp), ready, columns = 2)
        assertEquals(2, tall.favorites)
    }

    @Test
    fun `the picker shows this TV playing something, its favourites around it`() {
        assertEquals(ready, previewModel(ready))
        val idle = widgetModel(listed.copy(connection = Connection.Connecting), prefs)
        val preview = previewModel(idle)
        assertEquals(WidgetMode.READY, preview.mode)
        assertEquals("YouTube", preview.title)
        assertEquals("http://h/yt.png", preview.app?.iconUrl)
        assertTrue(preview.playing)
        assertFalse(preview.favorites.any { it.id == TvApps.YOUTUBE })
        assertEquals("Tenna", previewModel(widgetModel(TvState(host = "h", connection = Connection.Offline), prefs)).tvName)
    }

    @Test
    fun `four keys across a 4-wide widget, three on a narrow one`() {
        assertEquals(4, widgetColumns(314.dp))
        assertEquals(4, widgetColumns(210.dp))
        assertEquals(3, widgetColumns(200.dp))
    }

    private fun gapBetweenRows(stack: WidgetStack, height: androidx.compose.ui.unit.Dp) =
        stack.gap ?: ((height - stack.height - stack.margin * 2) / (stack.rows - 1))

    @Test
    fun `power arms first, then turns off`() {
        assertEquals(PowerPlan.ARM, powerPlan(on, canWake = true, armed = false))
        assertEquals(PowerPlan.TURN_OFF, powerPlan(on, canWake = true, armed = true))
        assertEquals(PowerPlan.ARM, powerPlan(on.copy(power = null), canWake = false, armed = false))
    }

    @Test
    fun `power wakes a TV that's off or didn't answer`() {
        assertEquals(PowerPlan.WAKE, powerPlan(on.copy(power = TvPower.STANDBY), canWake = true, armed = true))
        assertEquals(PowerPlan.WAKE, powerPlan(TvState(host = "h", connection = Connection.Offline), canWake = true, armed = false))
        assertEquals(PowerPlan.WAKE, powerPlan(null, canWake = true, armed = false))
    }

    @Test
    fun `power does nothing it can't`() {
        assertEquals(PowerPlan.NOTHING, powerPlan(null, canWake = false, armed = false))
        assertEquals(PowerPlan.NOTHING, powerPlan(on.copy(power = TvPower.STANDBY), canWake = false, armed = false))
        assertEquals(PowerPlan.NOTHING, powerPlan(TvState(host = "h", connection = Connection.NeedsPairing()), canWake = true, armed = false))
    }

    @Test
    fun `a connection settles up, down or unpaired`() {
        assertTrue(Connection.Connected.isSettled)
        assertTrue(Connection.Offline.isSettled)
        assertTrue(Connection.NeedsPairing().isSettled)
        assertFalse(Connection.Connecting.isSettled)
        assertFalse(Connection.AwaitingApproval.isSettled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `redraws come at most once a second, the latest each time`() = runTest {
        val sent = flow {
            emit(1)
            delay(100)
            emit(2)
            delay(100)
            emit(3)
            delay(1_300)
            emit(4)
            delay(3_000)
            emit(5)
        }
        val got = sent.atMostEvery(1_000).map { it to currentTime }.toList()
        assertEquals(listOf(1 to 0L, 3 to 1_000L, 4 to 2_000L, 5 to 4_500L), got)
    }
}
