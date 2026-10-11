package lt.tsx.tennarc.link

import lt.tsx.tennarc.link.Suggestion.LaunchApp
import lt.tsx.tennarc.link.Suggestion.OpenLink
import lt.tsx.tennarc.link.Suggestion.SearchApp
import lt.tsx.tennarc.link.Suggestion.SearchWeb
import lt.tsx.tennarc.link.Suggestion.TypeOnTv
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxTest {
    private val youTube = TvApp(TvApps.YOUTUBE, "YouTube", searchable = true)
    private val netflix = TvApp(TvApps.NETFLIX, "Netflix")
    private val browser = TvApp(TvApps.BROWSER, "Web Browser")
    private val apps = listOf(netflix, youTube, browser)
    private val ddg = SearchEngine.DUCKDUCKGO

    @Test
    fun `nothing typed suggests nothing`() {
        assertEquals(emptyList<Suggestion>(), Omnibox.suggest("   ", apps, tvText = null, engine = ddg))
    }

    @Test
    fun `an app's name opens it, without also searching it for its name`() {
        assertEquals(
            listOf(LaunchApp(youTube), SearchWeb(ddg, "you")),
            Omnibox.suggest(" you ", apps, tvText = null, engine = ddg),
        )
    }

    @Test
    fun `a text box on the TV comes first and replaces what is in it`() {
        val rows = Omnibox.suggest("big buck bunny", apps, tvText = 4, engine = ddg)
        assertEquals(TypeOnTv("big buck bunny", replace = 4), rows.first())
        assertEquals(listOf(SearchApp(youTube, "big buck bunny"), SearchWeb(ddg, "big buck bunny")), rows.drop(1))
    }

    @Test
    fun `a full link is only ever opened`() {
        val target = LinkRouter.classify("https://youtu.be/aqz-KE-bpKQ")
        assertEquals(
            listOf(OpenLink(target, LinkAction.APP), OpenLink(target, LinkAction.BROWSER)),
            Omnibox.suggest("https://youtu.be/aqz-KE-bpKQ", apps, tvText = null, engine = ddg),
        )
    }

    @Test
    fun `a bare domain might be a search too`() {
        val rows = Omnibox.suggest("node.js", apps, tvText = null, engine = ddg)
        assertEquals(OpenLink(LinkTarget.Web("https://node.js"), LinkAction.BROWSER), rows.first())
        assertTrue(SearchWeb(ddg, "node.js") in rows)
    }

    @Test
    fun `no browser, no web search`() {
        assertTrue(Omnibox.suggest("cats", apps, tvText = null, engine = null).none { it is SearchWeb })
    }

    @Test
    fun `apps rank by title start, then word start, then anywhere`() {
        val guide = TvApp("guide", "TV Guide")
        val live = TvApp("live", "Live TV")
        val apple = TvApp("apple", "Apple TV")
        assertEquals(listOf(guide, live, apple), Omnibox.matchingApps(listOf(apple, live, guide), "tv"))
        assertEquals(listOf(youTube), Omnibox.matchingApps(apps, "ube"))
        assertEquals(emptyList<TvApp>(), Omnibox.matchingApps(apps, "ub"))
    }

    @Test
    fun `at most three apps`() {
        val many = (1..6).map { TvApp("app$it", "App $it") }
        assertEquals(Omnibox.MAX_APPS, Omnibox.matchingApps(many, "app").size)
    }

    @Test
    fun `search urls encode the query for any decoder`() {
        assertEquals("https://duckduckgo.com/?q=big%20buck%20bunny%20%26%20co", ddg.url("big buck bunny & co"))
        assertEquals("q=a%20b%2Bc", TvApps.searchTarget(TvApps.YOUTUBE, "a b+c"))
        assertNull(TvApps.searchTarget(TvApps.NETFLIX, "a"))
    }

    @Test
    fun `titles come before the generic searches, and only for apps the TV has`() {
        val onNetflix = StreamingTitle("Stranger Things", LinkRouter.classify("https://www.netflix.com/title/80057281"))
        val rows = Omnibox.suggest("stranger", apps, tvText = null, engine = ddg, titles = listOf(onNetflix))
        assertEquals(listOf(Suggestion.OpenTitle(onNetflix, onNetflix.target), SearchApp(youTube, "stranger"), SearchWeb(ddg, "stranger")), rows)
        val noNetflix = Omnibox.suggest("stranger", apps - netflix, tvText = null, engine = ddg, titles = listOf(onNetflix), opens = { emptyList() })
        assertTrue(noNetflix.none { it is Suggestion.OpenTitle })
    }

    @Test
    fun `a title fits while every typed word starts one of its words`() {
        assertTrue(Omnibox.titleMatches("Stranger Things", "stranger t"))
        assertTrue(Omnibox.titleMatches("Stranger Things: Tales from '85", "tales 85"))
        assertTrue(Omnibox.titleMatches("Amélie", "amé"))
        assertTrue(!Omnibox.titleMatches("Stranger Things", "strangers"))
        assertTrue(!Omnibox.titleMatches("Stranger Things", "  "))
    }

    @Test
    fun `row keys survive typing`() {
        assertEquals(SearchWeb(ddg, "a").key, SearchWeb(SearchEngine.GOOGLE, "ab").key)
        assertEquals(SearchApp(youTube, "a").key, SearchApp(youTube, "ab").key)
    }

    @Test
    fun `a title opens on the first of its services the TV can open`() {
        val disney = LinkTarget.Streaming("https://www.disneyplus.com/movies/x/1", StreamingService.DISNEY_PLUS)
        val netflix = LinkRouter.classify("https://www.netflix.com/title/1")
        val both = StreamingTitle("X", disney, alternatives = listOf(netflix))
        val onlyNetflix: (LinkTarget) -> List<LinkAction> = { if (it is LinkTarget.Netflix) listOf(LinkAction.APP) else emptyList() }
        assertEquals(Suggestion.OpenTitle(both, netflix), Omnibox.suggest("x", apps, null, ddg, listOf(both), onlyNetflix).first())
    }

    @Test
    fun `a title the TV can't open at opens its app's start screen, if the TV has the app`() {
        val disney = LinkTarget.Streaming("https://www.disneyplus.com/browse/entity-1", StreamingService.DISNEY_PLUS)
        val mubi = LinkTarget.Streaming("https://mubi.com/films/x", StreamingService.MUBI)
        val title = StreamingTitle("X", disney, alternatives = listOf(mubi))
        val disneyApp = TvApp("com.disney.disneyplus-prod", "Disney+")
        val opensNothing: (LinkTarget) -> List<LinkAction> = { emptyList() }

        val rows = Omnibox.suggest("x", apps + disneyApp, null, ddg, listOf(title), opensNothing)
        assertEquals(Suggestion.OpenTitle(title, disney, disneyApp), rows.first())
        assertTrue(Omnibox.suggest("x", apps, null, ddg, listOf(title), opensNothing).none { it is Suggestion.OpenTitle })
    }

    @Test
    fun `an app whose id isn't known is found by its name`() {
        val telia = LinkTarget.Streaming("https://teliaplay.lt/play/3035/program/lt.telia.title.cinemaclub.3303050", StreamingService.TELIA_PLAY)
        val title = StreamingTitle("Hereditary", telia)
        val teliaApp = TvApp("some.unknown.id", "TELIA PLAY")
        val rows = Omnibox.suggest("hered", apps + teliaApp, null, ddg, listOf(title), opens = { emptyList() })
        assertEquals(Suggestion.OpenTitle(title, telia, teliaApp), rows.first())
        val input = TvApp("HDMI_1", "Telia Play", input = true)
        assertTrue(Omnibox.suggest("hered", apps + input, null, ddg, listOf(title), opens = { emptyList() }).none { it is Suggestion.OpenTitle })
    }

    @Test
    fun `opening at the title beats opening an app, whichever service comes first`() {
        val disney = LinkTarget.Streaming("https://www.disneyplus.com/browse/entity-1", StreamingService.DISNEY_PLUS)
        val netflix = LinkRouter.classify("https://www.netflix.com/title/1")
        val title = StreamingTitle("X", disney, alternatives = listOf(netflix))
        val onlyNetflix: (LinkTarget) -> List<LinkAction> = { if (it is LinkTarget.Netflix) listOf(LinkAction.APP) else emptyList() }
        val rows = Omnibox.suggest("x", apps + TvApp("com.disney.disneyplus-prod", "Disney+"), null, ddg, listOf(title), onlyNetflix)
        assertEquals(Suggestion.OpenTitle(title, netflix), rows.first())
    }

    @Test
    fun `at most three shows and three videos`() {
        val shows = (1..5).map { StreamingTitle("Show $it", LinkRouter.classify("https://www.netflix.com/title/$it")) }
        val videos = (1..5).map { StreamingTitle("Video $it", LinkRouter.classify("https://youtu.be/aqz-KE-bpK$it"), video = true) }
        val rows = Omnibox.suggest("zzz", apps, null, null, shows + videos).filterIsInstance<Suggestion.OpenTitle>()
        assertEquals(listOf("Show 1", "Show 2", "Show 3", "Video 1", "Video 2", "Video 3"), rows.map { it.title.title })
    }
}
