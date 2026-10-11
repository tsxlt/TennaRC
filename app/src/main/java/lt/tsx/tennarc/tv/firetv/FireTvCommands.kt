package lt.tsx.tennarc.tv.firetv

import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.queryParam
import lt.tsx.tennarc.icons.StoreIcons
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import java.text.Normalizer

object FireTvApps {
    const val YOUTUBE = "com.amazon.firetv.youtube"
    const val YOUTUBE_ANDROID_TV = "com.google.android.youtube.tv"
    const val NETFLIX = "com.netflix.ninja"
    const val PRIME_VIDEO = "com.amazon.firebat"
    const val DISNEY_PLUS = "com.disney.disneyplus"
    const val SILK = "com.amazon.cloud9"
    const val FIREFOX = "org.mozilla.tv.firefox"
    const val HOME = "com.amazon.tv.launcher"

    val youTubes = listOf(YOUTUBE, YOUTUBE_ANDROID_TV)

    val browsers = listOf(SILK, FIREFOX)

    val favorites = listOf(YOUTUBE, NETFLIX, PRIME_VIDEO, DISNEY_PLUS, SILK)

    val hidden = setOf(HOME)

    val forService: Map<StreamingService, List<String>> = mapOf(
        StreamingService.YOUTUBE to youTubes,
        StreamingService.NETFLIX to listOf(NETFLIX),
        StreamingService.PRIME_VIDEO to listOf(PRIME_VIDEO),
        StreamingService.DISNEY_PLUS to listOf(DISNEY_PLUS),
        StreamingService.MAX to listOf("com.wbd.stream", "com.hbo.hbonow"),
        StreamingService.APPLE_TV to listOf("com.apple.atve.amazon.appletv"),
        StreamingService.CRUNCHYROLL to listOf("com.crunchyroll.crunchyroid"),
        StreamingService.MUBI to listOf("com.mubi"),
        StreamingService.PLEX to listOf("com.plexapp.android"),
    )

    val extraLaunchers = mapOf(
        PRIME_VIDEO to listOf(
            "com.amazon.firebat/com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity",
            "com.amazon.firebat/com.amazon.firebat.deeplink.DeepLinkRoutingActivity",
        ),
    )

    val titles = mapOf(
        YOUTUBE to "YouTube",
        YOUTUBE_ANDROID_TV to "YouTube",
        "com.amazon.firetv.youtube.kids" to "YouTube Kids",
        NETFLIX to "Netflix",
        PRIME_VIDEO to "Prime Video",
        DISNEY_PLUS to "Disney+",
        "com.wbd.stream" to "HBO Max",
        "com.hbo.hbonow" to "HBO Max",
        "com.apple.atve.amazon.appletv" to "Apple TV",
        "com.crunchyroll.crunchyroid" to "Crunchyroll",
        "com.mubi" to "MUBI",
        "com.plexapp.android" to "Plex",
        "com.spotify.tv.android" to "Spotify",
        "tv.twitch.android.viewer" to "Twitch",
        "com.hulu.plus" to "Hulu",
        "com.cbs.ott" to "Paramount+",
        "com.peacocktv.peacockandroid" to "Peacock",
        "org.jellyfin.androidtv" to "Jellyfin",
        "tv.emby.embyatv" to "Emby",
        "org.xbmc.kodi" to "Kodi",
        "org.videolan.vlc" to "VLC",
        SILK to "Silk Browser",
        FIREFOX to "Firefox",
        "com.amazon.tv.settings" to "Settings",
        "com.amazon.tv.settings.v2" to "Settings",
        "com.amazon.venezia" to "Appstore",
    )
}

internal fun keyCode(button: TvButton): String = when (button) {
    TvButton.UP -> "KEYCODE_DPAD_UP"
    TvButton.DOWN -> "KEYCODE_DPAD_DOWN"
    TvButton.LEFT -> "KEYCODE_DPAD_LEFT"
    TvButton.RIGHT -> "KEYCODE_DPAD_RIGHT"
    TvButton.ENTER -> "KEYCODE_DPAD_CENTER"
    TvButton.BACK -> "KEYCODE_BACK"
    TvButton.EXIT, TvButton.HOME -> "KEYCODE_HOME"
    TvButton.MENU -> "KEYCODE_MENU"
    TvButton.QMENU -> "KEYCODE_SETTINGS"
    TvButton.INFO -> "KEYCODE_INFO"
    TvButton.INPUT_HUB -> "KEYCODE_TV_INPUT"
    TvButton.CAPTIONS -> "KEYCODE_CAPTIONS"
    TvButton.AUDIO_TRACK -> "KEYCODE_MEDIA_AUDIO_TRACK"
    TvButton.GUIDE, TvButton.CHANNEL_LIST -> "KEYCODE_GUIDE"
    TvButton.PLAY -> "KEYCODE_MEDIA_PLAY"
    TvButton.PAUSE -> "KEYCODE_MEDIA_PAUSE"
    TvButton.STOP -> "KEYCODE_MEDIA_STOP"
    TvButton.REWIND -> "KEYCODE_MEDIA_REWIND"
    TvButton.FAST_FORWARD -> "KEYCODE_MEDIA_FAST_FORWARD"
    TvButton.CHANNEL_UP -> "KEYCODE_CHANNEL_UP"
    TvButton.CHANNEL_DOWN -> "KEYCODE_CHANNEL_DOWN"
    TvButton.RED -> "KEYCODE_PROG_RED"
    TvButton.GREEN -> "KEYCODE_PROG_GREEN"
    TvButton.YELLOW -> "KEYCODE_PROG_YELLOW"
    TvButton.BLUE -> "KEYCODE_PROG_BLUE"
    TvButton.DIGIT_0 -> "KEYCODE_0"
    TvButton.DIGIT_1 -> "KEYCODE_1"
    TvButton.DIGIT_2 -> "KEYCODE_2"
    TvButton.DIGIT_3 -> "KEYCODE_3"
    TvButton.DIGIT_4 -> "KEYCODE_4"
    TvButton.DIGIT_5 -> "KEYCODE_5"
    TvButton.DIGIT_6 -> "KEYCODE_6"
    TvButton.DIGIT_7 -> "KEYCODE_7"
    TvButton.DIGIT_8 -> "KEYCODE_8"
    TvButton.DIGIT_9 -> "KEYCODE_9"
}

internal const val KEY_DELETE = "KEYCODE_DEL"
internal const val KEY_ENTER = "KEYCODE_ENTER"
internal const val KEY_PLAY_PAUSE = "KEYCODE_MEDIA_PLAY_PAUSE"
internal const val KEY_VOLUME_UP = "KEYCODE_VOLUME_UP"
internal const val KEY_VOLUME_DOWN = "KEYCODE_VOLUME_DOWN"
internal const val KEY_MUTE = "KEYCODE_VOLUME_MUTE"
internal const val KEY_WAKE = "KEYCODE_WAKEUP"

internal const val KEY_SLEEP = "KEYCODE_SLEEP"

internal fun shellQuote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

private val SPELLINGS = mapOf(
    'ß' to "ss", 'æ' to "ae", 'Æ' to "AE", 'œ' to "oe", 'Œ' to "OE", 'ø' to "o", 'Ø' to "O",
    'ł' to "l", 'Ł' to "L", 'đ' to "d", 'Đ' to "D", 'ð' to "d", 'þ' to "th", 'Þ' to "Th", 'ı' to "i",
    '‘' to "'", '’' to "'", '‚' to "'", '“' to "\"", '”' to "\"", '„' to "\"", '–' to "-", '—' to "-",
)

internal fun typeable(char: Char): String {
    if (char in ' '..'~') return char.toString()
    SPELLINGS[char]?.let { return it }
    return Normalizer.normalize(char.toString(), Normalizer.Form.NFKD).filter { it in ' '..'~' }
}

internal class TypedText {
    private val widths = ArrayDeque<Int>()

    fun apply(edit: TextEdit): Pair<Int, String> {
        var deletes = 0
        repeat(edit.delete) { deletes += widths.removeLastOrNull() ?: 1 }
        val text = StringBuilder()
        for (char in edit.insert) {
            val typed = typeable(char)
            widths.addLast(typed.length)
            text.append(typed)
        }
        while (widths.size > MAX_TRACKED_CHARS) widths.removeFirst()
        return deletes to text.toString()
    }

    private companion object {
        const val MAX_TRACKED_CHARS = 2_000
    }
}

internal sealed interface InputOp {
    data class Key(val code: String) : InputOp
    data class Text(val edit: TextEdit) : InputOp
}

private sealed interface InputRun {
    class Keys(val codes: MutableList<String>) : InputRun
    class Typing(val text: StringBuilder) : InputRun
}

internal fun inputCommand(ops: List<InputOp>, typed: TypedText): String {
    val runs = ArrayList<InputRun>()
    fun keys(codes: List<String>) {
        if (codes.isEmpty()) return
        (runs.lastOrNull() as? InputRun.Keys)?.codes?.addAll(codes) ?: runs.add(InputRun.Keys(codes.toMutableList()))
    }
    for (op in ops) when (op) {
        is InputOp.Key -> keys(listOf(op.code))
        is InputOp.Text -> {
            val (deletes, text) = typed.apply(op.edit)
            var remaining = deletes
            (runs.lastOrNull() as? InputRun.Typing)?.let { pending ->
                val cut = minOf(remaining, pending.text.length)
                pending.text.setLength(pending.text.length - cut)
                remaining -= cut
                if (pending.text.isEmpty()) runs.removeAt(runs.lastIndex)
            }
            keys(List(remaining) { KEY_DELETE })
            if (text.isNotEmpty()) {
                (runs.lastOrNull() as? InputRun.Typing)?.text?.append(text) ?: runs.add(InputRun.Typing(StringBuilder(text)))
            }
        }
    }
    return runs.flatMap { run ->
        when (run) {
            is InputRun.Keys -> listOf("input keyevent " + run.codes.joinToString(" "))
            is InputRun.Typing -> textCommands(run.text.toString())
        }
    }.joinToString("; ")
}

internal fun textCommands(text: String): List<String> {
    val runs = mutableListOf(StringBuilder())
    text.forEachIndexed { index, char ->
        if (char == 's' && index > 0 && text[index - 1] == '%') runs.add(StringBuilder())
        runs.last().append(if (char == ' ') "%s" else char.toString())
    }
    return runs.filter { it.isNotEmpty() }.map { "input text " + shellQuote(it.toString()) }
}

internal fun inputAccepted(output: String): Boolean = "Exception" !in output && "Error" !in output

internal data class Launcher(val component: String, val category: String)

internal const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
internal const val CATEGORY_LEANBACK = "android.intent.category.LEANBACK_LAUNCHER"

internal fun launchCommands(appId: String, launcher: Launcher?): List<String> = buildList {
    if (launcher != null) add("am start -a android.intent.action.MAIN -c ${launcher.category} -f 0x10200000 -n ${shellQuote(launcher.component)}")
    FireTvApps.extraLaunchers[appId]?.forEach { add("am start -n ${shellQuote(it)}") }
    add("monkey -p ${shellQuote(appId)} -c $CATEGORY_LAUNCHER -c $CATEGORY_LEANBACK --pct-syskeys 0 1")
}

internal fun started(output: String): Boolean =
    listOf("Error", "Exception", "monkey aborted", "No activities found").none { it in output }

internal fun viewCommand(url: String, appId: String, browsable: Boolean = false): String =
    "am start -a android.intent.action.VIEW" +
        (if (browsable) " -c android.intent.category.BROWSABLE" else "") +
        " -d ${shellQuote(url)} -p ${shellQuote(appId)}"

internal fun netflixCommand(titleId: String): String =
    "am start -n ${FireTvApps.NETFLIX}/.MainActivity -a android.intent.action.VIEW" +
        " -d ${shellQuote("netflix://title/$titleId")} -f 0x10000020" +
        " -e amzn_deeplink_data ${shellQuote(titleId)} -e source 30"

internal fun youTubeWatchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

internal fun youTubeSearchUrl(query: String) = "https://www.youtube.com/results?search_query=" + queryParam(query)

internal const val STATUS_COMMAND =
    "dumpsys power | grep mWakefulness=; dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp|mInputMethod(Input)?Target'"

private const val LAUNCHER_SECTION = "@launcher"
private const val LEANBACK_SECTION = "@leanback"

internal const val APPS_COMMAND =
    "pm list packages; echo $LAUNCHER_SECTION; " +
        "cmd package query-activities --components -a android.intent.action.MAIN -c $CATEGORY_LAUNCHER; " +
        "echo $LEANBACK_SECTION; " +
        "cmd package query-activities --components -a android.intent.action.MAIN -c $CATEGORY_LEANBACK"

private val WAKEFULNESS = Regex("""mWakefulness=(\w+)""")

internal fun parseWakefulness(output: String): TvPower? = when (WAKEFULNESS.find(output)?.groupValues?.get(1)) {
    "Awake", "Dreaming" -> TvPower.ON
    "Asleep", "Dozing" -> TvPower.STANDBY
    else -> null
}

private val FOCUS_KEYS = listOf("mCurrentFocus", "mFocusedApp", "mInputMethodInputTarget", "mInputMethodTarget")
private val WINDOW_OWNER = Regex("""\bu\d+ ([A-Za-z][\w.]*)/""")

internal fun parseFocusedApp(output: String): String? {
    val lines = output.lines().map { it.trim() }
    for (key in FOCUS_KEYS) {
        val line = lines.firstOrNull { it.startsWith("$key=") || it.startsWith("$key ") } ?: continue
        WINDOW_OWNER.find(line)?.let { return it.groupValues[1] }
    }
    return null
}

private val COMPONENT = Regex("""^([A-Za-z][\w.]*)/([\w.$]+)$""")

internal data class AppList(val apps: List<TvApp>, val launchers: Map<String, Launcher>)

internal fun parseApps(output: String): AppList {
    val installed = LinkedHashSet<String>()
    val launchers = LinkedHashMap<String, Launcher>()
    var category: String? = null
    for (line in output.lines().map { it.trim() }) {
        when {
            line == LAUNCHER_SECTION -> category = CATEGORY_LAUNCHER
            line == LEANBACK_SECTION -> category = CATEGORY_LEANBACK
            category == null -> line.removePrefix("package:").takeIf { it != line && it.isNotBlank() }?.let(installed::add)
            else -> COMPONENT.find(line)?.let { match -> launchers.putIfAbsent(match.groupValues[1], Launcher(line, category)) }
        }
    }
    val known = installed.filter { it in FireTvApps.titles }
    val ids = (launchers.keys + known).filterNot { it in FireTvApps.hidden }.distinct()
    val apps = ids.map { TvApp(id = it, title = FireTvApps.titles[it] ?: titleFromPackage(it), iconUrl = StoreIcons.url(it), searchable = it in FireTvApps.youTubes) }
        .sortedWith(compareBy<TvApp> { FireTvApps.favorites.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }.thenBy { it.title.lowercase() })
    return AppList(apps, launchers)
}

private val GENERIC_PARTS = setOf(
    "com", "org", "net", "tv", "android", "androidtv", "atv", "app", "apps", "amazon", "firetv", "fire",
    "google", "leanback", "mobile", "client", "player", "launcher", "ott", "stb", "release", "prod", "production",
)

internal fun titleFromPackage(packageName: String): String =
    packageName.split('.').lastOrNull { it.lowercase() !in GENERIC_PARTS && it.isNotEmpty() }
        ?.replaceFirstChar { it.uppercase() }
        ?: packageName

internal fun fireTvFeatures(apps: List<TvApp>): Set<Feature> {
    fun has(ids: List<String>) = apps.isEmpty() || ids.any { id -> apps.any { it.id == id } }
    return buildSet {
        add(Feature.TEXT_INPUT)
        add(Feature.CAPTIONS)
        add(Feature.APP_LIST)
        add(Feature.WAKE)
        if (has(FireTvApps.youTubes)) add(Feature.YOUTUBE_LINKS)
        if (has(listOf(FireTvApps.NETFLIX))) add(Feature.NETFLIX_LINKS)
        if (has(FireTvApps.browsers)) add(Feature.WEB_LINKS)
    }
}

internal const val TV_SCREEN_FEATURE = "com.amazon.hardware.tv_screen"

internal const val INPUT_PREFIX = "input:hdmi"

internal fun inputKey(appId: String): String? = appId.removePrefix(INPUT_PREFIX).takeIf { appId.startsWith(INPUT_PREFIX) }
    ?.toIntOrNull()?.takeIf { it in 1..4 }?.let { "KEYCODE_TV_INPUT_HDMI_$it" }
