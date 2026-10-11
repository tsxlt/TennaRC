package lt.tsx.tennarc.ui.components

import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckItemsTest {
    private val youtube = TvApp(TvApps.YOUTUBE, "YouTube")
    private val netflix = TvApp(TvApps.NETFLIX, "Netflix")
    private val browser = TvApp(TvApps.BROWSER, "Web Browser")
    private val music = TvApp("com.webos.app.music", "Music")
    private val store = TvApp("com.webos.app.discovery", "LG Content Store")
    private val apps = listOf(store, music, browser, netflix, youtube)
    private val pc = TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true)
    private val console = TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = null)
    private val empty = TvApp("com.webos.app.hdmi3", "HDMI 3", input = true, plugged = false)
    private val lrt = TvChannel("a", "1", "LRT")
    private val plius = TvChannel("b", "2", "LRT Plius")
    private val ten = TvChannel("c", "10", "TV10")
    private val sub = TvChannel("d", "1-2", "LRT HD")

    @Test fun restGoesByUseNotByName() {
        val items = deckItems(apps, emptyList(), emptyList(), activeId = null, DeckUse(apps = mapOf(store.id to 5f, music.id to 1f)), slots = 2)
        assertEquals(listOf(store, music), items.row)
        assertEquals(listOf(youtube, netflix, browser), items.rest)
    }

    @Test fun whatsOnScreenTakesTheRowsLastSlot() {
        val items = deckItems(apps, emptyList(), emptyList(), activeId = store.id, DeckUse(), slots = 3)
        assertEquals(listOf(youtube, netflix, store), items.row)
        assertEquals(listOf(browser, music), items.rest)
    }

    @Test fun anInputOnScreenIsntRepeatedUnderTheRow() {
        val items = deckItems(apps, listOf(pc, console, empty), emptyList(), activeId = pc.id, DeckUse(), slots = 3)
        assertEquals(pc, items.row.last())
        assertEquals(listOf(console), items.inputs)
    }

    @Test fun inputsGoByUseThenTheTvsOrder() {
        val items = deckItems(apps, listOf(pc, console, empty), emptyList(), activeId = null, DeckUse(apps = mapOf(console.id to 2f)), slots = 3)
        assertEquals(listOf(console, pc), items.inputs)
    }

    @Test fun channelsGoByUseThenByNumber() {
        val unused = deckItems(apps, emptyList(), listOf(ten, plius, sub, lrt), activeId = null, DeckUse(), slots = 3)
        assertEquals(listOf(lrt, sub, plius, ten), unused.channels)
        val watched = deckItems(apps, emptyList(), listOf(ten, plius, sub, lrt), activeId = null, DeckUse(channels = mapOf(ten.id to 3f)), slots = 3)
        assertEquals(listOf(ten, lrt, sub, plius), watched.channels)
    }

    @Test fun theTvsOwnAppsGetGlyphs() {
        assertEquals(Lucide.Globe, appGlyph(browser))
        assertEquals(Lucide.Music, appGlyph(music))
        assertEquals(Lucide.ShoppingBag, appGlyph(store))
        assertEquals(Lucide.Cast, appGlyph(TvApp(TvApps.WEB_VIDEO_CASTER, "Web Video Caster")))
        assertEquals(null, appGlyph(netflix))
    }
}
