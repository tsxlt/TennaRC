package lt.tsx.tennarc.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.ui.theme.ICON_CORNER
import lt.tsx.tennarc.ui.theme.IconShape

private const val QUIET_DB = -2f
private const val LOUD_DB = 10f

private val RING_REST = 15.dp
private val RING_SWELL = 6.dp

private const val RING_CORNER = 2 * ICON_CORNER

internal fun loudness(rmsdB: Float): Float = ((rmsdB - QUIET_DB) / (LOUD_DB - QUIET_DB)).coerceIn(0f, 1f)

@Stable
class VoiceSearch internal constructor(
    val available: Boolean,
) {
    var listening by mutableStateOf(false)
        internal set

    var heard by mutableStateOf("")
        internal set

    var level by mutableFloatStateOf(0f)
        internal set

    internal var onToggle: () -> Unit = {}
    internal var onCancel: () -> Unit = {}

    fun toggle() = onToggle()

    fun cancel() = onCancel()
}

@Composable
fun rememberVoiceSearch(onHeard: (String) -> Unit, snackbar: SnackbarHostState? = null): VoiceSearch {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val voice = remember(context) { VoiceSearch(available = SpeechRecognizer.isRecognitionAvailable(context)) }
    val engine = remember(voice) { Recognizer(context, voice) }
    val listen = {
        haptics.tick()
        engine.start()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen() }
    var asked by rememberSaveable { mutableStateOf(false) }
    val micOff = stringResource(R.string.voice_mic_off)
    val settings = stringResource(R.string.settings)
    SideEffect {
        engine.onHeard = onHeard
        voice.onCancel = engine::stop
        voice.onToggle = {
            when {
                voice.listening -> engine.stop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
                asked && snackbar != null && activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false -> {
                    if (snackbar.currentSnackbarData?.visuals?.message != micOff) {
                        scope.launch {
                            val result = snackbar.showSnackbar(micOff, actionLabel = settings, duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) {
                                catching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                                }
                            }
                        }
                    }
                }
                else -> {
                    asked = true
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
    if (voice.listening) BackHandler { engine.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { engine.stop() }
    DisposableEffect(engine) { onDispose { engine.stop() } }
    return voice
}

private class Recognizer(private val context: Context, private val voice: VoiceSearch) {
    var onHeard: (String) -> Unit = {}
    private var current: SpeechRecognizer? = null

    fun start() {
        stop()
        val recognizer = catching { SpeechRecognizer.createSpeechRecognizer(context) }.logged("Voice.start").getOrNull() ?: return
        current = recognizer
        recognizer.setRecognitionListener(Listener(recognizer))
        voice.heard = ""
        voice.level = 0f
        voice.listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        catching { recognizer.startListening(intent) }.onFailure { stop() }
    }

    fun stop() {
        val recognizer = current ?: return
        current = null
        voice.listening = false
        voice.level = 0f
        catching {
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    private inner class Listener(private val mine: SpeechRecognizer) : RecognitionListener {
        private val live get() = current === mine

        override fun onRmsChanged(rmsdB: Float) {
            if (live) voice.level = loudness(rmsdB)
        }

        override fun onEndOfSpeech() {
            if (live) voice.level = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (live) best(partialResults)?.let { voice.heard = it }
        }

        override fun onResults(results: Bundle?) {
            if (!live) return
            val text = best(results) ?: voice.heard
            stop()
            if (text.isNotBlank()) onHeard(text)
        }

        override fun onError(error: Int) {
            if (live) stop()
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun best(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

@Composable
internal fun MicButton(voice: VoiceSearch) {
    val colors = MaterialTheme.colorScheme
    val on by animateFloatAsState(
        if (voice.listening) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "micOn",
    )
    val level by animateFloatAsState(voice.level, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "micLevel")
    val wash = colors.onSurface
    val edge = colors.primary
    val disc = remember { SquircleCache() }
    val ring = remember { SquircleCache() }
    IconButton(
        onClick = voice::toggle,
        shape = IconShape,
        modifier = Modifier.drawBehind {
            if (on <= 0.01f) return@drawBehind
            val fade = on.coerceAtMost(1f)
            val stroke = 1.dp.toPx()
            val radius = (RING_REST + RING_SWELL * level).toPx() * on
            val inner = radius - stroke / 2
            val cx = size.width / 2
            val cy = size.height / 2
            drawPath(disc.of(cx - radius, cy - radius, 2 * radius, 2 * radius, radius * RING_CORNER), wash.copy(alpha = 0.08f * fade))
            drawPath(ring.of(cx - inner, cy - inner, 2 * inner, 2 * inner, inner * RING_CORNER), edge.copy(alpha = 0.7f * fade), style = Stroke(stroke))
        },
    ) {
        Icon(Lucide.Mic, contentDescription = stringResource(if (voice.listening) R.string.voice_stop else R.string.voice_search))
    }
}
