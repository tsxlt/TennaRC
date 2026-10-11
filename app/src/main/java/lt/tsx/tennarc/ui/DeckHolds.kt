package lt.tsx.tennarc.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.deckKey
import lt.tsx.tennarc.data.hideApp
import lt.tsx.tennarc.data.hideChannel
import lt.tsx.tennarc.data.showApp
import lt.tsx.tennarc.data.showChannel
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.ui.components.DeckHold
import lt.tsx.tennarc.ui.components.HoldAction

@Composable
internal fun rememberDeckHold(prefs: Prefs, actions: RemoteActions, snackbar: SnackbarHostState, onRename: (TvApp) -> Unit): DeckHold {
    val latestPrefs by rememberUpdatedState(prefs)
    val latestActions by rememberUpdatedState(actions)
    val latestSnackbar by rememberUpdatedState(snackbar)
    val rename by rememberUpdatedState(onRename)
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val hideLabel = stringResource(R.string.hold_hide)
    val renameLabel = stringResource(R.string.hold_rename)
    return remember(hideLabel, renameLabel) {
        fun hidden(name: String, undo: () -> Unit) {
            scope.launch {
                val result = latestSnackbar.showSnackbar(
                    resources.getString(R.string.channel_hidden, name),
                    actionLabel = resources.getString(R.string.action_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) undo()
            }
        }
        fun hideApp(app: TvApp) {
            val key = latestPrefs.deckKey(app.id)
            latestActions.updatePrefs { it.hideApp(app.id, app.title) }
            hidden(app.title) { latestActions.updatePrefs { it.showApp(key) } }
        }
        DeckHold(
            app = { app -> listOf(HoldAction(hideLabel, Icons.Rounded.VisibilityOff) { hideApp(app) }) },
            input = { input ->
                listOf(
                    HoldAction(renameLabel, Icons.Rounded.Edit) { rename(input) },
                    HoldAction(hideLabel, Icons.Rounded.VisibilityOff) { hideApp(input) },
                )
            },
            channel = { channel ->
                listOf(
                    HoldAction(hideLabel, Icons.Rounded.VisibilityOff) {
                        latestActions.updatePrefs { it.hideChannel(channel.id, channel.name) }
                        hidden(channel.name) { latestActions.updatePrefs { it.showChannel(channel.id) } }
                    },
                )
            },
        )
    }
}
