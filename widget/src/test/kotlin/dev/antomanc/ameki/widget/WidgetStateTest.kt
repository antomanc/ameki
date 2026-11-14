package dev.antomanc.ameki.widget

import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateTest {
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private val place = Place(1, "Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome")

    private fun current() = CurrentWeather(
        now, 15.0, null, WeatherCondition.CLEAR, null, true,
        null, null, null, null, null, null, null, null, null,
    )

    private fun daily() = DailyForecast(
        LocalDate.of(2026, 11, 14), 18.0, 9.0, WeatherCondition.RAIN, null, null, 40, 1.0, null,
        null, null, null, null, null,
    )

    private fun data(
        weather: PlaceWeather?,
        placeId: Long? = null,
        placeExists: Boolean = true,
    ) = WidgetData(WidgetConfig(placeId, WidgetStyle.DYNAMIC, 1f), weather, AppSettings(), placeExists)

    @Test
    fun `a configured place that disappeared is reported, never replaced by the first place`() {
        val gone = data(weather = null, placeId = 42, placeExists = false)
        assertEquals(WidgetState.PLACE_GONE, widgetState(gone, WidgetNeed.CURRENT, now))
    }

    @Test
    fun `no weather and no configured place means there is no place yet`() {
        assertEquals(WidgetState.NO_PLACE, widgetState(data(null), WidgetNeed.CURRENT, now))
    }

    @Test
    fun `a partial cache serves the widgets it can fill`() {
        val onlyDaily = WeatherSnapshot(
            SourceId.OPEN_METEO, ZoneId.of("Europe/Rome"), null,
            daily = listOf(daily()),
            fetchedAt = mapOf(DataPart.DAILY to now),
        )
        val w = data(PlaceWeather(place, onlyDaily, now, null))
        assertEquals(WidgetState.READY, widgetState(w, WidgetNeed.DAILY, now))
        assertEquals(WidgetState.PARTIAL, widgetState(w, WidgetNeed.CURRENT, now))
        // Rain can still say "data not available" from another cached part.
        assertEquals(WidgetState.READY, widgetState(w, WidgetNeed.RAIN, now))
    }

    @Test
    fun `specific errors are not endless loading`() {
        val old = now.minusSeconds(3600)
        assertEquals(WidgetState.OFFLINE, widgetState(data(PlaceWeather(place, null, old, SyncError.NETWORK)), WidgetNeed.CURRENT, now))
        assertEquals(WidgetState.QUOTA, widgetState(data(PlaceWeather(place, null, old, SyncError.QUOTA)), WidgetNeed.CURRENT, now))
        assertEquals(WidgetState.ACCESS_DENIED, widgetState(data(PlaceWeather(place, null, old, SyncError.ACCESS_DENIED)), WidgetNeed.CURRENT, now))
        assertEquals(WidgetState.UNSUPPORTED, widgetState(data(PlaceWeather(place, null, old, SyncError.UNSUPPORTED)), WidgetNeed.CURRENT, now))
        assertEquals(WidgetState.FAILED, widgetState(data(PlaceWeather(place, null, old, SyncError.UNKNOWN)), WidgetNeed.CURRENT, now))
    }

    @Test
    fun `a recent attempt without data is loading, an old one is not`() {
        val w = PlaceWeather(place, null, now.minusSeconds(10), null)
        assertEquals(WidgetState.LOADING, widgetState(data(w), WidgetNeed.CURRENT, now))
        val old = PlaceWeather(place, null, now.minusSeconds(600), null)
        assertEquals(WidgetState.FAILED, widgetState(data(old), WidgetNeed.CURRENT, now))
    }

    @Test
    fun `an old current reading is kept but not fresh`() {
        val s = WeatherSnapshot(
            SourceId.OPEN_METEO, ZoneId.of("Europe/Rome"), current(),
            fetchedAt = mapOf(DataPart.CURRENT to now),
        )
        assertTrue(s.currentFresh(now.plusSeconds(3600)))
        assertFalse(s.currentFresh(now.plusSeconds(3 * 3600)))
    }

    @Test
    fun `the preview sample can fill every widget type, including rain`() {
        val sample = PreviewData.sample(now)
        assertTrue(sample.hasData(WidgetNeed.CURRENT))
        assertTrue(sample.hasData(WidgetNeed.HOURLY))
        assertTrue(sample.hasData(WidgetNeed.DAILY))
        assertTrue(sample.hasData(WidgetNeed.RAIN))
    }
}
