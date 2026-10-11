package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.BuildConfig
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.AppLanguage
import lt.tsx.tennarc.data.answerFindTitles
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.ScrollStrip
import lt.tsx.tennarc.data.ThemeMode
import lt.tsx.tennarc.data.currentTv
import lt.tsx.tennarc.data.forgetTv
import lt.tsx.tennarc.data.hiddenHere
import lt.tsx.tennarc.data.movedTo
import lt.tsx.tennarc.data.removeDemo
import lt.tsx.tennarc.data.resetSettings
import lt.tsx.tennarc.data.showApp
import lt.tsx.tennarc.data.showChannel
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.data.useDemo
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.ui.components.ARMED_MS
import lt.tsx.tennarc.ui.components.ChoiceRow
import lt.tsx.tennarc.ui.components.GlassDialog
import lt.tsx.tennarc.ui.components.GlassField
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.Lucide
import lt.tsx.tennarc.ui.components.fadeTopEdge
import lt.tsx.tennarc.ui.components.mark
import lt.tsx.tennarc.ui.onboarding.label
import lt.tsx.tennarc.ui.onboarding.toSaved
import lt.tsx.tennarc.ui.theme.SectionLabel

private val HOST_NAME = Regex("""^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$""")

internal fun isValidHost(text: String): Boolean {
    val host = text.trim()
    if (host.all { it.isDigit() || it == '.' }) {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.toInt() <= 255 }
    }
    return HOST_NAME.matches(host)
}

@Composable
fun SettingsSheetContent(
    prefs: Prefs,
    onUpdate: ((Prefs) -> Prefs) -> Unit,
    onFindTvs: suspend () -> List<FoundTv>,
    onForgetPairing: () -> Unit,
    modifier: Modifier = Modifier,
    initialFound: List<FoundTv>? = null,
    findOnOpen: Boolean = false,
    onAddTv: (() -> Unit)? = null,
    devLink: DevLink? = null,
    onDevLink: (() -> Unit)? = null,
    onLanguage: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val tvKey = prefs.currentTv.copy(name = "", mac = "")
    var name by remember(tvKey) { mutableStateOf(prefs.tvName) }
    var host by remember(tvKey) { mutableStateOf(prefs.host) }
    var mac by remember(tvKey) { mutableStateOf(prefs.mac) }
    var nameDirty by remember(tvKey) { mutableStateOf(false) }
    var hostDirty by remember(tvKey) { mutableStateOf(false) }
    var macDirty by remember(tvKey) { mutableStateOf(false) }
    var speed by remember(prefs.touchpadSpeed) { mutableStateOf(prefs.touchpadSpeed) }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(initialFound) }
    var editing by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    val useLabel = stringResource(R.string.settings_use_tv)
    val editLabel = stringResource(R.string.settings_edit_tv)
    val devLinkLabel = stringResource(R.string.dev_link_title)
    val devLinkState = devLink?.let { stringResource(it.summary) }
    val forgetLabel = stringResource(R.string.settings_forget)
    val forgetConfirm = stringResource(R.string.settings_forget_confirm)
    val removeLabel = stringResource(R.string.settings_remove_tv)
    val removeConfirm = stringResource(R.string.settings_remove_confirm)
    suspend fun find() {
        searching = true
        found = onFindTvs()
        searching = false
    }
    LaunchedEffect(Unit) { if (findOnOpen) find() }
    val hostValid = isValidHost(host)
    val macValid = WakeOnLan.isValidSetting(mac)
    var hostChecked by remember { mutableStateOf(false) }
    var macChecked by remember { mutableStateOf(false) }

    fun commitName() {
        val trimmed = name.trim()
        if (!nameDirty || trimmed.isEmpty()) return
        nameDirty = false
        if (trimmed != prefs.tvName) onUpdate { it.copy(tvName = trimmed) }
    }

    fun commitHost() {
        if (!hostDirty || !hostValid) return
        hostDirty = false
        if (host.trim() != prefs.host) onUpdate { it.movedTo(host.trim()) }
    }

    fun commitMac() {
        if (!macDirty || !macValid) return
        macDirty = false
        if (mac.trim() != prefs.mac) onUpdate { it.copy(mac = mac.trim()) }
    }

    fun use(tv: SavedTv?) {
        haptics.confirm()
        commitName()
        onUpdate { if (tv == null) it.useDemo() else it.switchTo(tv) }
    }

    val latestCommit by rememberUpdatedState({ commitName(); commitHost(); commitMac() })
    DisposableEffect(Unit) { onDispose { latestCommit() } }

    val scroll = rememberScrollState()
    var labsAt by remember { mutableStateOf<Float?>(null) }
    var keepLabs by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .fadeTopEdge(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        Heading(R.string.settings_tv)
        Card {
            TvList(
                prefs = prefs,
                onUse = ::use,
                onReorder = { order -> onUpdate { it.copy(tvOrder = order) } },
                actionsFor = { tv, inUse ->
                    buildList {
                        if (!inUse) add(TvAction(useLabel, Icons.Rounded.Check) { use(tv) })
                        when {
                            inUse && tv != null -> {
                                add(TvAction(editLabel, Icons.Rounded.Edit) { editing = true })
                                if (devLink != null && onDevLink != null) add(TvAction(devLinkLabel, Icons.Rounded.DeveloperMode, detail = devLinkState) { onDevLink() })
                                add(TvAction(forgetLabel, Icons.Rounded.LinkOff, forgetConfirm) { onForgetPairing() })
                            }
                            tv == null -> add(TvAction(removeLabel, Icons.Rounded.DeleteOutline, removeConfirm) { onUpdate { it.removeDemo() } })
                            else -> add(TvAction(removeLabel, Icons.Rounded.DeleteOutline, removeConfirm) { onUpdate { it.forgetTv(tv) } })
                        }
                    }
                },
            )
            found?.let { all ->
                val tvs = all.filter { tv -> tv.host != prefs.host && prefs.otherTvs.none { it.host == tv.host } }
                if (tvs.isEmpty()) {
                    val none = if (all.isEmpty()) R.string.settings_found_none else R.string.settings_found_no_new
                    SettingRow(icon = null, title = stringResource(none), titleColor = colors.onSurfaceVariant)
                }
                tvs.forEach { tv ->
                    val label = tv.label
                    val press = remember { MutableInteractionSource() }
                    TvRow(
                        mark = tv.kind.mark,
                        name = label.name,
                        summary = label.detail,
                        press = press,
                        modifier = Modifier.clickable(press, indication = null, role = Role.Button) { use(tv.toSaved()) },
                    ) {
                        Text(stringResource(R.string.settings_add), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
            }
            val findPress = remember { MutableInteractionSource() }
            TvRow(
                mark = Icons.Rounded.Add,
                name = stringResource(if (searching) R.string.settings_finding else R.string.settings_find),
                summary = null,
                press = findPress,
                markSize = TV_GLYPH,
                bare = true,
                busy = searching,
                modifier = Modifier.clickable(findPress, indication = null, enabled = !searching, role = Role.Button) {
                    if (onAddTv != null) onAddTv() else scope.launch { find() }
                },
            )
        }

        if (editing) {
            fun cancel() {
                name = prefs.tvName
                host = prefs.host
                mac = prefs.mac
                nameDirty = false
                hostDirty = false
                macDirty = false
                hostChecked = false
                macChecked = false
                editing = false
            }
            fun save() {
                hostChecked = hostDirty
                macChecked = macDirty
                if ((!hostDirty || hostValid) && (!macDirty || macValid)) {
                    commitName()
                    commitHost()
                    commitMac()
                    editing = false
                }
            }
            GlassDialog(
                title = prefs.tvName,
                confirm = stringResource(R.string.rename_save),
                onConfirm = { save() },
                dismiss = stringResource(R.string.action_cancel),
                onDismissButton = { cancel() },
                onDismissRequest = { cancel() },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
                    GlassField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameDirty = true
                        },
                        label = stringResource(R.string.settings_name),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassField(
                        value = host,
                        onValueChange = {
                            host = it
                            hostDirty = true
                            hostChecked = false
                        },
                        label = stringResource(R.string.settings_host),
                        isError = hostChecked && !hostValid,
                        supporting = if (hostChecked && !hostValid) stringResource(R.string.settings_host_invalid) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassField(
                        value = mac,
                        onValueChange = {
                            mac = it
                            macDirty = true
                            macChecked = false
                        },
                        label = stringResource(R.string.settings_mac),
                        isError = macChecked && !macValid,
                        supporting = if (macChecked && !macValid) stringResource(R.string.settings_mac_invalid) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val hiddenApps = prefs.hiddenHere.sortedBy { prefs.hiddenNames[it] ?: it }
        val hiddenChannels = prefs.hiddenChannels.sortedBy { prefs.hiddenNames[it] ?: it }
        if (hiddenApps.isNotEmpty() || hiddenChannels.isNotEmpty()) {
            Heading(R.string.settings_hidden)
            Card {
                hiddenApps.forEach { key ->
                    HiddenRow(prefs.hiddenNames[key] ?: key.substringAfter('/')) { onUpdate { it.showApp(key) } }
                }
                hiddenChannels.forEach { id ->
                    HiddenRow(prefs.hiddenNames[id] ?: id) { onUpdate { it.showChannel(id) } }
                }
            }
        }

        Heading(R.string.settings_touchpad)
        Card(top = 0.dp) {
            SpeedSlider(speed, onSpeed = { speed = it }, onDone = { onUpdate { it.copy(touchpadSpeed = speed) } })
            SwitchRow(Icons.Rounded.SwapVert, stringResource(R.string.settings_natural_scroll), prefs.naturalScroll) { value ->
                onUpdate { it.copy(naturalScroll = value) }
            }
            ChoiceRow(
                options = listOf(ScrollStrip.LEFT, ScrollStrip.RIGHT, ScrollStrip.OFF),
                selected = prefs.scrollStrip,
                label = { stringResource(it.label) },
                optionIcon = { it.icon },
                onPick = { value -> onUpdate { it.copy(scrollStrip = value) } },
            ) { toggle, value ->
                SettingRow(Icons.Rounded.VerticalSplit, stringResource(R.string.settings_scroll_strip), toggle, trailing = value)
            }
        }

        Heading(R.string.settings_phone)
        Card {
            ChoiceRow(
                options = ThemeMode.entries,
                selected = prefs.themeMode,
                label = { stringResource(it.label) },
                optionIcon = { it.icon },
                onPick = { value -> onUpdate { it.copy(themeMode = value) } },
            ) { toggle, value ->
                SettingRow(
                    Icons.Rounded.Contrast,
                    stringResource(R.string.settings_theme),
                    toggle,
                    summary = null,
                    trailing = value,
                )
            }
            if (onLanguage != null) {
                val context = LocalContext.current
                val words = LocalConfiguration.current.locales.toLanguageTags()
                val language = remember(words) { AppLanguage.chosen(context) }
                val row = remember { BringIntoViewRequester() }
                var lastWords by rememberSaveable { mutableStateOf(words) }
                LaunchedEffect(words) {
                    if (words != lastWords) {
                        lastWords = words
                        withFrameNanos {}
                        row.bringIntoView()
                    }
                }
                SettingRow(
                    Icons.Rounded.Translate,
                    stringResource(R.string.settings_language),
                    Modifier.bringIntoViewRequester(row).clickable(role = Role.Button, onClick = onLanguage),
                ) {
                    Text(
                        language?.let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } } ?: stringResource(R.string.settings_theme_system),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            SwitchRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.settings_volume_keys), prefs.volumeKeys) { value ->
                onUpdate { it.copy(volumeKeys = value) }
            }
            SwitchRow(Icons.Rounded.Keyboard, stringResource(R.string.settings_auto_keyboard), prefs.autoKeyboard) { value ->
                onUpdate { it.copy(autoKeyboard = value) }
            }
            SwitchRow(Icons.Rounded.LightMode, stringResource(R.string.settings_keep_screen_on), prefs.keepScreenOn) { value ->
                onUpdate { it.copy(keepScreenOn = value) }
            }
            SwitchRow(Icons.Rounded.Notifications, stringResource(R.string.settings_notification_controls), prefs.notificationControls) { value ->
                onUpdate { it.copy(notificationControls = value, askedNotifications = it.askedNotifications && !value) }
            }
            SwitchRow(Icons.Rounded.Vibration, stringResource(R.string.settings_haptics), prefs.haptics) { value ->
                onUpdate { it.copy(haptics = value) }
            }
        }

        Heading(R.string.settings_search)
        Card {
            ChoiceRow(
                options = SearchEngine.entries,
                selected = prefs.searchEngine,
                label = { it.label },
                optionIcon = { it.mark },
                onPick = { value -> onUpdate { it.copy(searchEngine = value) } },
            ) { toggle, value ->
                SettingRow(Icons.Rounded.TravelExplore, stringResource(R.string.settings_search_engine), toggle, trailing = value)
            }
            SwitchRow(Icons.Rounded.Movie, stringResource(R.string.settings_find_titles), prefs.findTitles) { value ->
                onUpdate { it.answerFindTitles(value) }
            }
        }

        Box(
            Modifier.onPlaced { placed ->
                val at = placed.positionInParent().y
                val before = labsAt
                labsAt = at
                if (keepLabs && before != null && at != before) {
                    keepLabs = false
                    scroll.dispatchRawDelta(at - before)
                }
            },
        )
        Heading(R.string.settings_labs)
        Card {
            SwitchRow(
                Icons.Rounded.Science,
                stringResource(R.string.settings_demo_tv),
                prefs.demoListed,
                summary = stringResource(R.string.settings_demo_tv_listed),
            ) { value ->
                keepLabs = true
                onUpdate { if (value) it.copy(demoListed = true) else it.removeDemo() }
            }
        }

        Heading(R.string.settings_help)
        Card {
            val links = LocalUriHandler.current
            for ((icon, title, path) in listOf(
                Triple(Icons.Rounded.BugReport, R.string.settings_report, "/report"),
                Triple(Icons.Rounded.PrivacyTip, R.string.settings_privacy, "/privacy"),
            )) {
                SettingRow(icon, stringResource(title), Modifier.clickable(role = Role.Button) { runCatching { links.openUri(BuildConfig.SITE + path) } })
            }
            var resetArmed by remember { mutableStateOf(false) }
            LaunchedEffect(resetArmed) {
                if (resetArmed) {
                    delay(ARMED_MS)
                    resetArmed = false
                }
            }
            SettingRow(
                Icons.Rounded.RestartAlt,
                stringResource(R.string.settings_reset),
                Modifier.clickable(role = Role.Button) {
                    if (resetArmed) {
                        haptics.confirm()
                        resetArmed = false
                        onUpdate { it.resetSettings() }
                    } else {
                        haptics.tick()
                        resetArmed = true
                    }
                },
                summary = stringResource(if (resetArmed) R.string.settings_reset_confirm else R.string.settings_reset_body),
                iconTint = if (resetArmed) colors.error else colors.onSurfaceVariant,
                titleColor = if (resetArmed) colors.error else colors.onSurface,
            )
        }

        Spacer(Modifier.size(24.dp))
        val context = LocalContext.current
        val version = BuildConfig.VERSION_NAME
        Text(
            text = stringResource(R.string.settings_version, version),
            style = SectionLabel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

internal val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.AUTO -> R.string.settings_theme_auto
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

internal val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.AUTO -> Icons.Rounded.BrightnessAuto
        ThemeMode.SYSTEM -> Icons.Rounded.Smartphone
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }

internal val ScrollStrip.label: Int
    get() = when (this) {
        ScrollStrip.LEFT -> R.string.settings_scroll_strip_left
        ScrollStrip.RIGHT -> R.string.settings_scroll_strip_right
        ScrollStrip.OFF -> R.string.settings_scroll_strip_off
    }

// Lucide's panel icons (ISC).
private val stripIcons by lazy {
    val pad = "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"
    ScrollStrip.entries.associateWith { strip ->
        val line = when (strip) {
            ScrollStrip.LEFT -> "M9 3v18"
            ScrollStrip.RIGHT -> "M15 3v18"
            ScrollStrip.OFF -> ""
        }
        ImageVector.Builder(name = "strip_${strip.name}", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                PathParser().parsePathString(pad + line).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
}

internal val ScrollStrip.icon: ImageVector get() = stripIcons.getValue(this)
