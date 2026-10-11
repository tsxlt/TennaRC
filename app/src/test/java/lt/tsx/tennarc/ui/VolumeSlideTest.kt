package lt.tsx.tennarc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class VolumeSlideTest {
    @get:Rule val compose = createComposeRule()

    private var ups = 0
    private var downs = 0
    private val actions = object : RemoteActions by NoRemoteActions {
        override fun volumeUp() {
            ups++
        }

        override fun volumeDown() {
            downs++
        }
    }

    private fun remote() {
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(TvState(host = "192.168.1.70", connection = Connection.Connected, volume = 14), Prefs(), actions, SnackbarHostState())
                }
            }
        }
    }

    @Test
    fun `sliding towards the plus turns it up a step at a time`() {
        remote()
        compose.onNodeWithContentDescription("Volume down").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(4.dp.toPx(), 0f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, downs)
        assertEquals(5, ups)
    }

    @Test
    fun `sliding back the other way turns it down`() {
        remote()
        compose.onNodeWithContentDescription("Volume up").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-4.dp.toPx(), 0f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, ups)
        assertEquals(2, downs)
    }

    @Test
    fun `a tap on a key is still a tap`() {
        remote()
        compose.onNodeWithContentDescription("Volume up").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, ups)
        assertEquals(0, downs)
    }
}
