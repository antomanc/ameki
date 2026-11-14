package dev.antomanc.ameki.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ModelTest {
    private val googleCaps = Capability.entries.toSet() - Capability.NOWCAST

    private val t0 = Instant.parse("2026-11-14T10:00:00Z")

    private fun current(temp: Double) = CurrentWeather(
        time = t0, temperature = temp, feelsLike = null, condition = WeatherCondition.CLEAR,
        isDaytime = true, humidity = null, dewPoint = null, uvIndex = null, pressure = null,
        windSpeed = null, windGust = null, windDirection = null, visibility = null, cloudCover = null,
    )

    private fun daily(date: LocalDate) = DailyForecast(
        date = date, temperatureMax = 20.0, temperatureMin = 10.0, condition = WeatherCondition.RAIN,
        precipitationProbability = 40, precipitation = 2.0, windSpeedMax = 10.0, uvIndexMax = 3.0,
        sunrise = null, sunset = null, moonPhase = MoonPhase.FULL_MOON,
    )

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun `merge keeps parts that were not refetched`() {
        val old = WeatherSnapshot(
            SourceId.GOOGLE, rome, current(10.0), daily = listOf(daily(LocalDate.of(2026, 11, 14))),
            fetchedAt = mapOf(DataPart.CURRENT to t0, DataPart.DAILY to t0),
        )
        val newer = WeatherSnapshot(
            SourceId.GOOGLE, rome, current(12.0),
            fetchedAt = mapOf(DataPart.CURRENT to t0.plusSeconds(1800)),
        )
        val merged = old.mergedWith(newer)
        assertThat(merged.current!!.temperature).isEqualTo(12.0)
        assertThat(merged.daily).hasSize(1)
        assertThat(merged.fetchedAt[DataPart.DAILY]).isEqualTo(t0)
        assertThat(merged.updatedAt).isEqualTo(t0.plusSeconds(1800)) // current was refreshed
    }

    @Test
    fun `merge with another source replaces everything`() {
        val old = WeatherSnapshot(SourceId.GOOGLE, rome, current(10.0), fetchedAt = mapOf(DataPart.CURRENT to t0))
        val newer = WeatherSnapshot(SourceId.OPEN_METEO, rome, null, fetchedAt = mapOf(DataPart.DAILY to t0))
        assertThat(old.mergedWith(newer)).isEqualTo(newer)
    }

    @Test
    fun `snapshot round-trips through json`() {
        val s = WeatherSnapshot(
            SourceId.OPEN_METEO, rome, current(10.0), daily = listOf(daily(LocalDate.of(2026, 11, 14))),
            alerts = listOf(
                WeatherAlert("a", "Allerta", null, AlertSeverity.SEVERE, "Lazio", "DPC", start = t0, end = null),
            ),
            fetchedAt = mapOf(DataPart.CURRENT to t0),
        )
        val json = Json.encodeToString(WeatherSnapshot.serializer(), s)
        assertThat(Json.decodeFromString(WeatherSnapshot.serializer(), json)).isEqualTo(s)
    }

    @Test
    fun `due parts for google refresh alerts and daily less often`() {
        val s = WeatherSnapshot(
            SourceId.GOOGLE, rome, current(10.0),
            fetchedAt = DataPart.entries.associateWith { t0 },
        )
        val after30 = RefreshPolicy.dueParts(SourceId.GOOGLE, s, 30, t0.plusSeconds(30 * 60), googleCaps)
        assertThat(after30).containsExactly(DataPart.CURRENT, DataPart.HOURLY)
        val after60 = RefreshPolicy.dueParts(SourceId.GOOGLE, s, 30, t0.plusSeconds(60 * 60), googleCaps)
        assertThat(after60).containsExactly(DataPart.CURRENT, DataPart.HOURLY, DataPart.ALERTS)
        val after3h = RefreshPolicy.dueParts(SourceId.GOOGLE, s, 30, t0.plusSeconds(180 * 60), googleCaps)
        assertThat(after3h).containsExactlyElementsIn(DataPart.entries - DataPart.NOWCAST)
    }

    @Test
    fun `everything is due without a snapshot or with another source`() {
        assertThat(RefreshPolicy.dueParts(SourceId.GOOGLE, null, 30, t0)).hasSize(5)
        val om = WeatherSnapshot(SourceId.OPEN_METEO, rome, null, fetchedAt = DataPart.entries.associateWith { t0 })
        assertThat(RefreshPolicy.dueParts(SourceId.GOOGLE, om, 30, t0)).hasSize(5)
    }

    @Test
    fun `google monthly estimate`() {
        // 30 min: current 1440 + hourly 1440 + alerts 720 + daily 240 = 3840 per place
        assertThat(RefreshPolicy.estimateGoogleCallsPerMonth(1, 30)).isEqualTo(3840)
        assertThat(RefreshPolicy.estimateGoogleCallsPerMonth(3, 30)).isGreaterThan(RefreshPolicy.GOOGLE_FREE_CALLS_PER_MONTH)
    }

    @Test
    fun `google intervals are capped at one hour`() {
        assertThat(RefreshPolicy.clampInterval(SourceId.GOOGLE, 180)).isEqualTo(60)
        assertThat(RefreshPolicy.clampInterval(SourceId.OPEN_METEO, 180)).isEqualTo(180)
        assertThat(RefreshPolicy.clampInterval(SourceId.OPEN_METEO, 5)).isEqualTo(15)
    }

    @Test
    fun `unit conversions`() {
        val us = Units.forCountry("US")
        assertThat(us.temp(0.0)).isEqualTo("32°")
        assertThat(us.temp(21.0)).isEqualTo("70°")
        assertThat(Units().wind(36.0)).isEqualTo("36 km/h")
        assertThat(Units(windSpeed = WindSpeedUnit.METERS_PER_SECOND).wind(36.0)).isEqualTo("10 m/s")
        assertThat(Units(pressure = PressureUnit.INCH_OF_MERCURY).pressure(1013.25)).isEqualTo("29.92 inHg")
    }

    @Test
    fun `compass points`() {
        assertThat(compassPoint(0)).isEqualTo(0)
        assertThat(compassPoint(359)).isEqualTo(0)
        assertThat(compassPoint(90)).isEqualTo(4)
        assertThat(compassPoint(-90)).isEqualTo(12)
    }

    @Test
    fun `moon phase matches known dates`() {
        assertThat(Astronomy.moonPhase(LocalDate.of(2024, 1, 11))).isEqualTo(MoonPhase.NEW_MOON)
        assertThat(Astronomy.moonPhase(LocalDate.of(2024, 1, 25))).isEqualTo(MoonPhase.FULL_MOON)
        assertThat(Astronomy.moonPhase(LocalDate.of(2024, 1, 18))).isEqualTo(MoonPhase.FIRST_QUARTER)
        assertThat(Astronomy.illumination(LocalDate.of(2024, 1, 25))).isGreaterThan(0.95)
    }

    @Test
    fun `coarse point rounds to two decimals`() {
        assertThat(GeoPoint(41.892345, 12.485678).coarse()).isEqualTo(GeoPoint(41.89, 12.49))
    }
}

class NowcastTest {
    private val now = Instant.parse("2026-11-14T10:02:00Z")
    private fun snap(vararg mm: Double) = WeatherSnapshot(
        SourceId.OPEN_METEO, ZoneId.of("UTC"), null,
        nowcast = mm.mapIndexed { i, v -> NowcastSlot(Instant.parse("2026-11-14T10:00:00Z").plusSeconds(900L * i), v) },
        fetchedAt = mapOf(DataPart.NOWCAST to now),
    )

    @Test fun `dry two hours`() = assertThat(Nowcast.outlook(snap(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), now)).isEqualTo(RainOutlook.NoneSoon)
    @Test fun `rain starting`() = assertThat(Nowcast.outlook(snap(0.0, 0.0, 0.3, 0.5), now)).isEqualTo(RainOutlook.StartsIn(30))
    @Test fun `rain stopping`() = assertThat(Nowcast.outlook(snap(0.4, 0.2, 0.0), now)).isEqualTo(RainOutlook.StopsIn(30))
    @Test fun `raining throughout`() = assertThat(Nowcast.outlook(snap(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4), now)).isEqualTo(RainOutlook.RainingNow)

    @Test fun `falls back to hourly probability`() {
        val s = WeatherSnapshot(
            SourceId.GOOGLE, ZoneId.of("UTC"), null,
            hourly = listOf(
                HourlyForecast(Instant.parse("2026-11-14T10:00:00Z"), 10.0, null, WeatherCondition.CLOUDY, true, 20, 0.0, null, null, null, null, null, null),
                HourlyForecast(Instant.parse("2026-11-14T11:00:00Z"), 10.0, null, WeatherCondition.RAIN, true, 70, 1.0, null, null, null, null, null, null),
            ),
        )
        assertThat(Nowcast.outlook(s, now)).isEqualTo(RainOutlook.LikelyAround(Instant.parse("2026-11-14T11:00:00Z")))
    }
}
