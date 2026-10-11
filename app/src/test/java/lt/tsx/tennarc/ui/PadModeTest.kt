package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.data.touchpadIn
import lt.tsx.tennarc.data.withPad
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class PadModeTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.NETFLIX,
        volume = 10,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )
    private val touchpad = "Touchpad. Drag to move the pointer, tap to click, drag with two fingers to scroll."
    private val dpad = "Direction pad"

    @Test fun switchesAfterSearch() {
        var sheet by mutableStateOf<Sheet?>(null)
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, Prefs(autoKeyboard = false, askedFindTitles = true), NoRemoteActions, SnackbarHostState(), requestedSheet = sheet, onSheetRequestHandled = { sheet = null })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Touchpad").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertExists()
        compose.onNodeWithContentDescription("Arrows").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertDoesNotExist()

        sheet = Sheet.SEARCH
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Touchpad").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertExists()
        compose.onNodeWithContentDescription("Arrows").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertDoesNotExist()
    }

    @Test fun eachTvHasItsOwnPad() {
        val lounge = SavedTv("Lounge", "192.168.1.70", TvKind.WEBOS, mac = "")
        val bedroom = SavedTv("Bedroom", "192.168.1.50", TvKind.WEBOS, mac = "")
        var prefs by mutableStateOf(
            Prefs(autoKeyboard = false, tvName = lounge.name, host = lounge.host, otherTvs = listOf(bedroom))
                .withPad(lounge.host, touchpad = true, app = null)
                .withPad(bedroom.host, touchpad = false, app = null),
        )
        val actions = object : RemoteActions by NoRemoteActions {
            override fun updatePrefs(transform: (Prefs) -> Prefs) {
                prefs = transform(prefs)
            }
        }
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state.copy(host = prefs.host, foregroundAppId = null), prefs, actions, SnackbarHostState())
                }
            }
        }
        fun shows(touchpadUp: Boolean) {
            compose.onNodeWithContentDescription(touchpad).apply { if (touchpadUp) assertExists() else assertDoesNotExist() }
            compose.onNodeWithContentDescription(dpad).apply { if (touchpadUp) assertDoesNotExist() else assertExists() }
        }
        fun switchTo(tv: SavedTv, touchpadUp: Boolean) {
            compose.mainClock.autoAdvance = false
            prefs = prefs.switchTo(tv)
            repeat(3) {
                compose.mainClock.advanceTimeByFrame()
                val both = listOf(touchpad, dpad).all { compose.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty() }
                assertFalse("both pads at once", both)
            }
            shows(touchpadUp)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            shows(touchpadUp)
        }
        compose.waitForIdle()
        shows(touchpadUp = true)

        switchTo(bedroom, touchpadUp = false)
        switchTo(lounge, touchpadUp = true)

        compose.onNodeWithContentDescription("Arrows").performTouchInput { click() }
        compose.waitForIdle()
        shows(touchpadUp = false)
        switchTo(bedroom, touchpadUp = false)
        compose.onNodeWithContentDescription("Touchpad").performTouchInput { click() }
        compose.waitForIdle()
        shows(touchpadUp = true)
        switchTo(lounge, touchpadUp = false)
        switchTo(bedroom, touchpadUp = true)
        assertEquals(false, prefs.touchpadModes[lounge.host])
        assertEquals(true, prefs.touchpadModes[bedroom.host])

        val kitchen = SavedTv("Kitchen", "192.168.1.60", TvKind.WEBOS, mac = "")
        switchTo(kitchen, touchpadUp = true)
        assertEquals(true, prefs.touchpadModes[kitchen.host])
    }

    @Test fun eachAppKeepsItsOwnPad() {
        val lounge = SavedTv("Lounge", "192.168.1.70", TvKind.WEBOS, mac = "")
        val bedroom = SavedTv("Bedroom", "192.168.1.50", TvKind.WEBOS, mac = "")
        var prefs by mutableStateOf(
            Prefs(autoKeyboard = false, tvName = lounge.name, host = lounge.host, otherTvs = listOf(bedroom))
                .withPad(bedroom.host, touchpad = false, app = TvApps.NETFLIX)
                .withPad(lounge.host, touchpad = false, app = TvApps.NETFLIX),
        )
        var app by mutableStateOf(TvApps.NETFLIX)
        val actions = object : RemoteActions by NoRemoteActions {
            override fun updatePrefs(transform: (Prefs) -> Prefs) {
                prefs = transform(prefs)
            }
        }
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state.copy(host = prefs.host, foregroundAppId = app), prefs, actions, SnackbarHostState())
                }
            }
        }
        fun goTo(next: String, touchpadUp: Boolean) {
            app = next
            compose.waitForIdle()
            compose.onNodeWithContentDescription(touchpad).apply { if (touchpadUp) assertExists() else assertDoesNotExist() }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertDoesNotExist()

        goTo(TvApps.BROWSER, touchpadUp = true)
        compose.onNodeWithContentDescription("Arrows").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(false, prefs.touchpadIn(lounge.host, TvApps.BROWSER))

        goTo(TvApps.NETFLIX, touchpadUp = false)
        compose.onNodeWithContentDescription("Touchpad").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(true, prefs.touchpadIn(lounge.host, TvApps.NETFLIX))

        goTo(TvApps.BROWSER, touchpadUp = false)
        goTo(TvApps.NETFLIX, touchpadUp = true)

        prefs = prefs.switchTo(bedroom)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(touchpad).assertDoesNotExist()
        assertEquals(false, prefs.touchpadIn(bedroom.host, TvApps.NETFLIX))
    }
}
