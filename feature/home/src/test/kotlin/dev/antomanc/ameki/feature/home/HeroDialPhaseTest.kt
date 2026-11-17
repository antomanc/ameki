package dev.antomanc.ameki.feature.home

import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HeroDialPhaseTest {
    private fun current(
        temperature: Double = 15.0,
        condition: WeatherCondition = WeatherCondition.CLEAR,
        isDaytime: Boolean = true,
        feelsLike: Double? = 14.0,
    ) = CurrentWeather(
        time = Instant.parse("2026-11-17T12:00:00Z"),
        temperature = temperature,
        feelsLike = feelsLike,
        condition = condition,
        isDaytime = isDaytime,
        humidity = null,
        dewPoint = null,
        uvIndex = null,
        pressure = null,
        windSpeed = null,
        windGust = null,
        windDirection = null,
        visibility = null,
        cloudCover = null,
    )

    private fun today(min: Double, max: Double) = DailyForecast(
        date = LocalDate.of(2026, 11, 17),
        temperatureMax = max,
        temperatureMin = min,
        condition = WeatherCondition.CLEAR,
        precipitationProbability = null,
        precipitation = null,
        windSpeedMax = null,
        uvIndexMax = null,
        sunrise = null,
        sunset = null,
        moonPhase = null,
    )

    @Test
    fun currentReadyWinsOverBriefWait() {
        val phase = heroDialPhase(current = current(15.0), today = today(10.0, 18.0), briefWait = true)
        assertTrue(phase is HeroDialPhase.Ready)
        phase as HeroDialPhase.Ready
        assertEquals(15.0, phase.temperature, 0.0)
        assertEquals(10.0, phase.low, 0.0)
        assertEquals(18.0, phase.high, 0.0)
        assertEquals(HeroDialKind.Ready, phase.kind)
    }

    @Test
    fun briefWaitWithoutCurrentIsMute() {
        assertEquals(HeroDialPhase.Mute, heroDialPhase(current = null, today = today(10.0, 18.0), briefWait = true))
        assertEquals(HeroDialKind.Mute, HeroDialPhase.Mute.kind)
    }

    @Test
    fun settledWithoutCurrentIsUnavailable() {
        assertEquals(HeroDialPhase.Unavailable, heroDialPhase(current = null, today = null, briefWait = false))
        assertEquals(HeroDialKind.Unavailable, HeroDialPhase.Unavailable.kind)
    }

    @Test
    fun rangePadsWithCurrentWhenTodayMissing() {
        val phase = heroDialPhase(
            current = current(12.0, WeatherCondition.FOG, isDaytime = false, feelsLike = null),
            today = null,
            briefWait = false,
        ) as HeroDialPhase.Ready
        assertEquals(12.0, phase.low, 0.0)
        assertEquals(12.0, phase.high, 0.0)
    }
}
