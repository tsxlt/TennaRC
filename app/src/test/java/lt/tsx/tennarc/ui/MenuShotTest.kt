package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
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
class MenuShotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(
        TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO, TvApps.WEB_VIDEO_CASTER,
    ).map { TvApp(it, TvApps.knownTitles.getValue(it)) } + listOf(
        TvApp("com.webos.app.music", "Music"),
        TvApp("com.webos.app.livetv", "Live TV"),
    )
    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.BROWSER,
        volume = 10,
        apps = apps,
        inputs = listOf(
            TvApp("com.webos.app.hdmi1", "PC", input = true, plugged = true),
            TvApp("com.webos.app.hdmi2", "HDMI 2", input = true, plugged = true),
        ),
        channels = listOf(TvChannel("1", "1", "LRT"), TvChannel("2", "2", "LRT Plius")),
    )
    private val prefs = Prefs(
        autoKeyboard = false,
        tvName = "Living room",
        otherTvs = listOf(SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, ""), SavedTv("Kitchen", "192.168.1.51", TvKind.SAMSUNG, "")),
    )

    private fun show(dark: Boolean, tv: TvState = state, settings: Prefs = prefs, sheet: Sheet? = null, direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                TennaTheme(dark) {
                    ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                        RemoteScreen(tv, settings, NoRemoteActions, SnackbarHostState(), requestedSheet = sheet)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/menus/$name.png")
    }

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    private fun holdApp(dark: Boolean) {
        show(dark)
        compose.onNodeWithContentDescription("Netflix").performTouchInput { longClick() }
        shot("hold_app_${theme(dark)}")
    }

    @Test fun holdAppDark() = holdApp(true)

    @Test fun holdAppLight() = holdApp(false)

    private fun holdInput(dark: Boolean) {
        show(dark)
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("HDMI 2, input").performTouchInput { longClick() }
        shot("hold_input_${theme(dark)}")
    }

    @Test fun holdInputDark() = holdInput(true)

    @Test fun holdInputLight() = holdInput(false)

    private fun picker(dark: Boolean) {
        show(dark)
        compose.onNodeWithContentDescription("TVs, now Living room").performClick()
        shot("tv_picker_${theme(dark)}")
    }

    @Test fun pickerDark() = picker(true)

    @Test fun pickerLight() = picker(false)

    private val android = prefs.copy(kind = TvKind.ANDROID_TV)

    private fun tvInUse(dark: Boolean) {
        show(dark, state.copy(devLink = DevLink.Up), android, Sheet.SETTINGS)
        compose.onNodeWithText("Living room").performClick()
        shot("tv_menu_${theme(dark)}")
    }

    @Test fun tvInUseDark() = tvInUse(true)

    @Test fun tvInUseLight() = tvInUse(false)

    private fun tvArmed(dark: Boolean) {
        show(dark, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Bedroom").performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Remove").performClick()
        shot("tv_menu_armed_${theme(dark)}")
    }

    @Test fun tvArmedDark() = tvArmed(true)

    @Test fun tvArmedLight() = tvArmed(false)

    private fun choice(row: String, name: String, dark: Boolean) {
        show(dark, sheet = Sheet.SETTINGS)
        compose.onNodeWithText(row).performScrollTo().performClick()
        shot("${name}_${theme(dark)}")
    }

    @Test fun themeDark() = choice("Theme", "choice_theme", true)

    @Test fun themeLight() = choice("Theme", "choice_theme", false)

    @Test fun stripDark() = choice("Scroll strip", "choice_strip", true)

    @Test fun engineLight() = choice("Web search", "choice_engine", false)

    @Test fun themeRtl() {
        show(true, sheet = Sheet.SETTINGS, direction = LayoutDirection.Rtl)
        compose.onNodeWithText("Theme").performScrollTo().performClick()
        shot("choice_theme_rtl")
    }

    @Test fun holdRtl() {
        show(false, direction = LayoutDirection.Rtl)
        compose.onNodeWithContentDescription("Netflix").performTouchInput { longClick() }
        shot("hold_app_rtl")
    }

    @Test fun stripOverValue() {
        show(false, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Scroll strip").performScrollTo().performClick()
        compose.waitForIdle()
        val row = compose.onNode(hasText("Right") and isSelected()).fetchSemanticsNode()
        val value = compose.onNode(hasText("Right") and !hasAnyAncestor(isSelected()), useUnmergedTree = true).fetchSemanticsNode()
        val rowMiddle = row.positionOnScreen.y + row.size.height / 2f
        val valueMiddle = value.positionOnScreen.y + value.size.height / 2f
        assertEquals(valueMiddle, rowMiddle, 1.5f)
    }

    private fun frames(ms: Long) = repeat((ms / FRAME_MS).toInt()) {
        compose.mainClock.advanceTimeBy(FRAME_MS)
        compose.waitForIdle()
    }

    private fun film(name: String, open: () -> Unit) {
        compose.mainClock.autoAdvance = false
        open()
        var at = 0L
        for (step in FILM) {
            frames(step - at)
            at = step
            captureScreenRoboImage("build/shots/menus/film_${name}_${step}ms.png")
        }
    }

    @Test fun openingTheme() {
        show(true, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Theme").performScrollTo()
        compose.waitForIdle()
        film("theme") { compose.onNodeWithText("Theme").performClick() }
    }

    @Test fun openingStrip() {
        show(false, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Scroll strip").performScrollTo()
        compose.waitForIdle()
        film("strip") { compose.onNodeWithText("Scroll strip").performClick() }
    }

    @Test fun openingEngine() {
        show(false, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Web search").performScrollTo()
        compose.waitForIdle()
        film("engine") { compose.onNodeWithText("Web search").performClick() }
    }

    @Test fun openingThemeRtl() {
        show(true, sheet = Sheet.SETTINGS, direction = LayoutDirection.Rtl)
        compose.onNodeWithText("Theme").performScrollTo()
        compose.waitForIdle()
        film("theme_rtl") { compose.onNodeWithText("Theme").performClick() }
    }

    @Test fun openingPicker() {
        show(true)
        film("picker") { compose.onNodeWithContentDescription("TVs, now Living room").performClick() }
    }

    @Test fun openingHold() {
        show(false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Netflix").performTouchInput { down(center) }
        var waited = 0L
        while (compose.onAllNodesWithText("Hide").fetchSemanticsNodes().isEmpty() && waited < 2_000) {
            frames(FRAME_MS)
            waited += FRAME_MS
        }
        film("hold") {}
        compose.onNodeWithContentDescription("Netflix").performTouchInput { up() }
    }

    @Test fun closingTheme() {
        show(false, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Theme").performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Dark").performClick()
        for (step in listOf(48L, 96L)) {
            frames(48)
            captureScreenRoboImage("build/shots/menus/closing_theme_${step}ms.png")
        }
    }

    private fun rename(dark: Boolean) {
        show(dark)
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("HDMI 2, input").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Rename").performClick()
        frames(800)
        captureScreenRoboImage("build/shots/menus/dialog_rename_${theme(dark)}.png")
    }

    @Test fun renameDark() = rename(true)

    @Test fun renameLight() = rename(false)

    private fun edit(dark: Boolean) {
        show(dark, sheet = Sheet.SETTINGS)
        compose.onNodeWithText("Living room").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Edit").performClick()
        frames(800)
        captureScreenRoboImage("build/shots/menus/dialog_edit_${theme(dark)}.png")
    }

    @Test fun editDark() = edit(true)

    @Test fun editLight() = edit(false)

    private companion object {
        const val FRAME_MS = 16L
        val FILM = listOf(32L, 64L, 96L, 128L, 192L, 400L)
    }
}
