package lt.tsx.tennarc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.ui.theme.ICON_CORNER
import lt.tsx.tennarc.ui.theme.IconShape
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.Omnibox
import lt.tsx.tennarc.icons.PosterLoader
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.link.Suggestion
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.isPhoneFile
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.link.service
import lt.tsx.tennarc.icons.LocalPosterLoader
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvCommands
import lt.tsx.tennarc.ui.sheets.displayUrl
import lt.tsx.tennarc.ui.sheets.icon
import lt.tsx.tennarc.ui.sheets.kindIcon
import lt.tsx.tennarc.ui.sheets.label
import lt.tsx.tennarc.ui.theme.CornerSmall
import lt.tsx.tennarc.ui.theme.SectionLabel
import lt.tsx.tennarc.ui.theme.PillShape
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.ui.theme.addSquircle
import lt.tsx.tennarc.ui.theme.clearGlass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.sectionCase
import lt.tsx.tennarc.ui.theme.seeThrough

private const val TITLE_LOOKUP_DELAY_MS = 200L
private val PICK_WIDTH = 96.dp
private val LEADING = 56.dp

private const val SUGGESTION = "suggestion"
private const val RECENT = "recent"

suspend fun TvCommands.run(suggestion: Suggestion): Boolean = when (suggestion) {
    is Suggestion.TypeOnTv -> {
        type(TextEdit(delete = suggestion.replace, insert = suggestion.text))
        enter()
        true
    }
    is Suggestion.OpenLink -> open(suggestion.target, suggestion.action)
    is Suggestion.OpenTitle -> suggestion.app?.let { app ->
        launch(app.id)
        true
    } ?: open(suggestion.target, LinkAction.APP)
    is Suggestion.LaunchApp -> {
        launch(suggestion.app.id)
        true
    }
    is Suggestion.SearchApp -> search(suggestion.query, SearchTarget.InApp(suggestion.app.id))
    is Suggestion.SearchWeb -> search(suggestion.query, SearchTarget.Web(suggestion.engine))
}

fun Suggestion.recentEntry(): String? = when (this) {
    is Suggestion.OpenLink -> target.url.takeUnless { target.isPhoneFile }
    is Suggestion.SearchApp -> query
    is Suggestion.SearchWeb -> query
    is Suggestion.OpenTitle -> title.title
    is Suggestion.TypeOnTv, is Suggestion.LaunchApp -> null
}

@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    active: Boolean,
    onActive: (Boolean) -> Unit,
    onGo: () -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    shown: () -> Float = { if (active) 1f else 0f },
    edgeTravel: Density.() -> Float = { 0f },
    snackbar: SnackbarHostState? = null,
    voice: VoiceSearch = rememberVoiceSearch(onHeard = onQuery, snackbar = snackbar),
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val activate by rememberUpdatedState(onActive)
    LaunchedEffect(active) {
        if (active) catching { focus.requestFocus() } else focusManager.clearFocus()
    }
    LaunchedEffect(active, query.isEmpty(), voice.listening) {
        if (voice.listening && (!active || query.isNotEmpty())) voice.cancel()
    }
    val clip = rememberClipPeek()
    var pasted by rememberSpentClip()
    val shape = PillShape
    val fieldGlass = clearGlass(colors.surfaceContainer)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) activate(true) },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .glass(fieldGlass, shape)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.primary, modifier = Modifier.graphicsLayer { alpha = 1f - shown() })
                    if (active) {
                        IconButton(onClick = { onActive(false) }, modifier = Modifier.graphicsLayer { alpha = shown() }, shape = IconShape) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.search_close))
                        }
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.graphicsLayer { alpha = shown() })
                    }
                }
                Box(Modifier.weight(1f).padding(start = 2.dp), contentAlignment = Alignment.CenterStart) {
                    val typed = query.isNotEmpty()
                    Crossfade(
                        voice.listening,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (typed) 1f - shown() else 1f }
                            .then(if (typed) Modifier.clearAndSetSemantics {} else Modifier),
                        label = "searchHint",
                    ) { hearing ->
                        Text(
                            if (hearing) voice.heard.ifEmpty { stringResource(R.string.voice_listening) } else hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = if (hearing) TextOverflow.StartEllipsis else TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.graphicsLayer { alpha = if (typed) shown() else 1f }) { field() }
                }
                val live = trailingFor(query, voice.listening, mic = voice.available, paste = clip.offersPaste(pasted))
                var leaving by remember { mutableStateOf(live) }
                val shut by remember { derivedStateOf { shown() == 0f } }
                val current = active || shut
                SideEffect { if (current) leaving = live }
                AnimatedContent(
                    if (current) live else leaving,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 2 }) togetherWith (fadeOut() + slideOutHorizontally { it / 2 })
                    },
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val buttons = measurable.measure(constraints.copy(minWidth = 0))
                            val room = (buttons.width * shown()).roundToInt()
                            layout(room, buttons.height) { buttons.placeRelative(room - buttons.width, 0) }
                        }
                        .graphicsLayer {
                            val into = shown()
                            translationX = (1f - into) * edgeTravel()
                            alpha = if (into > 0f) 1f else 0f
                        },
                    label = "searchTrailing",
                ) { trailing ->
                    when {
                        trailing == Trailing.CLEAR -> IconButton(onClick = {
                            onQuery("")
                            catching { focus.requestFocus() }
                            keyboard?.show()
                        }, shape = IconShape) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                        active -> Row {
                            if (trailing != Trailing.MIC) {
                                IconButton(onClick = {
                                    pasted = clip?.stamp
                                    scope.launch {
                                        clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { onQuery(it.toString()) }
                                    }
                                }, shape = IconShape) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.link_paste))
                                }
                            }
                            if (trailing != Trailing.PASTE) MicButton(voice)
                        }
                        else -> Row {
                            if (trailing != Trailing.MIC) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                                }
                            }
                            if (trailing != Trailing.PASTE) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(Lucide.Mic, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResults(
    query: String,
    apps: List<TvApp>,
    tvText: Int?,
    engine: SearchEngine?,
    recent: List<String>,
    findTitles: (suspend (String) -> List<StreamingTitle>)?,
    goes: Int,
    onRun: suspend (Suggestion) -> Boolean,
    onFill: (String) -> Unit,
    onClearRecent: () -> Unit,
    onEngine: (SearchEngine) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    opens: (LinkTarget) -> List<LinkAction> = { it.possibleActions },
    findPicks: (suspend () -> List<StreamingTitle>)? = null,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val typed = query.trim()

    val lookups = remember { mutableStateMapOf<String, List<StreamingTitle>>() }
    val asked = remember { HashSet<String>() }
    val key = typed.lowercase().replace(Regex("\\s+"), " ")
    val lookupFind by rememberUpdatedState(findTitles)
    LaunchedEffect(key, findTitles != null) {
        if (findTitles == null || key.length < 2 || "://" in key || key in asked) return@LaunchedEffect
        delay(TITLE_LOOKUP_DELAY_MS)
        asked += key
        scope.launch {
            val found = try {
                lookupFind?.invoke(typed) ?: return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("Search.SearchResults", e)
                asked -= key
                return@launch
            }
            lookups[key] = found
        }
    }
    fun standIn(videos: Boolean): List<StreamingTitle> {
        if (findTitles == null || key.length < 2) return emptyList()
        lookups[key]?.filter { it.video == videos }?.takeIf { it.isNotEmpty() }?.let { return it }
        return generateSequence(key.dropLast(1)) { it.dropLast(1).takeIf { shorter -> shorter.length >= 2 } }
            .firstNotNullOfOrNull { earlier ->
                lookups[earlier]?.filter { it.video == videos && (videos || Omnibox.titleMatches(it.title, key)) }?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
    }
    val current = standIn(videos = false) + standIn(videos = true)
    val suggestions = remember(typed, apps, tvText, engine, current, opens) { Omnibox.suggest(typed, apps, tvText, engine, current, opens) }

    var picks by remember { mutableStateOf<List<Suggestion.OpenTitle>>(emptyList()) }
    LaunchedEffect(findPicks != null) {
        val found = try {
            findPicks?.invoke().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("Search.standIn", e)
            emptyList()
        }
        picks = found.mapNotNull { title -> title.offers.firstOrNull { LinkAction.APP in opens(it) }?.let { Suggestion.OpenTitle(title, it) } }
    }

    var busy by remember { mutableStateOf<Suggestion?>(null) }
    var done by remember { mutableStateOf<Suggestion?>(null) }
    var failed by remember(typed) { mutableStateOf<Suggestion?>(null) }

    fun run(suggestion: Suggestion) {
        if (busy != null || done != null) return
        scope.launch {
            busy = suggestion
            failed = null
            val ok = onRun(suggestion)
            busy = null
            if (ok) {
                done = suggestion
                haptics.confirm()
                if (suggestion !is Suggestion.LaunchApp && suggestion !is Suggestion.TypeOnTv) delay(450)
                onDone()
            } else {
                failed = suggestion
                haptics.reject()
            }
        }
    }
    val latest by rememberUpdatedState(suggestions)
    LaunchedEffect(goes) { if (goes > 0) latest.firstOrNull()?.let(::run) }

    val list = rememberLazyListState()
    val firstKey = suggestions.firstOrNull()?.key
    val shownFirst = remember { arrayOfNulls<Any>(1) }
    if (firstKey != shownFirst[0]) {
        shownFirst[0] = firstKey
        val atTop = Snapshot.withoutReadObservation { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
        if (atTop) list.requestScrollToItem(0)
    }
    LazyColumn(modifier.fillMaxWidth().fadeEnds(list).imeNestedScroll(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
        if (suggestions.isNotEmpty()) {
            itemsIndexed(suggestions, key = { _, suggestion -> suggestion.key }, contentType = { _, _ -> SUGGESTION }) { index, suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    status = when (suggestion) {
                        busy -> RowStatus.BUSY
                        done -> RowStatus.DONE
                        failed -> RowStatus.FAILED
                        else -> if (index == 0) RowStatus.DEFAULT else RowStatus.IDLE
                    },
                    onClick = { run(suggestion) },
                    onEngine = onEngine,
                    modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                )
            }
        }
        if (typed.isEmpty() && picks.isNotEmpty()) {
            item(key = "picks", contentType = "picks") {
                Column(Modifier.animateItem()) {
                    Text(
                        sectionCase(stringResource(R.string.search_picks)),
                        style = SectionLabel,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                        items(picks, key = { it.key }) { pick ->
                            fun of(suggestion: Suggestion?) = (suggestion as? Suggestion.OpenTitle)?.title == pick.title
                            Pick(
                                pick,
                                offers = remember(pick) { pick.title.offers.filter { LinkAction.APP in opens(it) }.map { Suggestion.OpenTitle(pick.title, it) } }
                                    .ifEmpty { listOf(pick) },
                                appFor = { target -> target.service?.let { service -> apps.firstOrNull { it.id in service.appIds } } },
                                status = when {
                                    of(busy) -> RowStatus.BUSY
                                    of(done) -> RowStatus.DONE
                                    of(failed) -> RowStatus.FAILED
                                    else -> RowStatus.IDLE
                                },
                                onRun = ::run,
                            )
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
        if (typed.isEmpty() && recent.isNotEmpty()) {
            item(key = "recent", contentType = "recentHeading") {
                Row(Modifier.animateItem().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sectionCase(stringResource(R.string.link_recent)), style = SectionLabel, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.link_clear_recent), onClearRecent, Modifier.overhang())
                }
            }
            items(recent, key = { "recent:$it" }, contentType = { RECENT }) { entry -> RecentRow(entry, onClick = { onFill(entry) }, modifier = Modifier.animateItem().padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun Pick(
    pick: Suggestion.OpenTitle,
    offers: List<Suggestion.OpenTitle>,
    appFor: (LinkTarget) -> TvApp?,
    status: RowStatus,
    onRun: (Suggestion) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = pick.title
    val services = offers.mapNotNull { it.target.service?.label }
    val description = listOfNotNull(title.title, services.joinToString(", ").ifEmpty { null }, title.year?.toString()).joinToString(", ")
    var choosing by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) PICK_SINK else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh), label = "pickSink")
    Column(
        Modifier
            .width(PICK_WIDTH)
            .graphicsLayer {
                scaleX = sink.value
                scaleY = sink.value
            }
            .clickable(press, indication = null, role = Role.Button) { if (offers.size > 1) choosing = true else onRun(offers.first()) }
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Thumbnail(title.posterUrl, width = PICK_WIDTH, height = PICK_WIDTH * 1.5f, fallback = Icons.Rounded.Movie, corner = 12.dp)
            Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                offers.take(MAX_PICK_LOGOS).forEach { offer ->
                    ServiceFace(offer.target, appFor(offer.target), Modifier.border(1.5.dp, colors.background, SquircleShape(PICK_LOGO * ICON_CORNER + 1.5.dp)).padding(1.5.dp))
                }
            }
            when (status) {
                RowStatus.BUSY -> CircularProgressIndicator(Modifier.size(28.dp), color = colors.primary, strokeWidth = 3.dp)
                RowStatus.DONE -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
                RowStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(32.dp))
                RowStatus.IDLE, RowStatus.DEFAULT -> Unit
            }
            ChoiceMenu(expanded = choosing, onDismiss = { choosing = false }) {
                offers.forEach { offer ->
                    ChoiceMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ServiceFace(offer.target, appFor(offer.target), size = 24.dp)
                                Text(offer.target.service?.label ?: title.title)
                            }
                        },
                        glyph = null,
                        selected = false,
                        onClick = {
                            choosing = false
                            onRun(offer)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceFace(target: LinkTarget, app: TvApp?, modifier: Modifier = Modifier, size: Dp = PICK_LOGO) {
    val label = target.service?.label ?: "?"
    AppFace(app ?: TvApp("service:$label", label), modifier, size = size)
}

private const val MAX_PICK_LOGOS = 3
private const val PICK_SINK = 0.96f
private val PICK_LOGO = 20.dp

private enum class RowStatus { IDLE, DEFAULT, BUSY, DONE, FAILED }

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    status: RowStatus,
    onClick: () -> Unit,
    onEngine: (SearchEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title: String
    var subtitle: String? = null
    when (suggestion) {
        is Suggestion.TypeOnTv -> title = stringResource(R.string.search_type_on_tv)
        is Suggestion.OpenLink -> title = if (suggestion.target.isPhoneFile) stringResource(R.string.phone_pick) else suggestion.action.label(suggestion.target)
        is Suggestion.OpenTitle -> {
            val found = suggestion.title
            title = found.title
            val service = suggestion.target.service?.label?.let { if (suggestion.app != null) stringResource(R.string.search_opens_app, it) else it }
            subtitle = listOfNotNull(service, found.byline ?: found.year?.toString()).joinToString(" · ")
        }
        is Suggestion.LaunchApp -> title = suggestion.app.title
        is Suggestion.SearchApp -> title = stringResource(R.string.search_in_app, suggestion.app.title)
        is Suggestion.SearchWeb -> title = stringResource(R.string.search_web, suggestion.engine.label)
    }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (status == RowStatus.DEFAULT) Modifier.glass(panelGlass(), shape) else Modifier)
            .clickable(interactionSource = null, indication = rememberPressWash(CornerSmall), role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(LEADING), contentAlignment = Alignment.Center) {
            when (suggestion) {
                is Suggestion.LaunchApp -> AppFace(suggestion.app, size = 40.dp)
                is Suggestion.SearchApp -> AppFace(suggestion.app, size = 40.dp)
                is Suggestion.TypeOnTv -> RoundIcon(Icons.Rounded.Keyboard)
                is Suggestion.SearchWeb -> RoundIcon(Icons.Rounded.Search)
                is Suggestion.OpenTitle -> if (suggestion.title.video) {
                    Thumbnail(suggestion.title.posterUrl, width = LEADING, height = 32.dp, fallback = Icons.Rounded.SmartDisplay)
                } else {
                    Thumbnail(suggestion.title.posterUrl, width = 36.dp, height = 54.dp, fallback = Icons.Rounded.Movie)
                }
                is Suggestion.OpenLink -> {
                    val video = suggestion.target as? LinkTarget.YouTube
                    if (video != null && suggestion.action == LinkAction.APP) {
                        Thumbnail(PosterLoader.youTubeStill(video.videoId), width = LEADING, height = 32.dp, fallback = suggestion.action.icon(video))
                    } else {
                        RoundIcon(if (suggestion.target.isPhoneFile) Icons.Rounded.PhotoLibrary else suggestion.action.icon(suggestion.target))
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when (status) {
                RowStatus.BUSY -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                RowStatus.DONE -> Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.link_sent), tint = MaterialTheme.colorScheme.tertiary)
                RowStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, contentDescription = stringResource(R.string.link_failed), tint = colors.error)
                RowStatus.DEFAULT, RowStatus.IDLE -> when {
                    suggestion is Suggestion.SearchWeb -> EnginePicker(suggestion.engine, onEngine)
                    status == RowStatus.DEFAULT -> Icon(
                        Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnginePicker(engine: SearchEngine, onPick: (SearchEngine) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER, inset = 4.dp, insetY = 4.dp), role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.settings_search_engine), tint = colors.onSurfaceVariant)
        }
        ChoiceMenu(expanded = open, onDismiss = { open = false }) {
            SearchEngine.entries.forEach { entry ->
                ChoiceMenuItem(entry.label, entry.mark, selected = entry == engine) {
                    onPick(entry)
                    open = false
                }
            }
        }
    }
}

@Composable
private fun RecentRow(entry: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val link = remember(entry) { LinkRouter.extract(entry)?.takeIf { "://" in entry }?.let(LinkRouter::classify) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = null,
                indication = rememberPressWash(CornerSmall),
                role = Role.Button,
                onClickLabel = stringResource(R.string.search_fill),
                onClick = onClick,
            )
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(LEADING), contentAlignment = Alignment.Center) {
            RoundIcon(link?.kindIcon ?: Icons.Rounded.History)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            if (link != null) displayUrl(entry) else entry,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.NorthWest, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.overhang(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
}

@Composable
private fun RoundIcon(icon: ImageVector) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Thumbnail(url: String?, width: Dp, height: Dp, fallback: ImageVector, corner: Dp = 8.dp) {
    val colors = MaterialTheme.colorScheme
    val loader = LocalPosterLoader.current
    val image by produceState(url?.let(loader::cached), url, loader) { value = url?.let { loader.load(it) } }
    val shape = SquircleShape(corner)
    val edge = colors.onSurface.copy(alpha = 0.08f)
    Box(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(seeThrough(colors.surfaceContainerHigh))
            .drawWithCache {
                val stroke = 1.dp.toPx()
                val line = Path().apply {
                    addSquircle(Rect(stroke / 2, stroke / 2, size.width - stroke / 2, size.height - stroke / 2), corner.toPx() - stroke / 2)
                }
                val style = Stroke(stroke)
                onDrawWithContent {
                    drawContent()
                    drawPath(line, edge, style = style)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(fallback, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
