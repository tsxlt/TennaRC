package lt.tsx.tennarc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.SearchResults
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchResultsTest {
    @get:Rule val compose = createComposeRule()

    private val strangerThings = StreamingTitle("Stranger Things", LinkRouter.classify("https://www.netflix.com/title/80057281"))

    @Test
    fun `found titles stay up while more letters are typed`() {
        var query by mutableStateOf("stranger")
        val asked = mutableListOf<String>()
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    SearchResults(
                        query = query,
                        apps = listOf(TvApp(TvApps.NETFLIX, "Netflix")),
                        tvText = null,
                        engine = null,
                        recent = emptyList(),
                        findTitles = { typed ->
                            asked += typed
                            if (typed == "stranger") listOf(strangerThings) else emptyList()
                        },
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
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Stranger Things").assertExists()

        query = "stranger t"
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("Stranger Things").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Stranger Things").assertExists()

        query = "strangers"
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Stranger Things").assertDoesNotExist()

        query = "stranger"
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Stranger Things").assertExists()
        assertEquals(listOf("stranger", "stranger t", "strangers"), asked)
    }
}
