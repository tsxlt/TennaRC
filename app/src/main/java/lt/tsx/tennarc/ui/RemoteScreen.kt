package lt.tsx.tennarc.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.answerFindTitles
import lt.tsx.tennarc.data.deckKey
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.data.renameInput
import lt.tsx.tennarc.data.withRecent
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.AppDeckChrome
import lt.tsx.tennarc.ui.components.DeckGrow
import lt.tsx.tennarc.ui.components.DeckShape
import lt.tsx.tennarc.ui.components.DeckUse
import lt.tsx.tennarc.ui.components.DeckValue
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.SearchField
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.components.grownTopPx
import lt.tsx.tennarc.ui.components.recentEntry
import lt.tsx.tennarc.ui.components.run
import lt.tsx.tennarc.ui.components.sortedApps
import lt.tsx.tennarc.ui.onboarding.FirstRun
import lt.tsx.tennarc.ui.sheets.KeyboardPanel
import lt.tsx.tennarc.ui.sheets.MoreSheetContent
import lt.tsx.tennarc.ui.theme.CornerExtraLarge
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.scrimAlpha

enum class Sheet { SEARCH, KEYBOARD, MORE, POWER, SETTINGS, ADD_TV, LINK_HELP, DEV_LINK, LANGUAGE }

private val SHORT_SCREEN = 660.dp

private val SHORT_PAD = 240.dp

internal val PAD_SIDE = 16.dp

internal val DPAD_PHONE = 320.dp

private val DPAD_WIDE = 360.dp

private val BROWSE_MAX = 720.dp

internal val BROWSE_GAP = 14.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    initialMode: PadMode? = null,
    requestedSheet: Sheet? = null,
    onSheetRequestHandled: () -> Unit = {},
    onKey: (android.view.KeyEvent) -> Boolean = { false },
    volumeNudges: Flow<Int> = emptyFlow(),
    fold: Fold? = rememberFold(),
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val window = with(density) {
        if (windowSize.width > 0) DpSize(windowSize.width.toDp(), windowSize.height.toDp())
        else DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
    }
    val (layout, postureShown) = fadeThroughPosture(remoteLayout(window.width, window.height, fold, rtl = LocalLayoutDirection.current == LayoutDirection.Rtl), window)
    val growsMore = layout == RemoteLayout.Upright || (layout is RemoteLayout.Tabletop && window.width < TABLETOP_SIDE_BY_SIDE)
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    val sheetShown = remember { mutableFloatStateOf(0f) }
    val deck = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(DeckValue.Collapsed) }
    val latestPrefs by rememberUpdatedState(prefs)
    var use by remember { mutableStateOf(DeckUse(prefs.appUse, prefs.channelUse)) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { use = DeckUse(latestPrefs.appUse, latestPrefs.channelUse) }
    var mode by rememberPadMode(prefs, state.foregroundAppId, initialMode, actions)
    val search = rememberSearchState()
    val more = rememberMoreSheetState(sheet = { sheet }, onSheet = { sheet = it }, shown = sheetShown)
    var renaming by remember { mutableStateOf<TvApp?>(null) }
    val hold = rememberDeckHold(prefs, actions, snackbar, onRename = { renaming = it })

    val haptics = LocalHaptics.current
    val backScope = rememberCoroutineScope()
    val deckOpen = layout.rollsDeck && deck.targetValue == DeckValue.Expanded
    BackHandler(enabled = sheet == null && !search.searching && state.isUsable && !deckOpen) {
        haptics.key()
        actions.press(TvButton.BACK)
    }
    PredictiveBackHandler(enabled = deckOpen && sheet == null && !search.searching) { swipe ->
        deck.closeWithBack(swipe, deck.anchors.positionOf(DeckValue.Expanded), DeckValue.Expanded, DeckValue.Collapsed, backScope)
    }
    PredictiveBackHandler(enabled = search.searching) { swipe -> search.followBack(swipe) }
    PredictiveBackHandler(enabled = sheet == Sheet.MORE) { swipe ->
        more.drag.closeWithBack(swipe, more.full.floatValue, MoreSheetValue.Shown, MoreSheetValue.Hidden, backScope)
    }

    LaunchedEffect(search.searching) {
        if (search.searching && layout.rollsDeck) {
            delay(DECK_ROLL_UP_MS)
            deck.snapTo(DeckValue.Collapsed)
        }
    }
    LaunchedEffect(requestedSheet) {
        when (requestedSheet) {
            null -> return@LaunchedEffect
            Sheet.SEARCH -> search.open()
            Sheet.MORE -> more.open()
            else -> sheet = requestedSheet
        }
        onSheetRequestHandled()
    }

    val tvWantsText = state.keyboard != null
    var keyboardAutoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tvWantsText) {
        if (tvWantsText && prefs.autoKeyboard && sheet == null && !search.searching) {
            sheet = Sheet.KEYBOARD
            keyboardAutoOpened = true
        }
    }
    val closeSheet = {
        sheet = null
        keyboardAutoOpened = false
    }
    var lastBadLink by remember { mutableStateOf<LinkReport?>(null) }
    LaunchedEffect(state.link) {
        state.link?.takeIf { it.verdict != LinkVerdict.GOOD }?.let { lastBadLink = it }
    }
    val linkBad = state.isConnected && state.link?.verdict.let { it != null && it != LinkVerdict.GOOD }
    val linkNote = LinkNote(verdict = state.link?.verdict.takeIf { linkBad }, onHelp = { sheet = Sheet.LINK_HELP })

    renaming?.let { input ->
        RenameInput(
            current = latestPrefs.inputNames[latestPrefs.deckKey(input.id)],
            original = state.inputs.firstOrNull { it.id == input.id }?.title ?: input.title,
            onRename = { name ->
                actions.updatePrefs { it.renameInput(input.id, name) }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    val moreGrow = DeckGrow(
        panelTopPx = more.panelTop,
        ceilingPx = { (layout as? RemoteLayout.Tabletop)?.let { with(density) { (it.gapBottom + TABLETOP_MORE_GAP).toPx() } } ?: Float.NaN },
        revealedPx = { more.offset },
        fullPx = more.full,
        onMeasured = { more.measured(it) },
        revealed = { more.revealed },
        scrollConnection = more.scroll,
        content = {
            Box(Modifier.heightIn(max = window.height * 0.86f)) {
                MoreSheetContent(
                    enabled = state.isUsable,
                    onPress = actions::press,
                    captions = Feature.CAPTIONS in state.features,
                    liveTv = Feature.LIVE_TV in state.features,
                    numberKeys = state.kind != TvKind.ROKU,
                    channel = state.channels.firstOrNull { it.id == state.channelId }?.number,
                    settings = tvSettings(state, actions, state.isUsable).takeIf { layout.rollsDeck },
                    contentPadding = GROWN_MORE_PADDING,
                )
            }
        },
    )
    val barShown: () -> Float = { search.barShown() }
    val leavingSearch: () -> Float = { search.leaving() }
    Box(modifier) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .frostBehind()
                .then(if (sheet == Sheet.KEYBOARD || prefs.needsTvSetup) Modifier.clearAndSetSemantics {} else Modifier),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { if (sheet == null && layout !is RemoteLayout.Panes) SnackbarHost(snackbar, Modifier.imePadding()) },
        ) { padding ->
            var headerIcons by remember { mutableIntStateOf(0) }
            val searchField: @Composable (Modifier) -> Unit = { placement ->
                SearchField(
                    query = search.query,
                    onQuery = { search.query = it },
                    active = search.searching,
                    onActive = { active -> if (active) search.open() else search.close() },
                    onGo = { search.go() },
                    hint = stringResource(R.string.search_bar_hint),
                    shown = barShown,
                    snackbar = snackbar,
                    edgeTravel = { headerIcons - 12.dp.toPx() },
                    modifier = placement,
                )
            }
            val results: @Composable (Modifier) -> Unit = { placement ->
                SearchResults(
                    query = search.query,
                    apps = remember(state.apps, state.inputs) { sortedApps(state.apps, state.inputs) },
                    tvText = state.keyboard?.textLength,
                    engine = prefs.searchEngine.takeIf { Feature.WEB_LINKS in state.features },
                    recent = prefs.recent,
                    findTitles = if (prefs.findTitles) actions::findTitles else null,
                    findPicks = if (prefs.findTitles) actions::findPicks else null,
                    goes = search.goes,
                    onRun = { suggestion ->
                        actions.run(suggestion).also { ok ->
                            if (ok) suggestion.recentEntry()?.let { entry -> actions.updatePrefs { it.withRecent(entry) } }
                        }
                    },
                    onFill = { search.query = it },
                    onClearRecent = { actions.updatePrefs { it.copy(recent = emptyList()) } },
                    onEngine = { engine -> actions.updatePrefs { it.copy(searchEngine = engine) } },
                    onDone = { search.close() },
                    opens = actions::actionsFor,
                    modifier = placement
                        .graphicsLayer {
                            val leaving = search.leaving()
                            alpha = 1f - leaving
                            val scale = 1f - SEARCH_LEAVE_SCALE * leaving
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.3f)
                        }
                        .background(MaterialTheme.colorScheme.background)
                        .frostBehind()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
                )
            }
            val pad: @Composable (TvState, PadMode, Dp, Modifier) -> Unit = { tv, shownMode, dpadMax, placement ->
                PadArea(tv, latestPrefs, actions, shownMode, placement, dpadMax)
            }
            val header: @Composable () -> Unit = {
                TopControls(search.searching, barShown, state, prefs, actions, onSheet = { sheet = it }, searchField, linkNote, onIconsWidth = { headerIcons = it })
            }
            val keys: @Composable (cast: Boolean, room: DpSize?) -> Unit = { cast, room ->
                Decks(
                    state, actions, mode, onMode = { mode = it }, onSheet = { sheet = it }, { more.open() }, more.swipeUp,
                    volumeNudges, prefs.autoKeyboard, cast, room,
                    grow = moreGrow.takeIf { growsMore },
                )
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val shown = postureShown()
                        alpha = shown
                        scaleX = POSTURE_SCALE + (1f - POSTURE_SCALE) * shown
                        scaleY = scaleX
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                val start = padding.calculateStartPadding(LocalLayoutDirection.current)
                val top = padding.calculateTopPadding()
                when (layout) {
                    RemoteLayout.Upright -> {
                        val short = maxHeight < SHORT_SCREEN
                        val pageScroll = rememberScrollState()
                        val scroll = if (short && !search.searching) Modifier.verticalScroll(pageScroll) else Modifier
                        Column(Modifier.fillMaxSize().then(scroll).padding(top = 8.dp)) {
                            header()
                            SearchOr(search.searching, results, back = leavingSearch) {
                                Spacer(Modifier.size(14.dp))
                                Apps(
                                    state,
                                    actions,
                                    deck,
                                    use,
                                    prefs,
                                    hold,
                                    if (short) Modifier.height(AppDeckChrome + SHORT_PAD) else Modifier.weight(1f),
                                ) { pad(state, mode, DPAD_PHONE, Modifier.fillMaxSize()) }
                                keys(true, null)
                            }
                        }
                    }
                    RemoteLayout.Sideways -> {
                        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                header()
                                SearchOr(search.searching, results, back = leavingSearch) {
                                    Spacer(Modifier.size(14.dp))
                                    Apps(state, actions, deck, use, prefs, hold, Modifier.weight(1f), peek = 0.dp) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { keys(false, null) }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).navigationBarsPadding()) {
                                Casting(state, actions, Modifier.padding(start = PAD_SIDE, end = PAD_SIDE, bottom = BROWSE_GAP))
                                pad(state, mode, DPAD_PHONE, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    is RemoteLayout.Panes -> {
                        val first = (layout.gapStart - start).coerceIn(0.dp, maxWidth)
                        val gap = (layout.gapEnd - layout.gapStart).coerceIn(0.dp, maxWidth - first)
                        val second = maxWidth - first - gap
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.width(first).fillMaxHeight()) {
                                Column(Modifier.align(Alignment.TopCenter).widthIn(max = BROWSE_MAX).fillMaxSize().padding(top = 8.dp)) {
                                    header()
                                    SearchOr(search.searching, results, back = leavingSearch) {
                                        Spacer(Modifier.size(BROWSE_GAP))
                                        Browse(state, actions, use, prefs, hold, WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues())
                                    }
                                }
                                if (sheet == null) {
                                    SnackbarHost(
                                        snackbar,
                                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
                                    )
                                }
                            }
                            Spacer(Modifier.width(gap))
                            Column(Modifier.width(second).fillMaxHeight().padding(top = 8.dp)) {
                                pad(state, mode, DPAD_WIDE, Modifier.weight(1f).fillMaxWidth())
                                keys(false, DpSize(second, window.height))
                            }
                        }
                    }
                    is RemoteLayout.Tabletop -> {
                        val upper = (layout.gapTop - top).coerceIn(0.dp, maxHeight)
                        val lower = (layout.gapBottom - top).coerceIn(upper, maxHeight)
                        val flat = DpSize(maxWidth, maxHeight - lower)
                        val sideBySide = flat.width >= TABLETOP_SIDE_BY_SIDE
                        Column(Modifier.fillMaxSize()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(upper)
                                    .consumeWindowInsets(PaddingValues(bottom = window.height - layout.gapTop))
                                    .padding(top = 8.dp),
                            ) {
                                header()
                                SearchOr(search.searching, results, back = leavingSearch) {
                                    Spacer(Modifier.size(BROWSE_GAP))
                                    Browse(state, actions, use, prefs, hold, PaddingValues(), cast = !sideBySide)
                                }
                            }
                            Spacer(Modifier.height(lower - upper))
                            if (sideBySide) {
                                Row(Modifier.fillMaxWidth().weight(1f)) {
                                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                                        keys(true, flat.copy(width = flat.width / 2))
                                    }
                                    pad(state, mode, DPAD_WIDE, Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp).navigationBarsPadding())
                                }
                            } else {
                                Column(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                                    pad(state, mode, DPAD_WIDE, Modifier.weight(1f).fillMaxWidth())
                                    keys(false, flat)
                                }
                            }
                        }
                    }
                }
            }
        }

        val scrim = MaterialTheme.colorScheme.scrimAlpha
        val grownMore = growsMore && sheet == Sheet.MORE
        val deckShape = DeckShape
        Box(
            Modifier.fillMaxSize()
                .drawWithCache {
                    val band = CornerExtraLarge.toPx() * 2
                    val corners = Path().apply {
                        addRect(Rect(0f, 0f, size.width, band))
                        op(this, Path().apply { addOutline(deckShape.createOutline(Size(size.width, band * 2), layoutDirection, this@drawWithCache)) }, PathOperation.Difference)
                    }
                    onDrawBehind {
                        val shown = sheetShown.floatValue
                        if (shown <= 0f) return@onDrawBehind
                        val top = moreGrow.grownTopPx()
                        if (grownMore && !top.isNaN()) {
                            val edge = top.coerceIn(0f, size.height)
                            drawRect(Color.Black, size = Size(size.width, edge), alpha = scrim * shown)
                            translate(0f, edge) { drawPath(corners, Color.Black, alpha = scrim * shown) }
                        } else {
                            drawRect(Color.Black, size = size, alpha = scrim * shown)
                        }
                    }
                },
        )
        if (growsMore) GrownMoreOutside(more, moreGrow)

        if (sheet == Sheet.KEYBOARD) {
            KeyboardPanel(
                keyboard = state.keyboard ?: TvKeyboard(0).takeIf { Feature.TEXT_INPUT in state.features && Feature.KEYBOARD_STATE !in state.features },
                enabled = state.isUsable,
                onType = actions::type,
                onEnter = actions::enter,
                leave = keyboardAutoOpened && !tvWantsText,
                onClosed = closeSheet,
                snackbar = snackbar,
            )
        }

        FirstRun(prefs, state, actions)

        if (search.searching && !prefs.askedFindTitles) FindTitlesAsk { on -> actions.updatePrefs { it.answerFindTitles(on) } }

        if (!growsMore) {
            MoreSheetOverlay(
                moreDrag = more.drag,
                full = more.full,
                shown = sheetShown,
                window = window,
                spot = sheetSpot(Sheet.MORE, layout, window),
                onClose = { more.hide() },
                content = moreGrow.content,
            )
        }
    }

    sheet?.takeIf { it != Sheet.KEYBOARD && it != Sheet.MORE }?.let { current ->
        RemoteSheet(
            current, state, prefs, actions, snackbar, onKey, lastBadLink, onClose = closeSheet, onSheet = { sheet = it }, shown = sheetShown,
            spot = sheetSpot(current, layout, window),
            settingsOnScreen = !layout.rollsDeck,
        )
    }
}

private val GROWN_MORE_PADDING = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
