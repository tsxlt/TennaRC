package lt.tsx.tennarc.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_HOST = ""
const val DEFAULT_MAC = ""
const val DEFAULT_TV_NAME = "TV"
private const val MAX_RECENT = 8

enum class ScrollStrip { RIGHT, LEFT, OFF }

enum class ThemeMode { AUTO, SYSTEM, LIGHT, DARK }

data class SavedTv(val name: String, val host: String, val kind: TvKind, val mac: String)

data class Prefs(
    val tvName: String = DEFAULT_TV_NAME,
    val kind: TvKind = TvKind.WEBOS,
    val host: String = DEFAULT_HOST,
    val mac: String = DEFAULT_MAC,
    val haptics: Boolean = true,
    val volumeKeys: Boolean = true,
    val autoKeyboard: Boolean = true,
    val keepScreenOn: Boolean = true,
    val notificationControls: Boolean = false,
    val askedNotifications: Boolean = false,
    val naturalScroll: Boolean = true,
    val touchpadMode: Boolean = false,
    val touchpadModes: Map<String, Boolean> = emptyMap(),
    val padFollowsApps: Map<String, String> = emptyMap(),
    val touchpadSpeed: Float = 1.6f,
    val recent: List<String> = emptyList(),
    val otherTvs: List<SavedTv> = emptyList(),
    val tvOrder: List<String> = emptyList(),
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val watchedServices: Set<StreamingService> = emptySet(),
    val findTitles: Boolean = false,
    val askedFindTitles: Boolean = false,
    val scrollStrip: ScrollStrip = ScrollStrip.RIGHT,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val demoTv: Boolean = false,
    val demoListed: Boolean = false,
    val tvChosen: Boolean = true,
    val appUse: Map<String, Float> = emptyMap(),
    val appUseAt: Long = 0L,
    val channelUse: Map<String, Float> = emptyMap(),
    val channelUseAt: Long = 0L,
    val hiddenChannels: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
    val hiddenNames: Map<String, String> = emptyMap(),
    val inputNames: Map<String, String> = emptyMap(),
    val sleepAt: Long = 0,
    val sleepMinutes: Int = 0,
)

val Prefs.currentTv: SavedTv get() = SavedTv(tvName, host, kind, mac)

val Prefs.needsTvSetup: Boolean get() = !tvChosen && !demoTv

const val DEMO_KEY = "demo"

val Prefs.tvKey: String get() = if (demoTv) DEMO_KEY else host

fun Prefs.touchpadOn(key: String): Boolean = touchpadModes[key] ?: touchpadMode

fun Prefs.touchpadIn(key: String, app: String): Boolean? = touchpadModes[appPadKey(key, app)] ?: when (app) {
    TvApps.BROWSER -> true
    TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.MEDIA_STATION_X -> false
    else -> null
}

private fun appPadKey(tv: String, app: String) = tv + APP_PAD_SEPARATOR + app

private const val APP_PAD_SEPARATOR = "\u001F"

fun Prefs.withPad(key: String, touchpad: Boolean, app: String?): Prefs {
    val modes = when {
        key.isEmpty() -> touchpadModes
        app == null -> touchpadModes + (key to touchpad)
        else -> touchpadModes + (key to touchpad) + (appPadKey(key, app) to touchpad)
    }
    return if (touchpad == touchpadMode && modes == touchpadModes) this
    else copy(touchpadMode = touchpad, touchpadModes = modes)
}

private fun Prefs.forgetPad(key: String): Prefs = copy(
    touchpadModes = touchpadModes.filterKeys { it != key && !it.startsWith(key + APP_PAD_SEPARATOR) },
    padFollowsApps = padFollowsApps - key,
)

val Prefs.tvKeys: List<String>
    get() {
        val all = listOf(host) + otherTvs.map { it.host } + listOfNotNull(DEMO_KEY.takeIf { demoListed })
        return all.distinct().sortedBy { key -> tvOrder.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
    }

private fun Prefs.pinnedOrder(): Prefs = if (tvOrder == tvKeys) this else copy(tvOrder = tvKeys)

fun Prefs.switchTo(tv: SavedTv): Prefs =
    if (tv.host == host) {
        if (demoTv) pinnedOrder().copy(demoTv = false) else this
    } else {
        pinnedOrder().copy(
            tvName = tv.name,
            host = tv.host,
            kind = tv.kind,
            mac = tv.mac,
            otherTvs = (otherTvs.filterNot { it.host == tv.host || it.host == host } + currentTv).sortedBy { it.name.lowercase() },
            demoTv = false,
        )
    }

fun Prefs.movedTo(to: String): Prefs = copy(
    host = to,
    tvOrder = tvOrder.map { if (it == host) to else it },
    touchpadModes = touchpadModes.mapKeys { (k, _) -> if (k == host || k.startsWith(host + APP_PAD_SEPARATOR)) to + k.removePrefix(host) else k },
    padFollowsApps = padFollowsApps.movedKey(host, to),
)

private fun <V : Any> Map<String, V>.movedKey(from: String, to: String): Map<String, V> = get(from)?.let { this - from + (to to it) } ?: this

fun Prefs.resetSettings(): Prefs {
    val defaults = Prefs()
    return copy(
        touchpadSpeed = defaults.touchpadSpeed,
        naturalScroll = defaults.naturalScroll,
        scrollStrip = defaults.scrollStrip,
        themeMode = defaults.themeMode,
        volumeKeys = defaults.volumeKeys,
        autoKeyboard = defaults.autoKeyboard,
        keepScreenOn = defaults.keepScreenOn,
        notificationControls = defaults.notificationControls,
        askedNotifications = defaults.askedNotifications,
        haptics = defaults.haptics,
        searchEngine = defaults.searchEngine,
        findTitles = defaults.findTitles,
        askedFindTitles = defaults.askedFindTitles,
    )
}

fun Prefs.answerFindTitles(on: Boolean): Prefs = copy(findTitles = on, askedFindTitles = true)

fun Prefs.useDemo(): Prefs = copy(demoListed = true).pinnedOrder().copy(demoTv = true)

fun Prefs.removeDemo(): Prefs = copy(demoListed = false, demoTv = false).forgetPad(DEMO_KEY)

fun Prefs.deckKey(id: String): String = "$tvKey/$id"

fun Prefs.hideApp(id: String, name: String): Prefs = deckKey(id).let { copy(hiddenApps = hiddenApps + it, hiddenNames = hiddenNames + (it to name)) }

fun Prefs.showApp(key: String): Prefs = copy(hiddenApps = hiddenApps - key, hiddenNames = hiddenNames - key)

fun Prefs.hideChannel(id: String, name: String): Prefs = copy(hiddenChannels = hiddenChannels + id, hiddenNames = hiddenNames + (id to name))

fun Prefs.showChannel(id: String): Prefs = copy(hiddenChannels = hiddenChannels - id, hiddenNames = hiddenNames - id)

fun Prefs.renameInput(id: String, name: String): Prefs =
    deckKey(id).let { key -> copy(inputNames = if (name.isBlank()) inputNames - key else inputNames + (key to name.trim())) }

val Prefs.hiddenHere: Set<String> get() = deckKey("").let { prefix -> hiddenApps.filterTo(mutableSetOf()) { it.startsWith(prefix) } }

fun Prefs.forgetTv(tv: SavedTv): Prefs =
    copy(otherTvs = otherTvs.filterNot { it.host == tv.host }).let { if (tv.host == host) it else it.forgetPad(tv.host) }

fun Prefs.withAppUse(appId: String, now: Long): Prefs = copy(appUse = appUse.withMinute(appId, appUseAt, now), appUseAt = now)

fun Prefs.withChannelUse(channelId: String, now: Long): Prefs =
    copy(channelUse = channelUse.withMinute(channelId, channelUseAt, now), channelUseAt = now)

private fun Map<String, Float>.withMinute(id: String, agedAt: Long, now: Long): Map<String, Float> {
    val age = if (agedAt == 0L) 1f else 0.5f.pow((now - agedAt).coerceAtLeast(0L).toFloat() / APP_USE_HALF_LIFE_MS)
    val aged = mapValues { it.value * age }.filterValues { it >= APP_USE_FORGET }
    return aged + (id to (aged[id] ?: 0f) + 1f)
}

private const val APP_USE_HALF_LIFE_MS = 14 * 24 * 60 * 60 * 1000f
private const val APP_USE_FORGET = 0.05f

fun Prefs.withRecent(entry: String): Prefs =
    copy(recent = (listOf(entry) + recent.filterNot { it == entry }).take(MAX_RECENT))

class Settings(context: Context) {
    private val store: SharedPreferences = context.getSharedPreferences("tenna", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val prefs: StateFlow<Prefs> = state.asStateFlow()

    fun update(transform: (Prefs) -> Prefs) {
        state.update(transform)
        write(state.value)
    }

    fun addRecent(entry: String) = update { it.withRecent(entry) }

    fun clientKey(host: String): String? = store.getString(keyName(host), null)

    fun setClientKey(host: String, key: String?) = store.edit {
        if (key == null) remove(keyName(host)) else putString(keyName(host), key)
    }

    private fun keyName(host: String) = "client_key:$host"

    fun tvId(host: String): String? = store.getString("tv_id:$host", null)

    fun setTvId(host: String, id: String) = store.edit { putString("tv_id:$host", id) }

    fun moveTv(from: String, to: String) {
        clientKey(from)?.let { setClientKey(to, it) }
        tvId(from)?.let { setTvId(to, it) }
        update { it.movedTo(to) }
    }

    private fun read(): Prefs {
        val defaults = Prefs()
        val read = Prefs(
            tvName = store.getString("tv_name", null) ?: defaults.tvName,
            kind = store.getString("tv_kind", null)?.let { raw -> TvKind.entries.firstOrNull { it.name == raw } } ?: defaults.kind,
            host = store.getString("host", null) ?: defaults.host,
            mac = store.getString("mac", null) ?: defaults.mac,
            haptics = store.getBoolean("haptics", defaults.haptics),
            volumeKeys = store.getBoolean("volume_keys", defaults.volumeKeys),
            autoKeyboard = store.getBoolean("auto_keyboard", defaults.autoKeyboard),
            keepScreenOn = store.getBoolean("keep_screen_on", defaults.keepScreenOn),
            notificationControls = store.getBoolean("notification_controls", defaults.notificationControls),
            askedNotifications = store.getBoolean("asked_notifications", defaults.askedNotifications),
            naturalScroll = store.getBoolean("natural_scroll", defaults.naturalScroll),
            touchpadMode = store.getBoolean("touchpad_mode", defaults.touchpadMode),
            touchpadModes = store.getString("touchpad_modes", null)?.let(::readModes) ?: defaults.touchpadModes,
            padFollowsApps = store.getString("pad_follows_apps", null)?.let(::readNames) ?: defaults.padFollowsApps,
            touchpadSpeed = store.getFloat("touchpad_speed", defaults.touchpadSpeed),
            recent = store.getString("recent_links", null)?.let(::readList) ?: defaults.recent,
            searchEngine = store.getString("search_engine", null)?.let { raw -> SearchEngine.entries.firstOrNull { it.name == raw } } ?: defaults.searchEngine,
            findTitles = store.getBoolean("find_titles", defaults.findTitles),
            askedFindTitles = store.getBoolean("asked_find_titles", defaults.askedFindTitles),
            demoTv = store.getBoolean("demo_tv", defaults.demoTv),
            demoListed = store.getBoolean("demo_listed", store.getBoolean("demo_tv", defaults.demoListed)),
            tvChosen = store.getBoolean("tv_chosen", store.contains("host")),
            sleepAt = store.getLong("sleep_at", defaults.sleepAt),
            sleepMinutes = store.getInt("sleep_minutes", defaults.sleepMinutes),
            otherTvs = store.getString("other_tvs", null)?.let(::readTvs) ?: defaults.otherTvs,
            tvOrder = store.getString("tv_order", null)?.let(::readList) ?: defaults.tvOrder,
            watchedServices = store.getString("watched_services", null)
                ?.split(',')
                ?.mapNotNullTo(LinkedHashSet()) { name -> StreamingService.entries.firstOrNull { it.name == name } }
                ?: defaults.watchedServices,
            scrollStrip = store.getString("scroll_strip", null)?.let { raw -> ScrollStrip.entries.firstOrNull { it.name == raw } } ?: defaults.scrollStrip,
            themeMode = store.getString("theme_mode", null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: defaults.themeMode,
            appUse = store.getString("app_use", null)?.let(::readUse) ?: defaults.appUse,
            appUseAt = store.getLong("app_use_at", defaults.appUseAt),
            channelUse = store.getString("channel_use", null)?.let(::readUse) ?: defaults.channelUse,
            channelUseAt = store.getLong("channel_use_at", defaults.channelUseAt),
            hiddenChannels = store.getString("hidden_channels", null)?.let(::readList)?.toSet() ?: defaults.hiddenChannels,
            hiddenApps = store.getString("hidden_apps", null)?.let(::readList)?.toSet() ?: defaults.hiddenApps,
            hiddenNames = store.getString("hidden_names", null)?.let(::readNames) ?: defaults.hiddenNames,
            inputNames = store.getString("input_names", null)?.let(::readNames) ?: defaults.inputNames,
        )
        val perTv = store.contains("touchpad_modes") || !store.contains("touchpad_mode")
        return if (perTv) read else read.withPad(read.tvKey, read.touchpadMode, store.getString("pad_follows_app", null))
    }

    private fun write(prefs: Prefs) = store.edit {
        putString("tv_name", prefs.tvName)
        putString("tv_kind", prefs.kind.name)
        putString("host", prefs.host)
        putString("mac", prefs.mac)
        putBoolean("haptics", prefs.haptics)
        putBoolean("volume_keys", prefs.volumeKeys)
        putBoolean("auto_keyboard", prefs.autoKeyboard)
        putBoolean("keep_screen_on", prefs.keepScreenOn)
        putBoolean("notification_controls", prefs.notificationControls)
        putBoolean("asked_notifications", prefs.askedNotifications)
        putBoolean("natural_scroll", prefs.naturalScroll)
        putBoolean("touchpad_mode", prefs.touchpadMode)
        putString("touchpad_modes", JSONObject(prefs.touchpadModes).toString())
        putString("pad_follows_apps", JSONObject(prefs.padFollowsApps).toString())
        remove("pad_follows_app")
        putFloat("touchpad_speed", prefs.touchpadSpeed)
        putString("recent_links", JSONArray(prefs.recent).toString())
        putString("search_engine", prefs.searchEngine.name)
        putString("scroll_strip", prefs.scrollStrip.name)
        putString("theme_mode", prefs.themeMode.name)
        putBoolean("find_titles", prefs.findTitles)
        putBoolean("asked_find_titles", prefs.askedFindTitles)
        putBoolean("demo_tv", prefs.demoTv)
        putBoolean("demo_listed", prefs.demoListed)
        putBoolean("tv_chosen", prefs.tvChosen)
        putLong("sleep_at", prefs.sleepAt)
        putInt("sleep_minutes", prefs.sleepMinutes)
        putString("other_tvs", writeTvs(prefs.otherTvs))
        putString("tv_order", JSONArray(prefs.tvOrder).toString())
        putString("watched_services", prefs.watchedServices.joinToString(",") { it.name })
        putString("app_use", JSONObject(prefs.appUse.mapValues { it.value.toDouble() }).toString())
        putLong("app_use_at", prefs.appUseAt)
        putString("channel_use", JSONObject(prefs.channelUse.mapValues { it.value.toDouble() }).toString())
        putLong("channel_use_at", prefs.channelUseAt)
        putString("hidden_channels", JSONArray(prefs.hiddenChannels.toList()).toString())
        putString("hidden_apps", JSONArray(prefs.hiddenApps.toList()).toString())
        putString("hidden_names", JSONObject(prefs.hiddenNames).toString())
        putString("input_names", JSONObject(prefs.inputNames).toString())
    }
}

private fun readList(raw: String): List<String>? =
    catching { JSONArray(raw).let { array -> List(array.length()) { array.getString(it) } } }.logged("Settings.readList").getOrNull()

private fun readNames(raw: String): Map<String, String>? =
    catching { JSONObject(raw).let { json -> json.keys().asSequence().associateWith { json.getString(it) } } }.logged("Settings.readNames").getOrNull()

private fun readModes(raw: String): Map<String, Boolean>? =
    catching { JSONObject(raw).let { json -> json.keys().asSequence().associateWith { json.getBoolean(it) } } }.logged("Settings.readModes").getOrNull()

private fun readUse(raw: String): Map<String, Float>? =
    catching { JSONObject(raw).let { json -> json.keys().asSequence().associateWith { json.getDouble(it).toFloat() } } }.logged("Settings.readUse").getOrNull()

private fun readTvs(raw: String): List<SavedTv> = catching {
    val array = JSONArray(raw)
    List(array.length()) { array.getJSONObject(it) }.mapNotNull { tv ->
        SavedTv(
            name = tv.optString("name"),
            host = tv.optString("host").ifEmpty { return@mapNotNull null },
            kind = TvKind.entries.firstOrNull { it.name == tv.optString("kind") } ?: TvKind.WEBOS,
            mac = tv.optString("mac"),
        )
    }
}.logged("Settings.readTvs").getOrDefault(emptyList())

private fun writeTvs(tvs: List<SavedTv>): String = JSONArray(
    tvs.map { JSONObject().put("name", it.name).put("host", it.host).put("kind", it.kind.name).put("mac", it.mac) },
).toString()
