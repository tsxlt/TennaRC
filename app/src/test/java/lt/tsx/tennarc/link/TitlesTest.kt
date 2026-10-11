package lt.tsx.tennarc.link

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class TitlesTest {
    @Test
    fun `JustWatch keeps every service a title streams on, once each, with posters`() {
        val reply = """
            {"data":{"popularTitles":{"edges":[
              {"node":{"objectType":"SHOW","content":{"title":"Stranger Things","originalReleaseYear":2016,"posterUrl":"/poster/301444843/{profile}/.{format}"},
                "offers":[{"monetizationType":"FLATRATE","standardWebURL":"https://www.netflix.com/title/80057281","package":{"technicalName":"netflix"}},
                          {"monetizationType":"FLATRATE","standardWebURL":"https://www.netflix.com/title/80057281","package":{"technicalName":"netflix"}},
                          {"monetizationType":"FLATRATE","standardWebURL":"https://www.go3.lt/stranger-things","package":{"technicalName":"go3"}}]}},
              {"node":{"objectType":"MOVIE","content":{"title":"Nowhere to stream","originalReleaseYear":2013,"posterUrl":null},"offers":[]}},
              {"node":{"objectType":"MOVIE","content":{"title":"Rent only","originalReleaseYear":2020,"posterUrl":null},
                "offers":[{"monetizationType":"RENT","standardWebURL":"https://www.netflix.com/title/1","package":{"technicalName":"netflix"}}]}},
              {"node":{"objectType":"MOVIE","content":{"title":"Elsewhere","originalReleaseYear":2021,"posterUrl":null},
                "offers":[{"monetizationType":"FLATRATE","standardWebURL":"https://www.disneyplus.com/movies/x/1","package":{"technicalName":"disneyplus"}}]}},
              {"node":{"objectType":"MOVIE","content":{"title":"Unknown service","originalReleaseYear":2022,"posterUrl":null},
                "offers":[{"monetizationType":"FLATRATE","standardWebURL":"https://example.com/watch/1","package":{"technicalName":"somethingelse"}}]}}
            ]}}}
        """.trimIndent()
        assertEquals(
            listOf(
                StreamingTitle(
                    "Stranger Things",
                    LinkRouter.classify("https://www.netflix.com/title/80057281"),
                    year = 2016,
                    show = true,
                    posterUrl = "https://images.justwatch.com/poster/301444843/s166/.webp",
                    alternatives = listOf(LinkTarget.Streaming("https://www.go3.lt/stranger-things", StreamingService.GO3)),
                ),
                StreamingTitle(
                    "Elsewhere",
                    LinkTarget.Streaming("https://www.disneyplus.com/movies/x/1", StreamingService.DISNEY_PLUS),
                    year = 2021,
                    show = false,
                ),
            ),
            JustWatch.parse(reply),
        )
    }

    @Test
    fun `YouTube results become videos with stills and channels`() {
        val reply = """
            {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
              {"videoWithContextRenderer":{"videoId":"n61ULEU7CO0","headline":{"runs":[{"text":"Best of lofi"}]},"shortBylineText":{"runs":[{"text":"Lofi Girl"}]}}},
              {"reelItemRenderer":{"videoId":"shortsShort"}},
              {"videoWithContextRenderer":{"videoId":"n61ULEU7CO0","headline":{"runs":[{"text":"Same again"}]}}},
              {"videoRenderer":{"videoId":"_jH2oTNRe7g","title":{"simpleText":"cozy autumn"}}}
            ]}}]}}}
        """.trimIndent()
        assertEquals(
            listOf(
                StreamingTitle(
                    "Best of lofi",
                    LinkRouter.classify("https://www.youtube.com/watch?v=n61ULEU7CO0"),
                    posterUrl = "https://i.ytimg.com/vi/n61ULEU7CO0/mqdefault.jpg",
                    byline = "Lofi Girl",
                    video = true,
                ),
                StreamingTitle(
                    "cozy autumn",
                    LinkRouter.classify("https://www.youtube.com/watch?v=_jH2oTNRe7g"),
                    posterUrl = "https://i.ytimg.com/vi/_jH2oTNRe7g/mqdefault.jpg",
                    video = true,
                ),
            ),
            YouTubeVideos.parse(reply),
        )
    }

    @Test
    fun `JustWatch errors count as down, not as nothing found`() {
        assertThrows(IOException::class.java) { JustWatch.parse("""{"errors":[{"message":"country not supported"}],"data":null}""") }
        assertThrows(IOException::class.java) { JustWatch.parse("""{"data":{}}""") }
    }

    @Test
    fun `Wikidata keeps items with a Netflix ID, in search order`() {
        val ids = Wikidata.parseSearch("""{"search":[{"id":"Q1"},{"id":"Q2"},{"id":"Q3"}]}""")
        assertEquals(listOf("Q1", "Q2", "Q3"), ids)
        val entities = """
            {"entities":{
              "Q3":{"labels":{"en":{"value":"A Film"}},"claims":{
                "P1874":[{"mainsnak":{"datavalue":{"value":"70000001"}}}],
                "P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q11424"}}}}],
                "P577":[{"mainsnak":{"datavalue":{"value":{"time":"+2019-07-04T00:00:00Z"}}}}]}},
              "Q1":{"labels":{"en":{"value":"Stranger Things"}},"claims":{
                "P1874":[{"mainsnak":{"datavalue":{"value":"80057281"}}}],
                "P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q5398426"}}}}],
                "P580":[{"mainsnak":{"datavalue":{"value":{"time":"+2016-07-15T00:00:00Z"}}}}]}},
              "Q2":{"labels":{"en":{"value":"A Novel"}},"claims":{}}
            }}
        """.trimIndent()
        assertEquals(
            listOf(
                StreamingTitle("Stranger Things", LinkRouter.classify("https://www.netflix.com/title/80057281"), year = 2016, show = true),
                StreamingTitle("A Film", LinkRouter.classify("https://www.netflix.com/title/70000001"), year = 2019, show = false),
            ),
            Wikidata.parseEntities(entities, ids),
        )
    }

    private val found = listOf(StreamingTitle("X", LinkRouter.classify("https://www.netflix.com/title/1")))

    @Test
    fun `the fallback answers only when the first source fails`() = runTest {
        val down = TitleSource { throw IOException("down") }
        val nothing = TitleSource { emptyList() }
        val fallback = TitleSource { found }
        assertEquals(found, TitleFinder(listOf(down, fallback)).find("x"))
        assertEquals(emptyList<StreamingTitle>(), TitleFinder(listOf(nothing, fallback)).find("x"))
        assertEquals(emptyList<StreamingTitle>(), TitleFinder(listOf(down, down)).find("x"))
    }

    @Test
    fun `videos come after shows, and their source failing loses only them`() = runTest {
        val clip = listOf(StreamingTitle("clip", LinkRouter.classify("https://youtu.be/aqz-KE-bpKQ"), video = true))
        assertEquals(found + clip, TitleFinder(listOf(TitleSource { found }), TitleSource { clip }).find("x"))
        assertEquals(found, TitleFinder(listOf(TitleSource { found }), TitleSource { throw IOException("down") }).find("x"))
    }
}
