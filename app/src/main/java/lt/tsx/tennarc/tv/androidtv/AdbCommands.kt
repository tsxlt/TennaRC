package lt.tsx.tennarc.tv.androidtv

import android.net.Uri
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.icons.StoreIcons
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.firetv.FireTvApps
import lt.tsx.tennarc.tv.firetv.shellQuote
import lt.tsx.tennarc.tv.firetv.titleFromPackage

internal const val TV_INPUT_PREFIX = "tvinput:"

internal fun isTvInput(id: String) = id.startsWith(TV_INPUT_PREFIX)

internal fun inputIdOf(id: String): String? = id.removePrefix(TV_INPUT_PREFIX).takeIf { isTvInput(id) && it.isNotEmpty() }

internal data class TvInput(val app: TvApp, val hdmiPort: Int?)

private const val LABELS_SECTION = "@labels"
private const val HIDDEN_SECTION = "@hidden"

internal const val INPUTS_COMMAND =
    "dumpsys tv_input; echo $LABELS_SECTION; settings --user 0 get secure tv_input_custom_labels; " +
        "echo $HIDDEN_SECTION; settings --user 0 get secure tv_input_hidden_inputs"

private val CURRENT_USER = Regex("""User Ids \(Current user: (\d+)\)""")

private val INPUT_STATE = Regex("""TvInputInfo\{id=([^,}]+)[^}]*\}; state: (\d)""")

private val HARDWARE = Regex("""TvInputHardwareInfo \{id=(\d+), type=(\d+)(?:[^}]*?hdmi_port=(\d+))?""")

private val MAP_ROW = Regex("""^\s+(\d+): (\S+)$""")

private val HW_SUFFIX = Regex("""/HW(\d+)$""")

private val CEC_SUFFIX = Regex("""/HDMI[0-9A-F]{6}$""")

private val PORT_IN_NAME = Regex("""hdmi_?(\d)""", RegexOption.IGNORE_CASE)

private const val TYPE_OTHER = 1
private const val TYPE_TUNER = 2
private const val TYPE_COMPOSITE = 3
private const val TYPE_SVIDEO = 4
private const val TYPE_SCART = 5
private const val TYPE_COMPONENT = 6
private const val TYPE_VGA = 7
private const val TYPE_DVI = 8
private const val TYPE_HDMI = 9
private const val TYPE_DISPLAY_PORT = 10

private const val STATE_DISCONNECTED = 2

internal fun parseTvInputs(output: String): List<TvInput> {
    val dump = output.substringBefore(LABELS_SECTION)
    val labels = parseCustomLabels(output.substringAfter(LABELS_SECTION, "").substringBefore(HIDDEN_SECTION).trim())
    val hidden = parseHiddenInputs(output.substringAfter(HIDDEN_SECTION, "").trim())
    val hardwareAt = dump.indexOf("TvInputHardwareManager Info:").let { if (it < 0) dump.length else it }
    val user = CURRENT_USER.find(dump)?.groupValues?.get(1) ?: "0"
    val services = dump.substring(0, hardwareAt).let { it.substringAfter("UserState ($user):", it).substringBefore("\nUserState (") }
    val hardwareDump = dump.substring(hardwareAt)

    val states = LinkedHashMap<String, Int>()
    INPUT_STATE.findAll(services).forEach { states.putIfAbsent(it.groupValues[1].trim(), it.groupValues[2].toInt()) }
    val hardware = HashMap<Int, Pair<Int, Int?>>()
    HARDWARE.findAll(hardwareDump).forEach { hardware[it.groupValues[1].toInt()] = it.groupValues[2].toInt() to it.groupValues[3].toIntOrNull() }
    val deviceOf = hardwareDump.substringAfter("mHardwareInputIdMap", "").lines().drop(1)
        .takeWhile { MAP_ROW.matches(it) }
        .associate { line -> MAP_ROW.find(line)!!.let { it.groupValues[2] to it.groupValues[1].toInt() } }

    class Found(val id: String, val kind: Int?, val port: Int?, val order: Long, val plugged: Boolean?)
    val found = states.mapNotNull { (id, state) ->
        if (CEC_SUFFIX.containsMatchIn(id) || id in hidden) return@mapNotNull null
        val device = deviceOf[id]
        val hw = HW_SUFFIX.find(id)?.groupValues?.get(1)?.toLongOrNull()
        if (device == null && hw == null) return@mapNotNull null
        val (kind, port) = device?.let { hardware[it] } ?: (kindFromId(id) to null)
        val named = if (kind == TYPE_HDMI) PORT_IN_NAME.find(id.substringBeforeLast('/'))?.groupValues?.get(1)?.toIntOrNull() else null
        Found(id, kind, port ?: named, device?.toLong() ?: hw ?: Long.MAX_VALUE, plugged = if (state == STATE_DISCONNECTED) false else null)
    }
    val tuner = found.filter { it.kind == TYPE_TUNER }.minByOrNull { it.order }
    val inputs = found.filter { it.kind != TYPE_TUNER }
    val taken = inputs.mapNotNull { it.port }.toMutableSet()
    val ports = HashMap<String, Int>()
    inputs.filter { it.kind == TYPE_HDMI }.sortedBy { it.order }.forEach { input ->
        ports[input.id] = input.port ?: generateSequence(1) { it + 1 }.first { it !in taken }.also { taken += it }
    }
    val unknown = inputs.filter { it.kind == null }.sortedBy { it.order }
    fun name(input: Found): String = labels[input.id] ?: when (input.kind) {
        TYPE_HDMI -> "HDMI ${ports[input.id]}"
        null -> "Input ${unknown.indexOf(input) + 1}"
        TYPE_OTHER -> serviceName(input.id)
        else -> kindName(input.kind)
    }
    val hdmi = inputs.filter { it.kind == TYPE_HDMI }.sortedBy { ports[it.id] }
    val rest = inputs.filter { it.kind != TYPE_HDMI }.sortedBy { it.order }
    return (hdmi + rest).map { TvInput(TvApp(TV_INPUT_PREFIX + it.id, name(it), input = true, plugged = it.plugged), ports[it.id]) } +
        listOfNotNull(tuner?.let { TvInput(TvApp(TV_INPUT_PREFIX + it.id, labels[it.id] ?: "TV", input = true), null) })
}

private fun kindFromId(id: String): Int? {
    val service = id.substringBeforeLast('/').lowercase()
    return when {
        "hdmi" in service -> TYPE_HDMI
        "tuner" in service || "dtv" in service || "atv" in service -> TYPE_TUNER
        "component" in service || "ypbpr" in service -> TYPE_COMPONENT
        "composite" in service || "cvbs" in service || ".av" in service -> TYPE_COMPOSITE
        "scart" in service -> TYPE_SCART
        "vga" in service -> TYPE_VGA
        "dvi" in service -> TYPE_DVI
        else -> null
    }
}

private fun kindName(kind: Int): String = when (kind) {
    TYPE_COMPOSITE -> "AV"
    TYPE_SVIDEO -> "S-Video"
    TYPE_SCART -> "SCART"
    TYPE_COMPONENT -> "Component"
    TYPE_VGA -> "VGA"
    TYPE_DVI -> "DVI"
    TYPE_DISPLAY_PORT -> "DisplayPort"
    else -> "Input"
}

private fun serviceName(id: String): String {
    val name = id.substringBeforeLast('/').substringAfterLast('.').substringAfterLast('/')
        .replace(Regex("(Tv)?(Input)?Service$"), "")
    return when {
        name.isEmpty() -> "Input"
        name.length <= 5 -> name.uppercase()
        else -> name
    }
}

internal fun parseCustomLabels(raw: String): Map<String, String> {
    if (raw.isEmpty() || raw == "null") return emptyMap()
    return raw.split(':').mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size < 2) return@mapNotNull null
        val label = decode(parts.last()).trim()
        if (label.isEmpty()) null else decode(parts.first()) to label
    }.toMap()
}

internal fun parseHiddenInputs(raw: String): Set<String> =
    if (raw.isEmpty() || raw == "null") emptySet() else raw.split(':').filter { it.isNotBlank() }.map(::decode).toSet()

private fun decode(text: String): String = catching { Uri.decode(text) }.getOrDefault(text)

internal fun passthroughUri(inputId: String): String = "content://android.media.tv/passthrough/" + Uri.encode(inputId)

internal const val INPUT_HANDLERS_COMMAND =
    "cmd package query-activities --components -a android.intent.action.VIEW " +
        "-d content://android.media.tv/passthrough/x -t vnd.android.cursor.item/channel"

private val GENERIC_TV_APPS = listOf("com.android.tv", "com.google.android.tv")

internal fun inputHandlers(output: String): List<String> =
    output.lineSequence().map { it.trim() }.filter { COMPONENT.matches(it) }.distinct().toList()
        .sortedBy { component -> GENERIC_TV_APPS.indexOf(component.substringBefore('/')) }

internal fun passthroughCommand(inputId: String, handler: String?): String =
    "am start" + (if (handler != null) " -n ${shellQuote(handler)}" else "") +
        " -a android.intent.action.VIEW -d ${shellQuote(passthroughUri(inputId))}"

internal fun started(output: String): Boolean = lt.tsx.tennarc.tv.firetv.started(output)

internal const val APPS_COMMAND =
    "cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"

private val COMPONENT = Regex("""^([A-Za-z][\w.]*)/([\w.$]+)$""")

private val HIDDEN_APPS = setOf(
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.google.android.leanbacklauncher",
    "com.google.android.tungsten.setupwraith",
    "com.google.android.apps.tv.dreamx",
    "com.google.android.katniss",
    "com.google.android.apps.mediashell",
    "com.google.android.tv.remote.service",
)

private val TITLES = mapOf(
    "com.google.android.youtube.tvkids" to "YouTube Kids",
    "com.google.android.youtube.tvmusic" to "YouTube Music",
    "com.google.android.youtube.tvunplugged" to "YouTube TV",
    "com.google.android.videos" to "Google TV",
    "com.google.android.tv" to "Live Channels",
    "com.google.android.play.games" to "Play Games",
    "com.android.vending" to "Play Store",
    "com.android.tv.settings" to "Settings",
    "com.hulu.livingroomplus" to "Hulu",
    "tv.twitch.android.app" to "Twitch",
    "tv.pluto.android" to "Pluto TV",
    "com.tubitv" to "Tubi",
    "com.dazn" to "DAZN",
    "com.amazon.music.tv" to "Amazon Music",
    "com.valvesoftware.steamlink" to "Steam Link",
    "com.limelight" to "Moonlight",
    "com.liskovsoft.smarttubetv.beta" to "SmartTube",
    "com.liskovsoft.smarttubetv" to "SmartTube",
    "com.teamsmart.videomanager.tv" to "SmartTube",
    "org.smarttube.stable" to "SmartTube",
    "org.smarttube.beta" to "SmartTube",
    "com.sony.dtv.tvx" to "TV",
    "com.tcl.tv" to "TV",
    "org.droidtv.playtv" to "Watch TV",
)

internal fun parseTvApps(output: String): List<TvApp> {
    val packages = output.lineSequence().mapNotNull { COMPONENT.find(it.trim())?.groupValues?.get(1) }
        .filterNot { it in HIDDEN_APPS }.distinct().toList()
    val curated = AndroidTvApps.curated.associateBy { it.id }
    return packages.map { id ->
        curated[id] ?: TvApp(
            id = id,
            title = TITLES[id] ?: FireTvApps.titles[id] ?: titleFromPackage(id),
            iconUrl = StoreIcons.url(id),
        )
    }.sortedWith(compareBy<TvApp> { app -> AndroidTvApps.curated.indexOfFirst { it.id == app.id }.let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { it.title.lowercase() })
}

internal sealed class TvBacklight {
    abstract val readCommand: String

    abstract fun parse(output: String): Int?

    abstract fun command(level: Int): String

    abstract fun took(output: String): Boolean

    data object MediaTek : TvBacklight() {
        override val readCommand = "settings get global picture_backlight"

        override fun parse(output: String): Int? = output.trim().toIntOrNull()?.takeIf { it in 0..100 }

        override fun command(level: Int) = "settings put global picture_backlight $level"

        override fun took(output: String) = output.isBlank()
    }

    data object Tcl : TvBacklight() {
        override val readCommand = "service call tcl_tv_display 101"

        override fun parse(output: String): Int? {
            val (status, value) = PARCEL.find(output)?.destructured ?: return null
            if (status.toLong(16) != 0L) return null
            return value.toLong(16).toInt().takeIf { it in 0..100 }
        }

        override fun command(level: Int) = "service call tcl_tv_display 100 i32 0 i32 $level i32 0"

        override fun took(output: String) = PARCEL_STATUS.find(output)?.groupValues?.get(1)?.toLongOrNull(16) == 0L
    }

    companion object {
        const val PROBE =
            "settings get global picture_backlight; settings get global tv_picture_video_adaptive_luma_control; service check tcl_tv_display"

        private val PARCEL = Regex("""Parcel\(\s*([0-9a-fA-F]{8}) ([0-9a-fA-F]{8})""")
        private val PARCEL_STATUS = Regex("""Parcel\(\s*([0-9a-fA-F]{8})""")

        fun find(output: String): TvBacklight? {
            val lines = output.lines().map { it.trim() }
            val level = lines.getOrNull(0)?.toIntOrNull()
            val luma = lines.getOrNull(1)
            return when {
                level != null && level in 0..100 && !luma.isNullOrEmpty() && luma != "null" -> MediaTek
                lines.any { it == "Service tcl_tv_display: found" } -> Tcl
                else -> null
            }
        }
    }
}
