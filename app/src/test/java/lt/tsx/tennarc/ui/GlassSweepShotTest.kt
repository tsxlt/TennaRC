package lt.tsx.tennarc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.LinkReport
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.onboarding.FakeHub
import lt.tsx.tennarc.ui.onboarding.FirstRun
import lt.tsx.tennarc.ui.onboarding.lgTv
import lt.tsx.tennarc.ui.onboarding.onWifi
import lt.tsx.tennarc.ui.onboarding.philipsTv
import lt.tsx.tennarc.ui.onboarding.samsungTv
import lt.tsx.tennarc.ui.sheets.ShareSheetContent
import lt.tsx.tennarc.ui.theme.ProvideGround
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.glass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class GlassSweepShotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(
        TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO, TvApps.WEB_VIDEO_CASTER,
    ).map { TvApp(it, TvApps.knownTitles.getValue(it)) } + listOf(TvApp("com.webos.app.livetv", "Live TV"))
    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = apps,
        channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius")),
        picture = TvPicture("normal", 60),
        soundOutput = "tv_speaker",
        keyboard = TvKeyboard(textLength = 0),
        link = LinkReport(LinkVerdict.SLOW, slowShare = 0.6f, medianMs = 400, worstMs = 1200),
    )
    private val prefs = Prefs(
        autoKeyboard = false,
        findTitles = true,
        askedFindTitles = true,
        tvName = "Living room",
        otherTvs = listOf(SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, ""), SavedTv("Kitchen", "192.168.1.51", TvKind.SAMSUNG, "")),
        demoListed = true,
        hiddenChannels = setOf("2"),
        hiddenNames = mapOf("2" to "LRT Plius"),
        recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ", "https://lrt.lt/mediateka"),
    )

    private fun show(dark: Boolean, sheet: Sheet?, tv: TvState = state, settings: Prefs = prefs) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(tv, settings, NoRemoteActions, remember { SnackbarHostState() }, requestedSheet = sheet)
                }
            }
        }
        settle()
    }

    private fun settle(ms: Long = 1_200) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun shot(name: String, dark: Boolean) {
        settle(400)
        captureScreenRoboImage("build/shots/sweep/${name}_${if (dark) "dark" else "light"}.png")
    }

    private fun more(dark: Boolean) {
        show(dark, Sheet.MORE)
        shot("01_more", dark)
    }

    @Test fun moreDark() = more(true)

    @Test fun moreLight() = more(false)

    private fun settings(dark: Boolean) {
        show(dark, Sheet.SETTINGS)
        shot("02_settings_top", dark)
        compose.onNodeWithText("Theme").performScrollTo()
        shot("03_settings_mid", dark)
        compose.onNodeWithText("TennaRC ", substring = true).performScrollTo()
        shot("04_settings_end", dark)
    }

    @Test fun settingsDark() = settings(true)

    @Test fun settingsLight() = settings(false)

    private fun power(dark: Boolean) {
        show(dark, Sheet.POWER)
        shot("05_power", dark)
    }

    @Test fun powerDark() = power(true)

    @Test fun powerLight() = power(false)

    private fun keyboard(dark: Boolean) {
        show(dark, Sheet.KEYBOARD)
        compose.onNode(hasSetTextAction()).performTextInput("stranger th")
        shot("06_keyboard", dark)
    }

    @Test fun keyboardDark() = keyboard(true)

    @Test fun keyboardLight() = keyboard(false)

    private fun search(dark: Boolean, typed: String?) {
        show(dark, Sheet.SEARCH)
        if (typed != null) compose.onNode(hasSetTextAction()).performTextInput(typed)
        shot(if (typed == null) "07_search_recent" else "08_search_typed", dark)
    }

    @Test fun searchRecentDark() = search(true, null)

    @Test fun searchRecentLight() = search(false, null)

    @Test fun searchTypedDark() = search(true, "you")

    @Test fun searchTypedLight() = search(false, "you")

    private fun language(dark: Boolean) {
        show(dark, Sheet.LANGUAGE)
        shot("09_language", dark)
    }

    @Test fun languageDark() = language(true)

    @Test fun languageLight() = language(false)

    private fun linkHelp(dark: Boolean) {
        show(dark, Sheet.LINK_HELP)
        shot("10_link_help", dark)
    }

    @Test fun linkHelpDark() = linkHelp(true)

    @Test fun linkHelpLight() = linkHelp(false)

    private fun devLink(dark: Boolean) {
        show(dark, Sheet.DEV_LINK, tv = state.copy(kind = TvKind.ANDROID_TV, devLink = DevLink.Off), settings = prefs.copy(kind = TvKind.ANDROID_TV))
        shot("11_dev_link", dark)
    }

    @Test fun devLinkDark() = devLink(true)

    @Test fun devLinkLight() = devLink(false)

    private fun addTv(dark: Boolean) {
        onWifi()
        show(dark, Sheet.ADD_TV)
        settle(9_000)
        shot("12_add_tv", dark)
    }

    @Test fun addTvDark() = addTv(true)

    @Test fun addTvLight() = addTv(false)

    private fun dialog(dark: Boolean) {
        show(dark, Sheet.SETTINGS)
        compose.onNodeWithText("Living room").performClick()
        settle()
        compose.onNodeWithText("Edit").performClick()
        settle()
        shot("13_dialog", dark)
    }

    @Test fun dialogDark() = dialog(true)

    @Test fun dialogLight() = dialog(false)

    private fun share(dark: Boolean) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).frostBehind(), contentAlignment = Alignment.BottomCenter) {
                        val frost = glass()
                        Column(Modifier.fillMaxWidth().glass(frost, BottomSheetDefaults.ExpandedShape)) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                                ProvideGround(frost.fill) {
                                    ShareSheetContent(
                                        actionsFor = { it.possibleActions },
                                        target = LinkRouter.classify("http://192.168.1.42:8088/files/Big%20Buck%20Bunny.mkv"),
                                        state = state,
                                        onOpen = { _, _ -> true },
                                        onOpenApp = {},
                                        onClose = {},
                                        modifier = Modifier.padding(bottom = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        shot("14_share", dark)
    }

    @Test fun shareDark() = share(true)

    @Test fun shareLight() = share(false)

    private fun firstRun(dark: Boolean, code: Boolean) {
        onWifi()
        compose.mainClock.autoAdvance = false
        val hub = FakeHub(listOf(900L to lgTv, 1_400L to samsungTv, 2_600L to philipsTv), passMs = 3_000)
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize()) {
                        RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, modifier = if (hub.prefs.needsTvSetup) Modifier.clearAndSetSemantics {} else Modifier)
                        FirstRun(hub.prefs, hub.state, hub)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(10_000)
        if (code) {
            compose.onNodeWithText("Philips 55PUS8807").performClick()
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithContentDescription("Code").performTextInput("4F2")
            compose.mainClock.advanceTimeBy(300)
        }
        compose.mainClock.advanceTimeBy(600)
        captureScreenRoboImage("build/shots/sweep/${if (code) "16_setup_code" else "15_setup_choose"}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun setupChooseDark() = firstRun(true, code = false)

    @Test fun setupChooseLight() = firstRun(false, code = false)

    @Test fun setupCodeDark() = firstRun(true, code = true)

    @Test fun setupCodeLight() = firstRun(false, code = true)

    private fun pairing(dark: Boolean) {
        show(dark, null, tv = TvState(host = "192.168.1.70", connection = Connection.NeedsPairing()))
        shot("17_remote_pairing", dark)
    }

    @Test fun pairingDark() = pairing(true)

    @Test fun pairingLight() = pairing(false)
}
