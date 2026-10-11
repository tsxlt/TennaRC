package lt.tsx.tennarc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.components.GlassDialog

@Composable
internal fun FindTitlesAsk(onAnswer: (Boolean) -> Unit) {
    GlassDialog(
        title = stringResource(R.string.settings_find_titles),
        confirm = stringResource(R.string.action_turn_on),
        onConfirm = { onAnswer(true) },
        dismiss = stringResource(R.string.find_titles_not_now),
        onDismissButton = { onAnswer(false) },
        onDismissRequest = { onAnswer(false) },
    ) {
        Text(stringResource(R.string.find_titles_ask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
