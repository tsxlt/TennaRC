package lt.tsx.tennarc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class RockerState(private val scope: CoroutineScope) {
    internal val tilt = Animatable(0f)
    internal val dip = Animatable(0f)
    internal val squash = Animatable(0f)

    private var held = 0
    private var tipped = 0
    private var reach = 0f
    private var steps = 0
    private var letGo: Job? = null
    private var dipping: Job? = null

    fun press(direction: Int) {
        letGo?.cancel()
        held = direction
        steps = 0
        tipTo(direction, if (tipped == direction) maxOf(reach, TAP_DEGREES) else TAP_DEGREES)
    }

    fun release(direction: Int) {
        if (held != direction) return
        held = 0
        lingerThenSettle()
    }

    fun fire(direction: Int) {
        when {
            held != direction -> tap(direction)
            ++steps >= 2 -> build(direction)
        }
    }

    fun tap(direction: Int) {
        if (held != 0) return
        letGo?.cancel()
        if (tipped == direction) build(direction) else tipTo(direction, TAP_DEGREES)
        lingerThenSettle()
    }

    fun thump() {
        scope.launch {
            squash.animateTo(THUMP, PRESS_IN)
            squash.animateTo(0f, SPRING_BACK)
        }
    }

    private fun build(direction: Int) {
        tipTo(direction, reach + (MAX_DEGREES - reach) * BUILD)
        dip(direction)
    }

    private fun tipTo(direction: Int, degrees: Float) {
        tipped = direction
        reach = degrees
        scope.launch { tilt.animateTo(direction * degrees, PRESS_IN) }
    }

    private fun lingerThenSettle() {
        letGo?.cancel()
        letGo = scope.launch {
            delay(LINGER_MS)
            tipped = 0
            reach = 0f
            tilt.animateTo(0f, SPRING_BACK)
        }
    }

    private fun dip(direction: Int) {
        dipping?.cancel()
        dipping = scope.launch {
            dip.snapTo(direction * DIP_DEGREES)
            dip.animateTo(0f, PRESS_IN)
        }
    }
}

@Composable
fun rememberRockerState(): RockerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { RockerState(scope) }
}

fun Modifier.rocker(state: RockerState, vertical: Boolean = false): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val sign = if (!vertical && layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val length = if (vertical) placeable.height else placeable.width
    val scale = (SIZED_FOR.toPx() / length).coerceAtMost(1f)
    layout(placeable.width, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin.Center
            val angle = sign * scale * (state.tilt.value + state.dip.value)
            if (vertical) rotationX = angle else rotationY = angle
            cameraDistance = CAMERA_DISTANCE * density
            val squash = state.squash.value
            val across = 1f - squash
            val along = 1f - squash * ALONG
            scaleX = if (vertical) across else along
            scaleY = if (vertical) along else across
        }
    }
}

private val SIZED_FOR = 170.dp

private const val TAP_DEGREES = 5f
private const val MAX_DEGREES = 15f
private const val BUILD = 0.15f

private const val DIP_DEGREES = 2f

private const val CAMERA_DISTANCE = 4f

private const val THUMP = 0.08f
private const val ALONG = 0.6f

private const val LINGER_MS = 250L

private val PRESS_IN = spring<Float>(dampingRatio = 1f, stiffness = 1_500f)
private val SPRING_BACK = spring<Float>(dampingRatio = 0.45f, stiffness = 220f)
