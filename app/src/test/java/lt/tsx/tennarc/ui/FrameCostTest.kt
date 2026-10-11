package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.DECK_HANDLE_TAG
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class FrameCostTest {
    @get:Rule(order = 0) val touchMode = object : ExternalResource() {
        override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @get:Rule(order = 1) val compose = createComposeRule()

    private val counter = Recompositions()

    private var requested by mutableStateOf<Sheet?>(null)

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.PHOTO_VIDEO, "com.webos.app.music", "com.webos.app.discovery", "spotify-beehive")
            .map { TvApp(it, TvApps.knownTitles[it] ?: it) },
    )
    private val prefs = Prefs(
        autoKeyboard = false,
        findTitles = true,
        askedFindTitles = true,
        tvName = "Tenna",
        otherTvs = listOf(SavedTv("Bedroom", "192.168.1.51", TvKind.ROKU, "")),
    )

    private fun show() {
        compose.setContent {
            counter.Attach()
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), requestedSheet = requested, onSheetRequestHandled = { requested = null })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        counter.frameDone()
    }

    private fun frames(n: Int) = repeat(n) { frame() }

    private fun measure(title: String, act: () -> Unit, check: Recompositions.() -> Unit) {
        counter.reset()
        counter.measuring = true
        act()
        counter.measuring = false
        println(counter.report(title))
        counter.check()
    }

    private fun Recompositions.composesNothing(what: String) = assertEquals("$what composed: ${report(what)}", 0, total)

    private fun Recompositions.fewFrames(what: String, most: Int, each: Int) {
        assertTrue("$what: ${frames.count { it > 0 }} frames composed: ${report(what)}", frames.count { it > 0 } <= most)
        assertTrue("$what: ${frames.max()} in one frame: ${report(what)}", frames.max() <= each)
    }

    private fun Recompositions.remoteHeldStill(what: String, besides: String? = null) {
        listOf("KeyButton", "DeckKey", "DeckIcon", "AppFace", "MoreSheetContent", "PadKey", "DPad").filter { it != besides }.forEach { name ->
            assertEquals("$what composed $name: ${report(what)}", 0, of(name))
        }
    }

    private fun centreOf(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center

    private val Float.px get() = with(compose.density) { this@px.dp.toPx() }

    @Test fun `the deck swiped up into More composes only as the gesture starts and crosses half way`() {
        show()
        val start = centreOf("Back")
        val root = compose.onRoot()
        root.performTouchInput { down(start) }
        frame()
        measure("more swipe, the first frames of the pull", { root.performTouchInput { moveTo(start - Offset(0f, 40f.px)) }; frames(3) }) {
            fewFrames("starting the pull", most = 1, each = 40)
            remoteHeldStill("starting the pull", besides = "KeyButton")
            assertTrue(report("starting the pull"), of("KeyButton") <= 3)
        }
        measure("more swipe, 20 frames of drag", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(start - Offset(0f, (40 + 12 * i).toFloat().px)) }
                frame()
            }
        }) {
            fewFrames("the drag", most = 1, each = 20)
            remoteHeldStill("the drag")
        }
        measure("more swipe, release and settle", { root.performTouchInput { up() }; frames(60) }) {
            fewFrames("settling", most = 1, each = 20)
            remoteHeldStill("settling")
        }
    }

    @Test fun `More dragged by its handle composes nothing`() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        frames(60)
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG).fetchSemanticsNode().boundsInRoot.center
        val root = compose.onRoot()
        root.performTouchInput { down(handle) }
        frame()
        measure("more handle, 20 frames down", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(handle + Offset(0f, (8 * i).toFloat().px)) }
                frame()
            }
        }) { composesNothing("dragging the handle down") }
        measure("more handle, 20 frames back up", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(handle + Offset(0f, (160 - 8 * i).toFloat().px)) }
                frame()
            }
        }) { composesNothing("dragging the handle back up") }
        measure("more handle, release and settle", { root.performTouchInput { up() }; frames(60) }) { composesNothing("settling open") }
    }

    @Test fun `More dragged by its keys composes nothing`() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        frames(60)
        val start = compose.onNodeWithTag(DECK_HANDLE_TAG).fetchSemanticsNode().boundsInRoot.center + Offset(0f, 120f.px)
        val root = compose.onRoot()
        root.performTouchInput { down(start) }
        frame()
        measure("more keys, 20 frames down", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(start + Offset(0f, (8 * i).toFloat().px)) }
                frame()
            }
        }) { composesNothing("dragging More by its keys") }
        measure("more keys, release and settle", { root.performTouchInput { up() }; frames(60) }) {
            fewFrames("settling", most = 5, each = 25)
            remoteHeldStill("settling")
        }
    }

    @Test fun `More dragged from the dim above it composes nothing`() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        frames(60)
        val start = compose.onNodeWithTag(DECK_HANDLE_TAG).fetchSemanticsNode().boundsInRoot.center - Offset(0f, 60f.px)
        val root = compose.onRoot()
        root.performTouchInput { down(start) }
        frame()
        root.performTouchInput { moveTo(start + Offset(0f, 24f.px)) }
        frames(3)
        measure("more from above, 20 frames down", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(start + Offset(0f, (24 + 8 * i).toFloat().px)) }
                frame()
            }
        }) { composesNothing("dragging More down from above it") }
        measure("more from above, release and settle", { root.performTouchInput { up() }; frames(60) }) {
            fewFrames("settling", most = 5, each = 25)
            remoteHeldStill("settling")
        }
    }

    @Test fun `the app deck unrolled composes only where it crosses half way`() {
        show()
        val start = centreOf("All apps")
        val root = compose.onRoot()
        root.performTouchInput { down(start) }
        frame()
        root.performTouchInput { moveTo(start + Offset(0f, 30f.px)) }
        frames(3)
        measure("deck unroll, 20 frames of drag", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(start + Offset(0f, (30 + 10 * i).toFloat().px)) }
                frame()
            }
        }) {
            fewFrames("unrolling", most = 1, each = 25)
            remoteHeldStill("unrolling")
        }
        measure("deck unroll, release and settle", { root.performTouchInput { up() }; frames(60) }) { composesNothing("settling") }
    }

    @Test fun `a sheet dragged composes none of the app's own`() {
        show()
        compose.onNodeWithContentDescription("Power").performClick()
        frames(60)
        val handle = compose.onNodeWithContentDescription("Drag handle")
        handle.performTouchInput { down(center) }
        frame()
        handle.performTouchInput { moveBy(Offset(0f, 30f.px)) }
        frames(3)
        measure("sheet drag, 20 frames of drag", {
            repeat(20) {
                handle.performTouchInput { moveBy(Offset(0f, 8f.px)) }
                frame()
            }
        }) { assertEquals(report("sheet drag"), 0, ours) }
        measure("sheet drag, release and settle", { handle.performTouchInput { up() }; frames(60) }) {
            fewFrames("the sheet going", most = 40, each = 25)
            remoteHeldStill("the sheet going")
        }
    }

    @Test fun `switching TVs in the picker composes only as it opens and as the switch lands`() {
        show()
        measure("tv picker, opening", { compose.onNodeWithContentDescription("TVs, now Tenna").performClick(); frames(30) }) {
            fewFrames("opening", most = 2, each = 50)
            remoteHeldStill("opening")
        }
        measure("tv picker, switching", { compose.onNodeWithText("Bedroom").performClick(); frames(30) }) {
            fewFrames("switching", most = 3, each = 40)
            remoteHeldStill("switching")
        }
    }

    @Test fun `search opens and closes without building the remote again`() {
        show()
        measure("search, opening", { requested = Sheet.SEARCH; frames(40) }) {
            remoteHeldStill("opening search")
            fewFrames("opening search", most = 3, each = 60)
        }
        measure("search, closing", { compose.onNodeWithContentDescription("Close search").performClick(); frames(40) }) {
            remoteHeldStill("closing search")
            fewFrames("closing search", most = 3, each = 40)
        }
        compose.onNodeWithContentDescription("Close search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Home").assertExists()
        measure("search, opening again", { requested = Sheet.SEARCH; frames(40) }) { remoteHeldStill("opening search again") }
    }

    @Test fun `a held D-pad press only draws`() {
        show()
        val pad = compose.onNodeWithContentDescription("Direction pad").fetchSemanticsNode().boundsInRoot
        val up = Offset(pad.center.x, pad.top + pad.height * 0.15f)
        val root = compose.onRoot()
        measure("dpad, a held press and its light going", {
            root.performTouchInput { down(up) }
            frames(10)
            root.performTouchInput { up() }
            frames(40)
        }) { composesNothing("the D-pad's press") }
    }

    @Test fun `a slide along the volume composes nothing for its steps`() {
        show()
        val start = centreOf("Volume down")
        val root = compose.onRoot()
        root.performTouchInput { down(start) }
        frame()
        measure("volume slide, 20 frames", {
            for (i in 1..20) {
                root.performTouchInput { moveTo(start + Offset((4 * i).toFloat().px, 0f)) }
                frame()
            }
        }) {
            assertEquals(report("volume slide"), 0, of("VolumeCapsule") + of("DeckKey"))
            fewFrames("the slide", most = 1, each = 6)
        }
        measure("volume slide, release and settle", { root.performTouchInput { up() }; frames(60) }) { composesNothing("the meter fading") }
    }

    @Test fun `a deck key's press fades in and out without composing`() {
        show()
        val root = compose.onRoot()
        val home = centreOf("Home")
        measure("deck key, a tap and its well fading", {
            root.performTouchInput { down(home) }
            frames(6)
            root.performTouchInput { up() }
            frames(40)
        }) {
            fewFrames("a key's press", most = 2, each = 6)
        }
    }

    @Test fun `an app's press`() {
        show()
        val root = compose.onRoot()
        val app = centreOf("Netflix")
        measure("app icon, a tap and its face fading", {
            root.performTouchInput { down(app) }
            frames(6)
            root.performTouchInput { up() }
            frames(40)
        }) {
            assertTrue(report("app press"), total <= 30)
        }
    }
}
