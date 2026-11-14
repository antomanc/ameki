package dev.antomanc.nembo.core.model

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.floor

/** Moon phase from the date alone, for providers that don't report it. Accurate to about a day. */
object Astronomy {
    private const val SYNODIC_MONTH = 29.530588853
    private const val KNOWN_NEW_MOON_EPOCH_DAY = 10_962.76 // 2000-01-06 18:14 UTC

    /** Age of the moon in days, 0 = new moon. */
    fun moonAge(date: LocalDate): Double {
        val days = date.atTime(12, 0).toEpochSecond(ZoneOffset.UTC) / 86_400.0 - KNOWN_NEW_MOON_EPOCH_DAY
        val cycles = days / SYNODIC_MONTH
        return (cycles - floor(cycles)) * SYNODIC_MONTH
    }

    fun moonPhase(date: LocalDate): MoonPhase {
        val age = moonAge(date)
        val step = SYNODIC_MONTH / 8
        val index = ((age + step / 2) / step).toInt() % 8
        return MoonPhase.entries[index]
    }

    /** 0.0 new → 1.0 full, for drawing. */
    fun illumination(date: LocalDate): Double {
        val phase = moonAge(date) / SYNODIC_MONTH
        return (1 - kotlin.math.cos(2 * Math.PI * phase)) / 2
    }
}
