package lt.tsx.tennarc.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import lt.tsx.tennarc.data.ThemeMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun rememberDarkTheme(mode: ThemeMode): Boolean {
    val system = isSystemInDarkTheme()
    val night by produceState(Daylight.isNight(ZonedDateTime.now()), mode) {
        if (mode != ThemeMode.AUTO) return@produceState
        while (true) {
            val now = ZonedDateTime.now()
            value = Daylight.isNight(now)
            val wait = Duration.between(now, Daylight.nextChange(now)).toMillis().coerceIn(1_000L, RECHECK_MS)
            delay(wait)
        }
    }
    return when (mode) {
        ThemeMode.AUTO -> night || system
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

private const val RECHECK_MS = 60_000L

object Daylight {
    fun isNight(at: ZonedDateTime): Boolean {
        val (rise, set) = sunTimes(at) ?: return polarNight(at)
        val instant = at.toInstant()
        return instant.isBefore(rise) || !instant.isBefore(set)
    }

    fun nextChange(at: ZonedDateTime): ZonedDateTime {
        val instant = at.toInstant()
        for (day in 0L..2L) {
            val (rise, set) = sunTimes(at.plusDays(day)) ?: continue
            listOf(rise, set).sorted().firstOrNull { it.isAfter(instant) }?.let { return it.atZone(at.zone) }
        }
        return at.plusDays(1)
    }

    internal fun sunTimes(at: ZonedDateTime, place: Place = placeOf(at.zone, at.toInstant())): Pair<Instant, Instant>? {
        val year = (2 * PI / 365.0) * (at.dayOfYear - 1)
        val equation = 229.18 * (0.000075 + 0.001868 * cos(year) - 0.032077 * sin(year) - 0.014615 * cos(2 * year) - 0.040849 * sin(2 * year))
        val declination = 0.006918 - 0.399912 * cos(year) + 0.070257 * sin(year) - 0.006758 * cos(2 * year) +
            0.000907 * sin(2 * year) - 0.002697 * cos(3 * year) + 0.00148 * sin(3 * year)
        val latitude = Math.toRadians(place.latitude)
        val cosHour = cos(Math.toRadians(90.833)) / (cos(latitude) * cos(declination)) - tan(latitude) * tan(declination)
        if (cosHour !in -1.0..1.0) return null
        val hour = Math.toDegrees(acos(cosHour))
        val midnight = at.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant()
        val rise = midnight.plusSeconds(((720 - 4 * (place.longitude + hour) - equation) * 60).toLong())
        val set = midnight.plusSeconds(((720 - 4 * (place.longitude - hour) - equation) * 60).toLong())
        return rise to set
    }

    private fun polarNight(at: ZonedDateTime): Boolean {
        val north = placeOf(at.zone, at.toInstant()).latitude >= 0
        val winter = at.monthValue in 10..12 || at.monthValue in 1..3
        return winter == north
    }

    internal data class Place(val latitude: Double, val longitude: Double)

    internal fun placeOf(zone: ZoneId, at: Instant): Place {
        val longitude = zone.rules.getStandardOffset(at).totalSeconds / 240.0
        val id = zone.id
        val latitude = LATITUDES[id] ?: REGIONS.entries.firstOrNull { id.startsWith(it.key) }?.value ?: 40.0
        return Place(latitude, longitude)
    }

    private val LATITUDES = mapOf(
        "Europe/Vilnius" to 54.7, "Europe/Riga" to 56.9, "Europe/Tallinn" to 59.4, "Europe/Helsinki" to 60.2,
        "Europe/Stockholm" to 59.3, "Europe/Oslo" to 59.9, "Europe/Copenhagen" to 55.7, "Europe/Warsaw" to 52.2,
        "Europe/Berlin" to 52.5, "Europe/London" to 51.5, "Europe/Dublin" to 53.3, "Europe/Amsterdam" to 52.4,
        "Europe/Kyiv" to 50.4, "Europe/Kiev" to 50.4, "Europe/Moscow" to 55.8, "Europe/Paris" to 48.9,
        "Europe/Madrid" to 40.4, "Europe/Lisbon" to 38.7, "Europe/Rome" to 41.9, "Europe/Athens" to 38.0,
        "America/New_York" to 40.7, "America/Chicago" to 41.9, "America/Denver" to 39.7, "America/Los_Angeles" to 34.1,
        "America/Toronto" to 43.7, "America/Vancouver" to 49.3, "America/Mexico_City" to 19.4, "America/Sao_Paulo" to -23.6,
        "America/Argentina/Buenos_Aires" to -34.6, "America/Buenos_Aires" to -34.6, "America/Santiago" to -33.4, "America/Lima" to -12.0,
        "America/Bogota" to 4.7, "Asia/Tokyo" to 35.7, "Asia/Seoul" to 37.6, "Asia/Shanghai" to 31.2, "Asia/Kolkata" to 22.6,
        "Asia/Dubai" to 25.2, "Asia/Singapore" to 1.3, "Asia/Jakarta" to -6.2, "Australia/Sydney" to -33.9,
        "Australia/Melbourne" to -37.8, "Australia/Perth" to -31.9, "Pacific/Auckland" to -36.8, "Africa/Johannesburg" to -26.2,
        "Africa/Cairo" to 30.0, "Africa/Lagos" to 6.5, "Africa/Nairobi" to -1.3,
    )

    private val REGIONS = mapOf(
        "Europe/" to 50.0, "America/" to 38.0, "Asia/" to 30.0, "Africa/" to 5.0, "Australia/" to -30.0,
        "Pacific/" to -15.0, "Atlantic/" to 35.0, "Indian/" to -10.0, "Antarctica/" to -75.0, "Arctic/" to 78.0,
    )
}

fun isDarkTheme(context: Context, mode: ThemeMode): Boolean {
    val system = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return when (mode) {
        ThemeMode.AUTO -> system || Daylight.isNight(ZonedDateTime.now())
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun FollowSystemBars(activity: ComponentActivity, dark: Boolean) {
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(dark) {
        if (!first) delay(THEME_FADE_MS / 2L)
        first = false
        activity.applySystemBars(dark)
    }
}

fun ComponentActivity.applySystemBars(dark: Boolean) {
    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
