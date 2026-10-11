package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class FindTitlesAskTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )
    private val ask = "To find them, TennaRC sends what you type to YouTube, JustWatch and Wikidata. You can change this in Settings."

    private var prefs by mutableStateOf(Prefs(autoKeyboard = false))

    private fun search(): Unit = compose.setContent {
        TennaTheme {
            ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                val actions = object : RemoteActions by NoRemoteActions {
                    override fun updatePrefs(transform: (Prefs) -> Prefs) {
                        prefs = transform(prefs)
                    }
                }
                RemoteScreen(state, prefs, actions, SnackbarHostState(), requestedSheet = Sheet.SEARCH)
            }
        }
    }.also { compose.waitForIdle() }

    @Test fun `searching for the first time asks, and yes turns lookups on`() {
        search()
        compose.onNodeWithText(ask).assertExists()
        captureScreenRoboImage("build/shots/find_titles_ask.png")
        compose.onNodeWithText("Turn on").performClick()
        compose.waitForIdle()
        assertTrue(prefs.findTitles)
        assertTrue(prefs.askedFindTitles)
        compose.onNodeWithText(ask).assertDoesNotExist()
    }

    @Test fun `not now keeps lookups off and stops asking`() {
        search()
        compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
        assertFalse(prefs.findTitles)
        assertTrue(prefs.askedFindTitles)
        compose.onNodeWithText(ask).assertDoesNotExist()
    }

    @Test fun `an answered question is not asked again`() {
        prefs = Prefs(autoKeyboard = false, askedFindTitles = true)
        search()
        compose.onNodeWithText(ask).assertDoesNotExist()
    }
}
