package lt.tsx.tennarc.ui.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
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
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class DevLinkShotTest {
    @get:Rule val compose = createComposeRule()

    private var connects = 0
    private var turnOffs = 0

    private fun sheet(link: DevLink, dark: Boolean = true) = show(dark) {
        DevLinkSheetContent(link, onConnect = { connects++ }, onTurnOff = { turnOffs++ })
    }

    @Test
    fun `off, it says what it's for and how to turn it on, then connects`() {
        sheet(DevLink.Off)
        compose.onRoot().captureRoboImage("build/shots/dev-link-off.png")
        compose.onNodeWithText("Connect").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun `off, in the light`() {
        sheet(DevLink.Off, dark = false)
        compose.onRoot().captureRoboImage("build/shots/dev-link-off-light.png")
    }

    @Test
    fun `waiting on the TV's prompt says what to tick`() {
        sheet(DevLink.AwaitingApproval)
        compose.onRoot().captureRoboImage("build/shots/dev-link-allow.png")
        compose.onNodeWithText("Allow on the TV").assertExists()
        compose.onNodeWithText("Tick Always allow from this computer, then OK").assertExists()
    }

    @Test
    fun `up, the steps go and it can be turned off`() {
        sheet(DevLink.Up)
        compose.onRoot().captureRoboImage("build/shots/dev-link-up.png")
        compose.onNodeWithText("Connect").assertDoesNotExist()
        compose.onNodeWithText("Turn off").performClick()
        assertEquals(1, turnOffs)
    }

    @Test
    fun `only Wireless debugging on, it says so, and to try the other`() {
        sheet(DevLink.Failed(DevProblem.WIRELESS_ONLY))
        compose.onRoot().captureRoboImage("build/shots/dev-link-wireless.png")
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun `set up but turned away later, it says why, and offers to try again or stop`() {
        sheet(DevLink.Down(DevProblem.DEBUGGING_OFF))
        compose.onRoot().captureRoboImage("build/shots/dev-link-debugging-off.png")
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Turn off").performClick()
        assertEquals(1 to 1, connects to turnOffs)
    }

    @Test
    fun `the TV in use offers it in its menu, which says how it is`() {
        var opened = 0
        show(dark = true) {
            SettingsSheetContent(
                prefs = Prefs(kind = TvKind.ANDROID_TV, tvName = "Living room"),
                onUpdate = {},
                onFindTvs = { emptyList() },
                onForgetPairing = {},
                devLink = DevLink.Down(DevProblem.DEBUGGING_OFF),
                onDevLink = { opened++ },
            )
        }
        compose.onNodeWithText("Living room").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Debugging is off on the TV").assertExists()
        compose.onRoot().captureRoboImage("build/shots/dev-link-settings-row.png")
        compose.onNodeWithText("Developer connection").performClick()
        assertEquals(1, opened)
    }

    private fun show(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            TennaTheme(dark = dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
                }
            }
        }
    }
}
