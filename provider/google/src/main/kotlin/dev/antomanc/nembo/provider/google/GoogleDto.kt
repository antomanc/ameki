package dev.antomanc.nembo.provider.google

import kotlinx.serialization.Serializable

// Field names follow https://developers.google.com/maps/documentation/weather/reference/rest

@Serializable internal data class TimeZoneDto(val id: String? = null)
@Serializable internal data class LocalizedText(val text: String? = null, val languageCode: String? = null)
@Serializable internal data class TemperatureDto(val degrees: Double? = null, val unit: String? = null)
@Serializable internal data class ConditionDto(val type: String? = null, val description: LocalizedText? = null, val iconBaseUri: String? = null)
@Serializable internal data class ProbabilityDto(val percent: Int? = null, val type: String? = null)
@Serializable internal data class QpfDto(val quantity: Double? = null, val unit: String? = null)
@Serializable internal data class PrecipitationDto(val probability: ProbabilityDto? = null, val qpf: QpfDto? = null, val snowQpf: QpfDto? = null)
@Serializable internal data class AirPressureDto(val meanSeaLevelMillibars: Double? = null)
@Serializable internal data class WindDirectionDto(val degrees: Int? = null, val cardinal: String? = null)
@Serializable internal data class WindSpeedDto(val value: Double? = null, val unit: String? = null)
@Serializable internal data class WindDto(val direction: WindDirectionDto? = null, val speed: WindSpeedDto? = null, val gust: WindSpeedDto? = null)
@Serializable internal data class VisibilityDto(val distance: Double? = null, val unit: String? = null)
@Serializable internal data class IntervalDto(val startTime: String? = null, val endTime: String? = null)
@Serializable internal data class DateDto(val year: Int = 0, val month: Int = 0, val day: Int = 0)

@Serializable
internal data class CurrentConditionsResponse(
    val currentTime: String? = null,
    val timeZone: TimeZoneDto? = null,
    val isDaytime: Boolean = true,
    val weatherCondition: ConditionDto? = null,
    val temperature: TemperatureDto? = null,
    val feelsLikeTemperature: TemperatureDto? = null,
    val dewPoint: TemperatureDto? = null,
    val relativeHumidity: Int? = null,
    val uvIndex: Int? = null,
    val precipitation: PrecipitationDto? = null,
    val thunderstormProbability: Int? = null,
    val airPressure: AirPressureDto? = null,
    val wind: WindDto? = null,
    val visibility: VisibilityDto? = null,
    val cloudCover: Int? = null,
)

@Serializable
internal data class ForecastHourDto(
    val interval: IntervalDto? = null,
    val isDaytime: Boolean = true,
    val weatherCondition: ConditionDto? = null,
    val temperature: TemperatureDto? = null,
    val feelsLikeTemperature: TemperatureDto? = null,
    val dewPoint: TemperatureDto? = null,
    val precipitation: PrecipitationDto? = null,
    val airPressure: AirPressureDto? = null,
    val wind: WindDto? = null,
    val visibility: VisibilityDto? = null,
    val relativeHumidity: Int? = null,
    val uvIndex: Int? = null,
    val thunderstormProbability: Int? = null,
    val cloudCover: Int? = null,
)

@Serializable
internal data class HoursResponse(
    val forecastHours: List<ForecastHourDto> = emptyList(),
    val timeZone: TimeZoneDto? = null,
    val nextPageToken: String? = null,
)

@Serializable
internal data class DayPartDto(
    val weatherCondition: ConditionDto? = null,
    val precipitation: PrecipitationDto? = null,
    val wind: WindDto? = null,
    val relativeHumidity: Int? = null,
    val uvIndex: Int? = null,
    val thunderstormProbability: Int? = null,
    val cloudCover: Int? = null,
)

@Serializable internal data class SunEventsDto(val sunriseTime: String? = null, val sunsetTime: String? = null)
@Serializable internal data class MoonEventsDto(val moonPhase: String? = null)

@Serializable
internal data class ForecastDayDto(
    val interval: IntervalDto? = null,
    val displayDate: DateDto? = null,
    val daytimeForecast: DayPartDto? = null,
    val nighttimeForecast: DayPartDto? = null,
    val fullDayForecast: DayPartDto? = null,
    val maxTemperature: TemperatureDto? = null,
    val minTemperature: TemperatureDto? = null,
    val sunEvents: SunEventsDto? = null,
    val moonEvents: MoonEventsDto? = null,
)

@Serializable
internal data class DaysResponse(
    val forecastDays: List<ForecastDayDto> = emptyList(),
    val timeZone: TimeZoneDto? = null,
    val nextPageToken: String? = null,
)

@Serializable internal data class DataSourceDto(val publisher: String? = null, val name: String? = null, val authorityUri: String? = null)
@Serializable internal data class SafetyRecommendationDto(val directive: String? = null, val subtext: String? = null)

@Serializable
internal data class AlertDto(
    val alertId: String? = null,
    val alertTitle: LocalizedText? = null,
    val eventType: String? = null,
    val areaName: String? = null,
    val instruction: List<String> = emptyList(),
    val safetyRecommendations: List<SafetyRecommendationDto> = emptyList(),
    val startTime: String? = null,
    val expirationTime: String? = null,
    val dataSource: DataSourceDto? = null,
    val description: String? = null,
    val severity: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
)

@Serializable
internal data class AlertsResponse(
    val weatherAlerts: List<AlertDto> = emptyList(),
    val regionCode: String? = null,
    val nextPageToken: String? = null,
)

@Serializable internal data class ErrorInfoDto(val reason: String? = null)
@Serializable internal data class ErrorBodyDto(val code: Int? = null, val message: String? = null, val status: String? = null, val details: List<ErrorInfoDto> = emptyList())
@Serializable internal data class ErrorEnvelope(val error: ErrorBodyDto? = null)
