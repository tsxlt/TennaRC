package lt.tsx.tennarc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object CrashReports {
    private const val FILE = "crash-report.txt"

    fun install(app: TennaApp) {
        val header = CrashHeader(
            version = BuildConfig.VERSION_NAME,
            code = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            android = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            device = deviceName(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()),
        )
        val current = Thread.getDefaultUncaughtExceptionHandler()
        val previous = if (current is CrashRecorder) current.previous else current
        Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(app.filesDir, header, previous) { CrashScene.of(app) })
    }

    fun pending(context: Context): String? = pending(context.filesDir)

    fun consume(context: Context) = consume(context.filesDir)

    suspend fun offer(context: Context, snackbar: SnackbarHostState) {
        val report = withContext(Dispatchers.IO) { pending(context) } ?: return
        val answer = snackbar.showSnackbar(
            message = context.getString(R.string.crash_offer),
            actionLabel = context.getString(R.string.crash_send),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (answer != SnackbarResult.ActionPerformed) {
            consume(context)
            return
        }
        val sent = upload(report)
        if (!sent) share(context, report)
        consume(context)
        if (sent) snackbar.showSnackbar(context.getString(R.string.crash_sent))
    }

    internal suspend fun upload(
        report: String,
        url: String = "${BuildConfig.SITE}/api/crash",
        key: String = BuildConfig.REPORT_KEY,
        http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    ): Boolean = withContext(Dispatchers.IO) {
        val sealed = ReportEnvelope.seal(report.encodeToByteArray(), key)
        val request = Request.Builder().url(url).post(sealed.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        try {
            http.newCall(request).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }

    fun share(context: Context, report: String) {
        val version = CrashReport.versionOf(report) ?: BuildConfig.VERSION_NAME
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject, version))
            putExtra(Intent.EXTRA_TEXT, report)
            BuildConfig.CRASH_REPORT_EMAIL.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        }
        val chooser = Intent.createChooser(share, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    internal fun pending(dir: File): String? = try {
        File(dir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: IOException) {
        null
    }

    internal fun consume(dir: File) {
        File(dir, FILE).delete()
    }

    internal fun save(dir: File, report: String) {
        val partial = File(dir, "$FILE.tmp")
        partial.writeText(report)
        if (!partial.renameTo(File(dir, FILE))) partial.delete()
    }

    internal fun deviceName(maker: String, model: String): String =
        if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
}

internal class CrashRecorder(
    private val dir: File,
    private val header: CrashHeader,
    val previous: Thread.UncaughtExceptionHandler?,
    private val scene: () -> CrashScene?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            val about = try {
                scene()
            } catch (_: Throwable) {
                null
            }
            CrashReports.save(dir, CrashReport.build(error, thread.name, header, about))
        } catch (_: Throwable) {
        }
        if (previous != null) {
            previous.uncaughtException(thread, error)
        } else {
            System.err.print("Exception in thread \"${thread.name}\" ")
            error.printStackTrace()
        }
    }
}

internal data class CrashHeader(
    val version: String,
    val code: Int,
    val buildType: String,
    val android: String,
    val sdk: Int,
    val device: String,
)

internal data class CrashScene(
    val tv: String,
    val connection: String,
    val features: List<String>,
    val personal: Collection<String> = emptyList(),
) {
    companion object {
        fun of(app: TennaApp): CrashScene? = of(
            state = catching { app.controller.state.value }.getOrNull(),
            prefs = catching { app.settings.prefs.value }.getOrNull(),
        )

        fun of(state: TvState?, prefs: Prefs?): CrashScene? {
            if (state == null && prefs == null) return null
            val kind = (state?.kind ?: prefs?.kind)?.label ?: "unknown"
            return CrashScene(
                tv = if (prefs?.demoTv == true) "$kind (demo)" else kind,
                connection = when (state?.connection) {
                    null -> "not started"
                    Connection.Connected -> "connected"
                    Connection.Connecting -> "connecting"
                    Connection.Offline -> "offline"
                    Connection.AwaitingApproval -> "awaiting approval"
                    is Connection.AwaitingCode -> "awaiting code"
                    is Connection.NeedsPairing -> "needs pairing"
                },
                features = state?.features.orEmpty().map { it.name }.sorted(),
                personal = buildList {
                    state?.let { add(it.host) }
                    if (prefs != null) {
                        add(prefs.tvName)
                        add(prefs.host)
                        add(prefs.mac)
                        prefs.otherTvs.forEach { tv -> addAll(listOf(tv.name, tv.host, tv.mac)) }
                        addAll(prefs.recent)
                    }
                },
            )
        }
    }
}

internal object CrashReport {
    const val MAX_BYTES = 64 * 1024
    private const val MAX_LINE = 300
    private const val TITLE = "TennaRC "
    private const val CUT = "\n[cut to 64 KB]\n"

    fun build(error: Throwable, thread: String, header: CrashHeader, scene: CrashScene?): String {
        val scrubber = Scrubber(scene?.personal.orEmpty())
        val report = StringBuilder()
        report.appendLine("$TITLE${header.version} (${header.code}, ${header.buildType})")
        report.appendLine("Android ${header.android} (API ${header.sdk}), ${header.device}")
        if (scene != null) {
            report.appendLine("TV: ${scene.tv}, ${scene.connection}")
            report.appendLine("Features: ${scene.features.joinToString(", ").ifEmpty { "none" }}")
        }
        report.appendLine("Thread: ${scrubber.scrub(thread)}")
        report.appendLine()
        val trace = catching { error.stackTraceToString() }.getOrElse { error.javaClass.name }
        for (line in trace.lineSequence()) {
            if (report.length > MAX_BYTES) break
            val clean = scrubber.scrub(line)
            report.append(if (clean.length > MAX_LINE) clean.take(MAX_LINE) + "…" else clean).append('\n')
        }
        return cap(report.toString().trimEnd() + "\n")
    }

    fun versionOf(report: String): String? = report.lineSequence().firstOrNull()
        ?.takeIf { it.startsWith(TITLE) }
        ?.removePrefix(TITLE)?.substringBefore(" (")?.trim()?.takeIf { it.isNotEmpty() }

    private fun cap(text: String): String {
        val bytes = text.encodeToByteArray()
        if (bytes.size <= MAX_BYTES) return text
        return bytes.decodeToString(0, MAX_BYTES - CUT.length).trimEnd('�') + CUT
    }
}

internal class Scrubber(personal: Collection<String> = emptyList()) {
    private val own: Regex? = personal.map { it.trim() }.filter { it.length >= 3 }.distinct()
        .sortedByDescending { it.length }
        .takeIf { it.isNotEmpty() }
        ?.let { words -> Regex(words.joinToString("|", "(?iu)(?<!\\w)(?:", ")(?!\\w)") { Regex.escape(it) }) }

    fun scrub(text: String): String {
        var out = URL.replace(text, "<url>")
        out = WWW.replace(out, "<url>")
        out = EMAIL.replace(out, "<email>")
        own?.let { out = it.replace(out, "<private>") }
        out = IPV6.replace(out) { if (isIpv6(it.value)) "<ip>" else it.value }
        out = MAC.replace(out, "<mac>")
        out = BARE_MAC.replace(out, "<mac>")
        out = IPV4.replace(out, "<ip>")
        out = LOCAL_HOST.replace(out, "<host>")
        out = QUOTED.replace(out, "\"…\"")
        return out
    }

    private companion object {
        val URL = Regex("""(?<![\w+.-])[A-Za-z][\w+.-]{0,30}://[^\s"'<>]*""")
        val WWW = Regex("""(?i)(?<![\w.-])www\.[^\s"'<>]+""")
        val EMAIL = Regex("""(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

        val IPV6 = Regex("""(?<![\w.])(?:[0-9A-Fa-f]{1,4})?(?::[0-9A-Fa-f]{0,4}){2,7}(?:\.\d{1,3}){0,3}(?:%[\w.-]+)?(?![\w:])""")
        val MAC = Regex("""(?<!\w)[0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5}(?!\w)""")
        val BARE_MAC = Regex("""(?<!\w)[0-9A-Fa-f]{12}(?!\w)""")
        val IPV4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?!\.?\d)""")
        val LOCAL_HOST = Regex("""(?<![\w.-])[\w-]+(?:\.[\w-]+)*\.(?:local|lan)(?![\w-])""")
        val QUOTED = Regex(""""[^"\n]+"""")

        fun isIpv6(candidate: String): Boolean {
            val address = candidate.substringBefore('%')
            val colons = address.count { it == ':' }
            return address.any { it.isLetterOrDigit() } &&
                ("::" in address || colons == 7 || (colons == 6 && '.' in address))
        }
    }
}
