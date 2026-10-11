package lt.tsx.tennarc.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.icons.IconLoader
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
class AmbilightRowTest {
    @get:Rule val compose = createComposeRule()

    private val styles = mutableListOf<AmbilightStyle>()
    private val pins = mutableListOf<String>()
    private var pairs = 0

    private fun row(ambilight: Ambilight, enabled: Boolean = true) = show {
        AmbilightRow(ambilight, enabled, onStyle = { styles += it }, onPair = { pairs++ }, onPin = { pins += it })
    }

    @Test
    fun `the style in use is lit, and another is a tap away`() {
        row(Ambilight(on = true, style = AmbilightStyle.FOLLOW_VIDEO))
        compose.onRoot().captureRoboImage("build/shots/ambilight-video.png")
        compose.onNodeWithContentDescription("Video").assertIsSelected()
        compose.onNodeWithContentDescription("Video").performClick()
        compose.onNodeWithContentDescription("Lounge").performClick()
        assertEquals(listOf(AmbilightStyle.LOUNGE), styles)
    }

    @Test
    fun `switched off, Off is lit whatever style it keeps`() {
        row(Ambilight(on = false, style = AmbilightStyle.LOUNGE))
        compose.onRoot().captureRoboImage("build/shots/ambilight-off.png")
        compose.onNodeWithContentDescription("Off").assertIsSelected()
    }

    @Test
    fun `unpaired, it offers pairing, then takes the PIN`() {
        row(Ambilight(AmbilightAccess.NeedsPairing))
        compose.onRoot().captureRoboImage("build/shots/ambilight-pair.png")
        compose.onNodeWithContentDescription("Pair").performClick()
        assertEquals(1, pairs)
    }

    @Test
    fun `the PIN goes once it has four digits`() {
        row(Ambilight(AmbilightAccess.AwaitingPin(wrong = true)))
        compose.onRoot().captureRoboImage("build/shots/ambilight-pin.png")
        compose.onNodeWithContentDescription("Pair").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Wrong PIN. Try the new one").performTextInput("42a7")
        compose.onNodeWithContentDescription("Pair").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Wrong PIN. Try the new one").performTextInput("1")
        compose.onNodeWithContentDescription("Pair").assertIsEnabled().performClick()
        assertEquals(listOf("4271"), pins)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Box(Modifier.padding(16.dp)) { content() }
                    }
                }
            }
        }
    }
}
