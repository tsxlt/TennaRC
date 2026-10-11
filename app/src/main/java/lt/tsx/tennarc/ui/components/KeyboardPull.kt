package lt.tsx.tennarc.ui.components

import android.graphics.Insets
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal interface KeyboardInHand {
    val travel: Int

    fun start(): Boolean

    fun moveTo(pulled: Float)

    fun finish(shown: Boolean)
}

internal class ImeDrag(private val view: View) : KeyboardInHand {
    private var controller: WindowInsetsAnimationController? = null
    private var cancel: CancellationSignal? = null
    private var wanted = 0f

    override var travel by mutableIntStateOf(0)
        private set

    override fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (cancel != null) return true
        val signal = CancellationSignal().also { cancel = it }
        view.windowInsetsController?.controlWindowInsetsAnimation(
            android.view.WindowInsets.Type.ime(),
            -1L,
            LinearInterpolator(),
            signal,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                    this@ImeDrag.controller = controller
                    travel = controller.shownStateInsets.bottom - controller.hiddenStateInsets.bottom
                    moveTo(wanted)
                }

                override fun onFinished(controller: WindowInsetsAnimationController) = forget()

                override fun onCancelled(controller: WindowInsetsAnimationController?) = forget()
            },
        )
        return true
    }

    override fun moveTo(pulled: Float) {
        wanted = pulled
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val controller = controller?.takeIf { it.isReady } ?: return
        val up = controller.shownStateInsets.bottom
        val down = controller.hiddenStateInsets.bottom
        val bottom = (up - pulled.roundToInt()).coerceIn(down, up)
        controller.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom), 1f, if (up > down) (up - bottom).toFloat() / (up - down) else 1f)
    }

    override fun finish(shown: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val controller = controller
        if (controller != null && controller.isReady) controller.finish(shown) else cancel?.cancel()
        forget()
    }

    private fun forget() {
        controller = null
        cancel = null
        travel = 0
    }
}

internal fun pullLetsGo(pulled: Float, velocity: Float, far: Float, fling: Float): Boolean =
    velocity > fling || (velocity > -fling && pulled > far)

internal val PULL_FLING = 400.dp

internal val PullSettle: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

internal val SHEET_FAR = 56.dp

private const val KEYBOARD_BACK_MS = 700L

@Stable
internal class SheetKeyboardPull(
    private val keyboard: KeyboardInHand,
    private val scope: CoroutineScope,
    private val keyboardUp: () -> Boolean,
    private val inset: () -> Int,
    private val fling: Float,
    private val far: Float,
    private val putAway: () -> Unit,
    private val bringBack: () -> Unit,
    private val onAway: () -> Unit,
) {
    var pulled by mutableFloatStateOf(0f)
        private set

    var held by mutableStateOf(false)
        private set

    var height = 0

    private var going = false
    private var heldInset by mutableIntStateOf(0)
    private var inHand = false
    private var putAwayForDrag = false
    private var settling: Job? = null
    private val up = derivedStateOf { keyboardUp() }

    val engaged: Boolean get() = held || up.value

    private val keyboardTravel: Float
        get() = when {
            keyboard.travel > 0 -> keyboard.travel.toFloat()
            inHand -> heldInset.toFloat()
            else -> 0f
        }

    fun take(): Boolean {
        if (going) return false
        stopSettling()
        if (held) return true
        if (!keyboardUp()) return false
        held = true
        heldInset = inset()
        if (keyboard.start()) {
            inHand = true
        } else {
            putAway()
            putAwayForDrag = true
        }
        return true
    }

    fun pull(delta: Float): Float {
        if (!held || going) return 0f
        val before = pulled
        pulled = (pulled + delta).coerceAtLeast(0f)
        keyboard.moveTo(pulled)
        return pulled - before
    }

    fun letGo(velocity: Float) {
        if (!held || going) return
        settle(away = pullLetsGo(pulled, velocity, far = keyboardTravel + far, fling = fling), velocity)
    }

    fun release() {
        stopSettling()
        if (inHand) keyboard.finish(shown = false)
        inHand = false
    }

    fun insets(live: WindowInsets): WindowInsets = HeldInsets(live, this)

    internal fun heldBottom(live: Int): Int? = if (held) min(heldInset, live + pulled.roundToInt()) else null

    private fun stopSettling() {
        settling?.cancel()
        settling = null
    }

    private fun settle(away: Boolean, velocity: Float) {
        if (away) going = true
        stopSettling()
        settling = scope.launch {
            val target = if (away) max(height.toFloat(), pulled) + heldInset else 0f
            AnimationState(pulled, velocity).animateTo(target, PullSettle) {
                pulled = value.coerceAtLeast(0f)
                keyboard.moveTo(pulled)
                if (away && pulled >= height) cancelAnimation()
            }
            if (inHand) keyboard.finish(shown = !away)
            inHand = false
            if (away) {
                onAway()
                return@launch
            }
            if (putAwayForDrag) {
                putAwayForDrag = false
                bringBack()
                withTimeoutOrNull(KEYBOARD_BACK_MS) { snapshotFlow { keyboardUp() }.first { it } }
            }
            pulled = 0f
            held = false
            settling = null
        }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val dy = available.y
            return when {
                dy > 0f && take() -> Offset(0f, pull(min(dy, max(0f, keyboardTravel - pulled))))
                dy < 0f && held && !going -> {
                    stopSettling()
                    Offset(0f, pull(dy))
                }
                else -> Offset.Zero
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0f && held) Offset(0f, pull(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!held || going || settling != null) return Velocity.Zero
            val moved = pulled > 0f
            letGo(available.y)
            return if (moved) available else Velocity.Zero
        }
    }

    internal val dragState = DraggableState { pull(it) }
}

private class HeldInsets(private val live: WindowInsets, private val pull: SheetKeyboardPull) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = live.getLeft(density, layoutDirection)
    override fun getTop(density: Density) = live.getTop(density)
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = live.getRight(density, layoutDirection)
    override fun getBottom(density: Density) = live.getBottom(density).let { now -> pull.heldBottom(now) ?: now }

    override fun equals(other: Any?) = other is HeldInsets && other.live == live && other.pull === pull

    override fun hashCode() = 31 * live.hashCode() + System.identityHashCode(pull)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberSheetKeyboardPull(insets: WindowInsets, onAway: () -> Unit): SheetKeyboardPull {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val softKeyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    val away by rememberUpdatedState(onAway)
    val live by rememberUpdatedState(insets)
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val pull = remember(view, density) {
        SheetKeyboardPull(
            keyboard = ImeDrag(view),
            scope = scope,
            keyboardUp = { imeTarget.getBottom(density).let { it > 0 && ime.getBottom(density) >= it } },
            inset = { live.getBottom(density) },
            fling = with(density) { PULL_FLING.toPx() },
            far = with(density) { SHEET_FAR.toPx() },
            putAway = { softKeyboard?.hide() },
            bringBack = { softKeyboard?.show() },
            onAway = { away() },
        )
    }
    DisposableEffect(pull) { onDispose { pull.release() } }
    return pull
}

internal fun Modifier.sheetKeyboardPull(pull: SheetKeyboardPull): Modifier =
    this
        .nestedScroll(pull.connection)
        .draggable(
            pull.dragState,
            Orientation.Vertical,
            enabled = pull.engaged,
            onDragStarted = { pull.take() },
            onDragStopped = { pull.letGo(it) },
        )
        .onSizeChanged { pull.height = it.height }
        .graphicsLayer { translationY = pull.pulled }
