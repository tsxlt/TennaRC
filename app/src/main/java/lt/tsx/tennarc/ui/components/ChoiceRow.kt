package lt.tsx.tennarc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.ui.theme.slightlySmaller

@Composable
internal fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    optionIcon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    row: @Composable (toggle: Modifier, value: @Composable RowScope.() -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var open by rememberSaveable { mutableStateOf(false) }
    val current = selected?.let { label(it) }
    val source = remember { MenuSource() }
    Box(if (enabled) modifier else modifier.alpha(DISABLED_ALPHA)) {
        row(
            Modifier
                .clickable(enabled = enabled, role = Role.DropdownList) {
                    haptics.key()
                    open = true
                }
                .semantics { if (current != null) stateDescription = current },
        ) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedContent(
                        selected,
                        modifier = Modifier
                            .onGloballyPositioned { source.rect = it.boundsInWindow() }
                            .graphicsLayer { alpha = 1f - source.away() },
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) using SizeTransform(clip = false) },
                        contentAlignment = Alignment.CenterEnd,
                        label = "choiceValue",
                    ) { value ->
                        if (value != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(optionIcon(value), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Text(
                                    label(value),
                                    modifier = Modifier.widthIn(max = VALUE_MAX),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = slightlySmaller(MaterialTheme.typography.labelLarge),
                                )
                            }
                        }
                    }
                    Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                ChoiceMenu(expanded = open, onDismiss = { open = false }, source = source) {
                    options.forEach { option ->
                        ChoiceMenuItem(label(option), optionIcon(option), selected = option == selected) {
                            open = false
                            if (option != selected) onPick(option)
                        }
                    }
                }
            }
        }
    }
}

private val VALUE_MAX = 128.dp
