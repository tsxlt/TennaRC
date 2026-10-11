package lt.tsx.tennarc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.seeThrough
import lt.tsx.tennarc.ui.theme.warning

@Composable
fun CastCard(
    cast: CastState?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = cast != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        var shown by remember { mutableStateOf(cast) }
        if (cast != null) shown = cast
        shown?.let { CastCardContent(it, onPlayPause, onStop, onSeek) }
    }
}

@Composable
private fun CastCardContent(cast: CastState, onPlayPause: () -> Unit, onStop: () -> Unit, onSeek: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val glass = clearGlass(colors.surfaceContainer)
    Surface(color = Color.Transparent, contentColor = colors.onSurface, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.glass(glass, shape).padding(8.dp)) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CastConnected, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cast.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = when {
                            cast.stalled -> stringResource(R.string.cast_stalled)
                            cast.phase == CastPhase.LOADING -> stringResource(R.string.cast_loading)
                            cast.phase == CastPhase.PAUSED -> stringResource(R.string.cast_paused)
                            else -> stringResource(R.string.cast_playing)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cast.stalled) colors.warning else colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause, enabled = cast.phase != CastPhase.LOADING, shape = IconShape) {
                    Icon(
                        imageVector = if (cast.phase == CastPhase.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (cast.phase == CastPhase.PLAYING) R.string.cast_pause else R.string.cast_play),
                    )
                }
                IconButton(onClick = onStop, shape = IconShape) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cast_stop))
                }
            }
            if (cast.durationSeconds > 0) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val position = dragging ?: cast.positionSeconds.toFloat().coerceAtMost(cast.durationSeconds.toFloat())
                Spacer(Modifier.size(8.dp))
                LevelSlider(
                    value = position,
                    onValueChange = { dragging = it },
                    icon = Icons.Rounded.Schedule,
                    valueRange = 0f..cast.durationSeconds.toFloat(),
                    valueLabel = "${clock(position.toInt())} / ${clock(cast.durationSeconds)}",
                    contentDescription = stringResource(R.string.cast_position),
                    trackColor = seeThrough(colors.surfaceContainerHigh, colors.surfaceContainer),
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toInt()) }
                        dragging = null
                    },
                )
            } else if (cast.phase == CastPhase.LOADING) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = colors.onSurfaceVariant,
                    trackColor = seeThrough(colors.surfaceContainerHighest, colors.surfaceContainer),
                )
            }
        }
    }
}

private fun clock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
