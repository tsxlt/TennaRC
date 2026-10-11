package lt.tsx.tennarc.link

import java.net.URLEncoder

enum class SearchEngine(val label: String, private val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    ;

    fun url(query: String): String = template.replace("%s", queryParam(query))
}

fun queryParam(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

sealed interface SearchTarget {
    data class InApp(val appId: String) : SearchTarget

    data class Web(val engine: SearchEngine) : SearchTarget
}
