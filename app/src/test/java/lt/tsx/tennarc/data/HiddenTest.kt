package lt.tsx.tennarc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenTest {
    private val lounge = Prefs(host = "10.0.0.2")

    @Test
    fun anAppHiddenOnOneTvStaysOnTheOthers() {
        val hidden = lounge.hideApp("netflix", "Netflix")
        assertTrue(hidden.deckKey("netflix") in hidden.hiddenHere)
        val bedroom = hidden.copy(host = "10.0.0.3")
        assertFalse(bedroom.deckKey("netflix") in bedroom.hiddenHere)
    }

    @Test
    fun shownAgainItsNameIsForgottenToo() {
        val hidden = lounge.hideApp("netflix", "Netflix")
        val key = hidden.deckKey("netflix")
        assertEquals("Netflix", hidden.hiddenNames[key])
        val shown = hidden.showApp(key)
        assertTrue(shown.hiddenHere.isEmpty())
        assertTrue(shown.hiddenNames.isEmpty())
    }

    @Test
    fun channelsHideOnEveryTv() {
        val hidden = lounge.hideChannel("7", "LRT")
        assertTrue("7" in hidden.copy(host = "10.0.0.3").hiddenChannels)
        assertTrue(hidden.showChannel("7").hiddenChannels.isEmpty())
    }

    @Test
    fun aBlankNameGoesBackToTheTvs() {
        val named = lounge.renameInput("hdmi2", "  PS5 ")
        assertEquals("PS5", named.inputNames[named.deckKey("hdmi2")])
        assertTrue(named.renameInput("hdmi2", "").inputNames.isEmpty())
    }

    @Test
    fun theDemoKeepsItsOwn() {
        val demo = lounge.copy(demoTv = true).hideApp("netflix", "Netflix")
        assertFalse(demo.copy(demoTv = false).deckKey("netflix") in demo.copy(demoTv = false).hiddenHere)
    }
}
