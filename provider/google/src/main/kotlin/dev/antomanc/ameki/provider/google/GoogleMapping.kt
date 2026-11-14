package dev.antomanc.ameki.provider.google

import dev.antomanc.ameki.core.model.AlertSeverity
import dev.antomanc.ameki.core.model.MoonPhase
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherCondition.*

/**
 * Google WeatherCondition.Type → domain condition. The Japan/Korea-only compound types
 * ("CLOUDY_WITH_RAIN_LATER", ...) are matched by keyword, most severe word first.
 */
internal fun googleCondition(type: String?): WeatherCondition = when (type) {
    null, "TYPE_UNSPECIFIED" -> UNKNOWN
    "CLEAR" -> CLEAR
    "MOSTLY_CLEAR" -> MOSTLY_CLEAR
    "PARTLY_CLOUDY" -> PARTLY_CLOUDY
    "MOSTLY_CLOUDY" -> MOSTLY_CLOUDY
    "CLOUDY" -> CLOUDY
    "WINDY" -> WINDY
    "WIND_AND_RAIN" -> RAIN
    "LIGHT_RAIN_SHOWERS", "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS", "RAIN_SHOWERS" -> SHOWERS
    "HEAVY_RAIN_SHOWERS" -> HEAVY_RAIN
    "LIGHT_TO_MODERATE_RAIN", "LIGHT_RAIN" -> LIGHT_RAIN
    "RAIN" -> RAIN
    "MODERATE_TO_HEAVY_RAIN", "HEAVY_RAIN", "RAIN_PERIODICALLY_HEAVY", "HEAVY_RAIN_TO_RAINSTORM" -> HEAVY_RAIN
    "LIGHT_SNOW_SHOWERS", "CHANCE_OF_SNOW_SHOWERS", "SCATTERED_SNOW_SHOWERS", "SNOW_SHOWERS", "HEAVY_SNOW_SHOWERS" -> SNOW_SHOWERS
    "LIGHT_TO_MODERATE_SNOW", "LIGHT_SNOW" -> LIGHT_SNOW
    "SNOW", "BLOWING_SNOW" -> SNOW
    "MODERATE_TO_HEAVY_SNOW", "HEAVY_SNOW", "SNOWSTORM", "SNOW_PERIODICALLY_HEAVY", "HEAVY_SNOW_STORM" -> HEAVY_SNOW
    "RAIN_AND_SNOW", "SLEET" -> SLEET
    "HAIL", "HAIL_SHOWERS" -> HAIL
    "THUNDERSTORM", "THUNDERSHOWER", "LIGHT_THUNDERSTORM_RAIN", "SCATTERED_THUNDERSTORMS", "HEAVY_THUNDERSTORM" -> THUNDERSTORM
    "FOG" -> FOG
    "VERY_HOT" -> CLEAR
    else -> when {
        "THUNDER" in type || "LIGHTNING" in type -> THUNDERSTORM
        "SLEET" in type || "RAINSNOW" in type || "SNOWRAIN" in type -> SLEET
        type.startsWith("SNOW") -> SNOW
        type.startsWith("RAIN") -> RAIN
        type.startsWith("CLOUDY") -> if ("RAIN" in type || "SNOW" in type) MOSTLY_CLOUDY else CLOUDY
        type.startsWith("CLEAR") -> if ("RAIN" in type || "SNOW" in type || "CLOUD" in type) PARTLY_CLOUDY else CLEAR
        "FOG" in type -> FOG
        else -> UNKNOWN
    }
}

internal fun googleMoonPhase(phase: String?): MoonPhase? =
    phase?.let { p -> MoonPhase.entries.firstOrNull { it.name == p } }

internal fun googleSeverity(severity: String?): AlertSeverity = when (severity) {
    "EXTREME" -> AlertSeverity.EXTREME
    "SEVERE" -> AlertSeverity.SEVERE
    "MODERATE" -> AlertSeverity.MODERATE
    "MINOR" -> AlertSeverity.MINOR
    else -> AlertSeverity.UNKNOWN
}
