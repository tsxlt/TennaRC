package lt.tsx.tennarc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SearchField
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.sheets.DevLinkSheetContent
import lt.tsx.tennarc.ui.sheets.KeyboardSheetContent
import lt.tsx.tennarc.ui.sheets.LanguageSheetContent
import lt.tsx.tennarc.ui.sheets.MoreSheetContent
import lt.tsx.tennarc.ui.sheets.PowerMode
import lt.tsx.tennarc.ui.sheets.PowerSheetContent
import lt.tsx.tennarc.ui.sheets.SettingsSheetContent
import lt.tsx.tennarc.ui.sheets.ShareSheetContent
import lt.tsx.tennarc.ui.theme.SquircleShape
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LongTextShotTest(private val language: String) {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(object : ExternalResource() { override fun before() = RuntimeEnvironment.setQualifiers("+$language") })
        .around(compose)

    private val apps = listOf(
        TvApp(TvApps.YOUTUBE, "YouTube"),
        TvApp(TvApps.NETFLIX, "Netflix"),
        TvApp(TvApps.BROWSER, "Web Browser"),
        TvApp(TvApps.MEDIA_STATION_X, "Media Station X"),
        TvApp("com.webos.app.livetv", "Live TV"),
    )
    private val connected = TvState(host = "192.168.1.70", connection = Connection.Connected, foregroundAppId = TvApps.YOUTUBE, volume = 14, apps = apps)
    private val searchApps = apps.map { it.copy(searchable = TvApps.isSearchable(it.id)) }
    private val prefs = Prefs(recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ"))

    private fun shoot(screen: String, content: @Composable () -> Unit) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/long/$screen/$language.png")
    }

    private fun remote(screen: String, state: TvState, mode: PadMode = PadMode.DPAD) = shoot(screen) {
        RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), initialMode = mode)
    }

    private fun sheet(screen: String, content: @Composable () -> Unit) = shoot(screen) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = SquircleShape(28.dp).copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
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

    @Test fun remote() = remote("remote", connected)

    @Test fun touchpad() = remote("touchpad", connected, PadMode.TOUCHPAD)

    @Test fun needsPairing() = remote("needs_pairing", TvState(host = "192.168.1.70", connection = Connection.NeedsPairing()))

    @Test fun offline() = remote("offline", TvState(host = "192.168.1.70", connection = Connection.Offline, apps = apps))

    @Test fun more() = sheet("more") { MoreSheetContent(enabled = true, onPress = {}, liveTv = true) }

    @Test fun power() = sheet("power") {
        PowerSheetContent(PowerMode.ON, canWake = true, onScreenOff = {}, onPowerOff = {}, onWake = {}, onDone = {})
    }

    @Test fun keyboard() = sheet("keyboard") {
        KeyboardSheetContent(null, enabled = true, text = "", onText = {}, onType = {}, onEnter = {}, autoFocus = false)
    }

    @Test fun settingsEmpty() = sheet("settings_empty") {
        SettingsSheetContent(prefs = Prefs(), onUpdate = {}, onFindTvs = { emptyList() }, onForgetPairing = {}, onAddTv = {}, onLanguage = {})
    }

    @Test fun settings() = sheet("settings") {
        SettingsSheetContent(
            prefs = prefs,
            onUpdate = {},
            onFindTvs = { emptyList() },
            onForgetPairing = {},
            initialFound = listOf(FoundTv("192.168.1.70", "[LG] webOS TV UM7450PLA"), FoundTv("192.168.1.40", "Bedroom TV")),
            onLanguage = {},
        )
    }

    @Test fun language() = sheet("language") { LanguageSheetContent(onPicked = {}) }

    @Test fun devLink() = sheet("dev_link") { DevLinkSheetContent(link = DevLink.Off, onConnect = {}, onTurnOff = {}) }

    @Test fun share() = sheet("share") {
        ShareSheetContent(
            actionsFor = { it.possibleActions },
            target = LinkRouter.classify("https://youtu.be/aqz-KE-bpKQ"),
            state = connected.copy(foregroundAppId = TvApps.NETFLIX),
            onOpen = { _, _ -> true },
            onOpenApp = {},
            onClose = {},
        )
    }

    @Test fun search() = shoot("search") {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            SearchField("", onQuery = {}, active = true, onActive = {}, onGo = {}, hint = "Search")
            SearchResults(
                query = "",
                apps = searchApps,
                tvText = null,
                engine = SearchEngine.DUCKDUCKGO,
                recent = prefs.recent,
                findTitles = null,
                goes = 0,
                onRun = { true },
                onFill = {},
                onClearRecent = {},
                onEngine = {},
                onDone = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages() = listOf("en", "lt", "de", "fr", "ru", "el", "eu", "gd", "ta", "ml", "my", "ka", "ar")
    }
}
