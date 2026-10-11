package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.ui.sheets.colorKeyWashes
import lt.tsx.tennarc.ui.sheets.shelfHues
import lt.tsx.tennarc.ui.sheets.shelfWash
import lt.tsx.tennarc.ui.theme.seeThrough
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvPicture
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
class ShelfWashShotTest {
    @get:Rule val compose = createComposeRule()

    private val three = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        soundOutput = "tv_speaker",
        picture = TvPicture("game", 64),
    )
    private val two = three.copy(soundOutput = null)
    private val one = three.copy(picture = null)

    private val prefs = Prefs(autoKeyboard = false)

    private fun shelf(name: String, state: TvState, dark: Boolean, direction: LayoutDirection = LayoutDirection.Ltr, tap: String? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                TennaTheme(dark) {
                    ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                        RemoteScreen(state, prefs, NoRemoteActions, SnackbarHostState(), requestedSheet = Sheet.MORE)
                    }
                }
            }
        }
        compose.waitForIdle()
        if (tap != null) {
            compose.onNodeWithContentDescription(tap).performClick()
            compose.waitForIdle()
        }
        val whole = "build/shots/shelf/$name.png"
        captureScreenRoboImage(whole)
        if (!File(whole).exists()) return
        val tabs = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
            hasAnyChild(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        val row = compose.onNode(tabs, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val density = compose.density.density
        val around = (INSET + MARGIN) * density
        val below = if (tap != null) PANEL * density else 0f
        val image = ImageIO.read(File(whole))
        val x = (row.left - around).toInt().coerceAtLeast(0)
        val y = (row.top - around).toInt().coerceAtLeast(0)
        val w = ((row.right + around).toInt() - x).coerceAtMost(image.width - x)
        val h = ((row.bottom + around + below).toInt() - y).coerceAtMost(image.height - y)
        ImageIO.write(image.getSubimage(x, y, w, h), "png", File("build/shots/shelf/${name}_shelf.png"))
    }

    @Test fun oneDark() = shelf("1_dark", one, dark = true)

    @Test fun oneLight() = shelf("1_light", one, dark = false)

    @Test fun oneOpenDark() = shelf("1_open_dark", one, dark = true, tap = "Sound")

    @Test fun oneOpenLight() = shelf("1_open_light", one, dark = false, tap = "Sound")

    @Test fun twoDark() = shelf("2_dark", two, dark = true)

    @Test fun twoLight() = shelf("2_light", two, dark = false)

    @Test fun twoRtl() = shelf("2_dark_rtl", two, dark = true, direction = LayoutDirection.Rtl)

    @Test fun threeDark() = shelf("3_dark", three, dark = true)

    @Test fun threeLight() = shelf("3_light", three, dark = false)

    @Test fun threeRtl() = shelf("3_dark_rtl", three, dark = true, direction = LayoutDirection.Rtl)

    @Test fun onlyALoneTabFades() {
        for ((plain, ground, hues) in schemes) {
            val washes = shelfHues(colorKeyWashes(plain), hues)
            for (n in 2..washes.size) {
                val some = washes.take(n)
                val before = Brush.horizontalGradient(*some.mapIndexed { i, wash -> (i + 0.5f) / n to seeThrough(wash, ground) }.toTypedArray())
                assertArrayEquals("$n tabs", pixels(before, ground), pixels(shelfWash(some, plain, ground)!!, ground))
            }
            val lone = pixels(shelfWash(washes.take(1), plain, ground)!!, ground)
            val shelf = pixels(SolidColor(seeThrough(plain, ground)), ground)
            val hue = pixels(SolidColor(seeThrough(washes.first(), ground)), ground)
            assertNear(shelf[0], lone[0])
            assertNear(shelf[WIDTH - 1], lone[WIDTH - 1])
            assertNear(hue[WIDTH / 2], lone[WIDTH / 2])
        }
    }

    private val schemes = listOf(
        Triple(Color(0xFF282A2E), Color(0xFF1B1C1F), listOf(Color(0xFFF2C25B), Color(0xFF7FA6FF), Color(0xFFB79CFF), Color(0xFF5FD3C4))),
        Triple(Color(0xFFE3E4E8), Color(0xFFF3F4F6), listOf(Color(0xFFB07A0E), Color(0xFF3A68D8), Color(0xFF7652D6), Color(0xFF16897C))),
    )

    private fun pixels(brush: Brush, ground: Color): IntArray {
        val bitmap = ImageBitmap(WIDTH, 1)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), 1f)) {
            drawRect(ground)
            drawRect(brush)
        }
        return IntArray(WIDTH).also { bitmap.readPixels(it) }
    }

    private fun assertNear(expected: Int, actual: Int) {
        for (shift in listOf(0, 8, 16, 24)) {
            val off = abs(((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF))
            assertTrue("#%08x, not #%08x".format(actual, expected), off <= 1)
        }
    }

    private companion object {
        const val WIDTH = 1035
        const val INSET = 4f
        const val MARGIN = 12f
        const val PANEL = 56f
    }
}
