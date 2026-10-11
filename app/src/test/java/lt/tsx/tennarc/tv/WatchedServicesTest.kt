package lt.tsx.tennarc.tv

import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.StreamingTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedServicesTest {
    private val netflix = LinkRouter.classify("https://www.netflix.com/title/1")
    private val max = LinkTarget.Streaming("https://play.max.com/movie/1", StreamingService.MAX)
    private val onBoth = StreamingTitle("Both", max, alternatives = listOf(netflix))
    private val maxOnly = StreamingTitle("Max only", max)
    private val video = StreamingTitle("Clip", LinkRouter.classify("https://youtu.be/aqz-KE-bpKQ"), video = true)

    @Test
    fun `titles only on unwatched services go, and a watched one goes first`() {
        assertEquals(
            listOf(onBoth.copy(target = netflix, alternatives = emptyList()), video),
            keepToWatched(listOf(onBoth, maxOnly, video), setOf(StreamingService.NETFLIX)),
        )
    }

    @Test
    fun `nothing watched yet keeps everything`() {
        assertEquals(listOf(onBoth, maxOnly), keepToWatched(listOf(onBoth, maxOnly), emptySet()))
    }

    @Test
    fun `apps are known on every kind of TV`() {
        assertEquals(StreamingService.NETFLIX, StreamingService.byAppId("netflix"))
        assertEquals(StreamingService.NETFLIX, StreamingService.byAppId("com.netflix.ninja"))
        assertEquals(StreamingService.YOUTUBE, StreamingService.byAppId("youtube.leanback.v4"))
    }
}
