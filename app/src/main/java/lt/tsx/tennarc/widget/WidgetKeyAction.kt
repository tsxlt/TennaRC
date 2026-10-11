package lt.tsx.tennarc.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.withAppUse
import lt.tsx.tennarc.tenna
import lt.tsx.tennarc.tv.TvHub
import lt.tsx.tennarc.tv.TvState

class WidgetKeyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY_PARAM]?.let { name -> WidgetKey.entries.firstOrNull { it.name == name } } ?: return
        val app = context.tenna
        val started = System.currentTimeMillis()
        withContext(Dispatchers.Main.immediate) {
            val hub = app.controller.forWidget()
            val shown = widgetModel(hub.state.value, app.settings.prefs.value)
            if (key == WidgetKey.POWER) {
                hub.holding { power(context, glanceId, hub, shown, started) }
            } else {
                sendKey(hub, key)
            }
        }
        redraw(context, glanceId)
    }

    private suspend fun power(context: Context, id: GlanceId, hub: WidgetRemote, shown: WidgetModel, started: Long) {
        val state = if (shown.mode == WidgetMode.OFF) null else hub.settled(CONNECT_MS)?.let { hub.withPower(it) }
        val armedUntil = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[TvWidget.ARMED_UNTIL] ?: 0L
        when (powerPlan(state, shown.canWake, armed = System.currentTimeMillis() < armedUntil)) {
            PowerPlan.TURN_OFF -> {
                hub.powerOff()
                mark(context, id, TvWidget.ARMED_UNTIL, null)
                delay(SEND_MS)
            }
            PowerPlan.ARM -> {
                val until = System.currentTimeMillis() + ARM_MS
                mark(context, id, TvWidget.ARMED_UNTIL, until)
                delay(ARM_MS)
                mark(context, id, TvWidget.ARMED_UNTIL, null, onlyIf = until)
            }
            PowerPlan.WAKE -> {
                hub.wake()
                val left = (started + BUDGET_MS - System.currentTimeMillis()).coerceAtLeast(0L)
                val until = System.currentTimeMillis() + left
                mark(context, id, TvWidget.WAKING_UNTIL, until)
                withTimeoutOrNull(left) { hub.state.first { it.isUsable } }
                mark(context, id, TvWidget.WAKING_UNTIL, null, onlyIf = until)
            }
            PowerPlan.NOTHING -> Unit
        }
    }

    private suspend fun mark(context: Context, id: GlanceId, key: Preferences.Key<Long>, until: Long?, onlyIf: Long? = null) {
        updateAppWidgetState(context, id) { prefs ->
            if (onlyIf == null || prefs[key] == onlyIf) {
                if (until == null) prefs.remove(key) else prefs[key] = until
            }
        }
        TvWidget().update(context, id)
    }
}

class WidgetLaunchAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appId = parameters[APP_PARAM] ?: return
        val app = context.tenna
        withContext(Dispatchers.Main.immediate) {
            launchApp(app.controller.forWidget(), appId, app.settings::update, demo = app.settings.prefs.value.demoTv)
        }
        redraw(context, glanceId)
    }
}

class WidgetSwitchTvAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.tenna
        withContext(Dispatchers.Main.immediate) {
            switchTv(app.controller.forWidget(), app.settings::update) { redraw(context, glanceId) }
        }
        redraw(context, glanceId)
    }
}

internal interface WidgetRemote {
    val state: StateFlow<TvState>
    fun hold()
    fun release()
    fun playPause()
    fun volumeUp()
    fun volumeDown()
    fun setMuted(muted: Boolean)
    fun launch(appId: String)
    fun wake()
    fun powerOff()
}

internal fun TvHub.forWidget(): WidgetRemote = object : WidgetRemote {
    override val state get() = this@forWidget.state
    override fun hold() = this@forWidget.hold()
    override fun release() = this@forWidget.release()
    override fun playPause() = this@forWidget.playPause()
    override fun volumeUp() = this@forWidget.volumeUp()
    override fun volumeDown() = this@forWidget.volumeDown()
    override fun setMuted(muted: Boolean) = this@forWidget.setMuted(muted)
    override fun launch(appId: String) = this@forWidget.launch(appId)
    override fun wake() = this@forWidget.wake()
    override fun powerOff() = this@forWidget.powerOff()
}

internal suspend fun <T> WidgetRemote.holding(block: suspend () -> T): T {
    hold()
    try {
        return block()
    } finally {
        release()
    }
}

internal suspend fun sendKey(remote: WidgetRemote, key: WidgetKey): Boolean = remote.holding {
    val state = remote.settled(CONNECT_MS)
    if (state == null || !state.isUsable) return@holding false
    when (key) {
        WidgetKey.PLAY_PAUSE -> remote.playPause()
        WidgetKey.VOLUME_DOWN -> remote.volumeDown()
        WidgetKey.VOLUME_UP -> remote.volumeUp()
        WidgetKey.MUTE -> remote.setMuted(!state.muted)
        WidgetKey.POWER -> return@holding false
    }
    delay(SEND_MS)
    true
}

internal suspend fun launchApp(
    remote: WidgetRemote,
    appId: String,
    updatePrefs: ((Prefs) -> Prefs) -> Unit,
    demo: Boolean,
    now: () -> Long = System::currentTimeMillis,
): Boolean = remote.holding {
    val state = remote.settled(CONNECT_MS)
    if (state == null || !state.isUsable) return@holding false
    remote.launch(appId)
    if (!demo) updatePrefs { it.withAppUse(appId, now()) }
    delay(SEND_MS)
    true
}

internal suspend fun switchTv(remote: WidgetRemote, updatePrefs: ((Prefs) -> Prefs) -> Unit, switched: suspend () -> Unit = {}): TvState? {
    val before = remote.state.value.host
    var moved = false
    updatePrefs { prefs -> prefs.switchToNext().also { moved = it != prefs } }
    if (!moved) return null
    switched()
    return remote.holding {
        withTimeoutOrNull(CONNECT_MS) { remote.state.first { it.host != before && it.connection.isSettled } }
    }
}

private suspend fun redraw(context: Context, id: GlanceId) {
    try {
        TvWidget().update(context, id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Tenna", "widget redraw failed", e)
    }
}

internal const val CONNECT_MS = 4_000L

private const val POWER_MS = 1_500L
internal const val SEND_MS = 800L
private const val ARM_MS = 3_500L

private const val BUDGET_MS = 9_000L

private suspend fun WidgetRemote.settled(ms: Long): TvState? = withTimeoutOrNull(ms) { state.first { it.connection.isSettled } }

private suspend fun WidgetRemote.withPower(connected: TvState): TvState {
    if (!connected.isConnected || connected.power != null) return connected
    return withTimeoutOrNull(POWER_MS) { state.first { it.power != null || !it.isConnected } } ?: state.value
}
