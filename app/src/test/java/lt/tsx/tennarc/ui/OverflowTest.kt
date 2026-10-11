package lt.tsx.tennarc.ui

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.ThemeMode
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SearchField
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.onboarding.FakeHub
import lt.tsx.tennarc.ui.onboarding.FirstRun
import lt.tsx.tennarc.ui.onboarding.TvSetup
import lt.tsx.tennarc.ui.onboarding.fireTv
import lt.tsx.tennarc.ui.onboarding.lgTv
import lt.tsx.tennarc.ui.onboarding.philipsTv
import lt.tsx.tennarc.ui.onboarding.rokuTv
import lt.tsx.tennarc.ui.onboarding.samsungTv
import lt.tsx.tennarc.ui.sheets.DevLinkSheetContent
import lt.tsx.tennarc.ui.sheets.LanguageSheetContent
import lt.tsx.tennarc.ui.sheets.LinkHelpSheetContent
import lt.tsx.tennarc.ui.sheets.PowerMode
import lt.tsx.tennarc.ui.sheets.PowerSheetContent
import lt.tsx.tennarc.ui.sheets.SettingsSheetContent
import lt.tsx.tennarc.ui.sheets.ShareSheetContent
import lt.tsx.tennarc.ui.sheets.SleepTimerControls
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class OverflowTest(private val folder: String) {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(object : ExternalResource() { override fun before() = RuntimeEnvironment.setQualifiers("+" + qualifier(folder)) })
        .around(compose)

    private val locale = Locale.forLanguageTag(languageTag(folder))

    private class Shot(val name: String, val wifi: Boolean = true, val settle: Long = 1_000, val act: () -> Unit = {}, val content: @Composable () -> Unit)

    private var shown by mutableStateOf<Shot?>(null)
    private lateinit var res: Resources

    @Test fun texts() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            res = LocalContext.current.resources
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        shown?.let { shot -> key(shot.name) { shot.content() } }
                    }
                }
            }
        }
        after(16)
        val strings = StringIndex(RES, folder)
        val found = linkedMapOf<Pair<String, Overflow>, Finding>()
        for (shot in shots()) {
            wifi(shot.wifi)
            shown = shot
            after(shot.settle)
            shot.act()
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            for ((text, overflow) in overflowing(nodes, locale)) {
                val (kind, detail) = overflow
                val names = strings.namesOf(text).joinToString("|").ifEmpty { "?" }
                found.getOrPut(text to kind) { Finding(folder, shot.name, names, text, kind, detail) }
            }
            shown = null
            after(16)
        }
        val allowed = found.values.associateWith { finding -> allowed().firstOrNull { it.matches(finding) }?.why }
        val failing = allowed.filterValues { it == null }.keys
        writeReport(File("build/overflow"), folder, allowed)
        val summary = buildString {
            append("${languageTag(folder)} ($folder): ${failing.size} overflowing, ${allowed.size - failing.size} let off\n")
            failing.forEach { append("  ${it.screen}: ${it.names} \"${it.text}\" (${it.kind.note}; ${it.detail})\n") }
        }
        println(summary)
        assertTrue(summary, failing.isEmpty())
    }

    private fun after(ms: Long) {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(ms)
    }

    private fun string(id: Int, vararg args: Any): String = res.getString(id, *args)

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction = compose.onAllNodes(matcher, useUnmergedTree = true)[0]

    private fun tap(matcher: SemanticsMatcher) {
        val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        val target = compose.onAllNodes(clickable.and(matcher.or(hasAnyDescendant(matcher))), useUnmergedTree = true).fetchSemanticsNodes().lastOrNull()
            ?: throw AssertionError("Nothing to tap: ${matcher.description} in ${shown?.name}\n" + compose.onAllNodes(isRoot()).fetchSemanticsNodes().joinToString("\n") { compose.onAllNodes(isRoot())[0].printToString() })
        node(SemanticsMatcher("node ${target.id}") { it.id == target.id }).performSemanticsAction(SemanticsActions.OnClick)
        after(600)
    }

    private fun hold(matcher: SemanticsMatcher) {
        node(matcher).performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        after(600)
    }

    private fun described(id: Int, vararg args: Any) = hasContentDescription(string(id, *args))

    private fun texted(id: Int) = hasText(string(id))

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun wifi(on: Boolean) {
        val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (on) shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
    }

    private val apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO, TvApps.WEB_VIDEO_CASTER)
        .map { TvApp(it, TvApps.knownTitles.getValue(it)) } +
        listOf(TvApp("com.webos.app.homeconnect", "Home Dashboard"), TvApp("com.webos.app.livetv", "Live TV"))
    private val inputs = listOf(
        TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true),
        TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true),
        TvApp("com.webos.app.hdmi3", "HDMI 3", input = true, plugged = false),
    )
    private val connected = TvState(
        host = HOST,
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 14,
        apps = apps,
        inputs = inputs,
        channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius")),
        channelId = "2",
        soundOutput = "tv_speaker",
        picture = TvPicture("normal", 64),
    )
    private val prefs = Prefs(
        tvName = TV_NAME,
        host = HOST,
        mac = "00:1a:2b:3c:4d:5e",
        autoKeyboard = false,
        otherTvs = listOf(SavedTv(OTHER_TV, "192.168.1.50", TvKind.ROKU, "")),
        demoListed = true,
        recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ"),
        hiddenApps = setOf("$HOST/${TvApps.NETFLIX}"),
        hiddenNames = mapOf("$HOST/${TvApps.NETFLIX}" to "Netflix"),
        themeMode = ThemeMode.AUTO,
    )

    private val reaching = object : RemoteActions by NoRemoteActions {
        override suspend fun isReachable(host: String, kind: TvKind) = true

        override suspend fun findTvs(): List<FoundTv> = awaitCancellation()
    }

    private fun remote(name: String, state: TvState, mode: PadMode = PadMode.DPAD, wifi: Boolean = true, sheet: Sheet? = null, act: () -> Unit = {}) =
        Shot(name, wifi = wifi, act = act) { RemoteScreen(state, prefs, reaching, remember { SnackbarHostState() }, initialMode = mode, requestedSheet = sheet) }

    private fun sheet(name: String, settle: Long = 1_000, act: () -> Unit = {}, content: @Composable () -> Unit) = Shot(name, settle = settle, act = act) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(top = SHEET_HANDLE)) {
                    Box(Modifier.heightIn(max = SHEET_MAX)) { content() }
                }
            }
        }
    }

    private fun settings(name: String, prefs: Prefs = this.prefs, found: List<FoundTv>? = null, devLink: DevLink? = null, act: () -> Unit = {}) = sheet(name, act = act) {
        SettingsSheetContent(
            prefs = prefs,
            onUpdate = {},
            onFindTvs = { awaitCancellation() },
            onForgetPairing = {},
            initialFound = found,
            devLink = devLink,
            onDevLink = {},
            onLanguage = {},
        )
    }

    private fun more(name: String, state: TvState, tab: Int? = null) = remote(
        name,
        state.copy(features = state.features + Feature.CAPTIONS + Feature.LIVE_TV),
        sheet = Sheet.MORE,
        act = { if (tab != null) tap(role(Role.Tab).and(described(tab))) },
    )

    private fun power(name: String, mode: PowerMode, timer: Boolean = false, act: () -> Unit = {}) = sheet(name, act = act) {
        PowerSheetContent(mode, canWake = true, onScreenOff = {}, onPowerOff = {}, onWake = {}, onDone = {}) {
            if (timer) SleepTimerControls(System.currentTimeMillis() + 59 * 60_000L, minutes = 60, onStart = {}, onCancel = {}) else SleepTimerControls(0, 0, onStart = {}, onCancel = {})
        }
    }

    private fun share(name: String, url: String?, state: TvState = connected, opens: Boolean = true, act: () -> Unit = {}) = sheet(name, act = act) {
        ShareSheetContent(
            target = url?.let(LinkRouter::classify),
            state = state,
            actionsFor = { it.possibleActions },
            onOpen = { _, _ -> opens },
            onOpenApp = {},
            onClose = {},
        )
    }

    private fun search(name: String, query: String, tvText: Int? = null, titles: List<StreamingTitle> = emptyList(), picks: List<StreamingTitle> = emptyList()) = Shot(name) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            SearchField(query, onQuery = {}, active = true, onActive = {}, onGo = {}, hint = string(R.string.search_bar_hint))
            SearchResults(
                query = query,
                apps = apps.map { it.copy(searchable = TvApps.isSearchable(it.id)) },
                tvText = tvText,
                engine = SearchEngine.DUCKDUCKGO,
                recent = prefs.recent,
                findTitles = if (titles.isEmpty()) null else { _ -> titles },
                goes = 0,
                onRun = { true },
                onFill = {},
                onClearRecent = {},
                onEngine = {},
                onDone = {},
                findPicks = if (picks.isEmpty()) null else { -> picks },
            )
        }
    }

    private fun setup(name: String, hub: FakeHub, settle: Long, wifi: Boolean = true, act: (FakeHub) -> Unit = {}) =
        Shot(name, wifi = wifi, settle = settle, act = { act(hub) }) { FirstRun(hub.prefs, hub.state, hub) }

    private fun pairing(name: String, hub: FakeHub, tv: FoundTv, then: (FakeHub) -> Unit = {}) = setup(name, hub, settle = 4_000) {
        tap(hasText(tv.name.removePrefix("[LG] ").let { if (tv.kind == TvKind.WEBOS) "LG webOS" else it }))
        after(800)
        then(it)
        after(1_000)
    }

    private val three = listOf(900L to lgTv, 1_400L to samsungTv, 2_600L to philipsTv)

    private fun snackbar(name: String, text: () -> String, action: (() -> String)? = null) = Shot(name) {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { host.showSnackbar(text(), actionLabel = action?.invoke(), withDismissAction = action != null, duration = SnackbarDuration.Indefinite) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(host) }
    }

    private fun shots(): List<Shot> = buildList {
        add(remote("remote", connected))
        add(remote("remote_touchpad", connected, PadMode.TOUCHPAD))
        add(remote("remote_tv_picker", connected, act = { tap(described(R.string.tv_picker, TV_NAME)) }))
        add(remote("remote_deck_open", connected, act = { tap(described(R.string.all_apps)) }))
        add(
            remote("remote_hold_input", connected, act = {
                tap(described(R.string.all_apps))
                node(described(R.string.input_label, "HDMI 2")).performTouchInput { longClick() }
                after(600)
            }),
        )
        add(remote("remote_screen_off", connected.copy(power = TvPower.SCREEN_OFF)))
        add(remote("remote_standby", connected.copy(power = TvPower.STANDBY)))
        add(remote("remote_casting", connected.copy(foregroundAppId = TvApps.BROWSER, cast = CastState("Big Buck Bunny", CastPhase.PLAYING, 754, 3596))))
        add(remote("remote_cast_loading", connected.copy(cast = CastState("Big Buck Bunny", CastPhase.LOADING))))
        add(remote("remote_cast_stalled", connected.copy(cast = CastState("Big Buck Bunny", CastPhase.PLAYING, 1312, 2410, stalled = true))))
        val pairing = TvState(host = HOST, connection = Connection.NeedsPairing())
        add(remote("remote_needs_pairing", pairing))
        add(remote("remote_declined", pairing.copy(connection = Connection.NeedsPairing(declined = true))))
        add(remote("remote_timed_out", pairing.copy(connection = Connection.NeedsPairing(timedOut = true))))
        add(remote("remote_forgotten", pairing.copy(connection = Connection.NeedsPairing(forgotten = true))))
        add(remote("remote_awaiting", pairing.copy(connection = Connection.AwaitingApproval)))
        add(remote("remote_code", pairing.copy(kind = TvKind.ANDROID_TV, connection = Connection.AwaitingCode())))
        add(remote("remote_code_wrong", pairing.copy(kind = TvKind.ANDROID_TV, connection = Connection.AwaitingCode(wrong = true))))
        add(remote("remote_connecting", pairing.copy(connection = Connection.Connecting)))
        add(remote("remote_offline", pairing.copy(connection = Connection.Offline, apps = apps)))
        add(remote("remote_offline_no_wifi", pairing.copy(connection = Connection.Offline, apps = apps), wifi = false))

        add(more("more", connected))
        add(more("more_brightness", connected, R.string.picture_backlight))
        add(more("more_picture", connected, R.string.picture_title))
        add(more("more_sound", connected, R.string.sound_title))
        val philips = connected.copy(features = connected.features + Feature.AMBILIGHT)
        add(more("more_ambilight", philips.copy(ambilight = Ambilight(on = true, style = AmbilightStyle.FOLLOW_VIDEO)), R.string.ambilight_title))
        add(more("more_ambilight_pair", philips.copy(ambilight = Ambilight(AmbilightAccess.NeedsPairing)), R.string.ambilight_title))
        add(more("more_ambilight_pin", philips.copy(ambilight = Ambilight(AmbilightAccess.AwaitingPin())), R.string.ambilight_title))
        add(more("more_ambilight_pin_wrong", philips.copy(ambilight = Ambilight(AmbilightAccess.AwaitingPin(wrong = true))), R.string.ambilight_title))
        add(power("power", PowerMode.ON))
        add(power("power_off", PowerMode.OFF))
        add(power("power_screen_off_timer", PowerMode.SCREEN_OFF, timer = true))
        add(remote("keyboard_waiting", connected, sheet = Sheet.KEYBOARD))
        add(remote("keyboard_ready", connected.copy(keyboard = TvKeyboard(12)), sheet = Sheet.KEYBOARD))
        add(settings("settings_empty", Prefs()))
        add(settings("settings", found = listOf(FoundTv("192.168.1.40", "[LG] webOS TV UM7450PLA"), FoundTv("192.168.1.41", "Kitchen", kind = TvKind.SAMSUNG)), devLink = DevLink.Down(DevProblem.DEBUGGING_OFF)))
        add(settings("settings_found_none", found = emptyList()))
        add(settings("settings_found_no_new", found = listOf(FoundTv(HOST, TV_NAME))))
        add(settings("settings_finding", act = { tap(texted(R.string.settings_find)) }))
        add(settings("settings_menu_tv", prefs.copy(kind = TvKind.ANDROID_TV), devLink = DevLink.Down(DevProblem.WIRELESS_ONLY), act = { tap(hasText(TV_NAME)) }))
        add(
            settings("settings_menu_tv_armed", act = {
                tap(hasText(TV_NAME))
                tap(texted(R.string.settings_forget))
            }),
        )
        add(settings("settings_menu_other_tv", act = { hold(hasText(OTHER_TV)) }))
        add(
            settings("settings_menu_other_tv_armed", act = {
                hold(hasText(OTHER_TV))
                tap(texted(R.string.settings_remove_tv))
            }),
        )
        add(settings("settings_menu_strip", act = { tap(role(Role.DropdownList).and(hasAnyDescendantText(R.string.settings_scroll_strip))) }))
        add(settings("settings_menu_theme", act = { tap(role(Role.DropdownList).and(hasAnyDescendantText(R.string.settings_theme))) }))
        add(settings("settings_menu_search", act = { tap(role(Role.DropdownList).and(hasAnyDescendantText(R.string.settings_search_engine))) }))
        add(sheet("language") { LanguageSheetContent(onPicked = {}) })
        listOf(
            DevLink.Off,
            DevLink.Connecting,
            DevLink.AwaitingApproval,
            DevLink.Up,
            DevLink.Down(),
            DevLink.Down(DevProblem.NOT_ALLOWED),
        ).plus(DevProblem.entries.map { DevLink.Failed(it) }).plus(DevProblem.entries.map { DevLink.Down(it) }).distinct().forEachIndexed { i, link ->
            add(sheet("dev_link_$i") { DevLinkSheetContent(link, onConnect = {}, onTurnOff = {}) })
        }
        add(share("share_youtube", "https://youtu.be/aqz-KE-bpKQ", connected.copy(foregroundAppId = TvApps.NETFLIX)))
        add(share("share_failed", "https://youtu.be/aqz-KE-bpKQ", opens = false, act = { tap(role(Role.Button)); after(1_000) }))
        add(share("share_media", "http://192.168.1.42:8088/files/Big%20Buck%20Bunny.mkv"))
        add(share("share_web", "https://lrt.lt/mediateka/irasas/2000", connected.copy(connection = Connection.Connecting)))
        add(share("share_not_paired", "https://lrt.lt/mediateka/irasas/2000", TvState(host = HOST, connection = Connection.NeedsPairing())))
        add(share("share_cant_open", "https://lrt.lt/mediateka/irasas/2000", connected.copy(features = emptySet(), connection = Connection.Offline)))
        add(share("share_no_link", null, connected.copy(connection = Connection.AwaitingApproval)))
        add(sheet("link_help_stalling") { LinkHelpSheetContent(LinkReport(LinkVerdict.STALLING, 0.4f, 3, 452)) })
        add(sheet("link_help_slow") { LinkHelpSheetContent(LinkReport(LinkVerdict.SLOW, 0.6f, 240, 900)) })

        add(search("search_recent", ""))
        add(search("search_picks", "", picks = listOf(StreamingTitle("The Sixth Sense", LinkRouter.classify("https://www.netflix.com/title/1"), year = 1999, show = false))))
        add(search("search_link", "https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        add(search("search_web", "https://lrt.lt/mediateka"))
        add(search("search_text_box", "you", tvText = 0))
        add(search("search_titles", "stranger things", titles = listOf(StreamingTitle("Stranger Things", LinkRouter.classify("https://www.netflix.com/title/80057281"), year = 2016, show = true))))

        Notice.entries.forEach { notice -> add(snackbar("snackbar_${notice.name.lowercase()}", { string(notice.text) })) }
        add(snackbar("snackbar_hidden", { string(R.string.channel_hidden, "HDMI 2") }, { string(R.string.action_undo) }))
        add(snackbar("snackbar_crash", { string(R.string.crash_offer) }, { string(R.string.crash_send) }))
        add(snackbar("snackbar_mic", { string(R.string.voice_mic_off) }, { string(R.string.settings) }))

        add(setup("setup_scanning", FakeHub(endless = true), settle = 1_500))
        add(setup("setup_found", FakeHub(three + (2_800L to fireTv) + (3_000L to rokuTv), passMs = 3_500), settle = 10_000))
        add(setup("setup_nothing", FakeHub(), settle = 9_000))
        add(
            setup("setup_address_missed", FakeHub(), settle = 9_000) {
                tap(texted(R.string.setup_enter_ip))
                node(hasSetTextAction()).performTextInput("192.168.1.7")
                after(300)
                tap(described(R.string.setup_connect))
                after(2_000)
            },
        )
        add(setup("setup_no_wifi", FakeHub(endless = true), settle = 1_000, wifi = false))
        add(pairing("setup_pair_lg", FakeHub(three, endless = true), lgTv))
        add(pairing("setup_pair_fire_tv", FakeHub(listOf(500L to fireTv), endless = true), fireTv))
        add(pairing("setup_pair_code", FakeHub(three, endless = true), philipsTv))
        add(pairing("setup_pair_code_wrong", FakeHub(three, endless = true), philipsTv) { it.submitPairingCode("4F2A9D") })
        add(pairing("setup_pair_roku", FakeHub(listOf(500L to rokuTv), endless = true, answer = { Connection.Connecting }), rokuTv))
        add(pairing("setup_pair_roku_limited", FakeHub(listOf(500L to rokuTv), endless = true, notice = Notice.ROKU_LIMITED), rokuTv))
        add(pairing("setup_pair_adb_off", FakeHub(listOf(500L to fireTv), endless = true, answer = { Connection.Offline }, notice = Notice.ADB_OFF), fireTv))
        add(pairing("setup_pair_offline", FakeHub(three, endless = true, answer = { Connection.Offline }), lgTv))
        listOf(
            "declined" to Connection.NeedsPairing(declined = true),
            "timed_out" to Connection.NeedsPairing(timedOut = true),
            "stopped" to Connection.NeedsPairing(),
        ).forEach { (why, answer) ->
            add(pairing("setup_pair_$why", FakeHub(three, endless = true), lgTv) { it.state = it.state.copy(connection = answer) })
        }
        add(pairing("setup_paired", FakeHub(three, endless = true), lgTv) { it.accept() })
        val adding = FakeHub(three, passMs = 3_000, start = Prefs(otherTvs = listOf(SavedTv("Bedroom", samsungTv.host, TvKind.SAMSUNG, ""))))
        add(sheet("setup_add_tv", settle = 10_000) { TvSetup(adding.state, adding.prefs, adding, onDone = {}, contentPadding = PaddingValues(bottom = 32.dp)) })
        val addingNone = FakeHub(start = Prefs(otherTvs = listOf(SavedTv("Bedroom", samsungTv.host, TvKind.SAMSUNG, ""))))
        add(sheet("setup_add_tv_none", settle = 10_000) { TvSetup(addingNone.state, addingNone.prefs, addingNone, onDone = {}) })
    }

    private fun hasAnyDescendantText(id: Int) = hasAnyDescendant(texted(id))

    private fun allowed() = listOf(
        Allowed(
            "TVs go by the names their owners or makers give them",
            listOf(TV_NAME, OTHER_TV, "Kitchen", "Kids room") + listOf(lgTv, samsungTv, philipsTv, rokuTv, fireTv).flatMap { listOfNotNull(it.name, it.model, it.host) },
        ),
        Allowed("apps, inputs and channels go by the names the TV gives them", (apps + inputs).map { it.title } + connected.channels.map { it.name }),
        Allowed("searches, links and titles are the user's or the web's", prefs.recent + listOf("Big Buck Bunny", "Stranger Things", "The Sixth Sense")),
        Allowed("each language goes by its own name, as ICU has it", screen = Regex("language")),
        Allowed(
            "the key's word is the language's own, stepped down as designed",
            kind = Overflow.SHRUNK,
            strings = listOf(
                "values-b+es+419/more_menu", "values-gd/more_menu", "values-gl/more_menu", "values-rw/more_menu", "values-sr/more_menu",
                "values-el/more_info", "values-hy/more_info", "values-my/more_info",
                "values-ml/more_subtitles", "values-so/more_subtitles",
                "values-ny/more_channel_list", "values-sn/more_channel_list",
                "values-b+ckb/more_guide", "values-mg/more_guide", "values-ny/more_guide", "values-si/more_guide", "values-ta/more_guide",
            ),
        ),
        Allowed(
            "the open tab shows its glyph alone where its name won't fit whole, as designed",
            kind = Overflow.HIDDEN,
            strings = listOf(
                "values-b+haw/picture_backlight", "values-el/picture_backlight", "values-gd/picture_backlight", "values-hy/picture_backlight",
                "values-lo/picture_backlight", "values-my/picture_backlight", "values-ro/picture_backlight",
                "values-ru/picture_title", "values-sn/picture_title", "values-st/picture_title", "values-uk/picture_title",
            ),
        ),
        Allowed("Burmese's line box is taller than the field's line, whatever it says", kind = Overflow.HEIGHT, strings = listOf("values-my/keyboard_placeholder_waiting")),
    )

    companion object {
        private val RES = File("src/main/res")
        private const val HOST = "192.168.1.70"
        private const val TV_NAME = "Living room"
        private const val OTHER_TV = "Bedroom"

        private val SHEET_HANDLE = 48.dp
        private val SHEET_MAX = 917.dp * 0.86f

        private fun qualifier(folder: String) = folder.removePrefix("values-").takeIf { folder != "values" } ?: "en"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun folders(): List<String> {
            val only = System.getenv("OVERFLOW_LANGUAGES").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return stringFolders(RES).filter { only.isEmpty() || it in only || languageTag(it) in only }
        }
    }
}

private class Allowed(
    val why: String,
    val texts: Collection<String> = emptyList(),
    val screen: Regex? = null,
    val kind: Overflow? = null,
    val strings: Collection<String> = emptyList(),
) {
    fun matches(finding: Finding) =
        finding.text in texts ||
            (screen?.matches(finding.screen) == true && finding.names == "?") ||
            (finding.kind == kind && finding.names.split('|').any { "${finding.locale}/$it" in strings })
}
