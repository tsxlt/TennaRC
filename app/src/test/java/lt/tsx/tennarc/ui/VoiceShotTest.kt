package lt.tsx.tennarc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import lt.tsx.tennarc.link.SearchEngine
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SearchField
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.components.VoiceSearch
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
class VoiceShotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, voice: VoiceSearch) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                            SearchField("", onQuery = {}, active = true, onActive = {}, onGo = {}, hint = "Search", voice = voice)
                            SearchResults(
                                query = "",
                                apps = emptyList(),
                                tvText = null,
                                engine = SearchEngine.DUCKDUCKGO,
                                recent = listOf("big buck bunny", "https://youtu.be/aqz-KE-bpKQ"),
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
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun mic() = shoot("40_search_mic", VoiceSearch(available = true))

    @Test fun listening() = shoot("41_search_listening", VoiceSearch(available = true).apply { listening = true })

    @Test fun hearing() = shoot(
        "42_search_hearing",
        VoiceSearch(available = true).apply {
            listening = true
            heard = "stranger things season five trailer"
            level = 0.6f
        },
    )
}
