package dev.antomanc.ameki.core.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@StringRes
fun WeatherCondition.label(isDay: Boolean = true): Int = when (this) {
    WeatherCondition.CLEAR -> if (isDay) R.string.cond_clear_day else R.string.cond_clear
    WeatherCondition.MOSTLY_CLEAR -> R.string.cond_mostly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.cond_partly_cloudy
    WeatherCondition.MOSTLY_CLOUDY -> R.string.cond_mostly_cloudy
    WeatherCondition.CLOUDY -> R.string.cond_cloudy
    WeatherCondition.FOG -> R.string.cond_fog
    WeatherCondition.WINDY -> R.string.cond_windy
    WeatherCondition.DRIZZLE -> R.string.cond_drizzle
    WeatherCondition.LIGHT_RAIN -> R.string.cond_light_rain
    WeatherCondition.RAIN -> R.string.cond_rain
    WeatherCondition.HEAVY_RAIN -> R.string.cond_heavy_rain
    WeatherCondition.SHOWERS -> R.string.cond_showers
    WeatherCondition.FREEZING_RAIN -> R.string.cond_freezing_rain
    WeatherCondition.SLEET -> R.string.cond_sleet
    WeatherCondition.LIGHT_SNOW -> R.string.cond_light_snow
    WeatherCondition.SNOW -> R.string.cond_snow
    WeatherCondition.HEAVY_SNOW -> R.string.cond_heavy_snow
    WeatherCondition.SNOW_SHOWERS -> R.string.cond_snow_showers
    WeatherCondition.HAIL -> R.string.cond_hail
    WeatherCondition.THUNDERSTORM -> R.string.cond_thunderstorm
    WeatherCondition.UNKNOWN -> R.string.cond_unknown
}

/** Time and date formatting that honours the user's 12/24 h choice and the place's time zone. */
class TimeFormatter(private val context: Context, private val format: TimeFormat, private val locale: Locale = context.resources.configuration.locales[0]) {
    private val is24 get() = when (format) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }

    fun time(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale).format(instant.atZone(zone))

    /** "14" or "2 PM": compact hour labels for timelines. */
    fun hour(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "H" else "ha"), locale).format(instant.atZone(zone))

    fun timestamp(instant: Instant, zone: ZoneId, now: Instant): String =
        if (instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) time(instant, zone)
        else "${dateMedium(instant.atZone(zone).toLocalDate())} ${time(instant, zone)}"

    fun weekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }

    fun dateMedium(date: LocalDate): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEE d MMM"), locale).format(date).replaceFirstChar { it.titlecase(locale) }
}
