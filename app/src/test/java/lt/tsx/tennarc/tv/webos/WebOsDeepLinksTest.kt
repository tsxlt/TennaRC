package lt.tsx.tennarc.tv.webos
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebOsDeepLinksTest {
    private fun offer(url: String, service: StreamingService) = LinkTarget.Streaming(url, service)

    @Test
    fun `YouTube and Netflix keep the formats they had`() {
        assertEquals(
            WebOsLaunch("youtube.leanback.v4", "v=dQw4w9WgXcQ"),
            WebOsDeepLinks.launchFor(LinkRouter.classify("https://youtu.be/dQw4w9WgXcQ")),
        )
        assertEquals(
            WebOsLaunch("netflix", "m=https://www.netflix.com/watch/80057281&source_type=4"),
            WebOsDeepLinks.launchFor(LinkRouter.classify("https://www.netflix.com/title/80057281")),
        )
    }

    @Test
    fun `Prime Video takes the gti, from any of Amazon's hosts`() {
        val gti = "amzn1.dv.gti.e8d4f30e-4ba7-48c0-a9f1-1f168d634f85"
        listOf(
            "https://app.primevideo.com/detail?gti=$gti",
            "https://watch.amazon.com/detail?gti=$gti",
            "https://watch.amazon.co.uk/detail?gti=$gti",
            "https://app.primevideo.com/detail?gti=$gti&ref_=atv_dp_share_mv&r=web",
        ).forEach { url ->
            assertEquals(url, WebOsLaunch("amazon", gti), WebOsDeepLinks.launchFor(offer(url, StreamingService.PRIME_VIDEO)))
        }
    }

    @Test
    fun `a Prime Video link without a gti doesn't open`() {
        assertNull(WebOsDeepLinks.launchFor(offer("https://www.primevideo.com/detail/0KRGHGZCHKS920ZQGY5LBRF7MA/", StreamingService.PRIME_VIDEO)))
        assertNull(WebOsDeepLinks.launchFor(offer("https://www.primevideo.com/", StreamingService.PRIME_VIDEO)))
    }

    @Test
    fun `Apple TV takes the link without its tracking, showId first`() {
        val episode = "https://tv.apple.com/us/episode/good-news-about-hell/umc.cmc.s80mx1ic96pu6ewupz8pfasf" +
            "?at=1000l3V2&ct=app_tvplus&itscg=30200&itsct=justwatch_tv&playableId=tvs.sbd.4000%3AVSEVR0560101&showId=umc.cmc.1srk2goyh2q2zdxcx605w8vtx"
        assertEquals(
            WebOsLaunch(
                "com.apple.appletv",
                "https://tv.apple.com/us/episode/good-news-about-hell/umc.cmc.s80mx1ic96pu6ewupz8pfasf" +
                    "?showId=umc.cmc.1srk2goyh2q2zdxcx605w8vtx&playableId=tvs.sbd.4000%3AVSEVR0560101",
            ),
            WebOsDeepLinks.launchFor(offer(episode, StreamingService.APPLE_TV)),
        )
        val show = "https://tv.apple.com/us/show/the-last-of-us/umc.cmc.43thr5tsmnra1kr4edwimsqvp?at=1000l3V2&ct=app_tv&itscg=30200&itsct=justwatch_tv"
        assertEquals(
            WebOsLaunch("com.apple.appletv", "https://tv.apple.com/us/show/the-last-of-us/umc.cmc.43thr5tsmnra1kr4edwimsqvp"),
            WebOsDeepLinks.launchFor(offer(show, StreamingService.APPLE_TV)),
        )
        val film = "https://tv.apple.com/lt/movie/andor/umc.cmc.1d7nb6rg92uys0talca99ffbk?at=1000l3V2&ct=app_tv&itscg=30200&itsct=justwatch_tv&playableId=tvs.sbd.9001%3A1658629995"
        assertEquals(
            WebOsLaunch("com.apple.appletv", "https://tv.apple.com/lt/movie/andor/umc.cmc.1d7nb6rg92uys0talca99ffbk?playableId=tvs.sbd.9001%3A1658629995"),
            WebOsDeepLinks.launchFor(offer(film, StreamingService.APPLE_TV)),
        )
    }

    @Test
    fun `Apple TV pages that aren't a title don't open`() {
        assertNull(WebOsDeepLinks.launchFor(offer("https://tv.apple.com/us/channel/tvs.sbd.4000", StreamingService.APPLE_TV)))
        assertNull(WebOsDeepLinks.launchFor(offer("https://tv.apple.com/", StreamingService.APPLE_TV)))
    }

    @Test
    fun `services with no known format don't open at the title`() {
        listOf(
            offer("https://www.disneyplus.com/browse/entity-faba988a-a9f5-45f2-a074-0775a7d6f67a", StreamingService.DISNEY_PLUS),
            offer("https://mubi.com/films/perfect-days", StreamingService.MUBI),
            offer("https://go3.lt/deeplink/detail/serial/the-last-of-us-podcast,11750895", StreamingService.GO3),
            offer("https://www.crunchyroll.com/watch/G0DUND0K2/the-journeys-end", StreamingService.CRUNCHYROLL),
            offer("https://teliaplay.lt/play/3035/program/lt.telia.title.cinemaclub.3303050", StreamingService.TELIA_PLAY),
            offer("https://play.hbomax.com/video/watch/303b13fe-711a-49dc-b64d-ebe4e6d7e814?utm_source=universal_search", StreamingService.MAX),
            offer("https://www.skyshowtime.com/nl/stream/tv/1923/6dd877bc-d6fa-3838-be58-1a147dc5ce36", StreamingService.SKYSHOWTIME),
            offer("https://watch.plex.tv/movie/the-terminator", StreamingService.PLEX),
        ).forEach { assertNull(it.url, WebOsDeepLinks.launchFor(it)) }
    }

    @Test
    fun `services without a format are known by their webOS apps, for search to open those`() {
        mapOf(
            "com.disney.disneyplus-prod" to StreamingService.DISNEY_PLUS,
            "com.wbd.stream" to StreamingService.MAX,
            "com.hbo.hbomax" to StreamingService.MAX,
            "lt.go3.lg.tv" to StreamingService.GO3,
            "com.mubi" to StreamingService.MUBI,
            "cdp-30" to StreamingService.PLEX,
        ).forEach { (appId, service) -> assertEquals(appId, service, StreamingService.byAppId(appId)) }
    }

    @Test
    fun `sample links open where the service has a format`() {
        assertEquals("amazon", WebOsDeepLinks.launchFor(StreamingService.PRIME_VIDEO.sampleLink)?.appId)
        assertEquals("com.apple.appletv", WebOsDeepLinks.launchFor(StreamingService.APPLE_TV.sampleLink)?.appId)
    }

    @Test
    fun `media and web pages are no app's`() {
        assertNull(WebOsDeepLinks.launchFor(LinkRouter.classify("http://nas.local/film.mp4")))
        assertNull(WebOsDeepLinks.launchFor(LinkRouter.classify("https://example.com/")))
    }
}
