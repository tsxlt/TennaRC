package lt.tsx.tennarc.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URLDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.isPhoneFile
import lt.tsx.tennarc.link.service
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.AccentButton
import lt.tsx.tennarc.ui.components.QuietButton
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.StatusDot
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.uriOrNull

typealias OpenLink = suspend (LinkTarget, LinkAction) -> Boolean

@Composable
private fun LinkTarget.kindLabel(): String = when (this) {
    is LinkTarget.YouTube -> stringResource(R.string.link_kind_youtube)
    is LinkTarget.Netflix, is LinkTarget.Streaming -> service?.label.orEmpty()
    is LinkTarget.Media -> stringResource(
        when {
            mime.startsWith("audio/") -> R.string.link_kind_audio
            mime.startsWith("image/") -> R.string.link_kind_image
            else -> R.string.link_kind_video
        },
    )
    is LinkTarget.Web -> stringResource(R.string.link_kind_web)
}

internal val LinkTarget.kindIcon: ImageVector
    get() = when (this) {
        is LinkTarget.YouTube -> Icons.Rounded.SmartDisplay
        is LinkTarget.Netflix, is LinkTarget.Streaming -> Icons.Rounded.Movie
        is LinkTarget.Media -> when {
            mime.startsWith("audio/") -> Icons.Rounded.MusicNote
            mime.startsWith("image/") -> Icons.Rounded.Image
            else -> Icons.Rounded.PlayCircle
        }
        is LinkTarget.Web -> Icons.Rounded.Public
    }

@Composable
internal fun LinkAction.label(target: LinkTarget): String = when (this) {
    LinkAction.APP -> stringResource(R.string.link_action_app, target.service?.label.orEmpty())
    LinkAction.PLAY_MEDIA -> stringResource(R.string.link_action_media)
    LinkAction.BROWSER -> stringResource(R.string.link_action_browser)
}

internal fun LinkAction.icon(target: LinkTarget): ImageVector = when (this) {
    LinkAction.APP -> if (target is LinkTarget.YouTube) Icons.Rounded.SmartDisplay else Icons.Rounded.Movie
    LinkAction.PLAY_MEDIA -> Icons.Rounded.PlayCircle
    LinkAction.BROWSER -> Icons.AutoMirrored.Rounded.OpenInNew
}

fun displayUrl(url: String): String {
    val uri = uriOrNull(url) ?: return url
    val host = uri.host?.removePrefix("www.") ?: return url
    val rest = buildString {
        append(uri.rawPath.orEmpty().trimEnd('/'))
        uri.rawQuery?.let { append('?').append(it) }
    }
    return host + catching { URLDecoder.decode(rest, "UTF-8") }.getOrDefault(rest)
}

@Composable
fun LinkPreview(target: LinkTarget, modifier: Modifier = Modifier, picture: ImageBitmap? = null) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Row(modifier.fillMaxWidth().glass(panelGlass(), MaterialTheme.shapes.large).padding(PREVIEW_INSET), verticalAlignment = Alignment.CenterVertically) {
            if (picture != null) {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(PREVIEW_PICTURE))
            } else {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(target.kindIcon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(target.kindLabel(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (target is LinkTarget.Media && target.isPhoneFile) target.title else displayUrl(target.url),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LinkActionButtons(
    target: LinkTarget,
    actions: List<LinkAction>,
    onOpen: OpenLink,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
    submits: Int = 0,
    failure: String? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var busy by remember(target) { mutableStateOf<LinkAction?>(null) }
    var outcome by remember(target) { mutableStateOf<Boolean?>(null) }

    fun submit(action: LinkAction) {
        if (busy != null || outcome == true) return
        scope.launch {
            busy = action
            outcome = null
            val opened = onOpen(target, action)
            busy = null
            outcome = opened
            if (opened) {
                haptics.confirm()
                delay(700)
                onOpened()
            } else {
                haptics.reject()
            }
        }
    }
    LaunchedEffect(submits) { if (submits > 0) actions.firstOrNull()?.let(::submit) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { index, action ->
            val onClick: () -> Unit = { submit(action) }
            val content: @Composable RowScope.() -> Unit = {
                if (busy == action) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                } else {
                    Icon(action.icon(target), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(if (busy == action) stringResource(R.string.link_sending) else action.label(target))
            }
            val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            if (index == 0) {
                AccentButton(onClick = onClick, modifier = buttonModifier, content = content)
            } else {
                QuietButton(onClick = onClick, modifier = buttonModifier, content = content)
            }
        }
        AnimatedVisibility(visible = outcome != null, enter = fadeIn(), exit = fadeOut()) {
            val ok = outcome == true
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                val message = if (ok) stringResource(R.string.link_sent) else failure ?: stringResource(R.string.link_failed)
                Text(message, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}

@Composable
fun ShareSheetContent(
    target: LinkTarget?,
    state: TvState,
    actionsFor: (LinkTarget) -> List<LinkAction>,
    onOpen: OpenLink,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    picture: ImageBitmap? = null,
    failure: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state.connection)
            Spacer(Modifier.width(8.dp))
            val status = when (state.connection) {
                Connection.Connected -> state.appTitle(state.foregroundAppId)
                    ?.let { stringResource(R.string.share_on_screen, it) }
                    ?: stringResource(R.string.status_connected)
                Connection.Connecting -> stringResource(R.string.share_waiting)
                Connection.AwaitingApproval -> stringResource(R.string.status_awaiting)
                is Connection.AwaitingCode -> stringResource(R.string.status_awaiting_code)
                Connection.Offline -> stringResource(R.string.status_offline)
                is Connection.NeedsPairing -> stringResource(R.string.status_not_paired)
            }
            Text(status, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(24.dp))
        when {
            target == null -> {
                Text(stringResource(R.string.share_no_link), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(24.dp))
                QuietButton(stringResource(R.string.action_close), onClose, Modifier.fillMaxWidth().heightIn(min = 52.dp))
            }
            state.connection is Connection.NeedsPairing -> {
                LinkPreview(target, picture = picture)
                Spacer(Modifier.size(16.dp))
                Text(stringResource(R.string.share_not_paired), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(16.dp))
                AccentButton(stringResource(R.string.share_open_app), onOpenApp, Modifier.fillMaxWidth().heightIn(min = 52.dp))
            }
            else -> {
                LinkPreview(target, picture = picture)
                Spacer(Modifier.size(16.dp))
                val actions = actionsFor(target)
                if (actions.isEmpty()) {
                    val cant = if (target.isPhoneFile) stringResource(R.string.share_cant_play_file) else stringResource(R.string.share_cant_open, target.kindLabel())
                    Text(cant, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(24.dp))
                    QuietButton(stringResource(R.string.action_close), onClose, Modifier.fillMaxWidth().heightIn(min = 52.dp))
                } else {
                    LinkActionButtons(target, actions, onOpen, onOpened = onClose, failure = failure)
                }
            }
        }
    }
}

private val PREVIEW_INSET = 12.dp
private val PREVIEW_PICTURE = SquircleShape(CornerLarge - PREVIEW_INSET)
