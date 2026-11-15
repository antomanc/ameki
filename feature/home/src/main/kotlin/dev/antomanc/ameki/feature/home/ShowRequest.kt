package dev.antomanc.ameki.feature.home

import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.ui.AppRequest
import java.time.Instant
import java.time.LocalDate

/** Which day page is open, and optionally the hour to scroll to. */
data class DayTarget(val date: LocalDate, val hour: Instant? = null)

/** Where a request to show a place stands. */
internal sealed interface ShowStep {
    /** The place has no forecast yet and a day was asked for: fetch it, then decide for good. */
    data class Fetch(val placeId: Long) : ShowStep

    /** Final. Show [placeId] (null: stay where you are), opening [day] when not null. */
    data class Show(val placeId: Long?, val day: DayTarget?) : ShowStep
}

/**
 * The next step for [request], given the places as loaded now. After one fetch ([fetched]) the
 * answer is always final: a day still missing then is not coming, and the request must not wait.
 */
internal fun nextStep(request: AppRequest.Show, places: List<PlaceWeather>, fetched: Boolean): ShowStep {
    val pw = places.firstOrNull { it.place.id == request.placeId } ?: return ShowStep.Show(null, null)
    val id = pw.place.id
    val day = request.day ?: return ShowStep.Show(id, null)
    val days = pw.snapshot?.daily.orEmpty()
    return when {
        days.any { it.date == day } -> ShowStep.Show(id, DayTarget(day, request.hour))
        // The day has passed, lies beyond the forecast, or a fetch did not bring it.
        days.isNotEmpty() || fetched -> ShowStep.Show(id, null)
        else -> ShowStep.Fetch(id)
    }
}
