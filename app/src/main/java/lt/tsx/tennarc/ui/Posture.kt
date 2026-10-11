package lt.tsx.tennarc.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CancellationException
import lt.tsx.tennarc.logFailure

@Immutable
data class Fold(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp, val vertical: Boolean, val separating: Boolean)

@Immutable
sealed interface RemoteLayout {
    data object Upright : RemoteLayout

    data object Sideways : RemoteLayout

    data class Panes(val gapStart: Dp, val gapEnd: Dp) : RemoteLayout

    data class Tabletop(val gapTop: Dp, val gapBottom: Dp) : RemoteLayout
}

val RemoteLayout.rollsDeck: Boolean get() = this == RemoteLayout.Upright || this == RemoteLayout.Sideways

fun remoteLayout(width: Dp, height: Dp, fold: Fold?, rtl: Boolean = false): RemoteLayout {
    if (fold != null && !fold.vertical && fold.separating &&
        fold.top >= TABLETOP_HALF && height - fold.bottom >= TABLETOP_HALF
    ) {
        return RemoteLayout.Tabletop(fold.top, fold.bottom)
    }
    val wide = width >= PANES_WIDTH
    if (wide && height >= PANES_HEIGHT) {
        if (fold != null && fold.vertical) {
            val start = if (rtl) width - fold.right else fold.left
            val end = if (rtl) width - fold.left else fold.right
            if (start >= PANE_MIN && width - end >= PANE_MIN) return RemoteLayout.Panes(start, end)
        }
        val control = (width / 2).coerceAtMost(CONTROL_PANE_MAX)
        return RemoteLayout.Panes(width - control, width - control)
    }
    if (wide && width > height) return RemoteLayout.Sideways
    return RemoteLayout.Upright
}

private val PANES_WIDTH = 600.dp
private val PANES_HEIGHT = 480.dp

val CONTROL_PANE_MAX = 440.dp

private val PANE_MIN = 280.dp

private val TABLETOP_HALF = 240.dp

internal val TABLETOP_SIDE_BY_SIDE = 600.dp

@Immutable
data class SheetSpot(val width: Dp, val shift: Dp, val height: Dp? = null)

fun sheetSpot(sheet: Sheet, layout: RemoteLayout, window: DpSize): SheetSpot? = when (layout) {
    RemoteLayout.Upright -> null
    RemoteLayout.Sideways -> SheetSpot(window.width / 2, -window.width / 4)
    is RemoteLayout.Panes -> if (sheet == Sheet.MORE) {
        val width = window.width - layout.gapEnd
        SheetSpot(width, layout.gapEnd + width / 2 - window.width / 2)
    } else {
        SheetSpot(layout.gapStart.coerceAtMost(SHEET_WIDTH), layout.gapStart / 2 - window.width / 2)
    }
    is RemoteLayout.Tabletop -> {
        val flat = window.height - layout.gapBottom
        if (sheet == Sheet.MORE && window.width >= TABLETOP_SIDE_BY_SIDE) {
            SheetSpot(window.width / 2, -window.width / 4, flat)
        } else {
            SheetSpot(window.width.coerceAtMost(SHEET_WIDTH), 0.dp, flat)
        }
    }
}

private val SHEET_WIDTH = 560.dp

internal val SHEET_HANDLE = 48.dp

@Composable
fun rememberFold(): Fold? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val features by produceState<List<DisplayFeature>>(emptyList(), context) {
        val activity = context.findActivity() ?: return@produceState
        try {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { value = it.displayFeatures }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("Posture.rememberFold", e)
        }
    }
    return remember(features, density) {
        val fold = features.filterIsInstance<FoldingFeature>().firstOrNull() ?: return@remember null
        val bounds = fold.bounds
        with(density) {
            Fold(
                left = bounds.left.toDp(),
                top = bounds.top.toDp(),
                right = bounds.right.toDp(),
                bottom = bounds.bottom.toDp(),
                vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL,
                separating = fold.isSeparating,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun fadeThroughPosture(target: RemoteLayout, window: DpSize): Pair<RemoteLayout, () -> Float> {
    var shown by remember { mutableStateOf(target) }
    var shownIn by remember { mutableStateOf(window) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(target, window) {
        if (window != shownIn) {
            shown = target
            shownIn = window
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        if (target != shown) {
            fade.animateTo(0f, POSTURE_OUT)
            shown = target
        }
        fade.animateTo(1f, POSTURE_IN)
    }
    return (if (window != shownIn) target else shown) to { fade.value }
}

private val POSTURE_OUT = tween<Float>(120, easing = FastOutLinearInEasing)
private val POSTURE_IN = tween<Float>(280, easing = LinearOutSlowInEasing)

internal const val POSTURE_SCALE = 0.97f

internal val TABLETOP_MORE_GAP = 8.dp
