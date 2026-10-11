package lt.tsx.tennarc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.components.AccentButton
import lt.tsx.tennarc.ui.components.ButtonPair
import lt.tsx.tennarc.ui.components.ButtonText
import lt.tsx.tennarc.ui.components.GlassSwitch
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.components.QuietButton
import lt.tsx.tennarc.ui.sheets.DevLinkSheetContent
import lt.tsx.tennarc.ui.sheets.MoreSheetContent
import lt.tsx.tennarc.ui.theme.ProvideGround
import lt.tsx.tennarc.ui.theme.TennaTheme
import lt.tsx.tennarc.ui.theme.frostBehind
import lt.tsx.tennarc.ui.theme.glass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class FamilyShotTest {
    @get:Rule val compose = createComposeRule()

    private val state = TvState(
        host = "192.168.1.70",
        connection = Connection.Connected,
        power = TvPower.ON,
        foregroundAppId = TvApps.YOUTUBE,
        volume = 10,
        apps = listOf(TvApps.YOUTUBE, TvApps.NETFLIX, TvApps.BROWSER, TvApps.MEDIA_STATION_X, TvApps.PHOTO_VIDEO).map { TvApp(it, TvApps.knownTitles.getValue(it)) },
    )

    private fun settle(ms: Long = 1_200) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun shot(name: String, dark: Boolean) {
        settle(400)
        captureScreenRoboImage("build/shots/family/${name}_${if (dark) "dark" else "light"}.png")
    }

    private fun sheet(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).frostBehind(), contentAlignment = Alignment.BottomCenter) {
                        val frost = glass()
                        Column(Modifier.fillMaxWidth().glass(frost, BottomSheetDefaults.ExpandedShape)) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                                ProvideGround(frost.fill) { content() }
                            }
                        }
                    }
                }
            }
        }
        settle()
    }

    private fun family(dark: Boolean) {
        sheet(dark) {
            MoreSheetContent(enabled = true, onPress = {}, captions = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp))
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AccentButton("Connect", {}, Modifier.fillMaxWidth())
                QuietButton("Turn off", {}, Modifier.fillMaxWidth())
                ButtonPair(quiet = { QuietButton("Cancel", {}) }, main = { AccentButton("Save", {}) })
                AccentButton({}, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    ButtonText("Wi-Fi settings")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassSwitch(true)
                    GlassSwitch(false)
                    AccentButton("Pair", {})
                }
            }
        }
        shot("01_family", dark)
    }

    @Test fun familyDark() = family(true)

    @Test fun familyLight() = family(false)

    private fun devLink(dark: Boolean, link: DevLink, name: String) {
        sheet(dark) { DevLinkSheetContent(link, onConnect = {}, onTurnOff = {}) }
        shot(name, dark)
    }

    @Test fun devLinkOffDark() = devLink(true, DevLink.Off, "02_dev_link_off")

    @Test fun devLinkOffLight() = devLink(false, DevLink.Off, "02_dev_link_off")

    @Test fun devLinkAllowDark() = devLink(true, DevLink.AwaitingApproval, "03_dev_link_allow")

    @Test fun devLinkAllowLight() = devLink(false, DevLink.AwaitingApproval, "03_dev_link_allow")

    @Test fun devLinkUpDark() = devLink(true, DevLink.Up, "04_dev_link_up")

    @Test fun devLinkUpLight() = devLink(false, DevLink.Up, "04_dev_link_up")

    @Test fun devLinkDownDark() = devLink(true, DevLink.Down(DevProblem.DEBUGGING_OFF), "05_dev_link_down")

    @Test fun devLinkDownLight() = devLink(false, DevLink.Down(DevProblem.DEBUGGING_OFF), "05_dev_link_down")

    private fun remote(dark: Boolean, touchpad: Boolean, name: String) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state.copy(foregroundAppId = if (touchpad) TvApps.BROWSER else TvApps.YOUTUBE), Prefs(tvName = "Living room", touchpadMode = touchpad), NoRemoteActions, remember { SnackbarHostState() })
                }
            }
        }
        settle()
        shot(name, dark)
    }

    @Test fun touchpadDark() = remote(true, touchpad = true, "06_touchpad")

    @Test fun touchpadLight() = remote(false, touchpad = true, "06_touchpad")

    @Test fun dpadDark() = remote(true, touchpad = false, "07_dpad")

    @Test fun dpadLight() = remote(false, touchpad = false, "07_dpad")

    private fun addTv(dark: Boolean) {
        compose.setContent {
            TennaTheme(dark) {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(state, Prefs(tvName = "Living room"), NoRemoteActions, remember { SnackbarHostState() }, requestedSheet = Sheet.ADD_TV)
                }
            }
        }
        settle(3_000)
        shot("08_add_tv_no_wifi", dark)
    }

    @Test fun addTvDark() = addTv(true)

    @Test fun addTvLight() = addTv(false)
}
