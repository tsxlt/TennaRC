package lt.tsx.tennarc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.theme.ICON_CORNER
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvCommands
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.PadMode
import lt.tsx.tennarc.ui.theme.glassPressed

private val PLAY_GLYPH = 22.dp

internal const val DIM_MS = 200

internal const val MUTE_MS = 200

private const val SWAP_MS = 150

@Composable
fun RemoteDeck(
    state: TvState,
    commands: TvCommands,
    mode: PadMode,
    onMode: (PadMode) -> Unit,
    onKeyboard: () -> Unit,
    onMore: () -> Unit,
    swipeUp: SheetDragHandle,
    modifier: Modifier = Modifier,
    nudges: Flow<Int> = emptyFlow(),
    autoKeyboard: Boolean = false,
    room: DpSize? = null,
    grow: DeckGrow? = null,
) {
    val enabled = state.isUsable
    val other = (if (mode == PadMode.DPAD) PadMode.TOUCHPAD else PadMode.DPAD).takeIf { Feature.POINTER in state.features }
    val typing = Feature.TEXT_INPUT in state.features
    val keyboardShown = typing && (!autoKeyboard || Feature.KEYBOARD_STATE !in state.features || state.keyboard != null)
    val keyboardShare by animateFloatAsState(if (keyboardShown) 1f else 0f, label = "keyboardKey")
    DeckPanel(swipeUp = swipeUp, modifier = modifier, enabled = enabled, room = room, grow = grow) {
        NavKeys(
            enabled = enabled,
            volume = VolumeReadout.of(state.volume, state.muted, reportsLevel = Feature.VOLUME_LEVEL in state.features),
            onPress = commands::press,
            onVolumeDown = commands::volumeDown,
            onVolumeUp = commands::volumeUp,
            onMute = commands::setMuted,
            nudges = nudges,
            columns = PLAYBACK_KEYS + (if (other != null) 1 else 0) + keyboardShare,
        )
        PlaybackRow(
            enabled = enabled,
            playing = state.playing,
            onPress = commands::press,
            onPlayPause = commands::playPause,
            onKeyboard = onKeyboard.takeIf { typing },
            onMore = onMore,
            mode = other,
            onMode = { other?.let(onMode) },
            keyboardShown = keyboardShown,
            keyboardShare = keyboardShare,
        )
    }
}

private const val PLAYBACK_KEYS = 4

private const val CAPSULE_SHARE = 0.4f

@Composable
fun NavKeys(
    enabled: Boolean,
    volume: VolumeReadout,
    onPress: (TvButton) -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onMute: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    nudges: Flow<Int> = emptyFlow(),
    columns: Float = PLAYBACK_KEYS.toFloat(),
) {
    val colors = MaterialTheme.colorScheme
    val side = (1f - CAPSULE_SHARE) / 2
    val at = (0.5f / columns / side).coerceIn(0f, 0.5f)
    Row(modifier.fillMaxWidth().height(LocalDeckMetrics.current.row)) {
        val cell = Modifier.weight(side).fillMaxHeight()
        DeckKey(Lucide.Undo2, stringResource(R.string.key_back), { onPress(TvButton.BACK) }, enabled, cell, tint = colors.onSurface, wellAt = at)
        VolumeCapsule(volume, enabled, onVolumeDown, onVolumeUp, onMute, nudges, Modifier.weight(CAPSULE_SHARE).fillMaxHeight())
        DeckKey(Lucide.House, stringResource(R.string.key_home), { onPress(TvButton.HOME) }, enabled, cell, tint = colors.onSurface, wellAt = 1f - at)
    }
}

@Composable
fun PlaybackRow(
    enabled: Boolean,
    playing: Boolean,
    onPress: (TvButton) -> Unit,
    onPlayPause: () -> Unit,
    onKeyboard: (() -> Unit)?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    mode: PadMode? = null,
    onMode: () -> Unit = {},
    keyboardShown: Boolean = true,
    keyboardShare: Float = if (keyboardShown) 1f else 0f,
) {
    Row(modifier.fillMaxWidth().height(LocalDeckMetrics.current.row)) {
        if (onKeyboard != null && keyboardShare > 0f) {
            DeckKey(
                Lucide.Keyboard,
                stringResource(R.string.key_keyboard),
                onKeyboard,
                enabled && keyboardShown,
                Modifier.weight(keyboardShare).fillMaxHeight().graphicsLayer { alpha = keyboardShare },
            )
        }
        if (mode != null) DeckKey(mode.glyph, stringResource(mode.label), onMode, enabled, Modifier.weight(1f).fillMaxHeight())
        val transport = Modifier.weight(3f).fillMaxHeight()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(transport) {
                val cell = Modifier.weight(1f).fillMaxHeight()
                DeckKey(Lucide.Rewind, stringResource(R.string.key_rewind), { onPress(TvButton.REWIND) }, enabled, cell)
                PlayPauseKey(playing, onPlayPause, enabled, cell)
                DeckKey(Lucide.FastForward, stringResource(R.string.key_fast_forward), { onPress(TvButton.FAST_FORWARD) }, enabled, cell)
            }
        }
        DeckKey(Lucide.Ellipsis, stringResource(R.string.key_more), onMore, enabled, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
internal fun DeckKey(
    icon: ImageVector,
    label: String,
    onPress: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    repeat: Boolean = false,
    well: Dp = LocalDeckMetrics.current.well,
    pressedColor: Color = MaterialTheme.colorScheme.glassPressed,
    glyph: Modifier = Modifier,
    onPressedChange: ((Boolean) -> Unit)? = null,
    wellCorner: Float? = ICON_CORNER,
    wellAt: Float = 0.5f,
) {
    val reportPressed by rememberUpdatedState(onPressedChange)
    KeyButton(
        onPress = onPress,
        contentDescription = label,
        modifier = modifier,
        enabled = enabled,
        repeat = repeat,
        shape = RectangleShape,
        color = Color.Transparent,
        pressedColor = pressedColor,
        dimWhenDisabled = false,
        well = well,
        wellCorner = wellCorner,
        wellAt = wellAt,
    ) { pressed ->
        LaunchedEffect(pressed) { reportPressed?.invoke(pressed) }
        Icon(icon, contentDescription = null, tint = if (pressed) MaterialTheme.colorScheme.primary else tint, modifier = glyph)
    }
}

@Composable
private fun PlayPauseKey(playing: Boolean, onPress: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    KeyButton(
        onPress = onPress,
        contentDescription = stringResource(if (playing) R.string.key_pause else R.string.key_play),
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        color = Color.Transparent,
        pressedColor = colors.glassPressed,
        dimWhenDisabled = false,
        well = LocalDeckMetrics.current.well,
        wellCorner = ICON_CORNER,
    ) { pressed ->
        AnimatedContent(
            targetState = playing,
            modifier = Modifier.size(PLAY_GLYPH),
            transitionSpec = {
                (fadeIn(tween(SWAP_MS)) + scaleIn(tween(SWAP_MS), initialScale = 0.85f))
                    .togetherWith(fadeOut(tween(SWAP_MS)) + scaleOut(tween(SWAP_MS), targetScale = 0.85f))
                    .using(null)
            },
            contentAlignment = Alignment.Center,
            label = "playPause",
        ) { shownPlaying ->
            Icon(
                imageVector = if (shownPlaying) Lucide.Pause else Lucide.Play,
                contentDescription = null,
                tint = if (pressed) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(PLAY_GLYPH),
            )
        }
    }
}

private val dpadGlyph by lazy {
    lucide("pad-dpad", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0")
}

private val touchpadGlyph by lazy {
    lucide(
        "pad-touchpad",
        "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z",
        "M9 9h0.01",
        "M15 9h0.01",
        "M9 15h0.01",
        "M15 15h0.01",
    )
}

internal val PadMode.glyph: ImageVector
    get() = when (this) {
        PadMode.DPAD -> dpadGlyph
        PadMode.TOUCHPAD -> touchpadGlyph
    }

internal val PadMode.label: Int
    get() = when (this) {
        PadMode.DPAD -> R.string.key_dpad
        PadMode.TOUCHPAD -> R.string.key_touchpad
    }

internal fun Modifier.scaled(scale: () -> Float): Modifier = graphicsLayer {
    val s = scale()
    scaleX = s
    scaleY = s
}
