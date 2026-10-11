package lt.tsx.tennarc.tv.roku

import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RokuLinksTest {
    private val youTube = LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ")
    private val netflix = LinkRouter.classify("https://www.netflix.com/title/80057281")
    private val video = LinkRouter.classify("http://192.168.1.10/films/Big%20Buck%20Bunny.mp4")
    private val song = LinkRouter.classify("http://192.168.1.10/music/song.mp3")
    private val avi = LinkRouter.classify("http://192.168.1.10/old.avi")

    private fun apps(vararg ids: String) = ids.map { TvApp(it, it) }

    @Test
    fun `YouTube and Netflix open in their channels while the app list is unknown`() {
        assertEquals(listOf(LinkAction.APP), rokuActions(youTube, emptyList()))
        assertEquals(listOf(LinkAction.APP), rokuActions(netflix, emptyList()))
    }

    @Test
    fun `a channel the Roku doesn't have can't open anything`() {
        val installed = apps(RokuChannels.NETFLIX)
        assertEquals(emptyList<LinkAction>(), rokuActions(youTube, installed))
        assertEquals(listOf(LinkAction.APP), rokuActions(netflix, installed))
    }

    @Test
    fun `no browser, and no deep links into other services`() {
        assertEquals(emptyList<LinkAction>(), rokuActions(LinkRouter.classify("https://example.com/"), emptyList()))
        assertEquals(emptyList<LinkAction>(), rokuActions(StreamingService.DISNEY_PLUS.sampleLink, emptyList()))
        assertEquals(emptyList<LinkAction>(), rokuActions(StreamingService.PRIME_VIDEO.sampleLink, apps("13")))
    }

    @Test
    fun `media plays only through Media Assistant, in formats it takes`() {
        assertEquals(emptyList<LinkAction>(), rokuActions(video, emptyList()))
        assertEquals(emptyList<LinkAction>(), rokuActions(video, apps(RokuChannels.YOUTUBE)))
        val installed = apps(RokuChannels.MEDIA_ASSISTANT)
        assertEquals(listOf(LinkAction.PLAY_MEDIA), rokuActions(video, installed))
        assertEquals(listOf(LinkAction.PLAY_MEDIA), rokuActions(song, installed))
        assertEquals(emptyList<LinkAction>(), rokuActions(avi, installed))
    }

    @Test
    fun `YouTube deep links take the video id`() {
        assertEquals(
            RokuLaunch("837", listOf("contentID" to "dQw4w9WgXcQ", "mediaType" to "live")),
            rokuLaunch(youTube, LinkAction.APP),
        )
    }

    @Test
    fun `Netflix deep links take the title id`() {
        assertEquals(
            RokuLaunch("12", listOf("contentID" to "80057281", "mediaType" to "movie")),
            rokuLaunch(netflix, LinkAction.APP),
        )
    }

    @Test
    fun `media goes to Media Assistant with its name and format`() {
        assertEquals(
            RokuLaunch(
                "782875",
                listOf("u" to (video as LinkTarget.Media).url, "t" to "v", "videoName" to "Big Buck Bunny", "videoFormat" to "mp4"),
            ),
            rokuLaunch(video, LinkAction.PLAY_MEDIA),
        )
        assertEquals(
            listOf("u" to (song as LinkTarget.Media).url, "t" to "a", "songName" to "song", "songFormat" to "mp3"),
            rokuLaunch(song, LinkAction.PLAY_MEDIA)?.params,
        )
    }

    @Test
    fun `ways a Roku can't open a link launch nothing`() {
        assertNull(rokuLaunch(youTube, LinkAction.BROWSER))
        assertNull(rokuLaunch(video, LinkAction.BROWSER))
        assertNull(rokuLaunch(avi, LinkAction.PLAY_MEDIA))
        assertNull(rokuLaunch(StreamingService.MAX.sampleLink, LinkAction.APP))
    }

    @Test
    fun `remote buttons map to ECP keys`() {
        assertEquals("Select", TvButton.ENTER.ecpKey)
        assertEquals("Up", TvButton.UP.ecpKey)
        assertEquals("Back", TvButton.BACK.ecpKey)
        assertEquals("Home", TvButton.HOME.ecpKey)
        assertEquals("Home", TvButton.EXIT.ecpKey)
        assertEquals("Info", TvButton.INFO.ecpKey)
        assertEquals("Rev", TvButton.REWIND.ecpKey)
        assertEquals("Fwd", TvButton.FAST_FORWARD.ecpKey)
        assertEquals("ChannelUp", TvButton.CHANNEL_UP.ecpKey)
    }

    @Test
    fun `play and pause are the same toggle`() {
        assertEquals("Play", TvButton.PLAY.ecpKey)
        assertEquals("Play", TvButton.PAUSE.ecpKey)
    }

    @Test
    fun `keys a Roku doesn't have do nothing`() {
        listOf(TvButton.STOP, TvButton.MENU, TvButton.INPUT_HUB, TvButton.RED, TvButton.DIGIT_5).forEach {
            assertNull(it.name, it.ecpKey)
        }
    }
}
