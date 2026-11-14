package dev.antomanc.nembo.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ModelTest {
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
            ProviderId.GOOGLE, rome, current(10.0), daily = listOf(daily(LocalDate.of(2026, 11, 14))),
            fetchedAt = mapOf(DataPart.CURRENT to t0, DataPart.DAILY to t0),
        )
        val newer = WeatherSnapshot(
            ProviderId.GOOGLE, rome, current(12.0),
            fetchedAt = mapOf(DataPart.CURRENT to t0.plusSeconds(1800)),
        )
        val merged = old.mergedWith(newer)
        assertThat(merged.current!!.temperature).isEqualTo(12.0)
        assertThat(merged.daily).hasSize(1)
        assertThat(merged.fetchedAt[DataPart.DAILY]).isEqualTo(t0)
        assertThat(merged.updatedAt).isEqualTo(t0)
    }

    @Test
    fun `merge with another provider replaces everything`() {
        val old = WeatherSnapshot(ProviderId.GOOGLE, rome, current(10.0), fetchedAt = mapOf(DataPart.CURRENT to t0))
        val newer = WeatherSnapshot(ProviderId.OPEN_METEO, rome, null, fetchedAt = mapOf(DataPart.DAILY to t0))
        assertThat(old.mergedWith(newer)).isEqualTo(newer)
    }

    @Test
    fun `snapshot round-trips through json`() {
        val s = WeatherSnapshot(
            ProviderId.OPEN_METEO, rome, current(10.0), daily = listOf(daily(LocalDate.of(2026, 11, 14))),
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
            ProviderId.GOOGLE, rome, current(10.0),
            fetchedAt = DataPart.entries.associateWith { t0 },
        )
        val after30 = RefreshPolicy.dueParts(ProviderId.GOOGLE, s, 30, t0.plusSeconds(30 * 60))
        assertThat(after30).containsExactly(DataPart.CURRENT, DataPart.HOURLY)
        val after60 = RefreshPolicy.dueParts(ProviderId.GOOGLE, s, 30, t0.plusSeconds(60 * 60))
        assertThat(after60).containsExactly(DataPart.CURRENT, DataPart.HOURLY, DataPart.ALERTS)
        val after3h = RefreshPolicy.dueParts(ProviderId.GOOGLE, s, 30, t0.plusSeconds(180 * 60))
        assertThat(after3h).containsExactlyElementsIn(DataPart.entries)
    }

    @Test
    fun `everything is due without a snapshot or with another provider`() {
        assertThat(RefreshPolicy.dueParts(ProviderId.GOOGLE, null, 30, t0)).hasSize(4)
        val om = WeatherSnapshot(ProviderId.OPEN_METEO, rome, null, fetchedAt = DataPart.entries.associateWith { t0 })
        assertThat(RefreshPolicy.dueParts(ProviderId.GOOGLE, om, 30, t0)).hasSize(4)
    }

    @Test
    fun `google monthly estimate`() {
        // 30 min: current 1440 + hourly 1440 + alerts 720 + daily 240 = 3840 per place
        assertThat(RefreshPolicy.estimateGoogleCallsPerMonth(1, 30)).isEqualTo(3840)
        assertThat(RefreshPolicy.estimateGoogleCallsPerMonth(3, 30)).isGreaterThan(RefreshPolicy.GOOGLE_FREE_CALLS_PER_MONTH)
    }

    @Test
    fun `google intervals are capped at one hour`() {
        assertThat(RefreshPolicy.clampInterval(ProviderId.GOOGLE, 180)).isEqualTo(60)
        assertThat(RefreshPolicy.clampInterval(ProviderId.OPEN_METEO, 180)).isEqualTo(180)
        assertThat(RefreshPolicy.clampInterval(ProviderId.OPEN_METEO, 5)).isEqualTo(15)
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
