package lt.tsx.tennarc.ui.sheets

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import lt.tsx.tennarc.ui.theme.PillShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.AppLanguage
import lt.tsx.tennarc.ui.components.CONTROL_HEIGHT
import lt.tsx.tennarc.ui.components.IN_USE_INK
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROWS_PAD
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.PANEL_ROW_PAD
import lt.tsx.tennarc.ui.components.SquircleCache
import lt.tsx.tennarc.ui.components.ICON_WELL_CORNER
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.components.panelSegment
import lt.tsx.tennarc.ui.components.rememberPressWash
import lt.tsx.tennarc.ui.theme.CornerLarge
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.glass
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private class Language(val locale: Locale?, val name: String, val here: String?)

@Composable
fun LanguageSheetContent(
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
    phoneLanguages: List<Locale> = rememberPhoneLanguages(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val ui = LocalConfiguration.current.locales[0]
    val chosen = remember { AppLanguage.chosen(context) }
    val all = remember(ui) {
        val collator = Collator.getInstance(ui)
        AppLanguage.supported(context).map { Language(it, ownName(it), hereName(it, ui)) }.sortedWith(compareBy(collator) { it.name })
    }
    val phone = remember(all, phoneLanguages) { phoneLanguages.mapNotNull { mine -> bestMatch(mine, all) }.distinctBy { it.locale } }
    val guessed = remember(all, phone) { AppLanguage.guessed(context).mapNotNull { bestMatch(it, all) }.distinctBy { it.locale }.filter { it !in phone } }
    val system = Language(null, stringResource(R.string.settings_theme_system), phoneLanguages.firstOrNull()?.let(::ownName))
    var query by remember { mutableStateOf("") }
    val shown = remember(query, all, phone, guessed) {
        val q = fold(query.trim())
        if (q.isEmpty()) {
            val first = (listOf(system) + phone + guessed + listOfNotNull(all.firstOrNull { it.locale == chosen })).distinctBy { it.locale }
            first to all.filter { it !in first }
        } else {
            emptyList<Language>() to all.filter { it.matches(q) }
        }
    }
    fun pick(language: Language) {
        haptics.confirm()
        onPicked()
        if (activity != null && language.locale != chosen) view.postDelayed({ AppLanguage.choose(activity, language.locale) }, SWITCH_AFTER_MS)
    }
    Column(modifier.fillMaxHeight()) {
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
        )
        SearchBox(query, onQuery = { query = it }, Modifier.padding(horizontal = 24.dp))
        val list = rememberLazyListState()
        LazyColumn(
            Modifier.weight(1f).padding(top = 8.dp).fadeTop(list),
            state = list,
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            val (top, rest) = shown
            itemsIndexed(top, key = { _, it -> "top-" + (it.locale?.toLanguageTag() ?: "system") }) { i, language ->
                LanguageRow(language, picked = language.locale == chosen, position = i, count = top.size) { pick(language) }
            }
            if (top.isNotEmpty() && rest.isNotEmpty()) item(key = "gap") { Spacer(Modifier.size(16.dp)) }
            itemsIndexed(rest, key = { _, it -> it.locale?.toLanguageTag() ?: "system" }) { i, language ->
                LanguageRow(language, picked = language.locale == chosen, position = i, count = rest.size) { pick(language) }
            }
        }
    }
}

private const val SWITCH_AFTER_MS = 400L

@Composable
private fun rememberPhoneLanguages(): List<Locale> {
    val context = LocalContext.current
    return remember(context) { AppLanguage.phone(context) }
}

private fun Modifier.fadeTop(list: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = if (list.canScrollBackward) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithContent {
        drawContent()
        val fade = EDGE_FADE.toPx()
        if (list.canScrollBackward) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade), blendMode = BlendMode.DstIn)
        }
    }

private val EDGE_FADE = 28.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = CONTROL_HEIGHT)
            .glass(panelGlass(), PillShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_bar_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(CONTROL_HEIGHT - 8.dp)
                    .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER), role = Role.Button) { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear), tint = colors.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun LanguageRow(language: Language, picked: Boolean, position: Int, count: Int, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = panelGlass()
    val lit = colors.onSurface.copy(alpha = IN_USE_INK)
    val pill = remember { SquircleCache() }
    val ends = PANEL_ROWS_PAD - PANEL_ROW_GAP / 2
    Box(
        Modifier
            .padding(horizontal = 24.dp)
            .panelSegment(glass, CornerLarge, position, count)
            .padding(horizontal = PANEL_INSET, vertical = PANEL_ROW_GAP / 2)
            .padding(top = if (position == 0) ends else 0.dp, bottom = if (position == count - 1) ends else 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { if (picked) drawPath(pill.of(size, CornerSmall.toPx()), lit) }
                .clickable(interactionSource = null, indication = rememberPressWash(CornerSmall), role = Role.RadioButton, onClick = onPick)
                .semantics { selected = picked }
                .heightIn(min = 52.dp)
                .padding(horizontal = PANEL_ROW_PAD, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(language.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (language.here != null && language.here != language.name) {
                    Text(language.here, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun ownName(locale: Locale): String = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

private fun hereName(locale: Locale, ui: Locale): String = locale.getDisplayName(ui).replaceFirstChar { it.titlecase(ui) }

private fun Language.matches(folded: String): Boolean {
    val locale = locale ?: return false
    return listOf(name, here.orEmpty(), locale.getDisplayName(Locale.ENGLISH), locale.toLanguageTag()).any { fold(it).contains(folded) }
}

private fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")

private val MARKS = Regex("\\p{Mn}+")

private fun bestMatch(mine: Locale, all: List<Language>): Language? {
    val same = all.filter { it.locale?.language == mine.language }
    return same.firstOrNull { it.locale?.toLanguageTag() == mine.toLanguageTag() }
        ?: same.firstOrNull { mine.country.isNotEmpty() && it.locale?.country == mine.country }
        ?: same.firstOrNull { it.locale?.script == mine.script && it.locale?.country.isNullOrEmpty() }
        ?: same.firstOrNull()
}
