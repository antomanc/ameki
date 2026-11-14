package dev.antomanc.ameki.core.model

import java.time.Duration
import java.time.Instant

/** What the Rain widget says about the next two hours. */
sealed interface RainOutlook {
    data object NoneSoon : RainOutlook
    data object RainingNow : RainOutlook
    data class StopsIn(val minutes: Int) : RainOutlook
    data class StartsIn(val minutes: Int) : RainOutlook
    /** No 15-minute data, but the hourly forecast says rain is likely at [at]. */
    data class LikelyAround(val at: Instant) : RainOutlook
}

object Nowcast {
    /** 0.05 mm in 15 minutes ≈ 0.2 mm/h: the threshold where you'd notice drops. */
    const val WET_MM = 0.05
    val HORIZON: Duration = Duration.ofHours(2)

    fun outlook(snapshot: WeatherSnapshot, now: Instant): RainOutlook {
        val slots = snapshot.nowcast.filter { it.time.plusSeconds(15 * 60) > now && it.time < now + HORIZON }
        if (slots.isNotEmpty()) {
            val wetNow = slots.first().precipitation >= WET_MM
            return if (wetNow) {
                val dry = slots.firstOrNull { it.precipitation < WET_MM }
                if (dry == null) RainOutlook.RainingNow else RainOutlook.StopsIn(minutesUntil(dry.time, now))
            } else {
                val wet = slots.firstOrNull { it.precipitation >= WET_MM }
                if (wet == null) RainOutlook.NoneSoon else RainOutlook.StartsIn(minutesUntil(wet.time, now))
            }
        }
        val likely = snapshot.hoursFrom(now).take(3).firstOrNull { (it.precipitationProbability ?: 0) >= 50 }
        return likely?.let { RainOutlook.LikelyAround(it.time) } ?: RainOutlook.NoneSoon
    }

    /** Intensity 0..1 for drawing: 2 mm in 15 minutes (8 mm/h, heavy rain) fills the dot. */
    fun intensity(mm: Double): Float = (mm / 2.0).coerceIn(0.0, 1.0).toFloat()

    private fun minutesUntil(t: Instant, now: Instant) =
        (Duration.between(now, t).toMinutes().toInt()).coerceAtLeast(0).let { ((it + 4) / 5) * 5 }
}
