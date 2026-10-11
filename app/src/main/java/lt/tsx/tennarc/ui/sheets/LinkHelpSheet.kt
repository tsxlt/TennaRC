package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROWS_PAD
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.PANEL_ROW_PAD
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.panelGlass

@Composable
fun LinkHelpSheetContent(report: LinkReport, modifier: Modifier = Modifier) {
    val stalling = report.verdict == LinkVerdict.STALLING
    val fixes = if (stalling) {
        listOf(
            Icons.Rounded.Wifi to R.string.link_fix_24_title,
            Icons.Rounded.SettingsEthernet to R.string.link_fix_ethernet_title,
            Icons.Rounded.WifiTethering to R.string.link_fix_extender_title,
            Icons.Rounded.ScreenShare to R.string.link_fix_listen_title,
        )
    } else {
        listOf(
            Icons.Rounded.Router to R.string.link_fix_closer_title,
            Icons.Rounded.WifiTethering to R.string.link_fix_extender_title,
            Icons.Rounded.SettingsEthernet to R.string.link_fix_ethernet_title,
            Icons.Rounded.Wifi to R.string.link_fix_band_title,
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).then(modifier)) {
        Text(
            stringResource(R.string.link_banner_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(if (stalling) R.string.link_help_stalling else R.string.link_help_slow),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(PANEL_GAP * 2))
        Column(Modifier.fillMaxWidth().panel(panelGlass(), MaterialTheme.shapes.large, inset = 0.dp).padding(vertical = PANEL_ROWS_PAD)) {
            fixes.forEach { (icon, title) -> Fix(icon, stringResource(title)) }
        }
    }
}

@Composable
private fun Fix(icon: ImageVector, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PANEL_INSET + PANEL_ROW_PAD, vertical = PANEL_ROW_GAP / 2 + 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
