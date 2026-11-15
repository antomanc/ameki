package dev.antomanc.ameki.core.ui

import android.content.Context
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.Nowcast
import dev.antomanc.ameki.core.model.NowcastSlot
import dev.antomanc.ameki.core.model.RainOutlook
import dev.antomanc.ameki.core.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One upcoming hour as shown: the "now" hour shows the current reading's temperature. */
data class HourView(val forecast: HourlyForecast, val isNow: Boolean, val temperature: Double)

/**
 * What the app and the widgets show from one snapshot at [now]. Both render this, so "now",
 * "stale", which days and hours are listed, and what is said about rain agree everywhere.
 */
class ForecastView(val snapshot: WeatherSnapshot, val now: Instant, refreshIntervalMinutes: Int) {
    val todayDate: LocalDate = now.atZone(snapshot.timeZone).toLocalDate()

    val currentIsFresh: Boolean = snapshot.currentIsFresh(now)

    val updatedAt: Instant? = snapshot.updatedAt

    /**
     * The update stamp must warn: the current reading no longer describes now, or nothing has
     * refreshed for three intervals (two hours at least).
     */
    val stale: Boolean = updatedAt != null && (
        (snapshot.current != null && !currentIsFresh) ||
            Duration.between(updatedAt, now) > Duration.ofMinutes(maxOf(120L, refreshIntervalMinutes * 3L))
        )

    val today: DailyForecast? = snapshot.today(now)

    /** From today on: a day that has ended is not a forecast. */
    val days: List<DailyForecast> = snapshot.daily.filter { !it.date.isBefore(todayDate) }

    /** Upcoming hours. The first is "now" only when it has started and the current reading is fresh. */
    val hours: List<HourView> = snapshot.hoursFrom(now).mapIndexed { i, h ->
        val isNow = i == 0 && !h.time.isAfter(now) && currentIsFresh
        HourView(h, isNow, if (isNow) snapshot.current!!.temperature else h.temperature)
    }

    val rain: RainOutlook = Nowcast.outlook(snapshot, now)

    /** 15-minute slots from now on, only while the nowcast is fresh enough to back [rain]. */
    val rainSlots: List<NowcastSlot> =
        if (snapshot.partIsFresh(DataPart.NOWCAST, now)) snapshot.nowcast.filter { it.time.plusSeconds(15 * 60) > now } else emptyList()

    fun isToday(date: LocalDate): Boolean = date == todayDate

    /** [rain] in words: [short] for widgets, the full sentence for the app. */
    fun rainText(context: Context, fmt: TimeFormatter, short: Boolean): String = when (val r = rain) {
        RainOutlook.Unavailable -> context.getString(if (short) R.string.rain_unavailable_short else R.string.rain_unavailable)
        RainOutlook.NoneSoon -> context.getString(if (short) R.string.rain_none_short else R.string.rain_none)
        RainOutlook.RainingNow -> context.getString(if (short) R.string.rain_now_short else R.string.rain_now)
        is RainOutlook.StopsIn -> context.getString(if (short) R.string.rain_stops_short else R.string.rain_stops, r.minutes)
        is RainOutlook.StartsIn -> context.getString(if (short) R.string.rain_starts_short else R.string.rain_starts, r.minutes)
        is RainOutlook.LikelyAround -> context.getString(if (short) R.string.rain_likely_short else R.string.rain_likely, fmt.time(r.at, snapshot.timeZone))
    }
}
