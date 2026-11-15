package dev.antomanc.ameki.core.model

import kotlinx.serialization.Serializable
import java.time.Duration
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
enum class DataPart(val capability: Capability) {
    CURRENT(Capability.CURRENT),
    HOURLY(Capability.HOURLY),
    DAILY(Capability.DAILY),
    ALERTS(Capability.ALERTS),
    /** Precipitation for the next couple of hours in 15-minute steps. */
    NOWCAST(Capability.NOWCAST),
}

/** One 15-minute slot of the precipitation nowcast. */
@Serializable
data class NowcastSlot(
    @Serializable(InstantSerializer::class) val time: Instant,
    /** Millimetres falling in this 15-minute slot. */
    val precipitation: Double,
)

/**
 * Hours past the regular refresh window, fetched on demand for a day page. They keep their own
 * fetch time: a later regular refresh replaces the hours it covers but must never extend how
 * long the older extra hours may be kept.
 */
@Serializable
data class HourlyExtension(
    @Serializable(InstantSerializer::class) val fetchedAt: Instant,
    /** End of the range asked for: up to here the hours are as complete as the source allows. */
    @Serializable(InstantSerializer::class) val requestedUntil: Instant,
    /** First hour that came with [fetchedAt]; null when every hour shares the hourly part's fetch. */
    @Serializable(InstantSerializer::class) val from: Instant? = null,
)

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
    val nowcast: List<NowcastSlot> = emptyList(),
    /** When each part was fetched. A part missing here was never fetched or has expired. */
    val fetchedAt: Map<DataPart, @Serializable(InstantSerializer::class) Instant> = emptyMap(),
    val hourlyExtension: HourlyExtension? = null,
) {
    /**
     * When the conditions on screen were fetched. Parts refresh on different schedules (daily
     * data less often), so the oldest part would make fresh weather look stale.
     */
    val updatedAt: Instant? get() = fetchedAt[DataPart.CURRENT] ?: fetchedAt[DataPart.HOURLY] ?: fetchedAt.values.maxOrNull()

    /**
     * Whether [part] still describes the weather it claims to at [now]. Retention can be longer
     * (offline we keep the last data), but an old current reading must not be shown as "now".
     */
    fun partIsFresh(part: DataPart, now: Instant): Boolean = fetchedAt[part]?.let { at ->
        Duration.between(at, now) < RefreshPolicy.weatherFreshness(source, part)
    } == true

    fun currentIsFresh(now: Instant): Boolean {
        val c = current ?: return false
        if (!partIsFresh(DataPart.CURRENT, now)) return false
        // The source's observation time must be current too: fetched recently, stale values still lie.
        return Duration.between(c.time, now).abs() < RefreshPolicy.weatherFreshness(source, DataPart.CURRENT)
    }

    /** Today's row in the place's own time zone. */
    fun today(now: Instant): DailyForecast? = daily.firstOrNull { it.date == now.atZone(timeZone).toLocalDate() }

    /** Upcoming hours, starting with the current one. */
    fun hoursFrom(now: Instant): List<HourlyForecast> =
        hourly.filter { it.time.plusSeconds(3600) > now }

    /** True when the hours reach [until], or the source was already asked for them and gave what it has. */
    fun hoursCover(until: Instant): Boolean =
        hourly.lastOrNull()?.time?.let { !it.isBefore(until.minusSeconds(3600)) } == true ||
            hourlyExtension?.requestedUntil?.let { !it.isBefore(until) } == true

    /** Keep only the parts listed, as if the others had never been fetched. */
    fun retain(parts: Set<DataPart>): WeatherSnapshot = copy(
        current = current.takeIf { DataPart.CURRENT in parts },
        hourly = if (DataPart.HOURLY in parts) hourly else emptyList(),
        daily = if (DataPart.DAILY in parts) daily else emptyList(),
        alerts = if (DataPart.ALERTS in parts) alerts else emptyList(),
        nowcast = if (DataPart.NOWCAST in parts) nowcast else emptyList(),
        fetchedAt = fetchedAt.filterKeys { it in parts },
        hourlyExtension = hourlyExtension.takeIf { DataPart.HOURLY in parts },
    )

    /**
     * What may still be kept at [now]: parts and extra hours past their retention limit are
     * gone (Google's caching terms), or null when nothing is left.
     */
    fun retainedAt(now: Instant): WeatherSnapshot? {
        val keep = fetchedAt.filter { (part, at) -> Duration.between(at, now) < RefreshPolicy.maxAge(source, part) }.keys
        if (keep.isEmpty()) return null
        val kept = retain(keep)
        val extension = kept.hourlyExtension ?: return kept
        if (Duration.between(extension.fetchedAt, now) < RefreshPolicy.maxAge(source, DataPart.HOURLY)) return kept
        val from = extension.from
        return kept.copy(hourly = if (from == null) kept.hourly else kept.hourly.filter { it.time < from }, hourlyExtension = null)
    }

    /**
     * The next moment after [now] when what this snapshot may show changes: a part or the extra
     * hours expire, a part stops being fresh, or the place's day ends. Null once nothing is left.
     */
    fun nextChange(now: Instant): Instant? {
        val alive = fetchedAt.filter { (part, at) -> at.plus(RefreshPolicy.maxAge(source, part)).isAfter(now) }
        if (alive.isEmpty()) return null
        return buildList {
            alive.forEach { (part, at) ->
                add(at.plus(RefreshPolicy.maxAge(source, part)))
                add(at.plus(RefreshPolicy.weatherFreshness(source, part)))
                if (part == DataPart.CURRENT) current?.let { add(it.time.plus(RefreshPolicy.weatherFreshness(source, part))) }
            }
            hourlyExtension?.let { add(it.fetchedAt.plus(RefreshPolicy.maxAge(source, DataPart.HOURLY))) }
            add(now.atZone(timeZone).toLocalDate().plusDays(1).atStartOfDay(timeZone).toInstant())
        }.filter { it.isAfter(now) }.minOrNull()
    }

    /**
     * Overlay the parts present in [newer] on top of this snapshot. A regular hourly refresh
     * keeps extra hours past its own range, with their original fetch time.
     */
    fun mergedWith(newer: WeatherSnapshot): WeatherSnapshot {
        if (newer.source != source) return newer
        val parts = newer.fetchedAt.keys
        val (hours, extension) = if (DataPart.HOURLY in parts) mergeHours(newer) else hourly to hourlyExtension
        return WeatherSnapshot(
            source = source,
            timeZone = newer.timeZone,
            current = if (DataPart.CURRENT in parts) newer.current else current,
            hourly = hours,
            daily = if (DataPart.DAILY in parts) newer.daily else daily,
            alerts = if (DataPart.ALERTS in parts) newer.alerts else alerts,
            nowcast = if (DataPart.NOWCAST in parts) newer.nowcast else nowcast,
            fetchedAt = fetchedAt + newer.fetchedAt,
            hourlyExtension = extension,
        )
    }

    private fun mergeHours(newer: WeatherSnapshot): Pair<List<HourlyForecast>, HourlyExtension?> {
        val newEnd = newer.hourly.lastOrNull()?.time
        if (newer.hourlyExtension != null || newEnd == null) return newer.hourly to newer.hourlyExtension
        val tail = hourly.filter { it.time > newEnd }
        val oldExtension = hourlyExtension
        val tailFetchedAt = oldExtension?.fetchedAt ?: fetchedAt[DataPart.HOURLY]
        val extension = when {
            tailFetchedAt == null -> null
            tail.isNotEmpty() -> HourlyExtension(
                fetchedAt = tailFetchedAt,
                requestedUntil = oldExtension?.requestedUntil ?: tail.last().time.plusSeconds(3600),
                from = tail.first().time,
            )
            // Asked for more than the source has: remember the request, not hours that don't exist.
            oldExtension != null && oldExtension.requestedUntil > newEnd.plusSeconds(3600) -> oldExtension.copy(from = null)
            else -> null
        }
        return (newer.hourly + tail) to extension
    }
}
