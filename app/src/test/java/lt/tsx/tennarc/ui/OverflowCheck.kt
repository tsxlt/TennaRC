package lt.tsx.tennarc.ui

import android.icu.text.BreakIterator
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.MultiParagraphIntrinsics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import java.io.File
import java.text.Normalizer
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal enum class Overflow(val note: String) {
    ELLIPSIS("ellipsis"),

    WIDTH("clipped sideways"),

    HEIGHT("clipped below"),

    SHRUNK("shrunk"),

    SPLIT("word split"),

    HIDDEN("left out"),
}

internal data class Finding(val locale: String, val screen: String, val names: String, val text: String, val kind: Overflow, val detail: String)

private const val SHRINK_FLOOR = 0.84f

private const val SLACK = 2f

private const val ENDLESS = 1e6f

private const val LINE_GIVE = 0.25f

internal fun overflowing(nodes: List<SemanticsNode>, locale: Locale): List<Pair<String, Pair<Overflow, String>>> =
    nodes.mapNotNull { node ->
        if (node.config.getOrNull(SemanticsProperties.EditableText) != null) return@mapNotNull null
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
        if (text.isBlank()) return@mapNotNull null
        if (!node.layoutInfo.isPlaced) {
            return@mapNotNull if (node.layoutInfo.parentInfo?.isPlaced == true) text to (Overflow.HIDDEN to "measured, not placed") else null
        }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.firstOrNull() ?: return@mapNotNull null
        overflowOf(layout, locale)?.let { text to it }
    }

private fun overflowOf(layout: TextLayoutResult, locale: Locale): Pair<Overflow, String>? {
    val lines = layout.lineCount
    val style = layout.layoutInput.style
    val box = "${layout.size.width}x${layout.size.height}px, $lines line${if (lines == 1) "" else "s"}, ${style.fontSize}"
    if (lines > 0 && (0 until lines).any { layout.isLineEllipsized(it) }) return Overflow.ELLIPSIS to "${need(layout)} in $box"
    val endless = layout.multiParagraph.width > ENDLESS
    val wide = (0 until lines).maxOfOrNull {
        if (endless) layout.multiParagraph.getLineWidth(it) else layout.getLineRight(it) - layout.getLineLeft(it)
    } ?: 0f
    if (wide > layout.size.width + SLACK) return Overflow.WIDTH to "${wide.toInt()}px wide in $box"
    val lastLine = if (lines > 0) layout.getLineBottom(lines - 1) - layout.getLineTop(lines - 1) else 0f
    if (layout.multiParagraph.height - layout.size.height > lastLine * LINE_GIVE) return Overflow.HEIGHT to "${layout.multiParagraph.height.toInt()}px tall in $box"
    if (layout.multiParagraph.didExceedMaxLines) return Overflow.HEIGHT to "${need(layout)} in $box"
    shrinkOf(layout, wide)?.let { share ->
        if (share < SHRINK_FLOOR) return Overflow.SHRUNK to "drawn at ${(share * 100).toInt()}% of its ${style.fontSize}"
    }
    splitWord(layout, locale)?.let { return Overflow.SPLIT to it }
    return null
}

private fun need(layout: TextLayoutResult): String {
    val input = layout.layoutInput
    val intrinsics = MultiParagraphIntrinsics(input.text, input.style, emptyList(), input.density, input.fontFamilyResolver)
    if (input.maxLines == 1 || !input.softWrap) return "needs ${(intrinsics.maxIntrinsicWidth * 100 / layout.size.width).toInt()}% of its width"
    val all = MultiParagraph(intrinsics, Constraints(maxWidth = layout.size.width), maxLines = Int.MAX_VALUE)
    return "needs ${all.lineCount} of ${input.maxLines} lines"
}

private fun shrinkOf(layout: TextLayoutResult, wide: Float): Float? {
    if (layout.lineCount != 1) return null
    val input = layout.layoutInput
    val natural = MultiParagraphIntrinsics(input.text, input.style, emptyList(), input.density, input.fontFamilyResolver).maxIntrinsicWidth
    return if (natural > 0f) wide / natural else null
}

private fun splitWord(layout: TextLayoutResult, locale: Locale): String? {
    val text = layout.layoutInput.text.text
    if (layout.lineCount < 2) return null
    val words = BreakIterator.getWordInstance(locale).apply { setText(text) }
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        val before = text.codePointBefore(end)
        val after = text.codePointAt(end)
        if (!Character.isLetterOrDigit(before) || !(Character.isLetterOrDigit(after) || Character.getType(after) in MARKS)) continue
        if (Character.UnicodeScript.of(before) in ANY_BREAK || Character.UnicodeScript.of(after) in ANY_BREAK) continue
        if (!words.isBoundary(end)) return "line ${line + 1} ends at \"${text.substring(maxOf(0, end - 8), end)}|${text.substring(end, minOf(text.length, end + 8))}\""
    }
    return null
}

private val MARKS = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())

private val ANY_BREAK = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)

internal class StringIndex(res: File, folder: String) {
    private class Entry(val name: String, val text: String) {
        val folded = fold(text)
        val pattern: Regex? = if (PLACEHOLDER.containsMatchIn(text) && PLACEHOLDER.replace(text, "").isNotBlank()) {
            Regex(PLACEHOLDER.split(text).joinToString("(.+?)") { Regex.escape(fold(it)) }, RegexOption.DOT_MATCHES_ALL)
        } else {
            null
        }
    }

    private val entries: List<Entry> = run {
        val english = load(File(res, "values/strings.xml"))
        val own = if (folder == "values") emptyMap() else load(File(res, "$folder/strings.xml"))
        (english + own).map { (name, text) -> Entry(name, text) }
    }

    fun namesOf(text: String): List<String> {
        val folded = fold(text)
        entries.filter { it.folded == folded }.takeIf { it.isNotEmpty() }?.let { return it.map(Entry::name) }
        entries.filter { it.pattern?.matches(folded) == true }.takeIf { it.isNotEmpty() }?.let { return it.map(Entry::name) }
        return entries.filter { it.pattern == null && it.folded.length >= 4 && folded.contains(it.folded) }
            .sortedByDescending { it.folded.length }
            .take(1)
            .map { "~" + it.name }
    }

    private companion object {
        val PLACEHOLDER = Regex("%(\\d+\\$)?[sd]")

        fun unescape(raw: String): String {
            var text = raw.trim()
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
            else text = text.replace(Regex("\\s+"), " ")
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    val next = text[i + 1]
                    when (next) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> if (i + 5 < text.length) {
                            out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(next)
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        fun load(file: File): Map<String, String> {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val strings = linkedMapOf<String, String>()
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                when (el.tagName) {
                    "string" -> strings[el.getAttribute("name")] = unescape(el.textContent)
                    "plurals" -> {
                        val items = el.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            strings[el.getAttribute("name") + "[" + item.getAttribute("quantity") + "]"] = unescape(item.textContent)
                        }
                    }
                }
            }
            return strings
        }

        fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[\\u00AD\\u200B-\\u200D\\u2060]"), "")
            .replace(Regex("[\\s\\u00A0\\u202F]+"), " ")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .trim()
    }
}

internal fun languageTag(folder: String): String {
    val qualifier = folder.removePrefix("values").removePrefix("-")
    if (qualifier.isEmpty()) return "en"
    if (qualifier.startsWith("b+")) return qualifier.removePrefix("b+").replace('+', '-')
    return qualifier.replace("-r", "-")
}

internal fun stringFolders(res: File): List<String> =
    listOf("values") + res.listFiles().orEmpty()
        .filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        .map { it.name }
        .sorted()

internal fun writeReport(dir: File, folder: String, findings: Map<Finding, String?>) {
    dir.mkdirs()
    val header = "locale\tscreen\tstring\ttext\toverflow\tdetail\tlet off"
    File(dir, "$folder.tsv").writeText(findings.entries.joinToString("") { (finding, why) -> finding.row(why) + "\n" })
    val all = dir.listFiles { file -> file.name.startsWith("values") && file.name.endsWith(".tsv") }.orEmpty()
        .sortedBy { it.name }
        .joinToString("") { it.readText() }
    File(dir, "report.tsv").writeText("$header\n$all")
}

private fun Finding.row(why: String?) =
    listOf(locale, screen, names, text, kind.note, detail, why.orEmpty()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
