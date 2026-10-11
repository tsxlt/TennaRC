package lt.tsx.tennarc.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DaylightTest {
    private val vilnius = ZoneId.of("Europe/Vilnius")

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, vilnius)

    @Test
    fun winterDaysAreShort() {
        assertTrue(Daylight.isNight(at(12, 21, 7, 30)))
        assertFalse(Daylight.isNight(at(12, 21, 12)))
        assertTrue(Daylight.isNight(at(12, 21, 17)))
    }

    @Test
    fun summerEveningsAreLight() {
        assertFalse(Daylight.isNight(at(6, 21, 21)))
        assertTrue(Daylight.isNight(at(6, 21, 23)))
        assertTrue(Daylight.isNight(at(6, 21, 2)))
        assertFalse(Daylight.isNight(at(6, 21, 6)))
    }

    @Test
    fun theNextChangeIsTheComingSunsetOrSunrise() {
        val noon = at(12, 21, 12)
        val sunset = Daylight.nextChange(noon)
        assertTrue(sunset.isAfter(noon) && Duration.between(noon, sunset) < Duration.ofHours(5))
        assertTrue(Daylight.isNight(sunset.plusMinutes(1)))
        val night = at(12, 21, 20)
        val sunrise = Daylight.nextChange(night)
        assertTrue(Duration.between(night, sunrise) in Duration.ofHours(10)..Duration.ofHours(15))
        assertFalse(Daylight.isNight(sunrise.plusMinutes(1)))
    }

    @Test
    fun theSunStaysDownAllDayFarNorthInWinter() {
        val longyearbyen = ZonedDateTime.of(2026, 12, 21, 12, 0, 0, 0, ZoneId.of("Arctic/Longyearbyen"))
        assertTrue(Daylight.isNight(longyearbyen))
        assertFalse(Daylight.isNight(longyearbyen.withMonth(6)))
    }
}
