package lt.tsx.tennarc.ui.sheets

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.ui.components.ImeDrag
import lt.tsx.tennarc.ui.components.PULL_FLING
import lt.tsx.tennarc.ui.components.PullSettle
import lt.tsx.tennarc.ui.components.backReach
import lt.tsx.tennarc.ui.components.pullLetsGo
import lt.tsx.tennarc.ui.theme.LocalGround
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.scrimAlpha

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.KeyboardPanel(
    keyboard: TvKeyboard?,
    enabled: Boolean,
    onType: (TextEdit) -> Unit,
    onEnter: () -> Unit,
    leave: Boolean,
    onClosed: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val softKeyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val navBars = WindowInsets.navigationBars
    var text by remember { mutableStateOf("") }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    var height by remember { mutableIntStateOf(0) }
    var imeFull by remember { mutableIntStateOf(0) }
    var risen by remember { mutableStateOf(false) }
    var sinking by remember { mutableStateOf(false) }
    var going by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val slide = remember { Animatable(0f) }
    var pulled by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    var base by remember { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val imeDrag = remember(view) { ImeDrag(view) }
    DisposableEffect(Unit) { onDispose { imeDrag.finish(shown = false) } }

    fun fraction(now: Int) = if (imeFull > 0) (now.toFloat() / imeFull).coerceIn(0f, 1f) else 0f
    fun follow(now: Int) = (fraction(now) / FOLLOW_SPLIT).coerceIn(0f, 1f)
    fun shown(now: Int) = when {
        sinking -> follow(now)
        !risen -> max(slide.value, follow(now))
        else -> 1f
    }
    fun pullOffset(now: Int, nav: Int) = if (held) max(0f, pulled - max(0, base - max(now, nav))) else pulled
    fun goneAt() = (base + height).toFloat()
    fun offset(now: Int, nav: Int) = (1f - shown(now)) * height + pullOffset(now, nav)
    fun hidden(now: Int) = if (height == 0) 1f else max(1f - shown(now), pulled / goneAt()).coerceIn(0f, 1f)

    fun close() {
        if (closed) return
        closed = true
        onClosed()
    }

    fun hold() {
        if (held) return
        held = true
        base = max(ime.getBottom(density), navBars.getBottom(density))
    }

    fun settle(away: Boolean, velocity: Float = 0f) {
        hold()
        if (going && !away) return
        if (away) going = true
        settling?.cancel()
        settling = scope.launch {
            animate(
                pulled,
                if (away) goneAt() else 0f,
                velocity,
                PullSettle,
            ) { value, _ ->
                pulled = value.coerceAtLeast(0f)
                imeDrag.moveTo(pulled)
            }
            imeDrag.finish(shown = !away)
            if (away) {
                close()
            } else {
                pulled = 0f
                held = false
            }
        }
    }

    fun dismiss() {
        if (going) return
        when {
            held -> settle(away = true)
            ime.getBottom(density) > 0 || imeTarget.getBottom(density) > 0 -> {
                going = true
                sinking = true
                softKeyboard?.hide()
                scope.launch {
                    delay(KEYBOARD_GONE_MS)
                    if (!closed) settle(away = true)
                }
            }
            else -> settle(away = true)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            delay(NO_KEYBOARD_MS)
            if (!risen && imeTarget.getBottom(density) == 0) {
                slide.animateTo(1f, tween(250))
                risen = true
            }
        }
        var wasUp = false
        snapshotFlow {
            Triple(imeTarget.getBottom(density), ime.getBottom(density), held || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
        }.collect { (target, now, busy) ->
            if (target > 0) imeFull = target
            if (!busy) {
                when {
                    target > 0 -> if (now >= target) {
                        risen = true
                        if (!going) sinking = false
                    }
                    now > 0 && imeFull > 0 && (text.isEmpty() || going || !risen) -> sinking = true
                }
                when {
                    sinking && target == 0 && now == 0 -> close()
                    wasUp && target == 0 && now == 0 && risen && text.isEmpty() -> dismiss()
                }
            }
            wasUp = now > 0
        }
    }

    LaunchedEffect(leave) {
        if (leave) {
            delay(AUTO_DISMISS_DELAY_MS)
            dismiss()
        }
    }

    PredictiveBackHandler(enabled = !going) { swipe ->
        if (!going) settling?.cancel()
        hold()
        try {
            swipe.collect { if (!going) pulled = backReach(it.progress) * height * BACK_GIVE }
        } catch (e: CancellationException) {
            settle(away = false)
            throw e
        }
        dismiss()
    }

    val dragState = rememberDraggableState { delta ->
        if (going) return@rememberDraggableState
        pulled = (pulled + delta).coerceAtLeast(0f)
        imeDrag.moveTo(pulled)
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - hidden(ime.getBottom(this)) }
            .background(Color.Black.copy(alpha = MaterialTheme.colorScheme.scrimAlpha))
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
    )
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(ime)) {
        SnackbarHost(snackbar)
        val title = stringResource(R.string.keyboard_title)
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .graphicsLayer { translationY = offset(ime.getBottom(this), navBars.getBottom(this)) }
                .glass(glass(), BottomSheetDefaults.ExpandedShape)
                .semantics {
                    paneTitle = title
                    dismiss {
                        dismiss()
                        true
                    }
                }
                .draggable(
                    dragState,
                    Orientation.Vertical,
                    enabled = !going,
                    onDragStarted = {
                        if (!going) settling?.cancel()
                        hold()
                        val target = imeTarget.getBottom(density)
                        if (target > 0 && ime.getBottom(density) >= target) imeDrag.start()
                    },
                    onDragStopped = { velocity ->
                        if (!going) {
                            val fling = with(density) { PULL_FLING.toPx() }
                            val far = if (imeDrag.travel > 0) imeDrag.travel * 0.4f else height * 0.35f
                            settle(away = pullLetsGo(pulled, velocity, far, fling), velocity)
                        }
                    },
                )
                .windowInsetsPadding(navBars),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, LocalGround provides MaterialTheme.colorScheme.surfaceContainerLow) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                KeyboardSheetContent(
                    keyboard = keyboard,
                    enabled = enabled,
                    text = text,
                    onText = { text = it },
                    onType = onType,
                    onEnter = onEnter,
                )
            }
        }
    }
}

private const val NO_KEYBOARD_MS = 400L
private const val KEYBOARD_GONE_MS = 700L
private const val AUTO_DISMISS_DELAY_MS = 800L
private const val BACK_GIVE = 0.85f
private const val FOLLOW_SPLIT = 0.5f
