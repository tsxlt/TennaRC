package lt.tsx.tennarc.link

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.USER_AGENT
import lt.tsx.tennarc.icons.PosterLoader
import lt.tsx.tennarc.logFailure
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class StreamingTitle(
    val title: String,
    val target: LinkTarget,
    val year: Int? = null,
    val show: Boolean? = null,
    val posterUrl: String? = null,
    val alternatives: List<LinkTarget> = emptyList(),
    val byline: String? = null,
    val video: Boolean = false,
) {
    val offers: List<LinkTarget> get() = listOf(target) + alternatives
}

fun interface TitleSource {
    suspend fun find(query: String): List<StreamingTitle>
}

class TitleFinder(
    private val catalogue: List<TitleSource>,
    private val videos: TitleSource? = null,
    private val picks: (suspend (Collection<StreamingService>) -> List<StreamingTitle>)? = null,
) {
    suspend fun picks(services: Collection<StreamingService>): List<StreamingTitle> =
        picks?.let { find -> answerOrNothing { find(services) } }.orEmpty()

    suspend fun find(query: String): List<StreamingTitle> = coroutineScope {
        val clips = videos?.let { source -> async { answerOrNothing { source.find(query) } } }
        var titles = emptyList<StreamingTitle>()
        for (source in catalogue) {
            try {
                titles = source.find(query)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("Titles.find", e)
            }
        }
        titles + clips?.await().orEmpty()
    }

    private suspend fun answerOrNothing(ask: suspend () -> List<StreamingTitle>): List<StreamingTitle> = try {
        ask()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure("Titles.answerOrNothing", e)
        emptyList()
    }
}

private val JSON = "application/json".toMediaType()

private const val MAX_REPLY = 512 * 1024

private suspend fun OkHttpClient.fetch(request: Request, limit: Int = MAX_REPLY): String = withContext(Dispatchers.IO) {
    runInterruptible {
        newCall(request.newBuilder().header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${request.url.host}")
            val source = response.body.source()
            if (source.request(limit + 1L)) throw IOException("Reply too large")
            source.readUtf8()
        }
    }
}

private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).ifEmpty { null }

private fun serviceLink(url: String, service: StreamingService): LinkTarget? = when (service) {
    StreamingService.NETFLIX -> LinkRouter.classify(url).takeIf { it is LinkTarget.Netflix }
    StreamingService.YOUTUBE -> LinkRouter.classify(url).takeIf { it is LinkTarget.YouTube }
    else -> LinkTarget.Streaming(url, service)
}

class JustWatch(private val http: OkHttpClient, private val country: () -> String) : TitleSource {
    override suspend fun find(query: String): List<StreamingTitle> {
        val body = JSONObject()
            .put("query", QUERY)
            .put(
                "variables",
                JSONObject()
                    .put("country", country())
                    .put("language", "en")
                    .put("first", 8)
                    .put("filter", JSONObject().put("searchQuery", query)),
            )
        val request = Request.Builder().url(ENDPOINT).post(body.toString().toRequestBody(JSON)).build()
        return parse(http.fetch(request))
    }

    suspend fun picks(services: Collection<StreamingService>, year: Int = java.time.Year.now().value): List<StreamingTitle> {
        val codes = services.mapNotNull { it.justWatchCode }
        if (codes.isEmpty()) return emptyList()
        val recent = trending(codes, since = year - 1)
        if (recent.size >= PICKS_ENOUGH) return recent
        return (recent + trending(codes, since = null)).distinctBy { it.target.url }
    }

    private suspend fun trending(codes: List<String>, since: Int?): List<StreamingTitle> {
        val filter = JSONObject()
            .put("objectTypes", JSONArray().put("MOVIE").put("SHOW"))
            .put("packages", JSONArray(codes))
            .put("imdbScore", JSONObject().put("min", 7.0))
        if (since != null) filter.put("releaseYear", JSONObject().put("min", since))
        val body = JSONObject()
            .put("query", PICKS_QUERY)
            .put(
                "variables",
                JSONObject().put("country", country()).put("language", "en").put("first", 20).put("filter", filter),
            )
        val request = Request.Builder().url(ENDPOINT).post(body.toString().toRequestBody(JSON)).build()
        return parse(http.fetch(request))
    }

    companion object {
        private const val ENDPOINT = "https://apis.justwatch.com/graphql"
        private const val IMAGES = "https://images.justwatch.com"
        private val SUBSCRIPTION = setOf("FLATRATE", "FREE", "ADS")
        private const val QUERY = "query Search(\$country: Country!, \$language: Language!, \$first: Int!, \$filter: TitleFilter) { " +
            "popularTitles(country: \$country, first: \$first, filter: \$filter) { edges { node { objectType " +
            "content(country: \$country, language: \$language) { title originalReleaseYear posterUrl } " +
            "offers(country: \$country, platform: WEB) { monetizationType standardWebURL package { technicalName } } } } } }"

        private val PICKS_QUERY = QUERY.replace("filter: \$filter)", "filter: \$filter, sortBy: TRENDING)")

        private const val PICKS_ENOUGH = 12

        internal fun parse(reply: String): List<StreamingTitle> {
            val root = JSONObject(reply)
            if (root.has("errors")) throw IOException("JustWatch: ${root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")}")
            val edges = root.optJSONObject("data")?.optJSONObject("popularTitles")?.optJSONArray("edges")
                ?: throw IOException("JustWatch: no titles in the reply")
            return edges.objects().mapNotNull { edge ->
                val node = edge.optJSONObject("node") ?: return@mapNotNull null
                val content = node.optJSONObject("content") ?: return@mapNotNull null
                val links = (node.optJSONArray("offers") ?: JSONArray()).objects()
                    .filter { it.optString("monetizationType") in SUBSCRIPTION }
                    .mapNotNull { offer ->
                        val url = offer.text("standardWebURL") ?: return@mapNotNull null
                        val service = offer.optJSONObject("package")?.text("technicalName")?.let(StreamingService::byJustWatch)
                            ?: LinkRouter.classify(url).service
                            ?: return@mapNotNull null
                        serviceLink(url, service)
                    }
                    .distinctBy { it.service }
                if (links.isEmpty()) return@mapNotNull null
                StreamingTitle(
                    title = content.text("title") ?: return@mapNotNull null,
                    target = links.first(),
                    alternatives = links.drop(1),
                    year = content.optInt("originalReleaseYear").takeIf { it > 0 },
                    show = when (node.optString("objectType")) {
                        "SHOW" -> true
                        "MOVIE" -> false
                        else -> null
                    },
                    posterUrl = content.text("posterUrl")?.let { IMAGES + it.replace("{profile}", "s166").replace("{format}", "webp") },
                )
            }.distinctBy { it.target.url }
        }
    }
}

class Wikidata(private val http: OkHttpClient) : TitleSource {
    override suspend fun find(query: String): List<StreamingTitle> {
        val search = http.fetch(Request.Builder().url("$API?action=wbsearchentities&type=item&limit=10&language=en&format=json&search=${queryParam(query)}").build())
        val ids = parseSearch(search)
        if (ids.isEmpty()) return emptyList()
        val entities = http.fetch(Request.Builder().url("$API?action=wbgetentities&props=claims%7Clabels&languages=en&format=json&ids=${ids.joinToString("%7C")}").build())
        return parseEntities(entities, ids)
    }

    companion object {
        private const val API = "https://www.wikidata.org/w/api.php"
        private const val NETFLIX_ID = "P1874"
        private val SHOWS = setOf("Q5398426", "Q1259759", "Q63952888", "Q526877", "Q581714", "Q117467246")
        private val FILMS = setOf("Q11424", "Q24869", "Q506240", "Q202866", "Q93204", "Q24862")

        internal fun parseSearch(reply: String): List<String> =
            (JSONObject(reply).optJSONArray("search") ?: throw IOException("Wikidata: no search results"))
                .objects().mapNotNull { it.text("id") }

        internal fun parseEntities(reply: String, order: List<String>): List<StreamingTitle> {
            val entities = JSONObject(reply).optJSONObject("entities") ?: throw IOException("Wikidata: no entities")
            return order.mapNotNull { id ->
                val entity = entities.optJSONObject(id) ?: return@mapNotNull null
                val claims = entity.optJSONObject("claims") ?: return@mapNotNull null
                val netflix = claims.values(NETFLIX_ID).firstOrNull { it is String } as String? ?: return@mapNotNull null
                val kinds = claims.values("P31").mapNotNull { (it as? JSONObject)?.text("id") }.toSet()
                val date = (claims.values("P577") + claims.values("P580")).firstNotNullOfOrNull { (it as? JSONObject)?.text("time") }
                StreamingTitle(
                    title = entity.optJSONObject("labels")?.optJSONObject("en")?.text("value") ?: return@mapNotNull null,
                    target = serviceLink("https://www.netflix.com/title/$netflix", StreamingService.NETFLIX) ?: return@mapNotNull null,
                    year = date?.let { Regex("""^[+]?(\d{4})-""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
                    show = when {
                        kinds.any { it in SHOWS } -> true
                        kinds.any { it in FILMS } -> false
                        else -> null
                    },
                )
            }.distinctBy { it.target.url }
        }

        private fun JSONObject.values(property: String): List<Any> =
            (optJSONArray(property) ?: JSONArray()).objects().mapNotNull { it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.opt("value") }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

class YouTubeVideos(private val http: OkHttpClient, private val country: () -> String) : TitleSource {
    override suspend fun find(query: String): List<StreamingTitle> {
        val client = JSONObject()
            .put("clientName", "MWEB")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", country())
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("query", query)
        val request = Request.Builder().url(ENDPOINT).post(body.toString().toRequestBody(JSON)).build()
        return parse(http.fetch(request, limit = 2 * 1024 * 1024))
    }

    companion object {
        private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        private const val CLIENT_VERSION = "2.20250930.01.00"
        private val RENDERERS = setOf("videoWithContextRenderer", "videoRenderer", "compactVideoRenderer")
        private const val MAX_VIDEOS = 6

        internal fun parse(reply: String): List<StreamingTitle> {
            val found = mutableListOf<JSONObject>()
            collect(JSONObject(reply), found)
            if (found.isEmpty() && !reply.contains("\"contents\"")) throw IOException("YouTube: no results in the reply")
            return found.mapNotNull { video ->
                val id = video.text("videoId") ?: return@mapNotNull null
                val title = video.optJSONObject("headline")?.firstText() ?: video.optJSONObject("title")?.firstText() ?: return@mapNotNull null
                val channel = listOf("shortBylineText", "ownerText", "longBylineText").firstNotNullOfOrNull { video.optJSONObject(it)?.firstText() }
                val target = serviceLink("https://www.youtube.com/watch?v=$id", StreamingService.YOUTUBE) ?: return@mapNotNull null
                StreamingTitle(title, target, posterUrl = PosterLoader.youTubeStill(id), byline = channel, video = true)
            }.distinctBy { it.target.url }.take(MAX_VIDEOS)
        }

        private fun collect(node: Any?, into: MutableList<JSONObject>) {
            when (node) {
                is JSONObject -> node.keys().forEach { key ->
                    val child = node.opt(key)
                    if (key in RENDERERS && child is JSONObject) into += child else collect(child, into)
                }
                is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), into)
            }
        }

        private fun JSONObject.firstText(): String? =
            text("simpleText") ?: optJSONArray("runs")?.objects()?.joinToString("") { it.optString("text") }?.ifEmpty { null }
    }
}
