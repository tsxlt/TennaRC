package lt.tsx.tennarc

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RegexSyntaxTest {
    private val literal = Regex("Regex\\(\\s*\"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)
    private val allowed = listOf(
        Regex("""\\[pP]\{\w+\}"""),
        Regex("""\\."""),
        Regex("""\[(?:\\.|[^\]\\])*\]"""),
        Regex("""\{\d+(?:,\d*)?\}"""),
    )

    @Test
    fun noBareBracesInPatterns() {
        val bare = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { file ->
            literal.findAll(file.readText()).map { it.groupValues[1] }
                .filter { pattern -> allowed.fold(pattern) { rest, ok -> ok.replace(rest, "") }.any { it == '{' || it == '}' } }
                .map { "${file.name}: $it" }
        }.toList()
        assertTrue(bare.joinToString("\n"), bare.isEmpty())
    }
}
