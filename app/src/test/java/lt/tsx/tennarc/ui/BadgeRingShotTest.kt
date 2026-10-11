package lt.tsx.tennarc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.onboarding.FakeHub
import lt.tsx.tennarc.ui.onboarding.FirstRun
import lt.tsx.tennarc.ui.onboarding.onWifi
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w386dp-h943dp-448dpi", fontScale = 1.15f)
class BadgeRingShotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        compose.mainClock.autoAdvance = false
    }

    private val connected = TvState(host = "192.168.1.70", connection = Connection.Connected, power = TvPower.ON)

    private fun content(dark: Boolean, body: @Composable () -> Unit) = compose.setContent {
        TennaTheme(dark) {
            ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) { body() }
        }
    }

    private fun after(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun ringsStart(title: String): Long {
        repeat(200) {
            if (compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()) return compose.mainClock.currentTime + FRAME
            after(FRAME)
        }
        error("$title never showed")
    }

    private fun beat(name: String, title: String, start: Long, phases: List<Float> = PHASES) {
        for (phase in phases) {
            val at = start + RING_MS + (phase * RING_MS).toLong()
            after(at - compose.mainClock.currentTime)
            shot("${name}_${(phase * 100).toInt().toString().padStart(2, '0')}", title)
        }
    }

    private fun shot(name: String, title: String) {
        val file = "build/shots/badge/$name.png"
        captureScreenRoboImage(file)
        closeUp(file, title)
    }

    private fun closeUp(file: String, title: String) {
        val whole = File(file)
        if (!whole.exists()) return
        val node = compose.onAllNodesWithText(title).onFirst().fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        val image = ImageIO.read(whole)
        val width = (CLOSE_WIDTH * density).toInt().coerceAtMost(image.width)
        val height = (CLOSE_HEIGHT * density).toInt().coerceAtMost(image.height)
        val centre = node.positionOnScreen.x + node.size.width / 2f
        val x = (centre - width / 2f).toInt().coerceIn(0, image.width - width)
        val y = (node.positionOnScreen.y - CLOSE_ABOVE * density).toInt().coerceIn(0, image.height - height)
        val close = BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB)
        close.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            drawImage(image.getSubimage(x, y, width, height), 0, 0, width * 2, height * 2, null)
            dispose()
        }
        ImageIO.write(close, "png", File(file.removeSuffix(".png") + "_close.png"))
    }

    private fun addTv(dark: Boolean) {
        onWifi()
        val hub = FakeHub(endless = true, start = Prefs(tvName = "Living room"))
        hub.state = connected.copy(host = hub.prefs.host, kind = hub.prefs.kind)
        content(dark) {
            RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, requestedSheet = Sheet.ADD_TV)
        }
        beat("add_tv_${theme(dark)}", LOOKING, ringsStart(LOOKING))
    }

    @Test fun addTvDark() = addTv(true)

    @Test fun addTvLight() = addTv(false)

    private fun fromSettings(dark: Boolean) {
        onWifi()
        val hub = FakeHub(endless = true, start = Prefs(tvName = "Living room"))
        hub.state = connected.copy(host = hub.prefs.host, kind = hub.prefs.kind)
        content(dark) {
            RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, requestedSheet = Sheet.SETTINGS)
        }
        after(1_500)
        compose.onNodeWithText(FIND).performScrollTo()
        after(500)
        compose.onNodeWithText(FIND).performClick()
        val start = ringsStart(LOOKING)
        for (ms in listOf(96L, 192L, 288L, 384L)) {
            after(start + ms - compose.mainClock.currentTime)
            shot("from_settings_${theme(dark)}_swap_${ms.toString().padStart(3, '0')}ms", LOOKING)
        }
        beat("from_settings_${theme(dark)}", LOOKING, start, listOf(0.7f, 0.98f))
    }

    @Test fun fromSettingsDark() = fromSettings(true)

    @Test fun fromSettingsLight() = fromSettings(false)

    private fun panel(dark: Boolean) {
        content(dark) {
            RemoteScreen(connected.copy(connection = Connection.AwaitingApproval), Prefs(tvName = "Living room"), NoRemoteActions, remember { SnackbarHostState() })
        }
        beat("panel_${theme(dark)}", ACCEPT, ringsStart(ACCEPT))
    }

    @Test fun panelDark() = panel(true)

    @Test fun panelLight() = panel(false)

    private fun welcome(dark: Boolean) {
        onWifi()
        val hub = FakeHub(endless = true)
        content(dark) {
            Box(Modifier.fillMaxSize()) {
                RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, modifier = if (hub.prefs.needsTvSetup) Modifier.clearAndSetSemantics {} else Modifier)
                FirstRun(hub.prefs, hub.state, hub)
            }
        }
        beat("welcome_${theme(dark)}", LOOKING, ringsStart(LOOKING))
    }

    @Test fun welcomeDark() = welcome(true)

    @Test fun welcomeLight() = welcome(false)

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    private companion object {
        const val FRAME = 16L
        const val RING_MS = 2_400L
        val PHASES = listOf(0.55f, 0.7f, 0.85f, 0.98f)
        const val LOOKING = "Looking for TVs"
        const val ACCEPT = "Accept on the TV"
        const val FIND = "Find TVs nearby"
        const val CLOSE_WIDTH = 200f
        const val CLOSE_HEIGHT = 190f
        const val CLOSE_ABOVE = 170f
    }
}
