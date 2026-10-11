package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], fontScale = 1.15f)
class AdaptiveShotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(
        TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO, TvApps.WEB_VIDEO_CASTER,
    ).map { TvApp(it, TvApps.knownTitles.getValue(it)) } + listOf(
        TvApp("com.webos.app.music", "Music"),
        TvApp("com.webos.app.discovery", "LG Content Store"),
        TvApp("com.webos.app.livetv", "Live TV"),
        TvApp("com.webos.app.notificationcenter", "Notifications"),
    )
    private val connected = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = apps,
        inputs = listOf(
            TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true),
            TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true),
        ),
        channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius"), TvChannel("3", "3", "LRT Lituanica")),
        channelId = "2",
        soundOutput = "tv_speaker",
        picture = TvPicture("game", 64),
    )
    private val casting = connected.copy(foregroundAppId = TvApps.BROWSER, cast = CastState("Big Buck Bunny", CastPhase.PLAYING, positionSeconds = 754, durationSeconds = 596 + 3000))
    private val prefs = Prefs(autoKeyboard = false, findTitles = true, askedFindTitles = true, recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ"))

    private val bookFlat = Fold(336.5.dp, 0.dp, 336.5.dp, 841.dp, vertical = true, separating = false)

    private val tabletopWide = Fold(0.dp, 336.5.dp, 841.dp, 336.5.dp, vertical = false, separating = true)

    private val tabletopFlip = Fold(0.dp, 457.5.dp, 412.dp, 457.5.dp, vertical = false, separating = true)

    private fun shoot(name: String, state: TvState = connected, sheet: Sheet? = null, fold: Fold? = null, mode: PadMode = PadMode.DPAD, tap: String? = null) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), initialMode = mode, requestedSheet = sheet, fold = fold)
                }
            }
        }
        compose.waitForIdle()
        if (tap != null) {
            compose.onNodeWithContentDescription(tap).performClick()
            compose.waitForIdle()
        }
        captureScreenRoboImage("build/shots/adaptive/$name.png")
    }

    @Test @Config(qualifiers = "w917dp-h385dp-xxhdpi")
    fun phoneLandscape() = shoot("01_phone_landscape")

    @Test @Config(qualifiers = "w917dp-h385dp-xxhdpi")
    fun phoneLandscapeDeckOpen() = shoot("02_phone_landscape_deck_open", tap = "All apps")

    @Test @Config(qualifiers = "w917dp-h385dp-xxhdpi")
    fun phoneLandscapeCasting() = shoot("03_phone_landscape_casting", casting, mode = PadMode.TOUCHPAD)

    @Test @Config(qualifiers = "w917dp-h385dp-xxhdpi")
    fun phoneLandscapeMore() = shoot("04_phone_landscape_more", sheet = Sheet.MORE)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpen() = shoot("10_fold_open", fold = bookFlat)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenTouchpadCasting() = shoot("11_fold_open_touchpad_casting", casting, fold = bookFlat, mode = PadMode.TOUCHPAD)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenSearch() = shoot("12_fold_open_search", sheet = Sheet.SEARCH, fold = bookFlat)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenSettings() = shoot("13_fold_open_settings", sheet = Sheet.SETTINGS, fold = bookFlat)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenMore() = shoot("14_fold_open_more", sheet = Sheet.MORE, fold = bookFlat)

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenPicture() = shoot("15_fold_open_picture", fold = bookFlat, tap = "Picture")

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenNoHinge() = shoot("16_fold_open_no_hinge")

    @Test @Config(qualifiers = "w673dp-h841dp-xxhdpi")
    fun foldOpenOffline() = shoot("17_fold_open_offline", TvState(host = "192.168.1.70", connection = Connection.Offline, apps = apps), fold = bookFlat)

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi", fontScale = 1f)
    fun tabletPortrait() = shoot("20_tablet_portrait")

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi", fontScale = 1f)
    fun tabletLandscape() = shoot("21_tablet_landscape")

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi", fontScale = 1f)
    fun tabletLandscapePower() = shoot("22_tablet_landscape_power", sheet = Sheet.POWER)

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi", fontScale = 1f)
    fun tabletLandscapeMore() = shoot("23_tablet_landscape_more", sheet = Sheet.MORE)

    @Test @Config(qualifiers = "w841dp-h673dp-xxhdpi")
    fun tabletopFold() = shoot("30_tabletop_fold", fold = tabletopWide)

    @Test @Config(qualifiers = "w841dp-h673dp-xxhdpi")
    fun tabletopFoldCasting() = shoot("31_tabletop_fold_casting", casting, fold = tabletopWide)

    @Test @Config(qualifiers = "w841dp-h673dp-xxhdpi")
    fun tabletopFoldMore() = shoot("32_tabletop_fold_more", sheet = Sheet.MORE, fold = tabletopWide)

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun tabletopFlip() = shoot("33_tabletop_flip", fold = tabletopFlip)

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun tabletopFlipSearch() = shoot("34_tabletop_flip_search", sheet = Sheet.SEARCH, fold = tabletopFlip)

    @Test @Config(qualifiers = "w841dp-h673dp-xxhdpi")
    fun foldFlatSideways() = shoot("35_fold_flat_sideways", fold = tabletopWide.copy(separating = false))
}
