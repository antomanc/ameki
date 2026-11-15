package dev.antomanc.ameki.feature.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
internal fun sampleState(): HomeUiState {
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
            null, null, listOf(80, 70, 0, 10)[i % 4], 1.0, 20.0, 200, 3.0, today.plusDays(i.toLong()).atTime(7, 0).atZone(zone).toInstant(), today.plusDays(i.toLong()).atTime(17, 0).atZone(zone).toInstant(), MoonPhase.WAXING_GIBBOUS)
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
    @org.junit.Before fun pauseAnimations() { compose.mainClock.autoAdvance = false }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun requestedCityIsAppliedAfterLoadingAndOnNewRequest() {
        val state = androidx.compose.runtime.mutableStateOf(HomeUiState())
        val requested = androidx.compose.runtime.mutableStateOf<Long?>(2L)
        val shown = mutableListOf<Long>()
        var applied = 0
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HomeScreen(state.value, requested.value, { shown += it }, {}, {}, {}, {},
                    requestId = requested.value ?: 0, onRequestApplied = { applied++ })
            }
        }
        compose.runOnIdle { state.value = sampleState(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(timeoutMillis = 5_000) { compose.mainClock.advanceTimeByFrame(); shown.lastOrNull() == 2L }
        compose.runOnIdle {
            val city = state.value.places.first()
            state.value = state.value.copy(places = state.value.places + city.copy(place = city.place.copy(id = 3, name = "Torino")))
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { org.junit.Assert.assertEquals("Applied requests must not replay when the city list changes", 1, applied) }
        compose.runOnIdle { requested.value = 1L; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.waitUntil(timeoutMillis = 5_000) { compose.mainClock.advanceTimeByFrame(); shown.lastOrNull() == 1L }
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun coldStartDayRequestUsesItsCityEvenWhenFirstCityHasNoDays() {
        val state = androidx.compose.runtime.mutableStateOf(HomeUiState())
        val loaded = sampleState().let { it.copy(places = it.places.mapIndexed { i, pw -> if (i == 0) pw.copy(snapshot = null) else pw }) }
        val date = loaded.places[1].snapshot!!.daily.first().date.toEpochDay()
        var applied = false
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HomeScreen(state.value, 2, {}, {}, {}, {}, {}, requestId = 20,
                    requestedDay = date, onRequestApplied = { applied = true })
            }
        }
        compose.runOnIdle { state.value = loaded; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil { compose.mainClock.advanceTimeByFrame(); applied }
        compose.onNodeWithText("Milano").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun pendingDayRequestDoesNotUndoAUserSwipeOnRefreshChange() {
        val loaded = sampleState()
        val state = androidx.compose.runtime.mutableStateOf(loaded.copy(places = loaded.places.mapIndexed { i, pw -> if (i == 1) pw.copy(snapshot = null) else pw }))
        val shown = mutableListOf<Long>()
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HomeScreen(state.value, 2, { shown += it }, {}, {}, {}, {}, requestId = 21,
                    requestedDay = loaded.places[0].snapshot!!.daily.first().date.toEpochDay())
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil { compose.mainClock.advanceTimeByFrame(); shown.lastOrNull() == 2L }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil { compose.mainClock.advanceTimeByFrame(); shown.lastOrNull() == 1L }
        compose.runOnIdle {
            state.value = state.value.copy(refreshing = setOf(1, 2))
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { org.junit.Assert.assertEquals(1L, shown.lastOrNull()) }
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun unavailableDayRequestIsConsumedAndDoesNotReplay() {
        val state = androidx.compose.runtime.mutableStateOf(sampleState())
        var applied = 0
        val yesterday = state.value.places.first().snapshot!!.daily.first().date.minusDays(1).toEpochDay()
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HomeScreen(state.value, 1, {}, {}, {}, {}, {}, requestId = 9,
                    requestedDay = yesterday, onRequestApplied = { applied++ })
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil { compose.mainClock.advanceTimeByFrame(); applied == 1 }
        compose.runOnIdle {
            state.value = state.value.copy(places = state.value.places.reversed())
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { org.junit.Assert.assertEquals(1, applied) }
        compose.onNodeWithContentDescription("Places").assertIsDisplayed()
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun invalidatedDayReturnsToHomeNavigation() {
        val state = androidx.compose.runtime.mutableStateOf(sampleState())
        val today = state.value.places.first().snapshot!!.daily.first().date.toEpochDay()
        var applied = false
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HomeScreen(state.value, 1, {}, {}, {}, {}, {}, requestId = 10,
                    requestedDay = today, onRequestApplied = { applied = true })
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil { compose.mainClock.advanceTimeByFrame(); applied }
        compose.runOnIdle {
            state.value = state.value.copy(places = state.value.places.map { it.copy(snapshot = null) })
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Places").assertIsDisplayed()
    }

    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun missingPrecipitationIsUnavailableRatherThanZero() {
        val snapshot = sampleState().places.first().snapshot!!
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                HourlyCharts(snapshot.hourly.take(3).map { it.copy(precipitation = null) }, snapshot,
                    dev.antomanc.ameki.core.model.Units(),
                    dev.antomanc.ameki.core.ui.TimeFormatter(androidx.compose.ui.platform.LocalContext.current, dev.antomanc.ameki.core.model.TimeFormat.H24))
            }
        }
        compose.onNodeWithText("Data unavailable").assertIsDisplayed()
        compose.onNodeWithText("0 of 3 hours available").assertIsDisplayed()
    }

    @Config(qualifiers = "w673dp-h891dp-xxhdpi")
    @Test fun foldAtLargeFont() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, 2f),
            ) {
                AmekiTheme(dynamicColor = false) { HomeScreen(sampleState(), 1, {}, {}, {}, {}, {}) }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_fold_font200.png")
    }

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
        org.junit.Assert.assertEquals(1, compose.onAllNodesWithText("Sunrise", substring = true)
            .fetchSemanticsNodes().count { it.boundsInRoot.overlaps(compose.onRoot().fetchSemanticsNode().boundsInRoot) })
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_page_dark.png")
    }

    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    @Test fun dayRequestStartsBeforeTheChipStripFinishesScrolling() {
        val base = sampleState()
        var requested = false
        compose.setContent {
            AmekiTheme(dynamicColor = false) {
                DayPage(base.places.first(), base, DayTarget(7), {}, {
                    requested = true
                    kotlinx.coroutines.awaitCancellation()
                }, {})
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { org.junit.Assert.assertTrue("The request must start independently of chip scrolling", requested) }
    }

    @Config(qualifiers = "w411dp-h1100dp-night-xxhdpi")
    @Test fun dayLoadingReservesTheChartBeforeHoursArrive() {
        val sample = sampleState()
        val base = sample.copy(settings = sample.settings.copy(timeFormat = dev.antomanc.ameki.core.model.TimeFormat.H12))
        val response = kotlinx.coroutines.CompletableDeferred<Unit>()
        val original = base.places.first()
        val snapshot = original.snapshot!!
        val day = snapshot.daily[3]
        val state = androidx.compose.runtime.mutableStateOf(base.copy(
            places = listOf(original.copy(snapshot = snapshot.copy(hourly = emptyList()))),
            loadingHours = setOf(original.place.id),
        ))
        compose.setContent {
            AmekiTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    DayPage(state.value.places.first(), state.value, DayTarget(3), {}, { response.await() }, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        fun visibleChartBounds() = compose.onAllNodesWithText("TEMPERATURE THROUGH THE DAY").fetchSemanticsNodes()
            .single { it.boundsInRoot.overlaps(compose.onRoot().fetchSemanticsNode().boundsInRoot) }.boundsInRoot
        val before = visibleChartBounds()
        val firstHourTag = "day-hour-${day.date.atStartOfDay(snapshot.timeZone).toEpochSecond()}"
        fun visibleFirstHourBounds() = compose.onNodeWithTag(firstHourTag).fetchSemanticsNode().boundsInRoot
        val firstHourBefore = visibleFirstHourBounds()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_loading_reserved.png")
        compose.runOnIdle {
            val hours = snapshot.hourly.take(24).mapIndexed { index, hour ->
                hour.copy(time = day.date.atStartOfDay(snapshot.timeZone).plusHours(index.toLong()).toInstant())
            }
            state.value = base.copy(places = listOf(original.copy(snapshot = snapshot.copy(hourly = hours))))
            response.complete(Unit)
        }
        compose.mainClock.advanceTimeBy(500)
        val after = visibleChartBounds()
        org.junit.Assert.assertEquals("The chart must not jump when hours arrive", before, after)
        org.junit.Assert.assertEquals("The first hour must keep its position", firstHourBefore, visibleFirstHourBounds())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_loading_resolved.png")
    }

    @Config(qualifiers = "w411dp-h1100dp-night-xxhdpi")
    @Test fun failedDayRequestKeepsItsPartialHoursAndOffersRetry() {
        val base = sampleState()
        val place = base.places.first()
        val snapshot = place.snapshot!!
        val time = snapshot.daily[3].date.atStartOfDay(snapshot.timeZone).toInstant()
        val state = base.copy(places = listOf(place.copy(snapshot = snapshot.copy(hourly = listOf(snapshot.hourly.first().copy(time = time))))),
            hoursError = dev.antomanc.ameki.core.data.sync.SyncError.NETWORK)
        compose.setContent {
            AmekiTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                DayPage(state.places.first(), state, DayTarget(3), {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("day-hour-${time.epochSecond}").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    @Test fun dayPageLoading() {
        val state = sampleState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AmekiTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    DayPage(state.places.first(), state, DayTarget(5), {}, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertEquals(1, compose.onAllNodesWithText("Sunrise", substring = true)
            .fetchSemanticsNodes().count { it.boundsInRoot.overlaps(compose.onRoot().fetchSemanticsNode().boundsInRoot) })
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_page_loading_dark.png")
    }
}
