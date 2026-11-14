package dev.antomanc.ameki.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherCondition.*

/**
 * Meteocons for every condition (see THIRD_PARTY_NOTICES.md). Three renditions share one
 * mapping: [color] for the app, [mono] (white, to be tinted) for widgets, [animated] (Lottie)
 * for the current-condition hero only.
 */
object WeatherIcons {
    private data class Set(val color: Int, val mono: Int, val animated: Int)

    private fun day(c: WeatherCondition): Set = when (c) {
        CLEAR -> Set(R.drawable.wx_clear_day, R.drawable.wxm_clear_day, R.raw.wxl_clear_day)
        MOSTLY_CLEAR -> Set(R.drawable.wx_mostly_clear_day, R.drawable.wxm_mostly_clear_day, R.raw.wxl_mostly_clear_day)
        PARTLY_CLOUDY -> Set(R.drawable.wx_partly_cloudy_day, R.drawable.wxm_partly_cloudy_day, R.raw.wxl_partly_cloudy_day)
        MOSTLY_CLOUDY -> Set(R.drawable.wx_overcast_day, R.drawable.wxm_overcast_day, R.raw.wxl_overcast_day)
        FOG -> Set(R.drawable.wx_fog_day, R.drawable.wxm_fog_day, R.raw.wxl_fog_day)
        SHOWERS -> Set(R.drawable.wx_partly_cloudy_day_rain, R.drawable.wxm_partly_cloudy_day_rain, R.raw.wxl_partly_cloudy_day_rain)
        SNOW_SHOWERS -> Set(R.drawable.wx_partly_cloudy_day_snow, R.drawable.wxm_partly_cloudy_day_snow, R.raw.wxl_partly_cloudy_day_snow)
        THUNDERSTORM -> Set(R.drawable.wx_thunderstorms_day_rain, R.drawable.wxm_thunderstorms_day_rain, R.raw.wxl_thunderstorms_day_rain)
        else -> common(c)
    }

    private fun night(c: WeatherCondition): Set = when (c) {
        CLEAR -> Set(R.drawable.wx_clear_night, R.drawable.wxm_clear_night, R.raw.wxl_clear_night)
        MOSTLY_CLEAR -> Set(R.drawable.wx_mostly_clear_night, R.drawable.wxm_mostly_clear_night, R.raw.wxl_mostly_clear_night)
        PARTLY_CLOUDY -> Set(R.drawable.wx_partly_cloudy_night, R.drawable.wxm_partly_cloudy_night, R.raw.wxl_partly_cloudy_night)
        MOSTLY_CLOUDY -> Set(R.drawable.wx_overcast_night, R.drawable.wxm_overcast_night, R.raw.wxl_overcast_night)
        FOG -> Set(R.drawable.wx_fog_night, R.drawable.wxm_fog_night, R.raw.wxl_fog_night)
        SHOWERS -> Set(R.drawable.wx_partly_cloudy_night_rain, R.drawable.wxm_partly_cloudy_night_rain, R.raw.wxl_partly_cloudy_night_rain)
        SNOW_SHOWERS -> Set(R.drawable.wx_partly_cloudy_night_snow, R.drawable.wxm_partly_cloudy_night_snow, R.raw.wxl_partly_cloudy_night_snow)
        THUNDERSTORM -> Set(R.drawable.wx_thunderstorms_night_rain, R.drawable.wxm_thunderstorms_night_rain, R.raw.wxl_thunderstorms_night_rain)
        else -> common(c)
    }

    private fun common(c: WeatherCondition): Set = when (c) {
        CLOUDY -> Set(R.drawable.wx_overcast, R.drawable.wxm_overcast, R.raw.wxl_overcast)
        WINDY -> Set(R.drawable.wx_wind, R.drawable.wxm_wind, R.raw.wxl_wind)
        DRIZZLE -> Set(R.drawable.wx_drizzle, R.drawable.wxm_drizzle, R.raw.wxl_drizzle)
        LIGHT_RAIN -> Set(R.drawable.wx_overcast_drizzle, R.drawable.wxm_overcast_drizzle, R.raw.wxl_overcast_drizzle)
        RAIN -> Set(R.drawable.wx_rain, R.drawable.wxm_rain, R.raw.wxl_rain)
        HEAVY_RAIN -> Set(R.drawable.wx_extreme_rain, R.drawable.wxm_extreme_rain, R.raw.wxl_extreme_rain)
        FREEZING_RAIN -> Set(R.drawable.wx_overcast_sleet, R.drawable.wxm_overcast_sleet, R.raw.wxl_overcast_sleet)
        SLEET -> Set(R.drawable.wx_sleet, R.drawable.wxm_sleet, R.raw.wxl_sleet)
        LIGHT_SNOW -> Set(R.drawable.wx_overcast_snow, R.drawable.wxm_overcast_snow, R.raw.wxl_overcast_snow)
        SNOW -> Set(R.drawable.wx_snow, R.drawable.wxm_snow, R.raw.wxl_snow)
        HEAVY_SNOW -> Set(R.drawable.wx_extreme_snow, R.drawable.wxm_extreme_snow, R.raw.wxl_extreme_snow)
        HAIL -> Set(R.drawable.wx_hail, R.drawable.wxm_hail, R.raw.wxl_hail)
        else -> Set(R.drawable.wx_not_available, R.drawable.wxm_not_available, R.raw.wxl_not_available)
    }

    private fun set(c: WeatherCondition, isDay: Boolean) = if (isDay) day(c) else night(c)

    @DrawableRes fun color(c: WeatherCondition, isDay: Boolean = true): Int = set(c, isDay).color
    @DrawableRes fun mono(c: WeatherCondition, isDay: Boolean = true): Int = set(c, isDay).mono
    @RawRes fun animated(c: WeatherCondition, isDay: Boolean = true): Int = set(c, isDay).animated
}
