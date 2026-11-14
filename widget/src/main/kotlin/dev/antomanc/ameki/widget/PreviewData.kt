package dev.antomanc.ameki.widget

import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.NowcastSlot
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Illustrative data for the widget picker preview and screenshot tests. Not real weather. */
object PreviewData {
    fun sample(now: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS)): WidgetData {
        val zone = ZoneId.systemDefault()
        val temps = listOf(15.0, 16.0, 17.0, 17.0, 16.0, 15.0, 13.0, 12.0, 11.0, 11.0, 10.0, 10.0)
        val rain = listOf(5, 10, 20, 60, 80, 40, 10, 0, 0, 0, 5, 10)
        val conds = listOf(
            WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.MOSTLY_CLOUDY,
            WeatherCondition.SHOWERS, WeatherCondition.RAIN, WeatherCondition.LIGHT_RAIN, WeatherCondition.CLOUDY,
            WeatherCondition.MOSTLY_CLEAR, WeatherCondition.CLEAR, WeatherCondition.CLEAR, WeatherCondition.CLEAR, WeatherCondition.MOSTLY_CLEAR,
        )
        val hourly = temps.indices.map { i ->
            HourlyForecast(
                time = now.plusSeconds(3600L * i), temperature = temps[i], condition = conds[i], isDaytime = i < 7,
                precipitationProbability = rain[i], precipitation = rain[i] / 40.0, windSpeed = 12.0, windDirection = 200,
                humidity = 70, uvIndex = 2.0,
            )
        }
        val today = LocalDate.now(zone)
        val dailyConds = listOf(WeatherCondition.SHOWERS, WeatherCondition.RAIN, WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.THUNDERSTORM, WeatherCondition.MOSTLY_CLEAR, WeatherCondition.CLOUDY)
        val daily = (0 until 7).map { i ->
            DailyForecast(
                date = today.plusDays(i.toLong()), temperatureMax = listOf(18.0, 15.0, 19.0, 20.0, 17.0, 16.0, 14.0)[i],
                temperatureMin = listOf(9.0, 10.0, 7.0, 8.0, 11.0, 9.0, 8.0)[i], condition = dailyConds[i],
                precipitationProbability = listOf(80, 70, 0, 10, 60, 5, 20)[i], precipitation = 1.0, windSpeedMax = 20.0,
                uvIndexMax = 3.0, sunrise = null, sunset = null, moonPhase = null,
            )
        }
        val snapshot = WeatherSnapshot(
            source = SourceId.OPEN_METEO, timeZone = zone,
            current = CurrentWeather(now, 15.0, 14.0, WeatherCondition.PARTLY_CLOUDY, null, true, 70, 9.0, 2.0, 1017.0, 12.0, 25.0, 200, 20.0, 50, 10),
            hourly = hourly, daily = daily,
            // The Rain preview should show its own dots, not fall back to the hourly strip.
            nowcast = (0 until 8).map { i ->
                NowcastSlot(now.plusSeconds(900L * i), listOf(0.0, 0.05, 0.2, 0.6, 0.8, 0.4, 0.1, 0.0)[i])
            },
            fetchedAt = DataPart.entries.associateWith { now },
        )
        val place = Place(0, "Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome")
        return WidgetData(WidgetConfig.Default, PlaceWeather(place, snapshot, now, null), AppSettings(), placeExists = true)
    }
}
