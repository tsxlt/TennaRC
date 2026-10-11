package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.min
import kotlin.math.roundToInt
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.ui.components.GlassSwitch
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROWS_PAD
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.PANEL_ROW_PAD
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.components.panelGround
import lt.tsx.tennarc.ui.components.rememberRowWash
import lt.tsx.tennarc.ui.components.LevelSlider
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.onboarding.label
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.LocalGround
import lt.tsx.tennarc.ui.theme.SectionLabel
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.ui.theme.sectionCase
import lt.tsx.tennarc.ui.theme.sheenBrush

@Composable
internal fun HiddenRow(name: String, onShow: () -> Unit) {
    val show = stringResource(R.string.settings_show)
    SettingRow(
        icon = Icons.Rounded.VisibilityOff,
        title = name,
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = show, onClick = onShow),
    ) {
        Text(show, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
internal fun SettingRow(
    icon: ImageVector?,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SETTING_ROW_MIN)
            .padding(horizontal = PANEL_INSET + PANEL_ROW_PAD, vertical = SETTING_ROW_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(SETTING_ICON))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
internal fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, summary: String? = null, onChange: (Boolean) -> Unit) {
    val press = remember { MutableInteractionSource() }
    val haptics = LocalHaptics.current
    SettingRow(
        icon = icon,
        title = title,
        summary = summary,
        modifier = Modifier.toggleable(value = checked, interactionSource = press, indication = LocalIndication.current, role = Role.Switch) {
            haptics.tick()
            onChange(it)
        },
    ) {
        GlassSwitch(checked, onCheckedChange = onChange, interactionSource = press)
    }
}

@Composable
internal fun SpeedSlider(speed: Float, onSpeed: (Float) -> Unit, onDone: () -> Unit) {
    val top = CornerSize(CornerLarge - CARD_RIM)
    val words = PANEL_INSET + PANEL_ROW_PAD - CARD_RIM
    LevelSlider(
        value = speedPosition(speed),
        onValueChange = { onSpeed(positionSpeed(it)) },
        detent = 0.5f,
        icon = Icons.Rounded.Speed,
        label = stringResource(R.string.settings_sensitivity),
        valueLabel = remember(speed) { NumberFormat.getPercentInstance().format((speed / SPEED_DEFAULT * 100f).roundToInt() / 100.0) },
        onValueChangeFinished = onDone,
        trackColor = Color.Transparent,
        shape = SquircleShape(top, top, CornerSize(0.dp), CornerSize(0.dp)),
        height = SETTING_ROW_MIN - CARD_RIM,
        contentPadding = PaddingValues(start = words, end = words, top = SETTING_ROW_PAD - CARD_RIM, bottom = SETTING_ROW_PAD),
        iconSize = SETTING_ICON,
        modifier = Modifier.rowKind(RowKind.SLIDE).padding(start = CARD_RIM, end = CARD_RIM, top = CARD_RIM),
    )
}

@Composable
internal fun Heading(title: Int) {
    Text(
        sectionCase(stringResource(title)),
        style = SectionLabel,
        modifier = Modifier.padding(start = PANEL_INSET + PANEL_ROW_PAD, end = PANEL_INSET + PANEL_ROW_PAD, top = PANEL_GAP * 3, bottom = PANEL_GAP),
    )
}

@Composable
internal fun Card(top: Dp = PANEL_ROWS_PAD, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val glass = panelGlass()
    val seams = remember { CardSeams() }
    Layout(
        content = {
            CompositionLocalProvider(LocalGround provides panelGround(), LocalIndication provides rememberRowWash()) { content() }
        },
        modifier = Modifier
            .fillMaxWidth()
            .panel(glass, shape, inset = 0.dp)
            .padding(top = top, bottom = PANEL_ROWS_PAD)
            .drawWithCache {
                val at = seams.at
                val rim = CARD_RIM.toPx()
                val thick = SEAM.roundToPx().coerceAtLeast(1).toFloat()
                val lights = at.map { top ->
                    val y = top.toFloat()
                    y to glass.sheenBrush(y, y + min(size.height - y, SEAM_SHEEN.toPx()))
                }
                onDrawWithContent {
                    drawContent()
                    val across = Size(size.width - 2f * rim, thick)
                    for ((y, sheen) in lights) {
                        if (glass.sheen.alpha > 0f) drawRect(sheen, topLeft = Offset(rim, y), size = Size(across.width, size.height - y))
                    }
                }
            },
    ) { measurables, constraints ->
        val seam = SEAM.roundToPx().coerceAtLeast(1)
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val at = ArrayList<Int>(0)
        var y = 0
        var kind: RowKind? = null
        val rows = measurables.map { measurable ->
            val row = measurable.measure(loose)
            if (row.height > 0) {
                val its = measurable.layoutId as? RowKind ?: RowKind.TAP
                if (kind != null && its != kind) {
                    at += y
                    y += seam
                }
                kind = its
            }
            val placedAt = y
            y += row.height
            row to placedAt
        }
        seams.at = at
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rows.maxOfOrNull { it.first.width } ?: 0
        layout(width, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            rows.forEach { (row, placedAt) -> row.placeRelative(0, placedAt) }
        }
    }
}

private enum class RowKind {
    TAP,

    SLIDE,
}

private fun Modifier.rowKind(kind: RowKind): Modifier = layoutId(kind)

private class CardSeams {
    private var seams by mutableStateOf(emptyList<Int>())

    var at: List<Int>
        get() = seams
        set(value) {
            if (value != seams) seams = value
        }
}

private val SEAM = 1.dp

private val SEAM_SHEEN = 112.dp

private val SETTING_ROW_MIN = 56.dp

private val SETTING_ROW_PAD = PANEL_ROW_GAP / 2 + 8.dp

private val SETTING_ICON = 24.dp

private val CARD_RIM = 1.dp

private const val SPEED_MIN = 0.6f

private const val SPEED_MAX = 3f

private val SPEED_DEFAULT = Prefs().touchpadSpeed

private fun speedPosition(speed: Float): Float =
    if (speed <= SPEED_DEFAULT) (speed - SPEED_MIN) / (SPEED_DEFAULT - SPEED_MIN) * 0.5f
    else 0.5f + (speed - SPEED_DEFAULT) / (SPEED_MAX - SPEED_DEFAULT) * 0.5f

private fun positionSpeed(position: Float): Float =
    if (position <= 0.5f) SPEED_MIN + (SPEED_DEFAULT - SPEED_MIN) * position / 0.5f
    else SPEED_DEFAULT + (SPEED_MAX - SPEED_DEFAULT) * (position - 0.5f) / 0.5f
