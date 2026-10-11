package lt.tsx.tennarc.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.deckKey
import lt.tsx.tennarc.data.hiddenHere
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.data.tvKey
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.AccentButton
import lt.tsx.tennarc.ui.components.AppDeck
import lt.tsx.tennarc.ui.components.AppGrid
import lt.tsx.tennarc.ui.components.ButtonText
import lt.tsx.tennarc.ui.components.CastCard
import lt.tsx.tennarc.ui.components.DECK_TILE_TOP
import lt.tsx.tennarc.ui.components.DPad
import lt.tsx.tennarc.ui.components.DeckGrow
import lt.tsx.tennarc.ui.components.DeckHold
import lt.tsx.tennarc.ui.components.DeckUse
import lt.tsx.tennarc.ui.components.DeckValue
import lt.tsx.tennarc.ui.components.GlassDialog
import lt.tsx.tennarc.ui.components.GlassField
import lt.tsx.tennarc.ui.components.PAD_PEEK
import lt.tsx.tennarc.ui.components.PadMorph
import lt.tsx.tennarc.ui.components.PairingCodePanel
import lt.tsx.tennarc.ui.components.QuietButton
import lt.tsx.tennarc.ui.components.RemoteDeck
import lt.tsx.tennarc.ui.components.SheetDragHandle
import lt.tsx.tennarc.ui.components.StatePanel
import lt.tsx.tennarc.ui.components.Touchpad
import lt.tsx.tennarc.ui.components.mirrorApp
import lt.tsx.tennarc.ui.components.rememberOnLocalNetwork
import lt.tsx.tennarc.ui.components.rememberPhoneSources
import lt.tsx.tennarc.ui.sheets.TvSettingControls
import lt.tsx.tennarc.ui.sheets.hasTvSettings

@Composable
internal fun Apps(
    state: TvState,
    actions: RemoteActions,
    deck: AnchoredDraggableState<DeckValue>,
    use: DeckUse,
    prefs: Prefs,
    hold: DeckHold,
    modifier: Modifier,
    peek: Dp = PAD_PEEK,
    below: (@Composable () -> Unit)? = null,
) {
    val phone = rememberPhoneSources(state, actions)
    val shown = rememberDeckContents(state, prefs)
    AppDeck(
        all = shown.apps,
        inputs = shown.inputs,
        channels = shown.channels,
        activeId = state.foregroundAppId.takeIf { state.isUsable },
        channelId = state.channelId,
        use = use,
        enabled = state.isUsable,
        deck = deck,
        onLaunch = actions::launch,
        onChannel = actions::openChannel,
        modifier = modifier.arrival(prefs.inUseKey),
        hold = hold,
        phone = phone,
        below = below,
        peek = peek,
    )
}

@Composable
internal fun ColumnScope.SearchOr(
    searching: Boolean,
    results: @Composable (Modifier) -> Unit,
    back: () -> Float = { 0f },
    gap: Dp = 0.dp,
    remote: @Composable ColumnScope.() -> Unit,
) {
    val reveal = animateFloatAsState(if (searching) 1f else 0f, tween(220), label = "searchReveal")
    val revealed by remember { derivedStateOf { reveal.value > 0f } }
    val currentRemote by rememberUpdatedState(remote)
    val kept = remember { movableContentWithReceiverOf<ColumnScope> { currentRemote() } }
    if (!searching && !revealed) {
        kept()
        Spacer(Modifier.size(gap))
        return
    }
    Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .matchParentSize()
                .padding(bottom = gap)
                .graphicsLayer { alpha = 1f - SEARCH_DIM * (reveal.value * (1f - back())).coerceIn(0f, 1f) }
                .then(
                    if (searching) {
                        Modifier
                            .clearAndSetSemantics {}
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            content = kept,
        )
        results(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 16.dp.toPx()
            },
        )
    }
}

@Composable
internal fun PadArea(
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    mode: PadMode,
    modifier: Modifier = Modifier,
    dpadMax: Dp = DPAD_PHONE,
) {
    val reconnecting = state.connection == Connection.Connecting && state.apps.isNotEmpty() && !state.appsFromMemory
    val inside = Modifier.fillMaxSize().padding(horizontal = PAD_SIDE)
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val connection = state.connection) {
            Connection.Connected if state.power == TvPower.STANDBY -> StatePanel(
                icon = Icons.Rounded.PowerSettingsNew,
                title = stringResource(R.string.standby_title),
                body = null,
                modifier = inside,
            ) {
                TurnOnButton(actions::wake)
            }
            Connection.Connected -> Pad(state, prefs, actions, mode, dpadMax)
            Connection.Connecting if reconnecting -> Pad(state, prefs, actions, mode, dpadMax)
            is Connection.NeedsPairing -> StatePanel(
                icon = Icons.Rounded.Tv,
                title = stringResource(R.string.pair_title),
                body = stringResource(
                    when {
                        connection.declined -> R.string.pair_declined_body
                        connection.timedOut -> R.string.pair_timeout_body
                        connection.forgotten -> R.string.pair_forgotten_body
                        else -> R.string.pair_body
                    },
                ),
                modifier = inside,
            ) {
                val again = connection.declined || connection.timedOut
                AccentButton(stringResource(if (again) R.string.action_retry else R.string.pair_action), actions::pair)
            }
            Connection.AwaitingApproval -> StatePanel(
                icon = Icons.Rounded.Tv,
                title = stringResource(R.string.approval_title),
                body = null,
                pulse = true,
                modifier = inside,
            ) {
                QuietButton(stringResource(R.string.action_cancel), actions::cancelPairing)
            }
            is Connection.AwaitingCode -> PairingCodePanel(
                wrong = connection.wrong,
                onSubmit = actions::submitPairingCode,
                modifier = inside,
            )
            Connection.Connecting -> Delayed(CONNECTING_PANEL_DELAY_MS) {
                StatePanel(
                    icon = null,
                    busy = true,
                    title = stringResource(R.string.connecting_title),
                    body = null,
                    modifier = inside,
                    actions = if (Feature.WAKE in state.features && prefs.mac.isNotBlank()) {
                        { TurnOnButton(actions::wake) }
                    } else {
                        null
                    },
                )
            }
            Connection.Offline -> {
                val elsewhere by produceState<SavedTv?>(null, prefs.otherTvs) {
                    value = prefs.otherTvs.firstOrNull { actions.isReachable(it.host, it.kind) }
                }
                val context = LocalContext.current
                val onNetwork = rememberOnLocalNetwork()
                var wasOnNetwork by remember { mutableStateOf(onNetwork) }
                LaunchedEffect(onNetwork) {
                    if (onNetwork && !wasOnNetwork) actions.retry()
                    wasOnNetwork = onNetwork
                }
                if (!onNetwork) {
                    StatePanel(
                        icon = Icons.Rounded.WifiOff,
                        title = stringResource(R.string.offline_no_wifi_title),
                        body = null,
                        modifier = inside,
                    ) {
                        AccentButton(stringResource(R.string.action_wifi_settings), { context.openWifiSettings() })
                    }
                } else {
                    val canWake = Feature.WAKE in state.features && prefs.mac.isNotBlank()
                    StatePanel(
                        icon = Icons.Rounded.PowerSettingsNew,
                        title = stringResource(R.string.offline_title),
                        body = null,
                        modifier = inside,
                    ) {
                        val other = elsewhere
                        if (canWake) {
                            TurnOnButton(actions::wake)
                        }
                        if (other != null) {
                            QuietButton(stringResource(R.string.offline_switch, other.name), { actions.updatePrefs { it.switchTo(other) } })
                        }
                    }
                }
            }
        }
    }
}

private fun Context.openWifiSettings() {
    val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
    catching { startActivity(Intent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.logged("RemoteBody.openWifiSettings")
}

@Composable
private fun Pad(state: TvState, prefs: Prefs, actions: RemoteActions, mode: PadMode, dpadMax: Dp) {
    key(prefs.tvKey) { MorphPad(state, prefs, actions, mode, dpadMax) }
}

@Composable
private fun MorphPad(state: TvState, prefs: Prefs, actions: RemoteActions, mode: PadMode, dpadMax: Dp) {
    val shown = if (Feature.POINTER in state.features) mode else PadMode.DPAD
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val disc = min(min(maxWidth - PAD_SIDE * 2, maxHeight), dpadMax)
        PadMorph(
            toDisc = shown == PadMode.DPAD,
            disc = disc,
            inset = 16.dp,
            touchpad = { padModifier ->
                Touchpad(
                    onMove = actions::pointerMove,
                    onScroll = actions::pointerScroll,
                    onClick = actions::pointerClick,
                    speed = prefs.touchpadSpeed,
                    naturalScroll = prefs.naturalScroll,
                    enabled = state.isUsable,
                    modifier = padModifier,
                    strip = prefs.scrollStrip,
                    inset = 16.dp,
                    drawGlass = false,
                )
            },
            discPad = { padModifier ->
                DPad(onPress = actions::press, enabled = state.isUsable, modifier = padModifier, drawGlass = false)
            },
        )
    }
}

@Composable
internal fun Decks(
    state: TvState,
    actions: RemoteActions,
    mode: PadMode,
    onMode: (PadMode) -> Unit,
    onSheet: (Sheet) -> Unit,
    onMore: () -> Unit,
    swipeUp: SheetDragHandle,
    nudges: Flow<Int>,
    autoKeyboard: Boolean,
    cast: Boolean = true,
    room: DpSize? = null,
    grow: DeckGrow? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        if (cast) Casting(state, actions, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        Spacer(Modifier.size(12.dp))
        RemoteDeck(
            state = state,
            commands = actions,
            mode = mode,
            onMode = onMode,
            onKeyboard = { onSheet(Sheet.KEYBOARD) },
            onMore = onMore,
            swipeUp = swipeUp,
            nudges = nudges,
            autoKeyboard = autoKeyboard,
            room = room,
            grow = grow,
        )
    }
}

@Composable
internal fun Casting(state: TvState, actions: RemoteActions, modifier: Modifier) {
    CastCard(
        cast = state.cast,
        onPlayPause = actions::castPlayPause,
        onStop = actions::castStop,
        onSeek = actions::castSeek,
        modifier = modifier,
    )
}

private class DeckContents(val apps: List<TvApp>, val inputs: List<TvApp>, val channels: List<TvChannel>)

@Composable
private fun rememberDeckContents(state: TvState, prefs: Prefs): DeckContents {
    val receiver = mirrorApp(state.apps)
    val hiddenHere = prefs.hiddenHere
    return remember(state.apps, state.inputs, state.channels, receiver, hiddenHere, prefs.hiddenChannels, prefs.inputNames, prefs.host, prefs.demoTv) {
        DeckContents(
            apps = state.apps.filterNot { it == receiver || prefs.deckKey(it.id) in hiddenHere },
            inputs = state.inputs
                .filterNot { prefs.deckKey(it.id) in hiddenHere }
                .map { input -> prefs.inputNames[prefs.deckKey(input.id)]?.let { input.copy(title = it) } ?: input },
            channels = state.channels.filterNot { it.id in prefs.hiddenChannels },
        )
    }
}

@Composable
internal fun RenameInput(current: String?, original: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(current ?: original, TextRange(0, (current ?: original).length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { catching { focus.requestFocus() } }
    GlassDialog(
        title = stringResource(R.string.rename_input),
        confirm = stringResource(R.string.rename_save),
        onConfirm = { onRename(name.text.takeUnless { it.trim() == original }.orEmpty()) },
        dismiss = stringResource(if (current != null) R.string.rename_reset else R.string.action_cancel),
        onDismissButton = { if (current != null) onRename("") else onDismiss() },
        onDismissRequest = onDismiss,
    ) {
        GlassField(
            value = name,
            onValueChange = { name = it },
            placeholder = original,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onRename(name.text.takeUnless { it.trim() == original }.orEmpty()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
internal fun ColumnScope.Browse(
    state: TvState,
    actions: RemoteActions,
    use: DeckUse,
    prefs: Prefs,
    hold: DeckHold,
    gridPadding: PaddingValues,
    cast: Boolean = true,
) {
    if (cast) Casting(state, actions, Modifier.padding(start = 16.dp, end = 16.dp, bottom = BROWSE_GAP))
    tvSettings(state, actions, state.isUsable)?.let { settings ->
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = BROWSE_GAP - DECK_TILE_TOP)) { settings() }
    }
    val phone = rememberPhoneSources(state, actions)
    val shown = rememberDeckContents(state, prefs)
    AppGrid(
        all = shown.apps,
        inputs = shown.inputs,
        channels = shown.channels,
        activeId = state.foregroundAppId.takeIf { state.isUsable },
        channelId = state.channelId,
        use = use,
        enabled = state.isUsable,
        onLaunch = actions::launch,
        onChannel = actions::openChannel,
        hold = hold,
        modifier = Modifier.weight(1f).fillMaxWidth().arrival(prefs.inUseKey),
        contentPadding = gridPadding,
        phone = phone,
    )
}

internal fun tvSettings(state: TvState, actions: RemoteActions, connected: Boolean): (@Composable () -> Unit)? {
    val picture = state.picture.takeIf { Feature.PICTURE in state.features }
    val output = state.soundOutput.takeIf { Feature.SOUND_OUTPUT in state.features }
    val ambilight = state.ambilight.takeIf { Feature.AMBILIGHT in state.features }
    if (!hasTvSettings(picture, output, ambilight)) return null
    return {
        TvSettingControls(
            picture = picture,
            output = output,
            ambilight = ambilight,
            enabled = connected,
            onMode = actions::setPictureMode,
            onBacklight = actions::setBacklight,
            onOutput = actions::setSoundOutput,
            onAmbilight = actions::setAmbilight,
            onPairAmbilight = actions::pairAmbilight,
            onAmbilightPin = actions::submitAmbilightPin,
        )
    }
}

private const val CONNECTING_PANEL_DELAY_MS = 300L

@Composable
private fun Delayed(delayMillis: Long, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        shown = true
    }
    if (shown) content()
}

internal data class LinkNote(val verdict: LinkVerdict?, val onHelp: () -> Unit)

@Composable
private fun TurnOnButton(onClick: () -> Unit) {
    AccentButton(onClick) {
        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
        ButtonText(stringResource(R.string.action_turn_on))
    }
}

internal const val DECK_ROLL_UP_MS = 250L

internal const val SEARCH_LEAVE_SCALE = 0.04f

private const val SEARCH_DIM = 0.35f

