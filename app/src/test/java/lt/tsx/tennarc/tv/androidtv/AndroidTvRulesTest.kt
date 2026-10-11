package lt.tsx.tennarc.tv.androidtv

import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class AndroidTvRulesTest {
    @Test
    fun `remote buttons send Android's key codes`() {
        assertEquals(19, TvButton.UP.keyCode)
        assertEquals(20, TvButton.DOWN.keyCode)
        assertEquals(21, TvButton.LEFT.keyCode)
        assertEquals(22, TvButton.RIGHT.keyCode)
        assertEquals(23, TvButton.ENTER.keyCode)
        assertEquals(4, TvButton.BACK.keyCode)
        assertEquals(3, TvButton.HOME.keyCode)
        assertEquals(176, TvButton.MENU.keyCode)
        assertEquals(82, TvButton.QMENU.keyCode)
        assertEquals(165, TvButton.INFO.keyCode)
        assertEquals(178, TvButton.INPUT_HUB.keyCode)
        assertEquals(126, TvButton.PLAY.keyCode)
        assertEquals(127, TvButton.PAUSE.keyCode)
        assertEquals(86, TvButton.STOP.keyCode)
        assertEquals(89, TvButton.REWIND.keyCode)
        assertEquals(90, TvButton.FAST_FORWARD.keyCode)
        assertEquals(166, TvButton.CHANNEL_UP.keyCode)
        assertEquals(167, TvButton.CHANNEL_DOWN.keyCode)
        assertEquals(listOf(183, 184, 185, 186), listOf(TvButton.RED, TvButton.GREEN, TvButton.YELLOW, TvButton.BLUE).map { it.keyCode })
        assertEquals((7..16).toList(), (0..9).map { TvButton.valueOf("DIGIT_$it").keyCode })
    }

    @Test
    fun `every streaming link opens in its app, and nothing else opens at all`() {
        val app = listOf(LinkAction.APP)
        assertEquals(app, androidTvActions(LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ")))
        assertEquals(app, androidTvActions(LinkRouter.classify("https://www.netflix.com/title/80057281")))
        assertEquals(app, androidTvActions(LinkRouter.classify("https://www.disneyplus.com/video/abc")))
        StreamingService.entries.forEach { assertEquals(it.name, app, androidTvActions(it.sampleLink)) }
        assertEquals(emptyList<LinkAction>(), androidTvActions(LinkRouter.classify("http://192.168.1.20/film.mp4")))
        assertEquals(emptyList<LinkAction>(), androidTvActions(LinkRouter.classify("https://example.com/")))
    }

    @Test
    fun `links open as the canonical web URL the app claims`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", appLinkFor(LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ?t=42")))
        assertEquals("https://www.netflix.com/title/80057281", appLinkFor(LinkRouter.classify("https://www.netflix.com/lt-en/watch/80057281?trackId=1")))
        val disney = "https://www.disneyplus.com/video/abc"
        assertEquals(disney, appLinkFor(LinkTarget.Streaming(disney, StreamingService.DISNEY_PLUS)))
        assertNull(appLinkFor(LinkTarget.Web("https://example.com/")))
    }

    @Test
    fun `only YouTube searches, through its results link`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=lo-fi%20beats",
            searchLinkFor("lo-fi beats", SearchTarget.InApp(AndroidTvApps.YOUTUBE)),
        )
        assertNull(searchLinkFor("lo-fi beats", SearchTarget.InApp(AndroidTvApps.NETFLIX)))
        assertNull(searchLinkFor("lo-fi beats", SearchTarget.Web(SearchEngine.DUCKDUCKGO)))
        assertEquals(listOf(AndroidTvApps.YOUTUBE), AndroidTvApps.curated.filter { it.searchable }.map { it.id })
    }

    @Test
    fun `apps launch by package through the Play Store's launch link`() {
        assertEquals("market://launch?id=com.netflix.ninja", launchLinkFor(AndroidTvApps.NETFLIX))
        assertEquals("https://www.netflix.com/title/1", launchLinkFor("https://www.netflix.com/title/1"))
        assertEquals(AndroidTvApps.curated.size, AndroidTvApps.curated.map { it.id }.toSet().size)
    }

    @Test
    fun `volume shows as a share of the TV's own maximum`() {
        assertEquals(12, volumePercent(RemoteEvent.Volume(level = 12, max = 100, muted = false)))
        assertEquals(47, volumePercent(RemoteEvent.Volume(level = 7, max = 15, muted = false)))
        assertNull(volumePercent(RemoteEvent.Volume(level = 0, max = 0, muted = false)))
    }

    @Test
    fun `setting the volume is a count of key presses`() {
        assertEquals(8, volumeSteps(RemoteEvent.Volume(level = 7, max = 15, muted = false), 100))
        assertEquals(-20, volumeSteps(RemoteEvent.Volume(level = 50, max = 100, muted = false), 30))
        assertEquals(0, volumeSteps(RemoteEvent.Volume(level = 7, max = 15, muted = false), 47))
        assertEquals(0, volumeSteps(RemoteEvent.Volume(level = 0, max = 0, muted = false), 50))
    }

    @Test
    fun `features are what the protocol can actually do`() {
        assertTrue(Feature.TEXT_INPUT in ANDROID_TV_FEATURES)
        assertTrue(Feature.VOLUME_LEVEL in ANDROID_TV_FEATURES)
        assertTrue(Feature.MUTE_STATE in ANDROID_TV_FEATURES)
        for (missing in listOf(Feature.POINTER, Feature.APP_LIST, Feature.WEB_LINKS, Feature.MEDIA_CAST, Feature.SCREEN_OFF, Feature.KEYBOARD_STATE)) {
            assertFalse(missing.name, missing in ANDROID_TV_FEATURES)
        }
    }

    @Test
    fun `a pin keeps both ports' certificates`() {
        val fresh = TvPin("aa")
        assertTrue(fresh.admits("bb"))
        assertEquals(fresh, TvPin.decode(fresh.encode()))
        val learned = fresh.copy(remote = "bb")
        assertEquals("aa bb", learned.encode())
        assertEquals(learned, TvPin.decode("aa bb"))
        assertTrue(learned.admits("bb"))
        assertFalse(learned.admits("cc"))
        assertNull(TvPin.decode(null))
        assertNull(TvPin.decode(""))
    }

    @Test
    fun `a turned-away certificate is told apart from a TV that isn't there`() {
        assertTrue(SSLHandshakeException("Received fatal alert: bad_certificate").isRejection())
        assertTrue(SocketException("Connection reset").isRejection())
        assertTrue(EOFException().isRejection())
        assertFalse(ConnectException("Connection refused").isRejection())
        assertFalse(SocketTimeoutException("Read timed out").isRejection())
        assertFalse(SSLException("handshake", SocketTimeoutException("Read timed out")).isRejection())
        assertFalse(SocketException("Socket closed").isRejection())
    }
}
