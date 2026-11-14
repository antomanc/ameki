package dev.antomanc.ameki.provider.openmeteo

import dev.antomanc.ameki.core.model.WeatherCondition

/** WMO 4677 weather interpretation codes as used by Open-Meteo. */
internal fun wmoToCondition(code: Int?, cloudCover: Int? = null): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1 -> WeatherCondition.MOSTLY_CLEAR
    2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> if (cloudCover != null && cloudCover < 90) WeatherCondition.MOSTLY_CLOUDY else WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55 -> WeatherCondition.DRIZZLE
    56, 57, 66, 67 -> WeatherCondition.FREEZING_RAIN
    61 -> WeatherCondition.LIGHT_RAIN
    63 -> WeatherCondition.RAIN
    65 -> WeatherCondition.HEAVY_RAIN
    71 -> WeatherCondition.LIGHT_SNOW
    73, 77 -> WeatherCondition.SNOW
    75 -> WeatherCondition.HEAVY_SNOW
    80, 81, 82 -> WeatherCondition.SHOWERS
    85, 86 -> WeatherCondition.SNOW_SHOWERS
    95 -> WeatherCondition.THUNDERSTORM
    96, 99 -> WeatherCondition.HAIL
    else -> WeatherCondition.UNKNOWN
}
