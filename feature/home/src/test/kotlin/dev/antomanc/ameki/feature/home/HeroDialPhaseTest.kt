package dev.antomanc.ameki.feature.home

import dev.antomanc.ameki.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroDialPhaseTest {
    @Test
    fun currentReadyWinsOverBriefWait() {
        val phase = heroDialPhase(
            temperature = 15.0,
            todayMin = 10.0,
            todayMax = 18.0,
            condition = WeatherCondition.CLEAR,
            isDaytime = true,
            feelsLike = 14.0,
            conditionText = null,
            briefWait = true,
        )
        assertTrue(phase is HeroDialPhase.Ready)
        phase as HeroDialPhase.Ready
        assertEquals(15.0, phase.temperature, 0.0)
        assertEquals(10.0, phase.low, 0.0)
        assertEquals(18.0, phase.high, 0.0)
    }

    @Test
    fun briefWaitWithoutCurrentIsMute() {
        assertEquals(
            HeroDialPhase.Mute,
            heroDialPhase(
                temperature = null,
                todayMin = 10.0,
                todayMax = 18.0,
                condition = null,
                isDaytime = true,
                feelsLike = null,
                conditionText = null,
                briefWait = true,
            ),
        )
    }

    @Test
    fun settledWithoutCurrentIsUnavailable() {
        assertEquals(
            HeroDialPhase.Unavailable,
            heroDialPhase(
                temperature = null,
                todayMin = null,
                todayMax = null,
                condition = null,
                isDaytime = false,
                feelsLike = null,
                conditionText = null,
                briefWait = false,
            ),
        )
    }

    @Test
    fun rangePadsWithCurrentWhenTodayMissing() {
        val phase = heroDialPhase(
            temperature = 12.0,
            todayMin = null,
            todayMax = null,
            condition = WeatherCondition.FOG,
            isDaytime = false,
            feelsLike = null,
            conditionText = "Fog",
            briefWait = false,
        ) as HeroDialPhase.Ready
        assertEquals(12.0, phase.low, 0.0)
        assertEquals(12.0, phase.high, 0.0)
    }
}
