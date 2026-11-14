package dev.antomanc.ameki.source.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ForecastResponse(
    val timezone: String,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
)

@Serializable
internal data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("is_day") val isDay: Int = 1,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("pressure_msl") val pressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    /** Meters. */
    val visibility: Double? = null,
)

@Serializable
internal data class HourlyDto(
    val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("relative_humidity_2m") val humidity: List<Int?>? = null,
    @SerialName("dew_point_2m") val dewPoint: List<Double?>? = null,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = null,
    val precipitation: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection: List<Int?>? = null,
    @SerialName("wind_gusts_10m") val windGusts: List<Double?>? = null,
    @SerialName("uv_index") val uvIndex: List<Double?>? = null,
    @SerialName("is_day") val isDay: List<Int?>? = null,
)

@Serializable
internal data class DailyDto(
    val time: List<Long>,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>,
    val sunrise: List<Long?>? = null,
    val sunset: List<Long?>? = null,
    @SerialName("uv_index_max") val uvIndexMax: List<Double?>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null,
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?>? = null,
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Int?>? = null,
)

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
internal data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val timezone: String? = null,
    @SerialName("feature_code") val featureCode: String? = null,
)

@Serializable
internal data class ErrorResponse(val error: Boolean = true, val reason: String? = null)
