package lt.tsx.tennarc.ui.sheets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.tvKeys
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class TvListTest {
    @get:Rule val compose = createComposeRule()

    private var prefs by mutableStateOf(
        Prefs(tvName = "Tenna", host = "192.168.1.70", otherTvs = listOf(SavedTv("Smart TV Pro", "192.168.1.123", TvKind.ANDROID_TV, "")), demoListed = true),
    )

    private fun show() = compose.setContent {
        TennaTheme {
            SettingsSheetContent(prefs = prefs, onUpdate = { change -> prefs = change(prefs) }, onFindTvs = { emptyList() }, onForgetPairing = {})
        }
    }

    @Test fun aTapSwitchesAndKeepsTheOrder() {
        show()
        val before = prefs.tvKeys
        compose.onNodeWithText("Smart TV Pro").performClick()
        compose.waitForIdle()
        assertEquals("192.168.1.123", prefs.host)
        assertEquals(before, prefs.tvKeys)
        compose.onNodeWithText("Use this TV").assertDoesNotExist()
    }

    @Test fun aHoldDragsATvToANewPlace() {
        show()
        compose.onNodeWithText("Tenna").performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(20) { moveBy(Offset(0f, 12f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("192.168.1.123", "192.168.1.70", "demo"), prefs.tvOrder)
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun talkBackMovesATvOnePlace() {
        show()
        compose.onNodeWithText("Smart TV Pro").performCustomAccessibilityActionWithLabel("Move down")
        compose.waitForIdle()
        assertEquals(listOf("192.168.1.70", "demo", "192.168.1.123"), prefs.tvOrder)
        compose.onNodeWithText("Tenna").performCustomAccessibilityActionWithLabel("Move down")
        compose.waitForIdle()
        assertEquals(listOf("demo", "192.168.1.70", "192.168.1.123"), prefs.tvKeys)
    }

    @Test fun aHoldLetGoOpensTheMenuAndRemoveAsksTwice() {
        show()
        compose.onNodeWithText("Smart TV Pro").performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertEquals(1, prefs.otherTvs.size)
        compose.onNodeWithText("Tap again to remove").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SavedTv>(), prefs.otherTvs)
    }

    @Test fun theTvInUseOffersEditAndForget() {
        show()
        compose.onNodeWithText("Tenna").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Forget pairing").assertExists()
        compose.onNodeWithText("Use this TV").assertDoesNotExist()
    }
}
