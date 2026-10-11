package lt.tsx.tennarc.link

import lt.tsx.tennarc.tv.TvApp

sealed interface Suggestion {
    val key: String
        get() = when (this) {
            is TypeOnTv -> "type"
            is OpenLink -> "link:$action"
            is LaunchApp -> "app:${app.id}"
            is OpenTitle -> "title:${title.target.url}"
            is SearchApp -> "search:${app.id}"
            is SearchWeb -> "web"
        }

    data class TypeOnTv(val text: String, val replace: Int) : Suggestion
    data class OpenLink(val target: LinkTarget, val action: LinkAction) : Suggestion
    data class LaunchApp(val app: TvApp) : Suggestion
    data class SearchApp(val app: TvApp, val query: String) : Suggestion
    data class SearchWeb(val engine: SearchEngine, val query: String) : Suggestion

    data class OpenTitle(val title: StreamingTitle, val target: LinkTarget, val app: TvApp? = null) : Suggestion
}

object Omnibox {
    const val MAX_APPS = 3
    const val MAX_TITLES = 3
    const val MAX_VIDEOS = 3

    fun suggest(
        text: String,
        apps: List<TvApp>,
        tvText: Int?,
        engine: SearchEngine?,
        titles: List<StreamingTitle> = emptyList(),
        opens: (LinkTarget) -> List<LinkAction> = { it.possibleActions },
    ): List<Suggestion> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val url = LinkRouter.extract(query)
        val onlyLink = url != null && "://" in query
        return buildList {
            if (tvText != null) add(Suggestion.TypeOnTv(query, tvText))
            if (url != null) {
                val target = LinkRouter.classify(url)
                opens(target).forEach { add(Suggestion.OpenLink(target, it)) }
            }
            if (!onlyLink) {
                val named = matchingApps(apps, query)
                named.forEach { add(Suggestion.LaunchApp(it)) }
                val openable = titles.mapNotNull { openTitle(it, apps, opens) }
                addAll(openable.filter { !it.title.video }.take(MAX_TITLES))
                addAll(openable.filter { it.title.video }.take(MAX_VIDEOS))
                apps.filter { it.searchable && it !in named }.forEach { add(Suggestion.SearchApp(it, query)) }
                engine?.let { add(Suggestion.SearchWeb(it, query)) }
            }
        }
    }

    private fun openTitle(title: StreamingTitle, apps: List<TvApp>, opens: (LinkTarget) -> List<LinkAction>): Suggestion.OpenTitle? {
        title.offers.firstOrNull { LinkAction.APP in opens(it) }?.let { return Suggestion.OpenTitle(title, it) }
        return title.offers.firstNotNullOfOrNull { offer ->
            offer.service?.let { appFor(it, apps) }?.let { Suggestion.OpenTitle(title, offer, it) }
        }
    }

    private fun appFor(service: StreamingService, apps: List<TvApp>): TvApp? =
        apps.firstOrNull { it.id in service.appIds } ?: apps.firstOrNull { !it.input && it.title.equals(service.label, ignoreCase = true) }

    fun titleMatches(title: String, query: String): Boolean {
        val titleWords = words(title)
        val queryWords = words(query)
        return queryWords.isNotEmpty() && queryWords.all { typed -> titleWords.any { it.startsWith(typed) } }
    }

    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(NOT_WORD).filter { it.isNotEmpty() }

    fun matchingApps(apps: List<TvApp>, query: String): List<TvApp> {
        val wanted = query.lowercase()
        return apps
            .mapNotNull { app ->
                val title = app.title.lowercase()
                val rank = when {
                    title.startsWith(wanted) -> 0
                    title.split(' ', '-', '+', '.', '&').any { it.startsWith(wanted) } -> 1
                    wanted.length >= 3 && wanted in title -> 2
                    else -> return@mapNotNull null
                }
                rank to app
            }
            .sortedWith(compareBy({ it.first }, { it.second.title.length }))
            .take(MAX_APPS)
            .map { it.second }
    }
}
