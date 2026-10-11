package lt.tsx.tennarc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import lt.tsx.tennarc.data.AppLanguage
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.Sheet
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.FollowSystemBars
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.applySystemBars
import lt.tsx.tennarc.ui.theme.isDarkTheme
import lt.tsx.tennarc.ui.theme.rememberDarkTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    private val app get() = tenna

    private val lowLatency: WifiManager.WifiLock? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@lazy null
        (applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "TennaRC remote")
            ?.apply { setReferenceCounted(false) }
    }

    private val volumeNudges = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    private var requestedSheet by mutableStateOf<Sheet?>(null)

    private val frameLog by lazy { FrameLog(window) }

    override fun onResume() {
        super.onResume()
        frameLog.start()
    }

    override fun onPause() {
        frameLog.stop()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(isDarkTheme(this, app.settings.prefs.value.themeMode))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedSheet = sheetFor(intent)
        val controller = app.controller
        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            val prefs by app.settings.prefs.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            val context = LocalContext.current
            LaunchedEffect(controller) {
                var showing: Job? = launch { CrashReports.offer(context, snackbar) }
                controller.notices.collect {
                    val text = context.getString(it.text)
                    if (snackbar.currentSnackbarData?.visuals?.message == text) return@collect
                    showing?.cancel()
                    showing = launch { snackbar.showSnackbar(text) }
                }
            }
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) RemoteService.start(context)
            }
            val remoteUp = state.isUsable && !prefs.needsTvSetup
            LaunchedEffect(remoteUp, prefs.notificationControls) {
                if (!remoteUp || !prefs.notificationControls) return@LaunchedEffect
                when {
                    canNotify() -> RemoteService.start(context)
                    !prefs.askedNotifications -> {
                        controller.updatePrefs { it.copy(askedNotifications = true) }
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            val view = LocalView.current
            DisposableEffect(prefs.keepScreenOn) {
                view.keepScreenOn = prefs.keepScreenOn
                onDispose { view.keepScreenOn = false }
            }
            val dark = rememberDarkTheme(prefs.themeMode)
            FollowSystemBars(this, dark)
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = prefs.haptics, iconLoader = controller.iconLoader, tvName = prefs.tvName, posterLoader = app.posters) {
                    RemoteScreen(
                        state = state,
                        prefs = prefs,
                        actions = controller,
                        snackbar = snackbar,
                        requestedSheet = requestedSheet,
                        onSheetRequestHandled = { requestedSheet = null },
                        onKey = ::routeVolumeKey,
                        volumeNudges = volumeNudges,
                    )
                }
            }
        }
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sheetFor(intent)?.let { requestedSheet = it }
    }

    private fun sheetFor(intent: Intent?): Sheet? = when (intent?.action) {
        ACTION_OPEN_LINK -> Sheet.SEARCH
        ACTION_KEYBOARD -> Sheet.KEYBOARD
        else -> null
    }

    override fun onStart() {
        super.onStart()
        lowLatency?.acquire()
        app.controller.onScreenStarted()
    }

    override fun onStop() {
        lowLatency?.takeIf { it.isHeld }?.release()
        app.controller.onScreenStopped()
        super.onStop()
    }

    fun routeVolumeKey(event: KeyEvent): Boolean {
        if (!routesVolumeKeys()) return false
        val press = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (press) {
                app.controller.volumeUp()
                volumeNudges.tryEmit(1)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (press) {
                app.controller.volumeDown()
                volumeNudges.tryEmit(-1)
            }
            KeyEvent.KEYCODE_VOLUME_MUTE ->
                if (press && event.repeatCount == 0) app.controller.setMuted(!app.controller.state.value.muted)
            else -> return false
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = routeVolumeKey(event) || super.dispatchKeyEvent(event)

    private fun routesVolumeKeys() = app.settings.prefs.value.volumeKeys && app.controller.state.value.isUsable

    private companion object {
        const val ACTION_OPEN_LINK = "lt.tsx.tennarc.OPEN_LINK"
        const val ACTION_KEYBOARD = "lt.tsx.tennarc.KEYBOARD"
    }
}
