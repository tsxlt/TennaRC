package lt.tsx.tennarc.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.data.useDemo
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.AccentButton
import lt.tsx.tennarc.ui.components.ButtonText
import lt.tsx.tennarc.ui.components.CONTROL_HEIGHT
import lt.tsx.tennarc.ui.components.DISABLED_ALPHA
import lt.tsx.tennarc.ui.components.FocusWhenSettled
import lt.tsx.tennarc.ui.components.GlassField
import lt.tsx.tennarc.ui.components.ICON_WELL_CORNER
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.PANEL_GAP
import lt.tsx.tennarc.ui.components.PANEL_INSET
import lt.tsx.tennarc.ui.components.PANEL_ROWS_PAD
import lt.tsx.tennarc.ui.components.PANEL_ROW_GAP
import lt.tsx.tennarc.ui.components.QuietButton
import lt.tsx.tennarc.ui.components.StateBadge
import lt.tsx.tennarc.ui.components.mark
import lt.tsx.tennarc.ui.components.panel
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.components.rememberOnLocalNetwork
import lt.tsx.tennarc.ui.components.rememberPressWash
import lt.tsx.tennarc.ui.components.rememberRowWash
import lt.tsx.tennarc.ui.components.stateBadgeRingRoom
import lt.tsx.tennarc.ui.sheets.isValidHost
import lt.tsx.tennarc.ui.theme.frostBehind

@Composable
fun FirstRun(prefs: Prefs, state: TvState, actions: RemoteActions, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = prefs.needsTvSetup,
        modifier = modifier,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(280, delayMillis = 40)) +
            scaleOut(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), targetScale = 1.08f),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .frostBehind()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            val top = (maxHeight * TOP_SHARE).coerceIn(24.dp, 160.dp)
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                TvSetup(
                    state = state,
                    prefs = prefs,
                    actions = actions,
                    onDone = {},
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    contentPadding = PaddingValues(top = top, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
fun TvSetup(
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    wifi: Boolean = rememberOnLocalNetwork(),
) {
    val haptics = LocalHaptics.current
    val done by rememberUpdatedState(onDone)
    val before = rememberSaveable(saver = TvChoiceSaver) { prefs.tvChoice }
    val adding = rememberSaveable { prefs.tvChosen }
    var setup by rememberSaveable(stateSaver = SetupSaver) { mutableStateOf(Setup(wifi = wifi)) }
    val dispatch: (SetupEvent) -> Unit = { setup = setup.reduce(it) }

    LaunchedEffect(wifi) { dispatch(SetupEvent.Wifi(wifi)) }

    val known = remember(before, adding) { if (adding) (before.others.map { it.host } + before.current.host).toSet() else emptySet() }
    LaunchedEffect(setup.scan, setup.scanning) {
        if (!setup.scanning) return@LaunchedEffect
        coroutineScope {
            var more = true
            val passes = launch {
                while (more) {
                    actions.scanTvs().collect { tvs -> dispatch(SetupEvent.Found(tvs.filterNot { it.host in known })) }
                    if (more) delay(PASS_GAP_MS)
                }
            }
            delay(SCAN_WINDOW_MS)
            more = false
            if (setup.found.isEmpty()) passes.cancel()
        }
        dispatch(SetupEvent.ScanEnded)
    }

    val picked = setup.picked
    LaunchedEffect(picked?.kind) {
        val kind = picked?.kind ?: return@LaunchedEffect
        actions.notices.collect { notice -> notice.asHint(kind)?.let { dispatch(SetupEvent.Hint(it)) } }
    }
    val ours = picked != null && !prefs.demoTv && state.host == picked.host && state.kind == picked.kind
    LaunchedEffect(ours, setup.asked) {
        if (ours && !setup.asked) {
            actions.pair()
            dispatch(SetupEvent.Asked)
        }
    }
    LaunchedEffect(ours, setup.asked, state.connection) {
        if (ours && setup.asked) dispatch(SetupEvent.Link(state.connection))
    }
    val connected = setup.link == Connection.Connected
    LaunchedEffect(connected) {
        if (connected) {
            delay(CONFIRM_MS)
            dispatch(SetupEvent.Confirmed)
        }
    }
    LaunchedEffect(setup.paired) {
        if (!setup.paired) return@LaunchedEffect
        haptics.confirm()
        delay(PAIRED_BEAT_MS)
        actions.updatePrefs { it.settled() }
        done()
    }

    val pick: (FoundTv) -> Unit = { tv ->
        haptics.key()
        actions.updatePrefs { it.trying(tv.toSaved(), adding) }
        dispatch(SetupEvent.Pick(tv))
    }
    val cancel: () -> Unit = {
        actions.cancelPairing()
        actions.updatePrefs { it.untrying(before) }
        dispatch(SetupEvent.Back)
    }
    BackHandler(enabled = picked != null && !setup.paired, onBack = cancel)

    val scope = rememberCoroutineScope()
    var probing by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(false) }
    val address = Address(
        probing = probing,
        missed = missed,
        onEdit = { missed = false },
        onSubmit = { typed ->
            val host = typed.trim()
            scope.launch {
                probing = true
                missed = false
                val kind = kindAt(host, actions::isReachable)
                probing = false
                if (kind == null) {
                    missed = true
                    haptics.reject()
                } else {
                    pick(FoundTv(host, kind.label, kind = kind))
                }
            }
        },
    )

    val screen = setup.screen
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val ringRoom = stateBadgeRingRoom(BADGE) - SPINNER_ROOM - contentPadding.calculateTopPadding()
            Spacer(Modifier.height(max(BADGE_GAP, ringRoom)))
            SetupBadge(screen)
            Spacer(Modifier.height(20.dp))
            SetupTitle(screen, adding)
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = screen,
                contentKey = { it.section },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 16 })
                        .togetherWith(fadeOut(tween(90)))
                        .using(SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold) })
                },
                contentAlignment = Alignment.TopCenter,
                label = "setupBody",
            ) { shown ->
                when (shown) {
                    is SetupScreen.Choose -> ChooseBody(shown.tvs, shown.scanning, onPick = pick, onRescan = { dispatch(SetupEvent.Rescan) }, address)
                    SetupScreen.NothingFound -> NothingFoundBody(
                        onRescan = { dispatch(SetupEvent.Rescan) },
                        address = address,
                        onDemo = if (adding) {
                            null
                        } else {
                            {
                                actions.updatePrefs { it.useDemo() }
                                done()
                            }
                        },
                    )
                    SetupScreen.NoWifi -> NoWifiBody()
                    is SetupScreen.Pairing -> PairingBody(
                        tv = shown.tv,
                        step = shown.step,
                        codeReady = setup.link is Connection.AwaitingCode,
                        onCode = actions::submitPairingCode,
                        onRetry = { dispatch(SetupEvent.Retry) },
                        onFinish = { dispatch(SetupEvent.Finish) },
                        onCancel = cancel,
                    )
                }
            }
        }
    }
}

private val SetupScreen.section: Any
    get() = when (this) {
        is SetupScreen.Choose -> "choose"
        SetupScreen.NothingFound -> "none"
        SetupScreen.NoWifi -> "wifi"
        is SetupScreen.Pairing -> tv.host
    }

@Composable
private fun SetupBadge(screen: SetupScreen) {
    val colors = MaterialTheme.colorScheme
    val pulse = when (screen) {
        is SetupScreen.Choose -> screen.scanning
        is SetupScreen.Pairing -> screen.step == PairStep.APPROVE
        else -> false
    }
    val busy = screen is SetupScreen.Pairing && screen.step == PairStep.CONNECTING
    val glyph = when (screen) {
        SetupScreen.NoWifi -> Glyph(SetupIcons.WifiOff, quiet = true)
        is SetupScreen.Pairing -> if (screen.step == PairStep.PAIRED) Glyph(SetupIcons.Check, quiet = false) else Glyph(screen.tv.kind.mark, quiet = false)
        else -> Glyph(SetupIcons.Tv, quiet = true)
    }
    Box(Modifier.size(BADGE + SPINNER_ROOM * 2), contentAlignment = Alignment.Center) {
        StateBadge(size = BADGE, pulse = pulse) {
            AnimatedContent(
                targetState = glyph,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f))
                        .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.8f))
                },
                label = "setupGlyph",
            ) { shown ->
                val size = if (shown.icon in BRAND_MARKS) 36.dp else 32.dp
                Icon(shown.icon, contentDescription = null, tint = if (shown.quiet) colors.onSurfaceVariant else colors.onSurface, modifier = Modifier.size(size))
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(BADGE + SPINNER_ROOM * 2), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
    }
}

private data class Glyph(val icon: ImageVector, val quiet: Boolean)

private val BRAND_MARKS: Set<ImageVector> by lazy { TvKind.entries.map { it.mark }.toSet() }

@Composable
private fun SetupTitle(screen: SetupScreen, adding: Boolean) {
    val text = stringResource(if (adding && screen == SetupScreen.NothingFound) R.string.settings_found_no_new else screen.title)
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(220, delayMillis = 60)).togetherWith(fadeOut(tween(90))).using(SizeTransform(clip = false)) },
        contentAlignment = Alignment.TopCenter,
        label = "setupTitle",
    ) { shown ->
        Text(
            shown,
            style = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

private val SetupScreen.title: Int
    get() = when (this) {
        SetupScreen.NoWifi -> R.string.offline_no_wifi_title
        is SetupScreen.Choose -> if (tvs.isEmpty()) R.string.setup_looking else R.string.setup_choose
        SetupScreen.NothingFound -> R.string.setup_none
        is SetupScreen.Pairing -> when (step) {
            PairStep.CONNECTING -> R.string.connecting_title
            PairStep.APPROVE -> R.string.approval_title
            PairStep.CODE, PairStep.WRONG_CODE -> R.string.pair_code_title
            PairStep.DECLINED, PairStep.TIMED_OUT, PairStep.STOPPED -> R.string.pair_title
            PairStep.OFFLINE -> R.string.offline_title
            PairStep.ROKU_HINT -> R.string.setup_roku_title
            PairStep.ADB_HINT -> R.string.setup_adb_title
            PairStep.PAIRED -> R.string.status_connected
        }
    }

@Composable
private fun ChooseBody(tvs: List<FoundTv>, scanning: Boolean, onPick: (FoundTv) -> Unit, onRescan: () -> Unit, address: Address) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(tvs.isNotEmpty(), enter = UnfoldIn, exit = FoldOut) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .panel(panelGlass(), MaterialTheme.shapes.large, inset = 0.dp)
                    .padding(vertical = PANEL_ROWS_PAD),
            ) {
                CompositionLocalProvider(LocalIndication provides rememberRowWash()) {
                    tvs.forEach { tv ->
                        key(tv.host) {
                            Unfolding { TvRow(tv) { onPick(tv) } }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(!scanning && tvs.isNotEmpty(), enter = UnfoldIn, exit = FoldOut) {
            var typing by rememberSaveable { mutableStateOf(false) }
            Column(
                Modifier.padding(top = PANEL_GAP * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
            ) {
                OtherWay(stringResource(R.string.setup_search_again), onRescan)
                OtherWay(stringResource(R.string.setup_enter_ip), onClick = { typing = !typing })
                AnimatedVisibility(typing, enter = UnfoldIn, exit = FoldOut) {
                    AddressField(address)
                }
            }
        }
    }
}

@Composable
private fun TvRow(tv: FoundTv, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = tv.label
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "rowSink")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = sink.value
                scaleY = sink.value
            }
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = PANEL_INSET + 12.dp, vertical = PANEL_ROW_GAP / 2 + 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(tv.kind.mark, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(SetupIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NothingFoundBody(onRescan: () -> Unit, address: Address, onDemo: (() -> Unit)?) {
    var typing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
        MainAction(stringResource(R.string.action_retry), onRescan)
        AnimatedContent(
            targetState = typing,
            transitionSpec = {
                fadeIn(tween(200, delayMillis = 60)).togetherWith(fadeOut(tween(60)))
                    .using(SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold) })
            },
            contentAlignment = Alignment.TopCenter,
            label = "address",
        ) { open ->
            if (open) AddressField(address) else OtherWay(stringResource(R.string.setup_enter_ip), onClick = { typing = true })
        }
        if (onDemo != null) OtherWay(stringResource(R.string.setup_demo), onDemo)
    }
}

@Composable
private fun NoWifiBody() {
    val context = LocalContext.current
    MainAction(stringResource(R.string.action_wifi_settings), onClick = { context.openWifiSettings() }, icon = SetupIcons.Wifi)
}

@Composable
private fun PairingBody(
    tv: FoundTv,
    step: PairStep,
    codeReady: Boolean,
    onCode: (String) -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tv.label.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnimatedContent(
            targetState = step,
            contentKey = { if (it == PairStep.WRONG_CODE) PairStep.CODE else it },
            transitionSpec = {
                fadeIn(tween(200, delayMillis = 60)).togetherWith(fadeOut(tween(60)))
                    .using(SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold) })
            },
            contentAlignment = Alignment.TopCenter,
            label = "pairStep",
        ) { shown ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (shown) {
                    PairStep.CONNECTING, PairStep.PAIRED -> Unit
                    PairStep.APPROVE -> if (tv.kind == TvKind.FIRE_TV) Note(stringResource(R.string.setup_fire_tv_allow))
                    PairStep.CODE, PairStep.WRONG_CODE -> {
                        Spacer(Modifier.height(PAIR_GAP))
                        PairingCodeBoxes(ready = codeReady, wrong = shown == PairStep.WRONG_CODE, onSubmit = onCode)
                        Note(if (shown == PairStep.WRONG_CODE) stringResource(R.string.setup_code_wrong) else "")
                    }
                    PairStep.DECLINED -> Retry(stringResource(R.string.pair_declined_body), onRetry)
                    PairStep.TIMED_OUT -> Retry(stringResource(R.string.pair_timeout_body), onRetry)
                    PairStep.STOPPED, PairStep.OFFLINE -> Retry(null, onRetry)
                    PairStep.ROKU_HINT -> {
                        Note(stringResource(R.string.setup_roku_path))
                        Spacer(Modifier.height(PAIR_GAP))
                        MainAction(stringResource(R.string.setup_done), onFinish)
                    }
                    PairStep.ADB_HINT -> Retry(stringResource(R.string.setup_adb_path), onRetry)
                }
            }
        }
        if (step != PairStep.PAIRED) {
            Spacer(Modifier.height(PAIR_GAP))
            OtherWay(stringResource(R.string.action_cancel), onCancel)
        }
    }
}

@Composable
private fun Retry(note: String?, onRetry: () -> Unit) {
    if (note != null) Note(note)
    Spacer(Modifier.height(PAIR_GAP))
    MainAction(stringResource(R.string.action_retry), onRetry)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        minLines = 1,
        modifier = Modifier.padding(top = PAIR_GAP).widthIn(max = 320.dp),
    )
}

@Composable
private fun MainAction(text: String, onClick: () -> Unit, icon: ImageVector? = null) {
    AccentButton(onClick, Modifier.widthIn(max = ACTION_WIDTH).fillMaxWidth()) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        ButtonText(text)
    }
}

@Composable
private fun OtherWay(text: String, onClick: () -> Unit) {
    QuietButton(text, onClick, Modifier.widthIn(max = ACTION_WIDTH).fillMaxWidth())
}

private class Address(val probing: Boolean, val missed: Boolean, val onEdit: () -> Unit, val onSubmit: (String) -> Unit)

@Composable
private fun AddressField(address: Address, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val valid = isValidHost(text)
    val submit = { if (valid && !address.probing) address.onSubmit(text) }
    val focus = remember { FocusRequester() }
    FocusWhenSettled(focus)
    GlassField(
        value = text,
        onValueChange = {
            text = it
            address.onEdit()
        },
        label = stringResource(R.string.setup_ip_label),
        isError = address.missed,
        supporting = if (address.missed) stringResource(R.string.setup_ip_none) else "",
        trailing = {
            Box(Modifier.size(CONTROL_HEIGHT), contentAlignment = Alignment.Center) {
                if (address.probing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER, inset = 4.dp, insetY = 4.dp), enabled = valid, role = Role.Button, onClick = submit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            SetupIcons.ArrowRight,
                            contentDescription = stringResource(R.string.setup_connect),
                            tint = if (valid) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_ALPHA),
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        modifier = modifier.widthIn(max = ACTION_WIDTH).fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun Unfolding(content: @Composable () -> Unit) {
    var seen by rememberSaveable { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(seen).apply { targetState = true } }
    LaunchedEffect(Unit) { seen = true }
    AnimatedVisibility(visible, enter = UnfoldIn) { content() }
}

private val UnfoldIn = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) +
    fadeIn(tween(220, delayMillis = 60)) +
    scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)

private val FoldOut = shrinkVertically(spring(stiffness = Spring.StiffnessMedium), shrinkTowards = Alignment.Top) + fadeOut(tween(90))

private fun Context.openWifiSettings() {
    val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.provider.Settings.Panel.ACTION_WIFI else android.provider.Settings.ACTION_WIFI_SETTINGS
    catching { startActivity(Intent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.logged("Welcome.openWifiSettings")
}

private const val SCAN_WINDOW_MS = 8_000L

private const val PASS_GAP_MS = 1_000L

private const val CONFIRM_MS = 250L
private const val PAIRED_BEAT_MS = 650L

private const val TOP_SHARE = 0.16f

private val BADGE = 72.dp
private val SPINNER_ROOM = 6.dp
private val BADGE_GAP = 12.dp
private val ROW_HEIGHT = 72.dp
private val ACTION_WIDTH = 320.dp

private val PAIR_GAP = 16.dp
