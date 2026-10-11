package lt.tsx.tennarc.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.tsx.tennarc.BuildConfig
import lt.tsx.tennarc.TennaApp
import lt.tsx.tennarc.ui.theme.isDarkTheme

internal object TvWidgets {
    private const val REDRAW_EVERY_MS = 1_000L
    private const val TAG = "Tenna"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val ticks = MutableStateFlow(0)

    private var feed: Pair<TennaApp, StateFlow<WidgetModel>>? = null

    fun models(app: TennaApp): StateFlow<WidgetModel> = synchronized(this) {
        feed?.takeIf { it.first === app }?.second
            ?: combine(app.controller.state, app.settings.prefs, ticks) { state, prefs, _ ->
                widgetModel(state, prefs, dark = isDarkTheme(app, prefs.themeMode))
            }
                .distinctUntilChanged()
                .atMostEvery(REDRAW_EVERY_MS)
                .stateIn(scope, SharingStarted.Eagerly, widgetModel(app.controller.state.value, app.settings.prefs.value, isDarkTheme(app, app.settings.prefs.value.themeMode)))
                .also { feed = app to it }
    }

    fun refresh() {
        ticks.value++
    }

    fun follow(app: TennaApp) {
        scope.launch {
            models(app).collect {
                if (!placed(app)) return@collect
                try {
                    TvWidget().updateAll(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "widget update failed", e)
                }
            }
        }
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = refresh()

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
        if (placed(app)) WidgetRefresh.schedule(app)
        offerPreviews(app)
    }

    private fun offerPreviews(app: TennaApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val store = app.getSharedPreferences("widget", Context.MODE_PRIVATE)
        if (store.getInt("previews_for", -1) == BuildConfig.VERSION_CODE) return
        scope.launch(Dispatchers.Default) {
            try {
                val result = GlanceAppWidgetManager(app).setWidgetPreviews(TvWidgetReceiver::class)
                if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                    store.edit().putInt("previews_for", BuildConfig.VERSION_CODE).apply()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "widget previews failed", e)
            }
        }
    }

    private fun placed(context: Context): Boolean =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, TvWidgetReceiver::class.java))?.isNotEmpty() == true
}
