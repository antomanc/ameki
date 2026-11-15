package dev.antomanc.ameki.core.model

import java.time.Duration
import java.time.Instant

/** What the Rain widget says about the next two hours. */
sealed interface RainOutlook {
    /** No nowcast and no hourly forecast: we cannot say, and must not claim "no rain". */
    data object Unavailable : RainOutlook
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
    private const val SLOT_SECONDS = 15 * 60L

    fun outlook(snapshot: WeatherSnapshot, now: Instant): RainOutlook {
        val slots = if (snapshot.partIsFresh(DataPart.NOWCAST, now)) {
            snapshot.nowcast.filter { it.time.plusSeconds(SLOT_SECONDS) > now && it.time < now + HORIZON }
        } else {
            emptyList()
        }
        if (slots.isNotEmpty() && !slots.first().time.isAfter(now) &&
            slots.zipWithNext().all { (a, b) -> Duration.between(a.time, b.time).seconds <= SLOT_SECONDS + 60 }) {
            val wetNow = slots.first().precipitation >= WET_MM
            if (wetNow) {
                val dry = slots.firstOrNull { it.precipitation < WET_MM }
                return if (dry == null) RainOutlook.RainingNow else RainOutlook.StopsIn(minutesUntil(dry.time, now))
            }
            val wet = slots.firstOrNull { it.precipitation >= WET_MM }
            if (wet != null) return RainOutlook.StartsIn(minutesUntil(wet.time, now))
            // A dry nowcast only answers the question when it covers the whole horizon.
            if (coversHorizon(slots, now)) return RainOutlook.NoneSoon
        }
        return hourlyOutlook(snapshot, now)
    }

    /** Intensity 0..1 for drawing: 2 mm in 15 minutes (8 mm/h, heavy rain) fills the dot. */
    fun intensity(mm: Double): Float = (mm / 2.0).coerceIn(0.0, 1.0).toFloat()

    private fun hourlyOutlook(snapshot: WeatherSnapshot, now: Instant): RainOutlook {
        if (!snapshot.partIsFresh(DataPart.HOURLY, now)) return RainOutlook.Unavailable
        val hours = snapshot.hoursFrom(now)
        if (hours.isEmpty()) return RainOutlook.Unavailable
        val likely = hours.take(3).firstOrNull { (it.precipitationProbability ?: 0) >= 50 }
        if (likely != null) return RainOutlook.LikelyAround(likely.time)
        val horizon = hours.takeWhile { it.time < now + HORIZON }
        if (horizon.isEmpty() || horizon.first().time > now ||
            horizon.last().time.plusSeconds(3600) < now + HORIZON ||
            horizon.any { it.precipitationProbability == null } ||
            horizon.zipWithNext().any { (a, b) -> Duration.between(a.time, b.time).seconds > 3600 }) return RainOutlook.Unavailable
        return RainOutlook.NoneSoon
    }

    /** A dry nowcast only answers the whole two hours when it is complete and contiguous. */
    private fun coversHorizon(slots: List<NowcastSlot>, now: Instant): Boolean {
        val first = slots.first()
        val last = slots.last()
        if (first.time.isAfter(now)) return false
        if (last.time.plusSeconds(SLOT_SECONDS) < now.plus(HORIZON).minusSeconds(SLOT_SECONDS)) return false
        return slots.zipWithNext().all { (a, b) -> Duration.between(a.time, b.time).seconds <= SLOT_SECONDS + 60 }
    }

    private fun minutesUntil(t: Instant, now: Instant) =
        (Duration.between(now, t).toMinutes().toInt()).coerceAtLeast(0).let { ((it + 4) / 5) * 5 }
}
