package dev.antomanc.ameki.feature.home

import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.ui.AppRequest
import java.time.Instant
import java.time.LocalDate

/** Which day page is open, and optionally the hour to scroll to. */
data class DayTarget(val date: LocalDate, val hour: Instant? = null)

/** Where a request to show a place stands. */
internal sealed interface ShowStep {
    /** The place's forecast is being fetched: show the place, decide about the day later. */
    data class Wait(val placeId: Long) : ShowStep

    /** The place has no forecast yet and a day was asked for: fetch it, then decide again. */
    data class Fetch(val placeId: Long) : ShowStep

    /** Final. Show [placeId] (null: stay where you are), opening [day] when not null. */
    data class Show(val placeId: Long?, val day: DayTarget?) : ShowStep
}

/**
 * The next step for [request], given the places as loaded now. [fetchedFrom] is null until this
 * request started a fetch; then it holds the place's last attempt time seen at that moment, so a
 * fetch only counts as finished once its own attempt reached the data.
 */
internal fun nextStep(
    request: AppRequest.Show,
    places: List<PlaceWeather>,
    refreshing: Set<Long>,
    fetchedFrom: Attempt?,
): ShowStep {
    val pw = places.firstOrNull { it.place.id == request.placeId } ?: return ShowStep.Show(null, null)
    val id = pw.place.id
    val day = request.day ?: return ShowStep.Show(id, null)
    val days = pw.snapshot?.daily.orEmpty()
    return when {
        days.any { it.date == day } -> ShowStep.Show(id, DayTarget(day, request.hour))
        // The day has passed or lies beyond the forecast: the place is all we can show.
        days.isNotEmpty() -> ShowStep.Show(id, null)
        fetchedFrom == null -> ShowStep.Fetch(id)
        id !in refreshing && pw.lastAttemptAt != fetchedFrom.lastAttemptAt -> ShowStep.Show(id, null)
        else -> ShowStep.Wait(id)
    }
}

/** The last attempt time seen when a fetch started; null when the place was never fetched. */
internal data class Attempt(val lastAttemptAt: Instant?)
