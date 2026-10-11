import org.gradle.api.provider.ProviderFactory
import java.io.File

data class AppVersion(val name: String, val code: Int)

fun appVersionOf(base: String, describe: String?, sha: String?, committedAt: Long?): AppVersion {
    val tag = describe?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { if (it.length > 1 && it[0] in "vV" && it[1].isDigit()) it.drop(1) else it }
    val commit = sha?.trim()?.takeIf { it.isNotEmpty() }
    val name = tag ?: if (commit != null) "$base-dev+$commit" else "$base-dev"
    return AppVersion(name, committedAt?.let { (it / 60).toInt() }?.coerceAtLeast(1) ?: 1)
}

fun ProviderFactory.appVersionFromGit(base: String, dir: File): AppVersion {
    fun git(vararg args: String): String? = try {
        val run = exec {
            workingDir = dir
            commandLine("git", *args)
            isIgnoreExitValue = true
        }
        run.standardOutput.asText.get().trim().takeIf { run.result.get().exitValue == 0 && it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
    return appVersionOf(
        base,
        describe = git("describe", "--tags", "--match", "v[0-9]*", "--match", "[0-9]*"),
        sha = git("rev-parse", "--short", "HEAD"),
        committedAt = git("log", "-1", "--format=%ct")?.toLongOrNull(),
    )
}
