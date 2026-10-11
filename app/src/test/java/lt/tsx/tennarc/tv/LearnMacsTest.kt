package lt.tsx.tennarc.tv

import lt.tsx.tennarc.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LearnMacsTest {
    private lateinit var settings: Settings

    @Before fun start() {
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.update { it.copy(host = HOST, mac = "") }
    }

    private fun mac() = settings.prefs.value.mac

    @Test
    fun `saves every MAC the TV reports`() {
        settings.learnMacs(HOST, listOf(WIRED, WIFI, WIRED.uppercase(), "junk"))
        assertEquals("$WIRED, $WIFI", mac())
    }

    @Test
    fun `keeps the user's MAC while it is one of the TV's`() {
        settings.update { it.copy(mac = "02-00-00-00-00-0B") }
        settings.learnMacs(HOST, listOf(WIRED, WIFI))
        assertEquals("02-00-00-00-00-0B", mac())
    }

    @Test
    fun `replaces a MAC the TV doesn't have`() {
        settings.update { it.copy(mac = "02:00:00:00:00:99") }
        settings.learnMacs(HOST, listOf(WIRED, WIFI))
        assertEquals("$WIRED, $WIFI", mac())
    }

    @Test
    fun `only fills a blank when told to`() {
        settings.update { it.copy(mac = "02:00:00:00:00:99") }
        settings.learnMacs(HOST, listOf(WIFI), onlyIfUnset = true)
        assertEquals("02:00:00:00:00:99", mac())
    }

    @Test
    fun `leaves another TV's settings alone`() {
        settings.learnMacs("192.168.1.99", listOf(WIRED))
        assertEquals("", mac())
    }

    private companion object {
        const val HOST = "192.168.1.70"
        const val WIRED = "02:00:00:00:00:0a"
        const val WIFI = "02:00:00:00:00:0b"
    }
}
