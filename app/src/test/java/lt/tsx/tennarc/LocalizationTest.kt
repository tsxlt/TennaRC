package lt.tsx.tennarc

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    private val res = File("src/main/res")

    private class Entry(val untranslatable: Boolean, val texts: Map<String, String>)

    private fun load(file: File): Map<String, Entry> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val entries = linkedMapOf<String, Entry>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val untranslatable = el.getAttribute("translatable") == "false"
            when (el.tagName) {
                "string" -> entries[el.getAttribute("name")] = Entry(untranslatable, mapOf("" to el.textContent))
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    entries[el.getAttribute("name")] = Entry(untranslatable, (0 until items.length).associate { (items.item(it) as Element).let { item -> item.getAttribute("quantity") to item.textContent } })
                }
            }
        }
        return entries
    }

    private fun placeholders(text: String) = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private val translations: List<File>
        get() = res.listFiles().orEmpty()
            .filter { it.name.startsWith("values-") }
            .map { File(it, "strings.xml") }
            .filter { it.exists() }

    @Test
    fun everyTranslationHasEveryStringAndNothingElse() {
        val english = load(File(res, "values/strings.xml"))
        val wanted = english.filterValues { !it.untranslatable }.keys
        val problems = translations.mapNotNull { file ->
            val keys = load(file).keys
            val missing = wanted - keys
            val extra = keys - wanted
            if (missing.isEmpty() && extra.isEmpty()) null else "${file.parentFile.name}: missing $missing, extra $extra"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun placeholdersSurviveTranslation() {
        val english = load(File(res, "values/strings.xml"))
        val problems = translations.flatMap { file ->
            load(file).mapNotNull { (name, entry) ->
                val want = english[name]?.texts?.get("") ?: english[name]?.texts?.get("other") ?: return@mapNotNull null
                val bad = entry.texts.filter { (quantity, text) ->
                    placeholders(text) != placeholders(want) && !(quantity in setOf("zero", "one", "two") && placeholders(text).isEmpty())
                }
                if (bad.isEmpty()) null else "${file.parentFile.name}: $name ${bad.values}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun apostrophesAndQuotesAreEscaped() {
        val problems = translations.flatMap { file ->
            ENTRY.findAll(file.readText()).map { it.groupValues[1] }
                .filterNot { it.trim().startsWith("\"") && it.trim().endsWith("\"") }
                .filter { UNESCAPED.containsMatchIn(it) }
                .map { "${file.parentFile.name}: $it" }
                .toList()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        val PLACEHOLDER = Regex("%(\\d+\\$)?[sd]")
        val ENTRY = Regex("<(?:string|item)[^>]*>(.*?)</(?:string|item)>", RegexOption.DOT_MATCHES_ALL)
        val UNESCAPED = Regex("(?<!\\\\)['\"]")
    }
}
