package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.link.LinkRouter
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
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
class FlipSearchShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 14,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )
    private val titles = listOf(
        StreamingTitle("Das Signal", LinkRouter.classify("https://www.netflix.com/title/81312828"), year = 2024),
    ) + (1..7).map {
        StreamingTitle("Hello, a video with a long enough name to wrap $it", LinkRouter.classify("https://www.youtube.com/watch?v=YQHsXMglC9$it"), byline = "Channel $it", video = true)
    }
    private val actions = object : RemoteActions by NoRemoteActions {
        override suspend fun findTitles(query: String) = titles
    }

    private val flip = Fold(0.dp, 458.dp, 385.dp, 458.dp, vertical = false, separating = true)

    private var fold by mutableStateOf<Fold?>(flip)

    private fun remote(sheet: Sheet? = null) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, Prefs(autoKeyboard = false, findTitles = true, askedFindTitles = true), actions, SnackbarHostState(), requestedSheet = sheet, fold = fold)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun frames(ms: Long) = repeat((ms / 16).toInt()) {
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
    }

    @Test
    fun searching() {
        remote(Sheet.SEARCH)
        compose.onNode(hasSetTextAction()).performTextInput("hello")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/flip/search.png")
    }

    @Test
    fun more() {
        remote()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/flip/more_open.png")
    }

    @Test
    fun swipe() {
        remote()
        val back = compose.onNodeWithContentDescription("Back")
        back.performTouchInput { down(center) }
        listOf(40, 120, 200, 300).forEach { pull ->
            back.performTouchInput { moveTo(center - Offset(0f, pull.dp.toPx())) }
            compose.waitForIdle()
            captureScreenRoboImage("build/shots/flip/swipe_$pull.png")
        }
        back.performTouchInput { up() }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/flip/swipe_let_go.png")
    }

    @Test
    fun folding() {
        fold = null
        remote()
        compose.mainClock.autoAdvance = false
        fold = flip
        var at = 0L
        for (step in listOf(0L, 64L, 112L, 160L, 240L, 320L, 480L)) {
            frames(step - at)
            at = step
            captureScreenRoboImage("build/shots/flip/fold_${step}ms.png")
        }
    }
}
