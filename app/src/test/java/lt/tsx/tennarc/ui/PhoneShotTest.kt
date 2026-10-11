package lt.tsx.tennarc.ui

import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKeyboard
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
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class PhoneShotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO)
        .map { TvApp(it, TvApps.knownTitles.getValue(it)) }
    private val connected = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.BROWSER,
        volume = 10,
        apps = apps,
        inputs = listOf(
            TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true),
            TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true),
            TvApp("com.webos.app.hdmi3", "HDMI 3", input = true, plugged = false),
        ),
        channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius"), TvChannel("3", "3", "LRT Lituanica")),
        channelId = "2",
        soundOutput = "tv_speaker",
        picture = TvPicture("game", 64),
        keyboard = TvKeyboard(0),
    )
    private val prefs = Prefs(autoKeyboard = false, findTitles = true, askedFindTitles = true, otherTvs = listOf(SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, "")), recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ", "https://lrt.lt/mediateka"))

    private fun shoot(name: String, state: TvState = connected, sheet: Sheet? = null, tap: String? = null, dark: Boolean = true) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), requestedSheet = sheet)
                }
            }
        }
        if (sheet == Sheet.KEYBOARD) compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        if (tap != null) {
            compose.onNodeWithContentDescription(tap).performClick()
            compose.waitForIdle()
        }
        captureScreenRoboImage("build/shots/phone/$name.png")
    }

    @Test fun remote() = shoot("01_remote")

    @Test fun standby() = shoot("02_standby", connected.copy(power = TvPower.STANDBY))

    @Test fun offline() {
        val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        val wifi = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, wifi)
        shoot("03_offline", TvState(host = "192.168.1.70", connection = Connection.Offline, apps = apps))
    }

    @Test fun offlineNoWifi() = shoot("03_offline_no_wifi", TvState(host = "192.168.1.70", connection = Connection.Offline, apps = apps))

    @Test fun settings() = shoot("10_settings", sheet = Sheet.SETTINGS)

    @Test fun keyboard() = shoot("11_keyboard", sheet = Sheet.KEYBOARD)

    @Test fun search() = shoot("12_search", sheet = Sheet.SEARCH)

    @Test fun more() = shoot("13_more", sheet = Sheet.MORE)

    @Test fun morePicture() = shoot("13_more_picture", sheet = Sheet.MORE, tap = "Picture")

    @Test fun moreBrightness() = shoot("13_more_brightness", sheet = Sheet.MORE, tap = "Brightness")

    @Test fun moreAmbilight() = shoot(
        "13_more_ambilight",
        connected.copy(
            features = connected.features + Feature.AMBILIGHT,
            picture = TvPicture("normal", 64),
            ambilight = Ambilight(on = true, style = AmbilightStyle.FOLLOW_VIDEO),
        ),
        sheet = Sheet.MORE,
        tap = "Ambilight",
    )

    private val allApps = apps + listOf(
        TvApp(TvApps.WEB_VIDEO_CASTER, "Web Video Caster"),
        TvApp("com.webos.app.homeconnect", "Home Dashboard"),
        TvApp("com.webos.app.music", "Music"),
        TvApp("com.webos.app.discovery", "LG Content Store"),
        TvApp("com.webos.app.livetv", "Live TV"),
    )

    @Test fun deckOpen() = shoot("14_deck_open", connected.copy(apps = allApps, channelId = null), tap = "All apps")

    @Test fun deckOpenLive() = shoot("14_deck_open_live", connected.copy(apps = allApps, foregroundAppId = "com.webos.app.livetv"), tap = "All apps")

    @Test fun deckOpenManyInputs() = shoot(
        "14_deck_open_inputs",
        connected.copy(
            apps = allApps,
            channelId = null,
            inputs = (1..4).map { TvApp("com.webos.app.hdmi$it", "HDMI $it") } + TvApp("com.webos.app.livetv", "Antenna"),
        ),
        tap = "All apps",
    )

    @Test fun power() = shoot("15_power", sheet = Sheet.POWER)

    @Test fun holdInput() {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(connected.copy(apps = allApps, channelId = null), prefs, NoRemoteActions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("HDMI 2, input").performTouchInput { longClick() }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/phone/17_hold_input.png")
    }

    @Test fun remoteLight() = shoot("20_light_remote", dark = false)

    @Test fun deckLight() = shoot("21_light_deck", connected.copy(apps = allApps, channelId = null), tap = "All apps", dark = false)

    @Test fun moreLight() = shoot("22_light_more", sheet = Sheet.MORE, dark = false)

    @Test fun morePictureLight() = shoot("23_light_more_picture", sheet = Sheet.MORE, tap = "Picture", dark = false)

    @Test fun settingsLight() = shoot("24_light_settings", sheet = Sheet.SETTINGS, dark = false)

    @Test fun searchLight() = shoot("25_light_search", sheet = Sheet.SEARCH, dark = false)

    @Test fun keyboardLight() = shoot("26_light_keyboard", sheet = Sheet.KEYBOARD, dark = false)

    private val stalling = connected.copy(link = LinkReport(LinkVerdict.STALLING, slowShare = 0.4f, medianMs = 3, worstMs = 452))

    @Test fun linkBanner() = shoot("04_link_banner", stalling)

    @Test fun linkHelp() = shoot("16_link_help", stalling, sheet = Sheet.LINK_HELP)
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.3f)
class SmallPhoneBigTextShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 100,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )

    private fun shoot(name: String, sheet: Sheet? = null, tv: TvState = state) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }, tvName = "Living room TV") {
                    RemoteScreen(tv, Prefs(autoKeyboard = false, findTitles = true, askedFindTitles = true), NoRemoteActions, SnackbarHostState(), requestedSheet = sheet)
                }
            }
        }
        if (sheet == Sheet.KEYBOARD) compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/small/$name.png")
    }

    @Test fun remote() = shoot("01_remote")

    @Test fun offline() = shoot("02_offline", tv = state.copy(connection = Connection.Offline, power = null, foregroundAppId = null, volume = null))

    @Test fun keyboard() = shoot("11_keyboard", Sheet.KEYBOARD)

    @Test fun settings() = shoot("12_settings", Sheet.SETTINGS)

    @Test fun more() = shoot("13_more", Sheet.MORE)

    @Test fun power() = shoot("14_power", Sheet.POWER)
}
