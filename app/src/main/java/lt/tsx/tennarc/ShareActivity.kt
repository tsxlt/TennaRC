package lt.tsx.tennarc

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.data.AppLanguage
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.describePhoneFile
import lt.tsx.tennarc.link.isPhoneFile
import lt.tsx.tennarc.link.phoneFileThumbnail
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.ui.components.NoInsets
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SheetInsets
import lt.tsx.tennarc.ui.sheets.ShareSheetContent
import lt.tsx.tennarc.ui.theme.FollowSystemBars
import lt.tsx.tennarc.ui.theme.ProvideGround
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.applySystemBars
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.isDarkTheme
import lt.tsx.tennarc.ui.theme.rememberDarkTheme

class ShareActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    private val app get() = tenna

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(isDarkTheme(this, app.settings.prefs.value.themeMode))
        super.onCreate(savedInstanceState)
        val shared = listOfNotNull(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            intent.dataString,
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
        ).joinToString(" ")
        val stream = sharedFile()
        val link = if (stream == null) LinkRouter.extract(shared)?.let(LinkRouter::classify) else null
        val controller = app.controller

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            val prefs by app.settings.prefs.collectAsStateWithLifecycle()
            var file by remember { mutableStateOf<LinkTarget.Media?>(null) }
            var ready by remember { mutableStateOf(stream == null) }
            var picture by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(stream) {
                if (stream == null) return@LaunchedEffect
                val described = withContext(Dispatchers.IO) { describePhoneFile(contentResolver, stream, intent.type) }
                file = described
                ready = true
                if (described != null) {
                    picture = withContext(Dispatchers.IO) { phoneFileThumbnail(this@ShareActivity, stream, described.mime, THUMBNAIL_PX) }?.asImageBitmap()
                }
            }
            var failure by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(controller) {
                controller.notices.collect { if (it in EXPLAINS_FAILURE) failure = getString(it.text) }
            }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            val close = remember {
                { scope.launch { sheetState.hide() }.invokeOnCompletion { finish() } }
            }
            val dark = rememberDarkTheme(prefs.themeMode)
            FollowSystemBars(this, dark)
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = prefs.haptics, iconLoader = controller.iconLoader, tvName = prefs.tvName) {
                    val shape = BottomSheetDefaults.ExpandedShape
                    val frost = glass()
                    ModalBottomSheet(
                        onDismissRequest = ::finish,
                        sheetState = sheetState,
                        shape = shape,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        contentWindowInsets = { NoInsets },
                        dragHandle = null,
                    ) {
                        Column(Modifier.glass(frost, shape).windowInsetsPadding(SheetInsets)) {
                            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                            if (ready) ProvideGround(frost.fill) {
                                ShareSheetContent(
                                    target = if (stream != null) file else link,
                                    state = state,
                                    actionsFor = controller::actionsFor,
                                    onOpen = { target, action ->
                                        failure = null
                                        controller.open(target, action).also { opened -> if (opened && !target.isPhoneFile) app.settings.addRecent(target.url) }
                                    },
                                    onOpenApp = {
                                        startActivity(Intent(this@ShareActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        finish()
                                    },
                                    onClose = { close() },
                                    picture = picture,
                                    failure = failure,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sharedFile(): Uri? {
        val kind = intent.type.orEmpty().substringBefore('/')
        if (kind != "video" && kind != "image" && kind != "audio") return null
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        return streams.firstOrNull() ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    override fun onStart() {
        super.onStart()
        app.controller.onScreenStarted()
    }

    override fun onStop() {
        app.controller.onScreenStopped()
        super.onStop()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        const val THUMBNAIL_PX = 256

        val EXPLAINS_FAILURE = setOf(
            Notice.NOT_CONNECTED,
            Notice.NO_RENDERER,
            Notice.CAST_UNSUPPORTED,
            Notice.NOT_ON_TV_NETWORK,
            Notice.FILE_UNREADABLE,
        )
    }
}
