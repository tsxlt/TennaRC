package lt.tsx.tennarc.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import lt.tsx.tennarc.data.DEMO_KEY
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.currentTv
import lt.tsx.tennarc.data.deckKey
import lt.tsx.tennarc.data.hiddenHere
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.data.tvKeys
import lt.tsx.tennarc.data.useDemo
import lt.tsx.tennarc.nowPlaying
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.DeckUse
import lt.tsx.tennarc.ui.components.deckItems
import lt.tsx.tennarc.ui.components.mirrorApp

internal data class WidgetModel(
    val tvName: String,
    val mode: WidgetMode,
    val title: String?,
    val playing: Boolean,
    val muted: Boolean,
    val canWake: Boolean,
    val app: WidgetApp? = null,
    val casting: Boolean = false,
    val favorites: List<WidgetApp> = emptyList(),
    val nextTv: NextTv? = null,
    val dark: Boolean = true,
)

internal data class WidgetApp(val id: String, val title: String, val iconUrl: String? = null, val input: Boolean = false)

private fun TvApp.forWidget() = WidgetApp(id, title, iconUrl, input)

internal data class NextTv(val name: String, val demo: Boolean = false)

internal enum class WidgetMode {
    READY,

    OFF,

    SETUP,
}

internal fun widgetModel(state: TvState, prefs: Prefs, dark: Boolean = true): WidgetModel {
    val mode = when (val connection = state.connection) {
        Connection.Connected -> if (state.power == TvPower.STANDBY) WidgetMode.OFF else WidgetMode.READY
        Connection.Offline -> WidgetMode.OFF
        is Connection.NeedsPairing, Connection.AwaitingApproval, is Connection.AwaitingCode -> WidgetMode.SETUP
        Connection.Connecting -> WidgetMode.READY
    }
    val live = mode == WidgetMode.READY && state.isConnected
    val now = nowPlaying(state, idle = "", onTv = "")
    val title = now.title.takeIf { live && it.isNotEmpty() }
    val app = state.foregroundAppId?.takeIf { title != null && state.cast == null }?.let { id ->
        (state.apps + state.inputs).firstOrNull { it.id == id }?.forWidget() ?: WidgetApp(id, title!!)
    }
    return WidgetModel(
        tvName = prefs.tvName,
        mode = mode,
        title = title,
        playing = live && now.playing,
        muted = live && state.muted,
        canWake = Feature.WAKE in state.features && prefs.mac.isNotBlank(),
        app = app,
        casting = title != null && state.cast != null,
        favorites = widgetFavorites(state, prefs),
        nextTv = nextTv(prefs),
        dark = dark,
    )
}

internal const val MAX_FAVORITES = 4

internal fun widgetFavorites(state: TvState, prefs: Prefs): List<WidgetApp> {
    if (Feature.APP_LIST !in state.features || state.apps.isEmpty()) return emptyList()
    val hidden = prefs.hiddenHere
    val receiver = mirrorApp(state.apps)
    val apps = state.apps.filterNot { it == receiver || prefs.deckKey(it.id) in hidden }
    if (apps.isEmpty()) return emptyList()
    val row = deckItems(apps, state.inputs, emptyList(), activeId = null, DeckUse(prefs.appUse), slots = MAX_FAVORITES + 1).row
    return row.filterNot { it.id == state.foregroundAppId }.take(MAX_FAVORITES).map { it.forWidget() }
}

internal fun nextTvKey(prefs: Prefs): String? {
    val keys = prefs.tvKeys
    if (keys.size < 2) return null
    val using = if (prefs.demoTv) DEMO_KEY else prefs.host
    return keys[(keys.indexOf(using) + 1).mod(keys.size)]
}

internal fun nextTv(prefs: Prefs): NextTv? = when (val key = nextTvKey(prefs)) {
    null -> null
    DEMO_KEY -> NextTv(name = "", demo = true)
    prefs.host -> NextTv(prefs.tvName)
    else -> prefs.otherTvs.firstOrNull { it.host == key }?.let { NextTv(it.name) }
}

internal fun Prefs.switchToNext(): Prefs {
    val key = nextTvKey(this) ?: return this
    return when (key) {
        DEMO_KEY -> useDemo()
        host -> switchTo(currentTv)
        else -> otherTvs.firstOrNull { it.host == key }?.let { switchTo(it) } ?: this
    }
}

internal enum class WidgetLine { ARMED, WAKING, OFF, SETUP, TITLE, NONE }

internal fun widgetLine(model: WidgetModel, armed: Boolean, waking: Boolean): WidgetLine = when {
    armed && model.mode == WidgetMode.READY -> WidgetLine.ARMED
    waking && model.mode != WidgetMode.SETUP && model.title == null -> WidgetLine.WAKING
    model.mode == WidgetMode.OFF -> WidgetLine.OFF
    model.mode == WidgetMode.SETUP -> WidgetLine.SETUP
    model.title != null -> WidgetLine.TITLE
    else -> WidgetLine.NONE
}

internal enum class WidgetLayout {
    COMPACT,

    SQUARE,

    ROW,

    CARD,

    HERO,
}

internal val WIDE = 200.dp

internal val TALL = 100.dp

internal val HERO_TALL = 190.dp

internal fun widgetLayout(size: DpSize): WidgetLayout = when {
    size.width < WIDE -> if (size.height < TALL) WidgetLayout.COMPACT else WidgetLayout.SQUARE
    size.height < TALL -> WidgetLayout.ROW
    size.height < HERO_TALL -> WidgetLayout.CARD
    else -> WidgetLayout.HERO
}

internal val KEY = 48.dp

internal val ART = 56.dp

internal val HEADER = 28.dp

internal val MIN_MARGIN = 8.dp
internal val MIN_GAP = 6.dp

internal val MAX_MARGIN = 16.dp

internal val MAX_GAP = 24.dp

internal data class WidgetStack(
    val layout: WidgetLayout,
    val art: Boolean = false,
    val chip: Boolean = false,
    val favorites: Int = 0,
    val margin: Dp = MIN_MARGIN,
    val gap: Dp? = null,
) {
    val heights: List<Dp>
        get() = buildList {
            when {
                !art -> add(KEY)
                layout == WidgetLayout.SQUARE -> addAll(listOf(KEY, ART))
                else -> addAll(listOf(HEADER, ART))
            }
            if (favorites > 0) add(KEY)
            add(KEY)
        }

    val rows: Int get() = heights.size

    val height: Dp get() = heights.fold(0.dp) { sum, row -> sum + row }
}

internal fun widgetStack(layout: WidgetLayout, size: DpSize, model: WidgetModel, columns: Int): WidgetStack {
    fun fits(stack: WidgetStack) = stack.height + MIN_MARGIN * 2 + MIN_GAP * (stack.rows - 1) <= size.height
    var stack = WidgetStack(layout)
    fun tryAdding(more: WidgetStack, always: Boolean = false) {
        if (always || fits(more)) stack = more
    }
    val favorites = model.favorites.size.coerceAtMost(columns)
    when (layout) {
        WidgetLayout.SQUARE -> {
            tryAdding(stack.copy(art = true))
            if (favorites > 0) tryAdding(stack.copy(favorites = favorites))
        }
        WidgetLayout.CARD, WidgetLayout.HERO -> {
            if (favorites > 0) tryAdding(stack.copy(favorites = favorites), always = layout == WidgetLayout.HERO)
            tryAdding(stack.copy(art = true, chip = model.nextTv != null))
        }
        WidgetLayout.COMPACT, WidgetLayout.ROW -> Unit
    }
    val spare = (size.height - stack.height).coerceAtLeast(0.dp)
    val margin = (spare / (stack.rows + 1)).coerceIn(0.dp, MAX_MARGIN)
    val gaps = (spare - margin * 2) / (stack.rows - 1)
    if (gaps <= MAX_GAP) return stack.copy(margin = margin)
    return stack.copy(margin = (spare - MAX_GAP * (stack.rows - 1)) / 2, gap = MAX_GAP)
}

internal fun widgetColumns(inner: Dp): Int = if (inner >= KEY * 4 + MIN_GAP * 3) 4 else 3

internal enum class WidgetKey { PLAY_PAUSE, VOLUME_DOWN, VOLUME_UP, MUTE, POWER }

internal enum class PowerPlan { WAKE, ARM, TURN_OFF, NOTHING }

internal fun powerPlan(state: TvState?, canWake: Boolean, armed: Boolean): PowerPlan = when {
    state != null && state.isUsable -> if (armed) PowerPlan.TURN_OFF else PowerPlan.ARM
    state?.connection is Connection.NeedsPairing -> PowerPlan.NOTHING
    canWake -> PowerPlan.WAKE
    else -> PowerPlan.NOTHING
}

internal val Connection.isSettled: Boolean
    get() = this == Connection.Connected || this == Connection.Offline || this is Connection.NeedsPairing

internal fun <T> Flow<T>.atMostEvery(periodMs: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMs)
}
