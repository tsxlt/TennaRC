package lt.tsx.tennarc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyboardPullTest {
    @get:Rule val compose = createComposeRule()

    private class FakeKeyboard(private val holdable: Boolean = true) : KeyboardInHand {
        override var travel = 0
        var starts = 0
        var at = 0f
        var finished: Boolean? = null

        override fun start(): Boolean {
            starts++
            if (holdable) travel = KEYBOARD.toInt()
            return holdable
        }

        override fun moveTo(pulled: Float) {
            at = pulled
        }

        override fun finish(shown: Boolean) {
            finished = shown
            travel = 0
        }
    }

    private var keyboardUp by mutableStateOf(true)
    private var liveInset by mutableIntStateOf(KEYBOARD.toInt())
    private var aways = 0
    private var awayAt: Pair<Float, Int>? = null
    private var putAways = 0
    private var bringBacks = 0
    private var slop = 0f
    private lateinit var pull: SheetKeyboardPull
    private lateinit var list: ScrollState

    private var sheetDragged = 0f
    private var sheetScrolled = 0f
    private val material = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            sheetScrolled += available.y
            return available
        }
    }

    private fun sheet(keyboard: FakeKeyboard = FakeKeyboard(), growing: Boolean = false): FakeKeyboard {
        compose.setContent {
            slop = LocalViewConfiguration.current.touchSlop
            val scope = rememberCoroutineScope()
            pull = remember {
                SheetKeyboardPull(
                    keyboard = keyboard,
                    scope = scope,
                    keyboardUp = { keyboardUp },
                    inset = { liveInset },
                    fling = FLING,
                    far = FAR,
                    putAway = { putAways++ },
                    bringBack = { bringBacks++ },
                    onAway = {
                        aways++
                        awayAt = pull.pulled to pull.height
                    },
                )
            }
            list = rememberScrollState()
            Box(Modifier.fillMaxSize().nestedScroll(material).draggable(rememberDraggableState { sheetDragged += it }, Orientation.Vertical)) {
                val size = if (growing) {
                    val room = with(LocalDensity.current) { (SHEET + min(pull.pulled, KEYBOARD)).toDp() }
                    Modifier.fillMaxWidth().height(room)
                } else {
                    Modifier.fillMaxSize()
                }
                Column(Modifier.sheetKeyboardPull(pull).then(size)) {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("handle"))
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(list).testTag("list")) {
                        Spacer(Modifier.height(2000.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        return keyboard
    }

    private fun TouchInjectionScope.dragSlowly(by: Float, from: Offset = Offset(centerX, 10f)) {
        down(from)
        val steps = 20
        repeat(steps) { moveBy(Offset(0f, by / steps)) }
        advanceEventTime(200)
    }

    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, 1.5f)

    @Test
    fun `a drag on the handle takes the keyboard down under the finger`() {
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput { dragSlowly(80f) }

        assertEquals(1, keyboard.starts)
        assertNear(80f - slop, pull.pulled)
        assertEquals(pull.pulled, keyboard.at)
        assertEquals(0f, sheetDragged)
    }

    @Test
    fun `a drag on a list takes the keyboard down before the list scrolls`() {
        val keyboard = sheet()
        compose.runOnIdle { runBlocking { list.scrollTo(50) } }
        compose.onNodeWithTag("list").performTouchInput { dragSlowly(80f) }

        assertEquals(1, keyboard.starts)
        assertNear(80f - slop, keyboard.at)
        assertEquals(50, list.value)
        assertEquals(0f, sheetScrolled)
    }

    @Test
    fun `past the keyboard the list scrolls back to its top, then the sheet goes on by itself`() {
        val keyboard = sheet()
        compose.runOnIdle { runBlocking { list.scrollTo(50) } }
        compose.onNodeWithTag("list").performTouchInput { dragSlowly(250f) }

        assertEquals(0, list.value)
        assertNear(250f - slop - 50f, pull.pulled)
        assertTrue(keyboard.at >= KEYBOARD)
        assertEquals(0f, sheetScrolled)
    }

    @Test
    fun `a list scrolled up leaves the keyboard be`() {
        val keyboard = sheet()
        compose.onNodeWithTag("list").performTouchInput { dragSlowly(-80f, from = Offset(centerX, 200f)) }

        assertEquals(0, keyboard.starts)
        assertNear(80f - slop, list.value.toFloat())
        assertFalse(pull.held)
    }

    @Test
    fun `with no keyboard up the drag is the sheet's own`() {
        keyboardUp = false
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput { dragSlowly(80f) }
        assertNear(80f - slop, sheetDragged)
        compose.onNodeWithTag("handle").performTouchInput { up() }

        compose.onNodeWithTag("list").performTouchInput { dragSlowly(80f) }
        assertNear(80f - slop, sheetScrolled)
        compose.onNodeWithTag("list").performTouchInput { up() }

        assertEquals(0, keyboard.starts)
        assertEquals(0f, pull.pulled)
    }

    @Test
    fun `letting go partway brings the keyboard back up with the sheet`() {
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput {
            dragSlowly(100f)
            up()
        }
        compose.waitForIdle()

        assertEquals(true, keyboard.finished)
        assertEquals(0f, keyboard.at)
        assertEquals(0f, pull.pulled)
        assertFalse(pull.held)
        assertEquals(0, aways)
    }

    @Test
    fun `letting go of a list partway brings the keyboard back up too`() {
        val keyboard = sheet()
        compose.onNodeWithTag("list").performTouchInput {
            dragSlowly(100f)
            up()
        }
        compose.waitForIdle()

        assertEquals(true, keyboard.finished)
        assertEquals(0f, pull.pulled)
        assertEquals(0, aways)
    }

    @Test
    fun `a flick sends the keyboard and the sheet away`() {
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput { swipeDown(startY = 10f, endY = 90f, durationMillis = 50) }
        compose.waitForIdle()

        assertEquals(false, keyboard.finished)
        assertEquals(1, aways)
    }

    @Test
    fun `a sheet growing back its keyboard room still slides all the way out before it closes`() {
        sheet(FakeKeyboard(holdable = false), growing = true)
        compose.onNodeWithTag("handle").performTouchInput { swipeDown(startY = 10f, endY = 90f, durationMillis = 50) }
        compose.waitForIdle()

        assertEquals(1, aways)
        val (pulled, height) = awayAt!!
        assertEquals(SHEET.toInt() + KEYBOARD.toInt(), height)
        assertTrue("closed at $pulled of $height", pulled >= height)
    }

    @Test
    fun `the keyboard all the way down but the sheet barely moved, it all comes back`() {
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput {
            dragSlowly(KEYBOARD + FAR - 10f + slop)
            up()
        }
        compose.waitForIdle()

        assertEquals(true, keyboard.finished)
        assertEquals(0, aways)
    }

    @Test
    fun `the sheet itself well on its way past the keyboard, it goes`() {
        val keyboard = sheet()
        compose.onNodeWithTag("handle").performTouchInput {
            dragSlowly(KEYBOARD + FAR + 20f + slop)
            up()
        }
        compose.waitForIdle()

        assertEquals(false, keyboard.finished)
        assertEquals(1, aways)
    }

    @Test
    fun `caught as it settles, it carries on from there with the keyboard still in hand`() {
        val keyboard = sheet()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("handle").performTouchInput {
            dragSlowly(100f)
            up()
        }
        compose.mainClock.advanceTimeBy(50)
        val caught = pull.pulled
        assertTrue("still on its way back: $caught", caught > 0f && caught < 100f - slop)
        compose.onNodeWithTag("handle").performTouchInput { dragSlowly(100f) }
        compose.mainClock.advanceTimeBy(500)

        assertNull(keyboard.finished)
        assertEquals(1, keyboard.starts)
        assertTrue(pull.held)
        assertTrue("caught at $caught, now ${pull.pulled}", pull.pulled > caught)
        assertEquals(pull.pulled, keyboard.at)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `the room kept for the keyboard holds while the sheet is pulled`() {
        sheet()
        val density = Density(1f)
        val live = object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getTop(density: Density) = 0
            override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getBottom(density: Density) = liveInset
        }
        val insets = pull.insets(live)
        assertEquals(KEYBOARD.toInt(), insets.getBottom(density))

        compose.onNodeWithTag("handle").performTouchInput { dragSlowly(100f) }
        val pulled = pull.pulled.roundToInt()
        liveInset = KEYBOARD.toInt() - pulled
        assertEquals(KEYBOARD.toInt(), insets.getBottom(density))
        liveInset = 0
        assertEquals(pulled, insets.getBottom(density))
        liveInset = 20

        compose.onNodeWithTag("handle").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(20, insets.getBottom(density))
    }

    @Test
    fun `where the keyboard can't be held, it's put away for the drag and brought back with the sheet`() {
        sheet(FakeKeyboard(holdable = false))
        compose.onNodeWithTag("handle").performTouchInput { dragSlowly(FAR - 10f + slop) }
        assertEquals(1, putAways)
        assertNear(FAR - 10f, pull.pulled)

        compose.onNodeWithTag("handle").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, bringBacks)
        assertEquals(0, aways)
    }

    @Test
    fun `letting go sends it away on a flick or once past far enough, otherwise back`() {
        assertTrue(pullLetsGo(pulled = 10f, velocity = 500f, far = 100f, fling = 400f))
        assertTrue(pullLetsGo(pulled = 150f, velocity = 0f, far = 100f, fling = 400f))
        assertFalse(pullLetsGo(pulled = 50f, velocity = 0f, far = 100f, fling = 400f))
        assertFalse(pullLetsGo(pulled = 50f, velocity = 300f, far = 100f, fling = 400f))
        assertFalse(pullLetsGo(pulled = 500f, velocity = -500f, far = 100f, fling = 400f))
    }

    private companion object {
        const val KEYBOARD = 120f
        const val SHEET = 300f
        const val FAR = 40f
        const val FLING = 400f
    }
}
