package lt.tsx.tennarc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTrailingTest {
    @Test fun emptyBoxWithNothingCopiedOffersTheMic() {
        assertEquals(Trailing.MIC, trailingFor("", listening = false, mic = true, paste = false))
    }

    @Test fun typingOffersClear() {
        assertEquals(Trailing.CLEAR, trailingFor("str", listening = false, mic = true, paste = false))
        assertEquals(Trailing.CLEAR, trailingFor("str", listening = false, mic = true, paste = true))
        assertEquals(Trailing.CLEAR, trailingFor(" ", listening = false, mic = false, paste = false))
    }

    @Test fun somethingCopiedSitsBesideTheMic() {
        assertEquals(Trailing.PASTE_AND_MIC, trailingFor("", listening = false, mic = true, paste = true))
    }

    @Test fun aPhoneThatCantListenKeepsPaste() {
        assertEquals(Trailing.PASTE, trailingFor("", listening = false, mic = false, paste = false))
    }

    @Test fun theMicStaysWhileListening() {
        assertEquals(Trailing.MIC, trailingFor("", listening = true, mic = true, paste = true))
    }

    @Test fun copiedTextIsOfferedUntilItsPasted() {
        val copied = ClipPeek(text = true, stamp = 42)
        assertTrue(copied.offersPaste(spent = null))
        assertFalse(copied.offersPaste(spent = 42))
        assertTrue(copied.copy(stamp = 43).offersPaste(spent = 42))
    }

    @Test fun noTextOrNothingOffersNoPaste() {
        assertFalse(ClipPeek(text = false, stamp = 1).offersPaste(spent = null))
        assertFalse(null.offersPaste(spent = null))
    }

    @Test fun loudnessRunsFromSilenceToARaisedVoice() {
        assertEquals(0f, loudness(-2f), 0f)
        assertEquals(0f, loudness(-10f), 0f)
        assertEquals(0.5f, loudness(4f), 0.001f)
        assertEquals(1f, loudness(10f), 0f)
        assertEquals(1f, loudness(30f), 0f)
    }
}
