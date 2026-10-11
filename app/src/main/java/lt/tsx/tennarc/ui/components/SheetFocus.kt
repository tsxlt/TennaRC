package lt.tsx.tennarc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.first
import lt.tsx.tennarc.catching

val LocalSheetSettled = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

@Composable
fun FocusWhenSettled(requester: FocusRequester) {
    val settled = LocalSheetSettled.current
    LaunchedEffect(requester) {
        snapshotFlow { settled.value }.first { it }
        catching { requester.requestFocus() }
    }
}
