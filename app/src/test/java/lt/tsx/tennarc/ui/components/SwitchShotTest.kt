package lt.tsx.tennarc.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.Sheet
import lt.tsx.tennarc.ui.theme.ProvideGround
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.glass
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
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi")
class SwitchShotTest {
    @get:Rule val compose = createComposeRule()

    private var checked by mutableStateOf(false)
    private var changes = 0

    private fun show(dark: Boolean, on: Boolean, rtl: Boolean = false, enabled: Boolean = true) {
        checked = on
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Card(Modifier.testTag(TAG)) {
                            val press = remember { MutableInteractionSource() }
                            val change = { value: Boolean ->
                                changes++
                                checked = value
                            }
                            Row(
                                Modifier
                                    .testTag(ROW)
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .toggleable(checked, press, LocalIndication.current, enabled, Role.Switch, change)
                                    .padding(horizontal = PANEL_INSET + PANEL_ROW_PAD),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Keep screen on", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                GlassSwitch(checked, Modifier.testTag(SWITCH), onCheckedChange = change, enabled = enabled, interactionSource = press)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
        val frost = glass()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(modifier.width(240.dp).background(MaterialTheme.colorScheme.background).frostBehind()) {
                Column(Modifier.fillMaxWidth().glass(frost, MaterialTheme.shapes.extraLarge).padding(16.dp)) {
                    ProvideGround(frost.fill) {
                        Column(Modifier.fillMaxWidth().panel(panelGlass(), MaterialTheme.shapes.large, inset = 0.dp).padding(vertical = PANEL_ROWS_PAD)) {
                            CompositionLocalProvider(LocalIndication provides rememberRowWash()) {
                                ProvideGround(panelGround()) { content() }
                            }
                        }
                    }
                }
            }
        }
    }

    private var frame = 0L

    private fun film(name: String, until: Long = 480, step: () -> Unit = {}) {
        val end = frame + until
        while (frame <= end) {
            step()
            compose.onNodeWithTag(TAG).captureRoboImage("build/shots/switch/${name}_${frame.toString().padStart(3, '0')}ms.png")
            compose.mainClock.advanceTimeBy(FRAME_MS)
            compose.waitForIdle()
            frame += FRAME_MS
        }
    }

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    private fun flip(dark: Boolean, from: Boolean, rtl: Boolean = false) {
        show(dark, from, rtl)
        compose.mainClock.autoAdvance = false
        checked = !from
        film("${if (from) "off" else "on"}_${theme(dark)}${if (rtl) "_rtl" else ""}")
    }

    @Test fun onDark() = flip(dark = true, from = false)

    @Test fun offDark() = flip(dark = true, from = true)

    @Test fun onLight() = flip(dark = false, from = false)

    @Test fun offLight() = flip(dark = false, from = true)

    @Test fun onRtl() = flip(dark = true, from = false, rtl = true)

    private fun tap(dark: Boolean) {
        show(dark, on = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ROW).performTouchInput {
            down(centerLeft + Offset(40f, 0f))
            up()
        }
        film("tap_${theme(dark)}")
        assertEquals(true, checked)
    }

    @Test fun tapDark() = tap(dark = true)

    @Test fun tapLight() = tap(dark = false)

    @Test fun hold() {
        show(dark = true, on = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput { down(center) }
        film("hold_dark", until = 320)
        compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput { up() }
        film("hold_dark", until = 480)
        assertEquals(true, checked)
    }

    private fun drag(dark: Boolean, rtl: Boolean = false) {
        show(dark, on = false, rtl = rtl)
        compose.mainClock.autoAdvance = false
        val way = if (rtl) -1f else 1f
        compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput { down(if (rtl) centerRight - Offset(20f, 0f) else centerLeft + Offset(20f, 0f)) }
        var moves = 0
        film("drag_${theme(dark)}${if (rtl) "_rtl" else ""}", until = 400) {
            if (moves < 14) compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput { moveBy(Offset(way * 9f, 0f), 0) }
            moves++
        }
        compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput { up() }
        film("drag_${theme(dark)}${if (rtl) "_rtl" else ""}", until = 400)
        assertEquals(true, checked)
        assertEquals(1, changes)
    }

    @Test fun dragDark() = drag(dark = true)

    @Test fun dragLight() = drag(dark = false)

    @Test fun dragRtl() = drag(dark = true, rtl = true)

    @Test fun dragBack() {
        show(dark = true, on = true)
        compose.onNodeWithTag(SWITCH, useUnmergedTree = true).performTouchInput {
            down(centerRight - Offset(20f, 0f))
            moveBy(Offset(-90f, 0f))
            moveBy(Offset(90f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals(true, checked)
        assertEquals(0, changes)
    }

    private fun disabled(dark: Boolean) {
        show(dark, on = true, enabled = false)
        compose.onNodeWithTag(TAG).captureRoboImage("build/shots/switch/disabled_${theme(dark)}.png")
    }

    @Test fun disabledDark() = disabled(dark = true)

    @Test fun disabledLight() = disabled(dark = false)

    @Test fun semantics() {
        show(dark = true, on = false)
        val row = compose.onNodeWithTag(ROW)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.assertIsOff()
        row.performClick()
        row.assertIsOn()
    }

    private fun settings(name: String, dark: Boolean) {
        val prefs = Prefs(autoKeyboard = false, notificationControls = false, tvName = "Living room")
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(
                        TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON),
                        prefs,
                        NoRemoteActions,
                        remember { SnackbarHostState() },
                        requestedSheet = Sheet.SETTINGS,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        compose.onNodeWithText("Keep screen on").performScrollTo()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/switch/$name.png")
    }

    @Test fun settingsDark() = settings("settings_dark", dark = true)

    @Test fun settingsLight() = settings("settings_light", dark = false)

    @Config(fontScale = 2f)
    @Test fun settingsLarge() = settings("settings_dark_large", dark = true)

    private companion object {
        const val TAG = "card"
        const val ROW = "row"
        const val SWITCH = "switch"
        const val FRAME_MS = 16L
    }
}
