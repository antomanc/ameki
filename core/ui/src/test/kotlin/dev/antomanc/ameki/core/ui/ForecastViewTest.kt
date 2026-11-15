package dev.antomanc.ameki.core.ui

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.NowcastSlot
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ForecastViewTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val fetched = Instant.parse("2026-11-15T10:00:00Z")

    private fun hour(at: Instant, temp: Double) = HourlyForecast(
        time = at, temperature = temp, condition = WeatherCondition.CLEAR, isDaytime = true,
        precipitationProbability = 0, precipitation = 0.0, windSpeed = null, windDirection = null, humidity = null, uvIndex = null,
    )

    private fun day(date: LocalDate) = DailyForecast(
        date = date, temperatureMax = 15.0, temperatureMin = 5.0, condition = WeatherCondition.CLEAR,
        precipitationProbability = 0, precipitation = 0.0, windSpeedMax = null, uvIndexMax = null,
        sunrise = null, sunset = null, moonPhase = null,
    )

    private val snapshot = WeatherSnapshot(
        SourceId.OPEN_METEO, zone,
        CurrentWeather(fetched, 12.4, null, WeatherCondition.CLEAR, null, true, null, null, null, null, null, null, null, null, null),
        hourly = (0 until 6).map { hour(fetched.plusSeconds(3600L * it), 10.0 + it) },
        daily = listOf(day(LocalDate.of(2026, 11, 14)), day(LocalDate.of(2026, 11, 15)), day(LocalDate.of(2026, 11, 16))),
        nowcast = (0 until 8).map { NowcastSlot(fetched.plusSeconds(900L * it), 0.0) },
        fetchedAt = DataPart.entries.associateWith { fetched },
    )

    @Test
    fun `the now hour shows the current reading while it is fresh`() {
        val view = ForecastView(snapshot, fetched.plusSeconds(600), 30)
        assertThat(view.hours.first().isNow).isTrue()
        assertThat(view.hours.first().temperature).isEqualTo(12.4)
        assertThat(view.hours[1].temperature).isEqualTo(11.0)

        val old = ForecastView(snapshot, fetched.plusSeconds(3 * 3600 + 60), 30)
        assertThat(old.hours.first().isNow).isFalse()
        assertThat(old.hours.first().temperature).isEqualTo(13.0)
        assertThat(old.stale).isTrue()
    }

    @Test
    fun `an hour that has not started is never labelled now`() {
        val view = ForecastView(snapshot.copy(hourly = snapshot.hourly.drop(1)), fetched.plusSeconds(600), 30)
        assertThat(view.hours.first().isNow).isFalse()
    }

    @Test
    fun `days start today in the place's zone`() {
        val view = ForecastView(snapshot, fetched, 30)
        assertThat(view.days.map { it.date }).containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16)).inOrder()
        assertThat(view.today!!.date).isEqualTo(LocalDate.of(2026, 11, 15))
        // 23:30 UTC is already the 16th in Rome.
        assertThat(ForecastView(snapshot, Instant.parse("2026-11-15T23:30:00Z"), 30).days.first().date).isEqualTo(LocalDate.of(2026, 11, 16))
    }

    @Test
    fun `rain dots disappear with the nowcast while the sentence falls back to the hours`() {
        assertThat(ForecastView(snapshot, fetched.plusSeconds(600), 30).rainSlots).isNotEmpty()
        val old = ForecastView(snapshot, fetched.plusSeconds(2 * 3600 + 60), 30)
        assertThat(old.rainSlots).isEmpty()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(old.rainText(context, TimeFormatter(context, TimeFormat.H24), short = true)).isEqualTo("No rain in the next 2 hours")
    }

    @Test
    fun `a long gap since the last update is stale even with a fresh-looking reading`() {
        val noCurrent = snapshot.copy(current = null, fetchedAt = snapshot.fetchedAt - DataPart.CURRENT)
        assertThat(ForecastView(noCurrent, fetched.plusSeconds(100 * 60), 30).stale).isFalse()
        assertThat(ForecastView(noCurrent, fetched.plusSeconds(121 * 60), 30).stale).isTrue()
        assertThat(ForecastView(noCurrent, fetched.plusSeconds(121 * 60), 60).stale).isFalse()
    }
}
