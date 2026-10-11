package lt.tsx.tennarc.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged

internal enum class Trailing { MIC, PASTE, PASTE_AND_MIC, CLEAR }

internal fun trailingFor(query: String, listening: Boolean, mic: Boolean, paste: Boolean): Trailing = when {
    listening -> Trailing.MIC
    query.isNotEmpty() -> Trailing.CLEAR
    paste && mic -> Trailing.PASTE_AND_MIC
    paste || !mic -> Trailing.PASTE
    else -> Trailing.MIC
}

internal data class ClipPeek(val text: Boolean, val stamp: Long)

internal fun ClipPeek?.offersPaste(spent: Long?): Boolean = this != null && text && stamp != spent

@Composable
internal fun rememberClipPeek(): ClipPeek? {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val manager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    var peek by remember { mutableStateOf<ClipPeek?>(null) }
    LaunchedEffect(manager, focused) { if (focused) peek = manager?.peek() }
    DisposableEffect(manager) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { peek = manager?.peek() }
        manager?.addPrimaryClipChangedListener(listener)
        onDispose { manager?.removePrimaryClipChangedListener(listener) }
    }
    return peek
}

private fun ClipboardManager.peek(): ClipPeek? = catching {
    val description = primaryClipDescription ?: return@catching null
    val text = description.hasMimeType("text/*") || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
    ClipPeek(text = text, stamp = description.timestamp)
}.logged("Clipboard.peek").getOrNull()

@Composable
internal fun rememberSpentClip(): MutableState<Long?> {
    val context = LocalContext.current
    val store = remember(context) { context.getSharedPreferences("clipboard", Context.MODE_PRIVATE) }
    val spent = remember(store) { mutableStateOf(store.getLong(SPENT_KEY, 0L).takeIf { it != 0L }) }
    LaunchedEffect(spent.value) { spent.value?.let { stamp -> store.edit { putLong(SPENT_KEY, stamp) } } }
    return spent
}

private const val SPENT_KEY = "pasted_stamp"
