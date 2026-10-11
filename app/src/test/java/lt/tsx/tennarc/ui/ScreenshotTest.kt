package lt.tsx.tennarc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SearchField
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.sheets.KeyboardSheetContent
import lt.tsx.tennarc.ui.sheets.MoreSheetContent
import lt.tsx.tennarc.ui.sheets.PowerMode
import lt.tsx.tennarc.ui.sheets.PowerSheetContent
import lt.tsx.tennarc.ui.sheets.SettingsSheetContent
import lt.tsx.tennarc.ui.sheets.SleepTimerControls
import lt.tsx.tennarc.ui.sheets.ShareSheetContent
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(
        TvApp(TvApps.YOUTUBE, "YouTube"),
        TvApp(TvApps.NETFLIX, "Netflix"),
        TvApp(TvApps.BROWSER, "Web Browser"),
        TvApp(TvApps.MEDIA_STATION_X, "Media Station X"),
        TvApp(TvApps.PHOTO_VIDEO, "Photo & Video"),
        TvApp(TvApps.WEB_VIDEO_CASTER, "Web Video Caster"),
        TvApp("com.webos.app.music", "Music"),
        TvApp("com.webos.app.homeconnect", "Home Dashboard"),
        TvApp("com.webos.app.discovery", "LG Content Store"),
        TvApp("com.webos.app.livetv", "Live TV"),
    )
    private val connected = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 14,
        apps = apps,
    )
    private val prefs = Prefs(recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ", "https://lrt.lt/mediateka", "http://192.168.1.42:8088/files/Big%20Buck%20Bunny.mkv"))

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun remote(name: String, state: TvState, mode: PadMode = PadMode.DPAD) = shoot(name) {
        RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), initialMode = mode)
    }

    private fun sheet(name: String, content: @Composable () -> Unit) = shoot(name) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 22.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    content()
                }
            }
        }
    }

    @Test fun remoteDpad() = remote("01_remote_dpad", connected)

    @Test fun remoteTouchpadCasting() = remote(
        "02_remote_touchpad_casting",
        connected.copy(
            foregroundAppId = TvApps.BROWSER,
            volume = 22,
            cast = CastState("Big Buck Bunny", CastPhase.PLAYING, positionSeconds = 754, durationSeconds = 596 + 3000),
        ),
        PadMode.TOUCHPAD,
    )

    @Test fun remoteMuted() = remote("03_remote_muted_placeholder_apps", connected.copy(apps = emptyList(), foregroundAppId = null, muted = true))

    @Test fun remoteNeedsPairing() = remote("04_remote_needs_pairing", TvState(host = "192.168.1.70", connection = Connection.NeedsPairing()))

    @Test fun remoteDeclined() = remote("05_remote_declined", TvState(host = "192.168.1.70", connection = Connection.NeedsPairing(declined = true)))

    @Test fun remoteAwaiting() = remote("06_remote_awaiting", TvState(host = "192.168.1.70", connection = Connection.AwaitingApproval))

    @Test fun remoteConnecting() = remote("07_remote_connecting", TvState(host = "192.168.1.70", connection = Connection.Connecting))

    @Test fun remoteOffline() = remote("08_remote_offline", TvState(host = "192.168.1.70", connection = Connection.Offline, apps = apps))

    @Test fun remoteStalledCast() = remote(
        "09_remote_cast_stalled",
        connected.copy(cast = CastState("Holiday video 2024-08-12 (4K remux)", CastPhase.PLAYING, 1312, 2410, stalled = true)),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun remoteSmallPhone() = remote("10_remote_small_phone", connected)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun remoteLandscape() = remote("11_remote_landscape", connected)

    private val searchApps = apps.map { it.copy(searchable = TvApps.isSearchable(it.id)) }
    private val strangerThings = listOf(
        StreamingTitle("Stranger Things", LinkRouter.classify("https://www.netflix.com/title/80057281"), year = 2016, show = true),
        StreamingTitle("Stranger Things: Tales from '85", LinkRouter.classify("https://www.netflix.com/title/81398721"), year = 2026, show = true),
        StreamingTitle("Stranger Things 5 | Final Trailer", LinkRouter.classify("https://www.youtube.com/watch?v=aqz-KE-bpKQ"), byline = "Netflix", video = true),
    )

    private fun search(
        name: String,
        query: String,
        tvText: Int? = null,
        titles: List<StreamingTitle> = emptyList(),
        picks: List<StreamingTitle> = emptyList(),
    ) = shoot(name) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            SearchField(query, onQuery = {}, active = true, onActive = {}, onGo = {}, hint = "Search")
            SearchResults(
                query = query,
                apps = searchApps,
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

    @Test fun searchRecent() = search("20_search_recent", "")

    @Test fun searchPicks() = search(
        "25_search_picks",
        "",
        picks = listOf("Spider-Man: No Way Home" to 2021, "The Sixth Sense" to 1999, "Nocturnal Animals" to 2016, "The Mummy" to 1999).mapIndexed { i, (title, year) ->
            StreamingTitle(title, LinkRouter.classify("https://www.netflix.com/title/${i + 1}"), year = year, show = false)
        },
    )

    @Test fun searchLink() = search("21_search_link", "https://www.youtube.com/watch?v=aqz-KE-bpKQ")

    @Test fun searchTextBox() = search("22_search_text_box", "you", tvText = 0)

    @Test fun searchTitles() = search("24_search_titles", "stranger things", titles = strangerThings)

    @Test fun keyboardSheetReady() = sheet("23_keyboard_ready") {
        KeyboardSheetContent(TvKeyboard(0), enabled = true, text = "big buck bunny", onText = {}, onType = {}, onEnter = {}, autoFocus = false)
    }

    @Test fun keyboardSheetWaiting() = sheet("24_keyboard_waiting") {
        KeyboardSheetContent(null, enabled = true, text = "", onText = {}, onType = {}, onEnter = {}, autoFocus = false)
    }

    @Test fun moreSheet() = sheet("25_more_sheet") { MoreSheetContent(enabled = true, onPress = {}, liveTv = true) }

    @Test fun powerSheet() = sheet("27_power_sheet") {
        PowerSheetContent(PowerMode.ON, canWake = true, onScreenOff = {}, onPowerOff = {}, onWake = {}, onDone = {})
    }

    @Test fun powerSheetOffline() = sheet("28_power_sheet_offline") {
        PowerSheetContent(PowerMode.OFF, canWake = true, onScreenOff = {}, onPowerOff = {}, onWake = {}, onDone = {})
    }

    @Test fun powerSheetTimer() {
        compose.mainClock.autoAdvance = false
        val at = System.currentTimeMillis() + 59 * 60_000L + 55_000L
        sheet("28_power_sheet_timer") {
            PowerSheetContent(PowerMode.SCREEN_OFF, canWake = true, onScreenOff = {}, onPowerOff = {}, onWake = {}, onDone = {}) {
                SleepTimerControls(at, minutes = 60, onStart = {}, onCancel = {})
            }
        }
    }

    @Test fun settingsSheet() = sheet("29_settings_sheet") {
        SettingsSheetContent(
            prefs = prefs,
            onUpdate = {},
            onFindTvs = { emptyList() },
            onForgetPairing = {},
            initialFound = listOf(FoundTv("192.168.1.70", "[LG] webOS TV UM7450PLA"), FoundTv("192.168.1.40", "Bedroom TV")),
        )
    }

    @Test fun shareYouTube() = sheet("30_share_youtube") {
        ShareSheetContent(
            actionsFor = { it.possibleActions },
            target = LinkRouter.classify("https://youtu.be/aqz-KE-bpKQ"),
            state = connected.copy(foregroundAppId = TvApps.NETFLIX),
            onOpen = { _, _ -> true },
            onOpenApp = {},
            onClose = {},
        )
    }

    @Test fun shareMedia() = sheet("31_share_media") {
        ShareSheetContent(
            actionsFor = { it.possibleActions },
            target = LinkRouter.classify("http://192.168.1.42:8088/files/Big%20Buck%20Bunny.mkv"),
            state = connected,
            onOpen = { _, _ -> true },
            onOpenApp = {},
            onClose = {},
        )
    }

    @Test fun shareNotPaired() = sheet("32_share_not_paired") {
        ShareSheetContent(
            actionsFor = { it.possibleActions },
            target = LinkRouter.classify("https://lrt.lt/mediateka/irasas/2000"),
            state = TvState(host = "192.168.1.70", connection = Connection.NeedsPairing()),
            onOpen = { _, _ -> true },
            onOpenApp = {},
            onClose = {},
        )
    }

    @Test fun shareNoLink() = sheet("33_share_no_link") {
        ShareSheetContent(target = null, state = connected, actionsFor = { it.possibleActions }, onOpen = { _, _ -> true }, onOpenApp = {}, onClose = {})
    }
}
