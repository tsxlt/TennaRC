package lt.tsx.tennarc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.offset
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import lt.tsx.tennarc.SleepTimer
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.DECK_HANDLE_TAG
import lt.tsx.tennarc.ui.components.DECK_SPRING
import lt.tsx.tennarc.ui.components.LocalSheetSettled
import lt.tsx.tennarc.ui.components.NoInsets
import lt.tsx.tennarc.ui.components.SheetInsets
import lt.tsx.tennarc.ui.components.mark
import lt.tsx.tennarc.ui.components.rememberSheetKeyboardPull
import lt.tsx.tennarc.ui.components.sheetKeyboardPull
import lt.tsx.tennarc.ui.onboarding.TvSetup
import lt.tsx.tennarc.ui.sheets.DevLinkSheetContent
import lt.tsx.tennarc.ui.sheets.LanguageSheetContent
import lt.tsx.tennarc.ui.sheets.LinkHelpSheetContent
import lt.tsx.tennarc.ui.sheets.PowerMode
import lt.tsx.tennarc.ui.sheets.PowerSheetContent
import lt.tsx.tennarc.ui.sheets.SettingsSheetContent
import lt.tsx.tennarc.ui.sheets.SleepTimerControls
import lt.tsx.tennarc.ui.theme.LocalGround
import lt.tsx.tennarc.ui.theme.glass

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RemoteSheet(
    sheet: Sheet,
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    snackbar: SnackbarHostState,
    onKey: (android.view.KeyEvent) -> Boolean,
    link: LinkReport?,
    onClose: () -> Unit,
    onSheet: (Sheet) -> Unit,
    shown: MutableFloatState,
    spot: SheetSpot? = null,
    settingsOnScreen: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DisposableEffect(Unit) { onDispose { shown.floatValue = 0f } }
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onClose() }
    }
    var trail by rememberSaveable { mutableStateOf(emptyList<Sheet>()) }
    val left = rememberSaveableStateHolder()
    val open: (Sheet) -> Unit = { next ->
        trail = trail + sheet
        onSheet(next)
    }
    val back: () -> Unit = {
        val to = trail.lastOrNull()
        if (to == null) {
            hide()
        } else {
            trail = trail.dropLast(1)
            left.removeState(sheet)
            onSheet(to)
        }
    }
    val settled = remember { derivedStateOf { sheetState.currentValue == SheetValue.Expanded } }
    val glass = glass()
    val shape = BottomSheetDefaults.ExpandedShape
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = if (spot != null) Modifier.offset(x = spot.shift) else Modifier,
        sheetState = sheetState,
        sheetMaxWidth = spot?.width ?: BottomSheetDefaults.SheetMaxWidth,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Transparent,
        contentWindowInsets = { NoInsets },
        dragHandle = null,
    ) {
        BackHandler(enabled = trail.isNotEmpty(), onBack = back)
        val density = LocalDensity.current
        val window = LocalWindowInfo.current
        var height by remember { mutableIntStateOf(0) }
        val sheetInsets = SheetInsets
        val keyboardPull = rememberSheetKeyboardPull(sheetInsets, onAway = onClose)
        val inset = keyboardPull.insets(sheetInsets).getBottom(density)
        LaunchedEffect(sheetState) {
            snapshotFlow {
                val full = window.containerSize.height.toFloat()
                val placed = try {
                    sheetState.requireOffset()
                } catch (_: IllegalStateException) {
                    full
                }
                val offset = placed + keyboardPull.pulled
                val tall = min(full, height.toFloat())
                if (tall <= 0f) 0f else ((full - offset) / tall).coerceIn(0f, 1f)
            }.collect { shown.floatValue = it }
        }
        Column(
            Modifier
                .onSizeChanged { height = it.height }
                .sheetKeyboardPull(keyboardPull)
                .glass(glass, shape)
                .windowInsetsPadding(keyboardPull.insets(sheetInsets)),
        ) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            CompositionLocalProvider(LocalSheetSettled provides settled, LocalGround provides glass.fill) {
                val maxHeight = with(density) {
                    spot?.height?.let { it - SHEET_HANDLE - inset.toDp() } ?: (window.containerSize.height * 0.86f).toDp()
                }
                val keys = remember { FocusRequester() }
                LaunchedEffect(Unit) { catching { keys.requestFocus() } }
                Box(
                    Modifier
                        .heightIn(max = maxHeight)
                        .nestedScroll(KeepOverscroll)
                        .focusRequester(keys)
                        .onPreviewKeyEvent { onKey(it.nativeKeyEvent) }
                        .onFocusChanged { if (!it.hasFocus) catching { keys.requestFocus() } }
                        .focusTarget(),
                ) {
                    AnimatedContent(
                        targetState = sheet,
                        transitionSpec = {
                            (
                                fadeIn(tween(SWAP_IN_MS, delayMillis = SWAP_LAG_MS, easing = LinearOutSlowInEasing)) +
                                    scaleIn(tween(SWAP_IN_MS, delayMillis = SWAP_LAG_MS, easing = FastOutSlowInEasing), initialScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f))
                                )
                                .togetherWith(fadeOut(tween(SWAP_OUT_MS, easing = FastOutLinearInEasing)))
                                .using(SizeTransform(clip = true) { _, _ -> tween(SWAP_SIZE_MS, easing = FastOutSlowInEasing) })
                        },
                        contentAlignment = Alignment.TopCenter,
                        label = "sheetBody",
                    ) { body ->
                        left.SaveableStateProvider(body) {
                            Column { SheetBody(body, state, prefs, actions, link, hide, back, open, settingsOnScreen) }
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreSheetOverlay(
    moreDrag: AnchoredDraggableState<MoreSheetValue>,
    full: MutableFloatState,
    shown: MutableFloatState,
    window: DpSize,
    spot: SheetSpot?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val inset = SheetInsets.getBottom(density)
    val fullyOpen by remember { derivedStateOf { moreDrag.settledValue == MoreSheetValue.Shown } }
    Box(Modifier.fillMaxSize()) {
        if (fullyOpen) {
            Box(Modifier.fillMaxSize().pointerInput(onClose) { detectTapGestures { onClose() } })
        }
        val maxHeight = with(density) {
            spot?.height?.let { it - SHEET_HANDLE - inset.toDp() } ?: (window.height * 0.86f)
        }
        val shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        val glass = glass()
        val flingPx = with(density) { MORE_FLING_SPEED.toPx() }
        val grabScope = rememberCoroutineScope()
        val sheetScroll = remember(moreDrag, full, flingPx) {
            MoreSheetScroll(moreDrag, full, flingPx, hold = { grabScope.launch(start = CoroutineStart.UNDISPATCHED) { moreDrag.anchoredDrag(MutatePriority.UserInput) {} } })
        }
        val handleDrag = Modifier.anchoredDraggable(
            state = moreDrag,
            orientation = Orientation.Vertical,
            enabled = full.floatValue > 0f,
            reverseDirection = true,
            flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                moreDrag,
                positionalThreshold = { it * MORE_OPEN_FRACTION },
                animationSpec = DECK_SPRING,
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .then(if (spot != null) Modifier.width(spot.width).offset(x = spot.shift) else Modifier.fillMaxWidth())
                .graphicsLayer {
                    val offset = moreDrag.offset.takeUnless { it.isNaN() } ?: 0f
                    translationY = (full.floatValue - offset).coerceAtLeast(0f)
                    alpha = if (offset > 0.5f) 1f else 0f
                }
                .heightIn(max = maxHeight)
                .clip(shape)
                .glass(glass, shape)
                .windowInsetsPadding(SheetInsets)
                .then(if (fullyOpen) Modifier.semantics { dismiss(null) { onClose(); true } } else Modifier)
                .onSizeChanged { size ->
                    full.floatValue = size.height.toFloat()
                    moreDrag.updateAnchors(
                        DraggableAnchors {
                            MoreSheetValue.Hidden at 0f
                            MoreSheetValue.Shown at size.height.toFloat()
                        },
                    )
                },
        ) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally).testTag(DECK_HANDLE_TAG).then(handleDrag))
            Box(Modifier.nestedScroll(sheetScroll)) { CompositionLocalProvider(LocalGround provides glass.fill) { content() } }
        }
    }
}

internal class MoreSheetScroll(
    private val state: AnchoredDraggableState<MoreSheetValue>,
    private val full: MutableFloatState,
    private val flingPx: Float,
    private val hold: () -> Unit,
    private val pull: (Float) -> Float = { state.dispatchRawDelta(it) },
    private val letGo: ((Float) -> Unit)? = null,
) : NestedScrollConnection {
    private val offset get() = state.offset.takeUnless { it.isNaN() } ?: 0f

    private var holding = false

    private fun take(up: Float): Offset {
        if (!holding) {
            holding = true
            hold()
        }
        return Offset(0f, -pull(up))
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || offset >= full.floatValue) return Offset.Zero
        return take(-available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        return take(-available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val held = holding
        holding = false
        val all = full.floatValue
        if (!held || all <= 0f) return Velocity.Zero
        val release = letGo
        if (release != null) {
            release(-available.y)
            return available
        }
        if (offset >= all - 0.5f) return Velocity.Zero
        val down = available.y
        val target = when {
            down >= flingPx -> MoreSheetValue.Hidden
            down <= -flingPx -> MoreSheetValue.Shown
            offset < all * (1f - MORE_OPEN_FRACTION) -> MoreSheetValue.Hidden
            else -> MoreSheetValue.Shown
        }
        state.animateTo(target, DECK_SPRING)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

internal class MoreHold {
    private var start = 0f
    private var mark = 0f
    private var past = false

    fun take(offset: Float, full: Float, shutPx: Float) {
        start = offset
        mark = if (offset > full / 2) full - min(full * MORE_OPEN_FRACTION, shutPx) else full * MORE_OPEN_FRACTION
        past = offset >= mark
    }

    fun moved(offset: Float): Boolean {
        val now = offset >= mark
        val crossed = now != past
        past = now
        return crossed
    }

    fun opens(offset: Float, velocity: Float, flingPx: Float, flickMinPx: Float): Boolean = when {
        velocity >= flingPx && offset - start >= flickMinPx -> true
        velocity <= -flingPx && start - offset >= flickMinPx -> false
        else -> offset >= mark
    }

    fun flicked(opens: Boolean) = opens != past

    fun letGo() {
        past = false
    }
}

@Composable
private fun SheetBody(
    sheet: Sheet,
    state: TvState,
    prefs: Prefs,
    actions: RemoteActions,
    link: LinkReport?,
    hide: () -> Unit,
    back: () -> Unit,
    onSheet: (Sheet) -> Unit,
    settingsOnScreen: Boolean = false,
) {
    when (sheet) {
        Sheet.SEARCH, Sheet.KEYBOARD, Sheet.MORE -> Unit
        Sheet.POWER -> PowerSheetContent(
            mode = when {
                !state.isConnected -> PowerMode.OFF
                else -> when (state.power) {
                    TvPower.ON -> PowerMode.ON
                    TvPower.SCREEN_OFF -> PowerMode.SCREEN_OFF
                    TvPower.STANDBY -> PowerMode.OFF
                    null -> PowerMode.UNKNOWN
                }
            },
            canWake = Feature.WAKE in state.features && prefs.mac.isNotBlank(),
            onScreenOff = { off: Boolean -> if (off) actions.screenOff() else actions.screenOn() }.takeIf { Feature.SCREEN_OFF in state.features },
            onPowerOff = actions::powerOff,
            onWake = actions::wake,
            onDone = hide,
            sleep = {
                val context = LocalContext.current
                SleepTimerControls(prefs.sleepAt, prefs.sleepMinutes, onStart = { SleepTimer.start(context, it) }, onCancel = { SleepTimer.cancel(context) })
            },
        )
        Sheet.LINK_HELP -> if (link != null) LinkHelpSheetContent(link)
        Sheet.SETTINGS -> SettingsSheetContent(
            prefs = prefs,
            onUpdate = actions::updatePrefs,
            onFindTvs = actions::findTvs,
            onForgetPairing = { actions.forgetPairing(); hide() },
            onAddTv = { onSheet(Sheet.ADD_TV) },
            devLink = state.devLink.takeIf { !prefs.demoTv },
            onDevLink = { onSheet(Sheet.DEV_LINK) },
            onLanguage = { onSheet(Sheet.LANGUAGE) },
        )
        Sheet.LANGUAGE -> LanguageSheetContent(onPicked = back)
        Sheet.DEV_LINK -> DevLinkSheetContent(
            link = state.devLink ?: DevLink.Off,
            onConnect = actions::setUpDevLink,
            onTurnOff = actions::turnOffDevLink,
        )
        Sheet.ADD_TV -> TvSetup(state, prefs, actions, onDone = hide, contentPadding = PaddingValues(bottom = 24.dp))
    }
}

private val KeepOverscroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0) Velocity(0f, available.y) else Velocity.Zero
}

private const val MORE_OPEN_FRACTION = 1f / 3f

internal val MORE_SHUT_PULL = 96.dp

internal val MORE_FLING_SPEED = 700.dp

internal val MORE_FLICK_PULL = 16.dp

private const val SWAP_OUT_MS = 120

private const val SWAP_LAG_MS = 110

private const val SWAP_IN_MS = 260

private const val SWAP_SIZE_MS = 380
