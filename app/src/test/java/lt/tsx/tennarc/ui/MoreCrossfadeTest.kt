package lt.tsx.tennarc.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.DECK_HANDLE_TAG
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
class MoreCrossfadeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 14,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
        picture = TvPicture("normal", 60),
        soundOutput = "tv_speaker",
    )

    private fun setUp() {
        compose.setContent {
            TennaTheme(dark = true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, Prefs(autoKeyboard = false), NoRemoteActions, remember { SnackbarHostState() })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun frames(count: Int) = repeat(count) {
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
    }

    private val soundTab get() = compose.onNode(hasContentDescription("Sound") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun placed(name: String): Boolean =
        compose.onAllNodesWithContentDescription(name, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).any { it.layoutInfo.isPlaced }

    private val rowsShown get() = placed("Home") || placed("Rewind")
    private val moreShown get() = placed("Exit")

    private fun openSoundThenShut() {
        compose.onNodeWithContentDescription("More").performClick()
        settle()
        soundTab.performClick()
        settle()
        compose.onRoot().performTouchInput { click(Offset(center.x, 40.dp.toPx())) }
        settle()
        assertFalse("shut, More's keys should be gone", moreShown)
    }

    private fun assertAtRest(how: String) {
        settle()
        val rows = rowsShown
        val more = moreShown
        assertFalse("$how: More came to rest part way, the deck's rows and More's keys both drawn", rows && more)
        assertTrue("$how: neither the deck's rows nor More's keys are there", rows || more)
    }

    private fun assertOpen(how: String) {
        assertAtRest(how)
        assertTrue("$how: More should be open", moreShown)
        assertFalse("$how: the deck's rows should have faded out under More's keys", rowsShown)
    }

    private fun middleShade(area: Rect): Int {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val inset = area.deflate(minOf(area.width, area.height) / 4f)
        val shades = mutableListOf<Int>()
        var y = inset.top.toInt()
        while (y < inset.bottom.toInt()) {
            var x = inset.left.toInt()
            while (x < inset.right.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24
                shades += (((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255)) / 3 * alpha / 255
                x += 2
            }
            y += 2
        }
        return shades.sorted()[shades.size / 2]
    }

    private fun shoot(name: String) {
        val drawn = compose.onRoot().captureToImage().asAndroidBitmap()
        val shown = android.graphics.Bitmap.createBitmap(drawn.width, drawn.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(shown).apply {
            drawColor(android.graphics.Color.BLACK)
            drawBitmap(drawn, 0f, 0f, null)
        }
        val file = java.io.File("build/shots/more_crossfade/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { shown.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertShelfNotBlack(how: String) {
        val tab = middleShade(soundTab.fetchSemanticsNode().boundsInRoot)
        val choice = middleShade(compose.onNodeWithText("HDMI ARC").fetchSemanticsNode().boundsInRoot)
        assertTrue("$how: the open tab is near-black ($tab)", tab >= NOT_BLACK)
        assertTrue("$how: the row of choices is near-black ($choice)", choice >= NOT_BLACK)
    }

    @Test
    fun `a tap on More opens it all the way`() {
        setUp()
        openSoundThenShut()
        compose.onNodeWithContentDescription("More").performClick()
        assertOpen("tap")
        assertShelfNotBlack("tap")
        shoot("01_tap_open")
    }

    @Test
    fun `a swipe up from the deck opens it all the way`() {
        setUp()
        openSoundThenShut()
        compose.onNodeWithContentDescription("Back").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 560.dp.toPx()))
            up()
        }
        assertOpen("swipe")
        assertShelfNotBlack("swipe")
    }

    @Test
    fun `held part way up, the shelf is faint, not black`() {
        setUp()
        openSoundThenShut()
        val back = compose.onNodeWithContentDescription("Back")
        back.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 60.dp.toPx()))
        }
        compose.waitForIdle()
        shoot("02_held_low")
        assertShelfNotBlack("held low")
        back.performTouchInput { moveTo(center - Offset(0f, 140.dp.toPx())) }
        compose.waitForIdle()
        shoot("03_held_higher")
        assertShelfNotBlack("held higher")
        back.performTouchInput { up() }
        assertAtRest("let go")
    }

    @Test
    fun `caught from above while rising, it comes to rest`() {
        setUp()
        openSoundThenShut()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("More").performClick()
        frames(2)
        compose.onRoot().performTouchInput {
            down(Offset(center.x, 40.dp.toPx()))
            repeat(2) { moveBy(Offset(0f, 30.dp.toPx())) }
        }
        frames(3)
        compose.onRoot().performTouchInput { repeat(4) { moveBy(Offset(0f, 30.dp.toPx())) } }
        frames(3)
        shoot("04_caught_above_held")
        compose.onRoot().performTouchInput { up() }
        frames(2)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        shoot("05_caught_above_let_go")
        assertAtRest("caught from above")
    }

    @Test
    fun `caught from above while rising and let go high, it opens`() {
        setUp()
        openSoundThenShut()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("More").performClick()
        frames(10)
        compose.onRoot().performTouchInput {
            down(Offset(center.x, 40.dp.toPx()))
            moveBy(Offset(0f, 20.dp.toPx()))
            moveBy(Offset(0f, 10.dp.toPx()))
        }
        frames(2)
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, -30.dp.toPx()))
            up()
        }
        compose.mainClock.autoAdvance = true
        assertAtRest("caught from above, let go high")
    }

    @Test
    fun `caught by the handle while rising and let go up, it opens`() {
        setUp()
        openSoundThenShut()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("More").performClick()
        frames(4)
        val handle = compose.onNodeWithTag(DECK_HANDLE_TAG)
        handle.performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(0f, -40.dp.toPx())) }
            up()
        }
        compose.mainClock.autoAdvance = true
        assertOpen("caught by the handle")
        assertShelfNotBlack("caught by the handle")
    }

    @Test
    fun `flung on its keys, it comes to rest`() {
        setUp()
        openSoundThenShut()
        compose.onNodeWithContentDescription("More").performClick()
        settle()
        compose.onNodeWithContentDescription("Exit").performTouchInput { swipeDown(centerY, centerY + 80.dp.toPx(), durationMillis = 60) }
        assertAtRest("flung down a little")
        compose.onNodeWithContentDescription("More").performClick()
        settle()
        compose.onNodeWithContentDescription("Exit").performTouchInput { swipeDown(centerY, centerY + 400.dp.toPx(), durationMillis = 120) }
        assertAtRest("flung down a long way")
    }

    @Test
    fun `dragged down from its keys, it shuts`() {
        setUp()
        for (key in listOf("Exit", "Settings", "1")) {
            compose.onNodeWithContentDescription("More").performClick()
            settle()
            assertOpen("open before dragging $key")
            val node = compose.onAllNodesWithContentDescription(key).onFirst().takeIf { runCatching { it.fetchSemanticsNode() }.isSuccess }
                ?: compose.onAllNodesWithText(key).onFirst()
            node.performTouchInput { swipeDown(centerY, centerY + 150.dp.toPx(), durationMillis = 900) }
            settle()
            assertFalse("dragged down from $key, More should be shut", moreShown)
        }
    }

    @Test
    fun `the TV dropping while it is open, it still drags down`() {
        val tv = androidx.compose.runtime.mutableStateOf(state)
        compose.setContent {
            TennaTheme(dark = true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(tv.value, Prefs(autoKeyboard = false), NoRemoteActions, remember { SnackbarHostState() })
                }
            }
        }
        compose.waitForIdle()
        for (from in listOf("handle", "Exit", "1")) {
            tv.value = state
            settle()
            compose.onNodeWithContentDescription("More").performClick()
            settle()
            assertOpen("open before the TV drops, $from")
            tv.value = state.copy(connection = Connection.Connecting)
            settle()
            val node = when (from) {
                "handle" -> compose.onNodeWithTag(DECK_HANDLE_TAG)
                else -> compose.onAllNodesWithContentDescription(from).onFirst().takeIf { runCatching { it.fetchSemanticsNode() }.isSuccess }
                    ?: compose.onAllNodesWithText(from).onFirst()
            }
            node.performTouchInput { swipeDown(centerY, centerY + 150.dp.toPx(), durationMillis = 900) }
            settle()
            assertFalse("TV dropped, dragged down from $from: More should be shut", moreShown)
        }
    }

    @Test
    fun `the TV changing mid-drag, the drag keeps going`() {
        val tv = androidx.compose.runtime.mutableStateOf(state)
        compose.setContent {
            TennaTheme(dark = true) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(tv.value, Prefs(autoKeyboard = false), NoRemoteActions, remember { SnackbarHostState() })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("More").performClick()
        settle()
        assertOpen("open before dragging")
        val exit = compose.onNodeWithContentDescription("Exit")
        exit.performTouchInput { down(center) }
        repeat(10) {
            exit.performTouchInput { moveBy(Offset(0f, 8.dp.toPx())) }
            compose.waitForIdle()
        }
        tv.value = state.copy(connection = Connection.Offline, picture = null, soundOutput = null)
        compose.waitForIdle()
        repeat(10) {
            compose.onRoot().performTouchInput { moveBy(Offset(0f, 8.dp.toPx())) }
            compose.waitForIdle()
        }
        compose.onRoot().performTouchInput { up() }
        settle()
        assertFalse("the TV changed mid-drag: More should be shut", moreShown)
    }

    @Test
    fun `back from the background it is still open`() {
        setUp()
        compose.onNodeWithContentDescription("More").performClick()
        settle()
        soundTab.performClick()
        settle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertOpen("back from the background")
        assertShelfNotBlack("back from the background")
        shoot("06_resumed")
    }

    @Test
    fun `backgrounded while rising, it finishes on coming back`() {
        setUp()
        openSoundThenShut()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("More").performClick()
        frames(4)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        frames(4)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.autoAdvance = true
        assertOpen("backgrounded while rising")
        assertShelfNotBlack("backgrounded while rising")
    }

    private companion object {
        const val NOT_BLACK = 20
    }
}
