package lt.tsx.tennarc.ui.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import lt.tsx.tennarc.data.DEFAULT_HOST
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.needsTvSetup
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.ui.RemoteScreen
import lt.tsx.tennarc.ui.components.ProvideTennaEnvironment
import lt.tsx.tennarc.ui.theme.TennaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities

internal val lgTv = FoundTv("192.168.1.70", "[LG] webOS TV OLED55C14LB", kind = TvKind.WEBOS)
internal val samsungTv = FoundTv("192.168.1.30", "Bedroom", kind = TvKind.SAMSUNG, model = "UE43AU7172")
internal val philipsTv = FoundTv("192.168.1.50", "Philips 55PUS8807", kind = TvKind.ANDROID_TV)
internal val rokuTv = FoundTv("192.168.1.40", "Kids room", kind = TvKind.ROKU, model = "Roku Express")
internal val fireTv = FoundTv("192.168.1.60", "Kitchen", kind = TvKind.FIRE_TV)

internal fun onWifi() {
    val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
    val wifi = ShadowNetworkCapabilities.newInstance()
    shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, wifi)
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w385dp-h917dp-xxhdpi", fontScale = 1.15f)
class FirstRunShotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        compose.mainClock.autoAdvance = false
    }

    private fun show(hub: FakeHub, wifi: Boolean = true) {
        if (wifi) onWifi()
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize()) {
                        RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, modifier = if (hub.prefs.needsTvSetup) Modifier.clearAndSetSemantics {} else Modifier)
                        FirstRun(hub.prefs, hub.state, hub)
                    }
                }
            }
        }
    }

    private fun after(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    private fun shot(name: String) = captureScreenRoboImage("build/shots/setup/$name.png")

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        after(16)
    }

    @Test fun today() {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    RemoteScreen(TvState(host = DEFAULT_HOST, connection = Connection.NeedsPairing()), Prefs(), NoRemoteActions, SnackbarHostState())
                }
            }
        }
        compose.waitForIdle()
        shot("00_today_new_install")
    }

    @Test fun scanning() {
        show(FakeHub(endless = true))
        after(1_500)
        shot("01_scanning")
    }

    @Test fun foundOne() {
        show(FakeHub(listOf(1_200L to lgTv), endless = true))
        after(2_500)
        shot("02_found_1")
    }

    private val three = listOf(900L to lgTv, 1_400L to samsungTv, 2_600L to philipsTv)

    @Test fun foundThree() {
        show(FakeHub(three, endless = true))
        after(3_500)
        shot("03_found_3")
    }

    @Test fun foundThreeDone() {
        show(FakeHub(three, passMs = 3_000))
        after(10_000)
        shot("04_found_3_done")
    }

    @Test fun nothingFound() {
        val hub = FakeHub()
        show(hub)
        after(7_000)
        shot("05_nothing_found_7s")
        after(2_000)
        shot("05_nothing_found")
    }

    @Test fun nothingFoundAddress() {
        show(FakeHub())
        after(9_000)
        tap("Enter IP address")
        after(600)
        compose.onNodeWithText("IP address").performTextInput("192.168.1.7")
        after(300)
        shot("06_nothing_found_address")
    }

    @Test fun tryDemo() {
        val hub = FakeHub()
        show(hub)
        after(9_000)
        tap("Try demo")
        assertTrue(hub.prefs.demoTv)
        assertFalse(hub.prefs.tvChosen)
    }

    @Test fun noWifi() {
        show(FakeHub(endless = true), wifi = false)
        after(1_000)
        shot("07_no_wifi")
    }

    private fun pairWith(hub: FakeHub, name: String) {
        show(hub)
        after(4_000)
        tap(name)
        after(800)
    }

    @Test fun pairLg() {
        val hub = FakeHub(three + (2_800L to fireTv) + (3_000L to rokuTv), passMs = 3_500)
        pairWith(hub, "LG webOS")
        assertEquals(TvKind.WEBOS, hub.prefs.kind)
        assertEquals(lgTv.host, hub.prefs.host)
        shot("10_pair_lg_accept")
    }

    @Test fun pairSamsung() {
        pairWith(FakeHub(three, endless = true), "Bedroom")
        shot("11_pair_samsung_accept")
    }

    @Test fun pairAndroidTvCode() {
        val hub = FakeHub(three, endless = true)
        pairWith(hub, "Philips 55PUS8807")
        compose.onNodeWithContentDescription("Code").performTextInput("4F2")
        after(300)
        shot("12_pair_android_tv_code")
    }

    @Test fun pairAndroidTvWrongCode() {
        val hub = FakeHub(three, endless = true)
        pairWith(hub, "Philips 55PUS8807")
        compose.onNodeWithContentDescription("Code").performTextInput("4F2A9D")
        after(1_500)
        assertEquals(listOf("4F2A9D"), hub.codes)
        shot("13_pair_android_tv_wrong_code")
    }

    @Test fun pairRokuConnecting() {
        pairWith(FakeHub(listOf(500L to rokuTv), endless = true, answer = { Connection.Connecting }), "Kids room")
        shot("14_pair_roku_connecting")
    }

    @Test fun pairRokuLimited() {
        val hub = FakeHub(listOf(500L to rokuTv), endless = true, notice = Notice.ROKU_LIMITED)
        pairWith(hub, "Kids room")
        shot("15_pair_roku_limited")
        tap("Done")
        after(1_000)
        assertTrue(hub.prefs.tvChosen)
    }

    @Test fun pairFireTv() {
        pairWith(FakeHub(listOf(500L to fireTv), endless = true), "Kitchen")
        shot("16_pair_fire_tv_accept")
    }

    @Test fun pairFireTvAdbOff() {
        pairWith(FakeHub(listOf(500L to fireTv), endless = true, answer = { Connection.Offline }, notice = Notice.ADB_OFF), "Kitchen")
        shot("17_pair_fire_tv_adb_off")
    }

    @Test fun pairOffline() {
        pairWith(FakeHub(three, endless = true, answer = { Connection.Offline }), "LG webOS")
        shot("18_pair_offline")
    }

    @Test fun backReturnsToTheList() {
        val hub = FakeHub(three, passMs = 3_000)
        pairWith(hub, "LG webOS")
        tap("Cancel")
        after(600)
        assertEquals(Prefs(tvChosen = false), hub.prefs)
        shot("19_back_to_list")
    }

    @Test fun paired() {
        val hub = FakeHub(three, endless = true)
        pairWith(hub, "LG webOS")
        hub.accept()
        after(600)
        shot("20_paired")
        after(350)
        assertTrue(hub.prefs.tvChosen)
        after(100)
        shot("21_lifting_off")
        after(1_000)
        shot("22_remote")
    }

    @Test fun rotationKeepsPairing() {
        onWifi()
        val hub = FakeHub(three, endless = true)
        val restoring = StateRestorationTester(compose)
        restoring.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    FirstRun(hub.prefs, hub.state, hub)
                }
            }
        }
        after(4_000)
        tap("LG webOS")
        after(800)
        assertEquals(1, hub.pairs)
        restoring.emulateSavedInstanceStateRestore()
        after(800)
        compose.onNodeWithText("Accept on the TV").assertExists()
        assertEquals(1, hub.pairs)
        tap("Cancel")
        after(600)
        compose.onNodeWithText("Bedroom").assertExists()
    }

    @Test fun codePaired() {
        val hub = FakeHub(three, endless = true)
        pairWith(hub, "Philips 55PUS8807")
        compose.onNodeWithContentDescription("Code").performTextInput(FakeHub.GOOD_CODE)
        after(2_000)
        assertTrue(hub.prefs.tvChosen)
        assertEquals(TvKind.ANDROID_TV, hub.prefs.kind)
    }

    @Test fun addTvSheet() {
        onWifi()
        val hub = FakeHub(
            three + (2_000L to rokuTv),
            passMs = 3_000,
            start = Prefs(otherTvs = listOf(SavedTv("Bedroom", samsungTv.host, TvKind.SAMSUNG, ""))),
        )
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = 22.dp)
                                        .size(width = 32.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                                )
                                TvSetup(hub.state, hub.prefs, hub, onDone = {}, contentPadding = PaddingValues(bottom = 32.dp))
                            }
                        }
                    }
                }
            }
        }
        after(10_000)
        shot("30_add_tv_sheet")
    }
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.3f)
class FirstRunSmallShotTest {
    @get:Rule val compose = createComposeRule()

    private fun show(hub: FakeHub) {
        onWifi()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TennaTheme {
                ProvideTennaEnvironment(haptics = false, iconLoader = IconLoader { null }) {
                    Box(Modifier.fillMaxSize()) {
                        RemoteScreen(hub.state, hub.prefs, hub, remember { SnackbarHostState() }, modifier = if (hub.prefs.needsTvSetup) Modifier.clearAndSetSemantics {} else Modifier)
                        FirstRun(hub.prefs, hub.state, hub)
                    }
                }
            }
        }
    }

    @Test fun foundFive() {
        show(FakeHub(listOf(500L to lgTv, 700L to samsungTv, 900L to philipsTv, 1_100L to rokuTv, 1_300L to fireTv), passMs = 2_000))
        compose.mainClock.advanceTimeBy(10_000)
        captureScreenRoboImage("build/shots/setup/small/01_found_5_done.png")
    }

    @Test fun code() {
        show(FakeHub(listOf(500L to philipsTv), endless = true))
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Philips 55PUS8807").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Code").performTextInput("4F")
        compose.mainClock.advanceTimeBy(300)
        captureScreenRoboImage("build/shots/setup/small/02_code.png")
    }

    @Test fun nothingFound() {
        show(FakeHub())
        compose.mainClock.advanceTimeBy(9_000)
        captureScreenRoboImage("build/shots/setup/small/03_nothing_found.png")
    }
}
