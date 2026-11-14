package dev.antomanc.ameki.core.model

/**
 * Source-independent condition. Every source maps its own codes onto this set, and the
 * icon set in :core:ui draws exactly these, so app and widgets look the same whatever the source.
 */
enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    MOSTLY_CLOUDY,
    CLOUDY,
    FOG,
    WINDY,
    DRIZZLE,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
    SHOWERS,
    FREEZING_RAIN,
    SLEET,
    LIGHT_SNOW,
    SNOW,
    HEAVY_SNOW,
    SNOW_SHOWERS,
    HAIL,
    THUNDERSTORM,
    UNKNOWN,
    ;

    val isPrecipitation: Boolean
        get() = this in setOf(
            DRIZZLE, LIGHT_RAIN, RAIN, HEAVY_RAIN, SHOWERS, FREEZING_RAIN, SLEET,
            LIGHT_SNOW, SNOW, HEAVY_SNOW, SNOW_SHOWERS, HAIL, THUNDERSTORM,
        )

    val isSnowy: Boolean
        get() = this in setOf(LIGHT_SNOW, SNOW, HEAVY_SNOW, SNOW_SHOWERS, SLEET)
}
