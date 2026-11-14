package dev.antomanc.ameki.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Canonical units everywhere in the model: °C, km/h, hPa, mm, km, percent 0..100.
 * Conversion to the user's units happens only at display time (see Units.kt), so changing
 * units never needs a new network call.
 */

@Serializable
data class CurrentWeather(
    @Serializable(InstantSerializer::class) val time: Instant,
    val temperature: Double,
    val feelsLike: Double?,
    val condition: WeatherCondition,
    /** Localized text from the source, when it has one; the UI falls back to its own label. */
    val conditionText: String? = null,
    val isDaytime: Boolean,
    val humidity: Int?,
    val dewPoint: Double?,
    val uvIndex: Double?,
    val pressure: Double?,
    val windSpeed: Double?,
    val windGust: Double?,
    val windDirection: Int?,
    val visibility: Double?,
    val cloudCover: Int?,
    val precipitationProbability: Int? = null,
)

@Serializable
data class HourlyForecast(
    @Serializable(InstantSerializer::class) val time: Instant,
    val temperature: Double,
    val feelsLike: Double? = null,
    val condition: WeatherCondition,
    val isDaytime: Boolean,
    val precipitationProbability: Int?,
    val precipitation: Double?,
    val windSpeed: Double?,
    val windGust: Double? = null,
    val windDirection: Int?,
    val humidity: Int?,
    val dewPoint: Double? = null,
    val uvIndex: Double?,
)

@Serializable
enum class MoonPhase {
    NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
    FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
}

@Serializable
data class DailyForecast(
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val condition: WeatherCondition,
    val conditionText: String? = null,
    val nightCondition: WeatherCondition? = null,
    val precipitationProbability: Int?,
    val precipitation: Double?,
    val windSpeedMax: Double?,
    val windDirection: Int? = null,
    val uvIndexMax: Double?,
    @Serializable(InstantSerializer::class) val sunrise: Instant?,
    @Serializable(InstantSerializer::class) val sunset: Instant?,
    val moonPhase: MoonPhase?,
)

@Serializable
enum class AlertSeverity { EXTREME, SEVERE, MODERATE, MINOR, UNKNOWN }

@Serializable
data class WeatherAlert(
    val id: String,
    val title: String,
    val description: String?,
    val severity: AlertSeverity,
    val areaName: String?,
    /** Issuing authority, e.g. "Protezione Civile" or "MeteoAlarm". */
    val source: String?,
    val sourceUrl: String? = null,
    @Serializable(InstantSerializer::class) val start: Instant?,
    @Serializable(InstantSerializer::class) val end: Instant?,
    val instructions: List<String> = emptyList(),
)

/** The parts a source can be asked for. Each part is refreshed and expired independently. */
@Serializable
enum class DataPart { CURRENT, HOURLY, DAILY, ALERTS }

/**
 * Everything we know about one place from one source. Sections the source does not
 * support (see [Capability]) stay empty and the UI hides them.
 */
@Serializable
data class WeatherSnapshot(
    val source: SourceId,
    @Serializable(ZoneIdSerializer::class) val timeZone: ZoneId,
    val current: CurrentWeather?,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    /** When each part was fetched. A part missing here was never fetched or has expired. */
    val fetchedAt: Map<DataPart, @Serializable(InstantSerializer::class) Instant> = emptyMap(),
) {
    /** Oldest fetch time among the parts we have: what "updated at" should honestly show. */
    val updatedAt: Instant? get() = fetchedAt.values.minOrNull()

    val today: DailyForecast? get() = daily.firstOrNull()

    /** Upcoming hours, starting with the current one. */
    fun hoursFrom(now: Instant): List<HourlyForecast> =
        hourly.filter { it.time.plusSeconds(3600) > now }

    /** Keep only the parts listed, as if the others had never been fetched. */
    fun retain(parts: Set<DataPart>): WeatherSnapshot = copy(
        current = current.takeIf { DataPart.CURRENT in parts },
        hourly = if (DataPart.HOURLY in parts) hourly else emptyList(),
        daily = if (DataPart.DAILY in parts) daily else emptyList(),
        alerts = if (DataPart.ALERTS in parts) alerts else emptyList(),
        fetchedAt = fetchedAt.filterKeys { it in parts },
    )

    /** Overlay the parts present in [newer] on top of this snapshot. */
    fun mergedWith(newer: WeatherSnapshot): WeatherSnapshot {
        if (newer.source != source) return newer
        val parts = newer.fetchedAt.keys
        return WeatherSnapshot(
            source = source,
            timeZone = newer.timeZone,
            current = if (DataPart.CURRENT in parts) newer.current else current,
            hourly = if (DataPart.HOURLY in parts) newer.hourly else hourly,
            daily = if (DataPart.DAILY in parts) newer.daily else daily,
            alerts = if (DataPart.ALERTS in parts) newer.alerts else alerts,
            fetchedAt = fetchedAt + newer.fetchedAt,
        )
    }
}
