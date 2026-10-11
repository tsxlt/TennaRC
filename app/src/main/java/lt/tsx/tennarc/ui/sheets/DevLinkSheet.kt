package lt.tsx.tennarc.ui.sheets

import android.icu.text.BreakIterator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.Locale
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.ui.components.AccentButton
import lt.tsx.tennarc.ui.components.ButtonPair
import lt.tsx.tennarc.ui.components.CONTROL_HEIGHT
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROWS_PAD
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.PANEL_ROW_PAD
import lt.tsx.tennarc.ui.components.QuietButton
import lt.tsx.tennarc.ui.components.SquircleCache
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.theme.ICON_CORNER
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.warning

@Composable
fun DevLinkSheetContent(
    link: DevLink,
    onConnect: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val title = stringResource(R.string.dev_link_title)
    val body = stringResource(R.string.dev_link_body)
    val (lead, aside) = remember(body, locale) { firstSentence(body, locale) }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Header(title, link)
            Spacer(Modifier.height(16.dp))
            Text(lead, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            AnimatedVisibility(link != DevLink.Up, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(PANEL_GAP * 3))
                    Steps(active = link.activeStep)
                }
            }
            if (aside != null) {
                Spacer(Modifier.height(PANEL_GAP * 2))
                Text(aside, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = PANEL_ROW_PAD))
            }
            Spacer(Modifier.height(PANEL_GAP * 2))
        }
        AnimatedContent(
            targetState = link,
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)).togetherWith(fadeOut(tween(60))).using(SizeTransform(clip = false)) },
            contentAlignment = Alignment.BottomStart,
            label = "devLinkDock",
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = PANEL_GAP, bottom = 24.dp),
        ) { shown ->
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
                when (shown) {
                    DevLink.Off -> AccentButton(stringResource(R.string.dev_link_connect), onConnect, Modifier.fillMaxWidth())
                    DevLink.Connecting -> Underway(stringResource(R.string.status_connecting))
                    DevLink.AwaitingApproval -> {
                        Callout(Icons.Rounded.TouchApp, colors.primary, stringResource(R.string.dev_link_allow_body))
                        Underway(stringResource(R.string.dev_link_allow))
                    }
                    DevLink.Up -> QuietButton(stringResource(R.string.dev_link_turn_off), onTurnOff, Modifier.fillMaxWidth())
                    is DevLink.Down -> {
                        Callout(Icons.Rounded.ErrorOutline, colors.warning, stringResource(shown.note))
                        ButtonPair(
                            quiet = { QuietButton(stringResource(R.string.dev_link_turn_off), onTurnOff) },
                            main = { AccentButton(stringResource(R.string.action_retry), onConnect) },
                        )
                    }
                    is DevLink.Failed -> {
                        Callout(Icons.Rounded.ErrorOutline, colors.error, stringResource(shown.problem.note))
                        AccentButton(stringResource(R.string.action_retry), onConnect, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, link: DevLink) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(MARK_TILE).glass(panelGlass(), IconShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Adb, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (link == DevLink.Up || link is DevLink.Down || link is DevLink.Failed) {
                val tone = if (link == DevLink.Up) colors.tertiary else colors.error
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(tone, CircleShape))
                    Text(stringResource(link.summary), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Steps(active: Int?) {
    Column(Modifier.fillMaxWidth().panel(panelGlass(), MaterialTheme.shapes.large, inset = 0.dp).padding(vertical = PANEL_ROWS_PAD)) {
        STEPS.forEachIndexed { index, step ->
            Step(index + 1, stringResource(step), first = index == 0, last = index == STEPS.lastIndex, active = active == index + 1)
        }
    }
}

@Composable
private fun Step(number: Int, text: String, first: Boolean, last: Boolean, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val (title, detail) = remember(text, locale) { firstSentence(text, locale) }
    val path = SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)
    val line = colors.onSurface.copy(alpha = STEP_LINE)
    val tile = if (active) colors.primary.copy(alpha = STEP_TILE_ACTIVE) else colors.onSurface.copy(alpha = STEP_TILE)
    val tilePath = remember { SquircleCache() }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = PANEL_INSET + PANEL_ROW_PAD),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(STEP_TILE_SIZE)
                .fillMaxHeight()
                .drawBehind {
                    val side = STEP_TILE_SIZE.toPx()
                    val top = STEP_PAD.toPx()
                    val gap = 4.dp.toPx()
                    val x = size.width / 2
                    val stroke = 1.5.dp.toPx()
                    if (!first) drawLine(line, Offset(x, 0f), Offset(x, top - gap), stroke)
                    if (!last) drawLine(line, Offset(x, top + side + gap), Offset(x, size.height), stroke)
                    drawPath(tilePath.of(0f, top, side, side, side * ICON_CORNER), tile)
                },
        ) {
            Box(Modifier.padding(top = STEP_PAD).size(STEP_TILE_SIZE), contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (active) colors.primary else colors.onSurface,
                )
            }
        }
        Column(Modifier.padding(vertical = STEP_PAD).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(menus(title, path), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (detail != null) Text(menus(detail, path), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Underway(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = CONTROL_HEIGHT).glass(panelGlass(), MaterialTheme.shapes.large).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun Callout(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().glass(panelGlass(), MaterialTheme.shapes.large).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun menus(text: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    for (char in text) {
        if (char == '›') withStyle(style) { append(char) } else append(char)
    }
}

internal fun firstSentence(text: String, locale: Locale): Pair<String, String?> {
    val sentences = BreakIterator.getSentenceInstance(locale)
    sentences.setText(text)
    val end = sentences.next()
    if (end == BreakIterator.DONE || end >= text.length) return text.trim() to null
    val rest = text.substring(end).trim()
    return text.substring(0, end).trim() to rest.ifEmpty { null }
}

private val DevLink.activeStep: Int?
    get() = when (this) {
        DevLink.Connecting, DevLink.AwaitingApproval -> 3
        is DevLink.Failed -> problem.step
        is DevLink.Down -> problem?.step
        else -> null
    }

private val DevProblem.step: Int?
    get() = when (this) {
        DevProblem.DEBUGGING_OFF, DevProblem.WIRELESS_ONLY -> 2
        DevProblem.NOT_ALLOWED -> 3
        DevProblem.UNREACHABLE -> null
    }

private val MARK_TILE = 48.dp

private val STEP_TILE_SIZE = 28.dp
private val STEP_PAD = PANEL_ROW_GAP / 2 + 10.dp

private const val STEP_TILE = 0.08f
private const val STEP_TILE_ACTIVE = 0.16f
private const val STEP_LINE = 0.12f

internal val DevLink.summary: Int
    get() = when (this) {
        DevLink.Off -> R.string.dev_link_off
        DevLink.Connecting -> R.string.status_connecting
        DevLink.AwaitingApproval -> R.string.dev_link_allow
        DevLink.Up -> R.string.status_connected
        is DevLink.Down -> if (problem == DevProblem.NOT_ALLOWED) R.string.dev_link_forgotten_short else problem?.summary ?: R.string.dev_link_away_short
        is DevLink.Failed -> problem.summary
    }

private val DevProblem.summary: Int
    get() = when (this) {
        DevProblem.DEBUGGING_OFF -> R.string.dev_link_debugging_off_short
        DevProblem.WIRELESS_ONLY -> R.string.dev_link_wireless_short
        DevProblem.NOT_ALLOWED -> R.string.dev_link_not_allowed_short
        DevProblem.UNREACHABLE -> R.string.dev_link_away_short
    }

private val DevLink.Down.note: Int
    get() = when (problem) {
        null, DevProblem.UNREACHABLE -> R.string.dev_link_away
        DevProblem.NOT_ALLOWED -> R.string.dev_link_forgotten
        else -> problem.note
    }

private val DevProblem.note: Int
    get() = when (this) {
        DevProblem.DEBUGGING_OFF -> R.string.dev_link_debugging_off
        DevProblem.WIRELESS_ONLY -> R.string.dev_link_wireless
        DevProblem.NOT_ALLOWED -> R.string.dev_link_not_allowed
        DevProblem.UNREACHABLE -> R.string.dev_link_away
    }

private val STEPS = listOf(R.string.dev_link_step_developer, R.string.dev_link_step_debugging, R.string.dev_link_step_allow)
