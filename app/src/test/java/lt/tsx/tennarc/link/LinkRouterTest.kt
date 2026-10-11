package lt.tsx.tennarc.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkRouterTest {
    @Test
    fun `finds the link inside shared text`() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            LinkRouter.extract("Watch this! https://youtu.be/dQw4w9WgXcQ?si=abc (shared via YouTube)"),
        )
    }

    @Test
    fun `drops trailing punctuation`() {
        assertEquals("https://example.com/page", LinkRouter.extract("see https://example.com/page."))
        assertEquals("https://example.com/a_(b)", LinkRouter.extract("https://example.com/a_(b)"))
    }

    @Test
    fun `accepts a bare domain typed by hand`() {
        assertEquals("https://lrt.lt", LinkRouter.extract("lrt.lt"))
        assertEquals("https://example.com/x?y=1", LinkRouter.extract("  example.com/x?y=1 "))
    }

    @Test
    fun `a typed LAN address gets plain http`() {
        assertEquals("http://192.168.1.42:8088/a.mp4", LinkRouter.extract("192.168.1.42:8088/a.mp4"))
        assertEquals("http://nas.local/films", LinkRouter.extract("nas.local/films"))
        assertEquals("https://lrt.lt/mediateka", LinkRouter.extract("lrt.lt/mediateka"))
    }

    @Test
    fun `rejects text without a link`() {
        assertNull(LinkRouter.extract("dinner is ready"))
        assertNull(LinkRouter.extract(""))
    }

    @Test
    fun `recognises YouTube links in all their shapes`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id&t=42",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id?si=xyz",
            "https://youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id?feature=shared",
            "https://music.youtube.com/watch?v=$id",
        ).forEach { url ->
            assertEquals(url, LinkTarget.YouTube(url, id), LinkRouter.classify(url))
        }
    }

    @Test
    fun `a YouTube page without a video is just a web page`() {
        val url = "https://www.youtube.com/@channel"
        assertEquals(LinkTarget.Web(url), LinkRouter.classify(url))
    }

    @Test
    fun `recognises Netflix titles`() {
        assertEquals(
            LinkTarget.Netflix("https://www.netflix.com/watch/80057281?trackId=1", "80057281"),
            LinkRouter.classify("https://www.netflix.com/watch/80057281?trackId=1"),
        )
        assertEquals(
            LinkTarget.Netflix("https://www.netflix.com/lt-en/title/80057281", "80057281"),
            LinkRouter.classify("https://www.netflix.com/lt-en/title/80057281"),
        )
    }

    @Test
    fun `media files go to the TV's player with a pinned type`() {
        assertEquals(
            LinkTarget.Media("http://nas.local/films/Big%20Buck%20Bunny.mkv", "video/x-matroska", "Big Buck Bunny"),
            LinkRouter.classify("http://nas.local/films/Big%20Buck%20Bunny.mkv"),
        )
        assertEquals("video/mp2t", (LinkRouter.classify("http://x/a.TS") as LinkTarget.Media).mime)
    }

    @Test
    fun `formats the TV can't play stay in the browser`() {
        val url = "https://example.com/clip.webm"
        assertEquals(LinkTarget.Web(url), LinkRouter.classify(url))
    }

    @Test
    fun `actions put the best route first`() {
        assertEquals(listOf(LinkAction.APP, LinkAction.BROWSER), LinkTarget.YouTube("u", "id").possibleActions)
        assertEquals(listOf(LinkAction.APP), LinkTarget.Netflix("u", "1").possibleActions)
        assertEquals(listOf(LinkAction.APP), LinkTarget.Streaming("u", StreamingService.DISNEY_PLUS).possibleActions)
        assertEquals(listOf(LinkAction.PLAY_MEDIA, LinkAction.BROWSER), LinkTarget.Media("u", "video/mp4", "t").possibleActions)
        assertEquals(listOf(LinkAction.BROWSER), LinkTarget.Web("u").possibleActions)
    }

    @Test
    fun `other services' links belong in their apps`() {
        val url = "https://www.disneyplus.com/browse/entity-1234"
        assertEquals(LinkTarget.Streaming(url, StreamingService.DISNEY_PLUS), LinkRouter.classify(url))
        assertEquals(StreamingService.MAX, LinkRouter.classify("https://play.max.com/show/abc").service)
        assertEquals(LinkTarget.Web("https://www.disneyplus.com/"), LinkRouter.classify("https://www.disneyplus.com/"))
    }
}
