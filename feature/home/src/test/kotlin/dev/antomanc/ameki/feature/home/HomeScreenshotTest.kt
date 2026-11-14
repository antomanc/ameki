package dev.antomanc.ameki.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.MoonPhase
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.ThemeMode
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.theme.AmekiTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Illustrative data, not real weather. */
private fun sampleState(): HomeUiState {
    val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
    val zone = ZoneId.of("Europe/Rome")
    val temps = listOf(15.0, 16.0, 17.0, 17.0, 16.0, 15.0, 13.0, 12.0, 11.0, 11.0, 10.0, 10.0, 9.0, 9.0, 9.0, 8.0, 8.0, 9.0, 10.0, 12.0, 14.0, 15.0, 16.0, 16.0)
    val rain = listOf(5, 10, 20, 60, 80, 40, 10, 0, 0, 0, 5, 10, 10, 5, 0, 0, 0, 0, 0, 5, 10, 10, 5, 0)
    val hourly = (0 until 48).map { it % 24 }.mapIndexed { i, k -> i to k }.map { (i, k) ->
        HourlyForecast(now.plusSeconds(3600L * i), temps[k], null, if (rain[k] > 50) WeatherCondition.RAIN else WeatherCondition.PARTLY_CLOUDY, k < 7,
            rain[k], rain[k] / 30.0, 10.0 + k % 7, 18.0, 180 + k * 5, 60 + k % 30, 8.0, 2.0)
    }
    val today = LocalDate.now(zone)
    val daily = (0 until 10).map { i ->
        DailyForecast(today.plusDays(i.toLong()), 18.0 - (i % 4), 9.0 - (i % 3), listOf(WeatherCondition.SHOWERS, WeatherCondition.RAIN, WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY)[i % 4],
            null, null, listOf(80, 70, 0, 10)[i % 4], 1.0, 20.0, 200, 3.0, now.minusSeconds(3 * 3600), now.plusSeconds(5 * 3600), MoonPhase.WAXING_GIBBOUS)
    }
    val snap = WeatherSnapshot(
        SourceId.OPEN_METEO, zone,
        CurrentWeather(now, 15.0, 14.0, WeatherCondition.PARTLY_CLOUDY, null, true, 72, 9.8, 2.0, 1017.0, 12.0, 26.0, 200, 24.0, 55, 10),
        hourly, daily, fetchedAt = DataPart.entries.associateWith { now },
    )
    val place = Place(1, "Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome")
    val milano = Place(2, "Milano", "Lombardia", "Italia", GeoPoint(45.46, 9.19), "Europe/Rome")
    return HomeUiState(
        loading = false,
        places = listOf(PlaceWeather(place, snap, now, null), PlaceWeather(milano, snap.copy(current = snap.current!!.copy(temperature = 11.0, condition = WeatherCondition.FOG)), now, null)),
        settings = AppSettings(onboardingDone = true),
        capabilities = Capability.entries.toSet(),
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, theme: ThemeMode) {
        compose.setContent {
            AmekiTheme(themeMode = theme, dynamicColor = false) {
                HomeScreen(sampleState(), 1, {}, {}, {}, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun phoneLight() = shoot("home_phone_light", ThemeMode.LIGHT)

    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    @Test fun phoneDark() = shoot("home_phone_dark", ThemeMode.DARK)

    @Config(qualifiers = "w900dp-h800dp-xhdpi")
    @Test fun unfolded() = shoot("home_unfolded_light", ThemeMode.LIGHT)

    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    @Test fun dayPage() {
        val state = sampleState()
        compose.setContent {
            AmekiTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    DayPage(state.places.first(), state, DayTarget(1), {}, {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_page_dark.png")
    }
}
