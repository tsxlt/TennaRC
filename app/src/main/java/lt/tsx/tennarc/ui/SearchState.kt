package lt.tsx.tennarc.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import lt.tsx.tennarc.ui.components.backReach

@Stable
internal class SearchState(
    searching: MutableState<Boolean>,
    query: MutableState<String>,
    private val reveal: State<Float>,
    private val keyboardNow: () -> Int,
) {
    var searching by searching
        private set
    var query by query
    var goes by mutableIntStateOf(0)
        private set
    private var backProgress by mutableFloatStateOf(0f)
    private var keyboardFull by mutableIntStateOf(0)
    private var keyboardLeaving by mutableStateOf(false)

    fun open() {
        if (searching) return
        keyboardLeaving = false
        backProgress = 0f
        searching = true
    }

    fun close() {
        searching = false
        query = ""
        goes = 0
    }

    fun go() {
        goes++
    }

    fun leaving(): Float = max(backProgress, 1f - keyboardShare())

    fun barShown(): Float = min(reveal.value, 1f - leaving())

    private fun keyboardShare(): Float =
        if (keyboardLeaving && keyboardFull > 0) (keyboardNow().toFloat() / keyboardFull).coerceIn(0f, 1f) else 1f

    suspend fun followKeyboard(readings: Flow<KeyboardReading>) {
        var wasUp = false
        readings.collect { (target, now, resumed) ->
            when {
                !resumed -> Unit
                target > 0 -> {
                    keyboardFull = target
                    if (now >= target) keyboardLeaving = false
                }
                now > 0 && keyboardFull > 0 && query.isBlank() -> keyboardLeaving = true
                now == 0 && (keyboardLeaving || wasUp && query.isBlank()) -> close()
            }
            wasUp = now > 0
        }
    }

    suspend fun followBack(swipe: Flow<BackEventCompat>) {
        try {
            swipe.collect { backProgress = backReach(it.progress) }
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
        close()
    }
}

internal data class KeyboardReading(val target: Int, val now: Int, val resumed: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberSearchState(): SearchState {
    val searching = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    val reveal = animateFloatAsState(if (searching.value) 1f else 0f, tween(SEARCH_REVEAL_MS), label = "barReveal")
    val density by rememberUpdatedState(LocalDensity.current)
    val ime by rememberUpdatedState(WindowInsets.ime)
    val imeTarget = WindowInsets.imeAnimationTarget
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val search = remember { SearchState(searching, query, reveal) { ime.getBottom(density) } }
    LaunchedEffect(search.searching) {
        if (!search.searching) return@LaunchedEffect
        search.followKeyboard(
            snapshotFlow { KeyboardReading(imeTarget.getBottom(density), ime.getBottom(density), lifecycle.isAtLeast(Lifecycle.State.RESUMED)) },
        )
    }
    return search
}

private const val SEARCH_REVEAL_MS = 220
