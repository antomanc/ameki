package dev.antomanc.ameki.core.data.sync

import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.source.api.SourceException
import java.time.Instant
import java.time.LocalDate

enum class SyncError {
    NETWORK,
    MISSING_KEY,
    INVALID_KEY,
    ACCESS_DENIED,
    QUOTA,
    UNSUPPORTED,
    NO_LOCATION,
    UNKNOWN,
    ;

    /** Errors the user must fix in settings; retrying won't help. */
    val needsUserAction: Boolean get() = this in setOf(MISSING_KEY, INVALID_KEY, ACCESS_DENIED, NO_LOCATION)

    companion object {
        fun from(e: Throwable): SyncError = when (e) {
            is SourceException.Network -> NETWORK
            is SourceException.MissingApiKey -> MISSING_KEY
            is SourceException.InvalidApiKey -> INVALID_KEY
            is SourceException.AccessDenied -> ACCESS_DENIED
            is SourceException.QuotaExceeded -> QUOTA
            is SourceException.Unsupported -> UNSUPPORTED
            else -> UNKNOWN
        }
    }
}

/** Extra hours for a place's day pages that are being fetched, or failed, in this process. */
data class HourRequests(
    val loadingUntil: Instant? = null,
    val failedUntil: Instant? = null,
    val error: SyncError? = null,
)

/** Whether a day page can show all of a day's hours. */
enum class DayHours {
    READY,
    /** On their way. */
    LOADING,
    /** Missing and nobody asked yet: the page should ask, and show placeholders meanwhile. */
    NEEDED,
    /** The last request failed; whatever hours exist stay visible. */
    FAILED,
}

/** What the UI and widgets render for one place. */
data class PlaceWeather(
    val place: Place,
    /** Already filtered: expired parts and data from another source are gone. */
    val snapshot: WeatherSnapshot?,
    val lastAttemptAt: Instant?,
    val error: SyncError?,
    /** Makes time-based expiry observable even when the retained snapshot is unchanged. */
    val evaluatedAt: Instant = Instant.now(),
    /** The source fetches later hours only when a day page asks (Google). */
    val hoursOnDemand: Boolean = false,
    val hourRequests: HourRequests = HourRequests(),
) {
    val hasData: Boolean get() = snapshot?.current != null || snapshot?.daily?.isNotEmpty() == true

    fun dayHours(date: LocalDate): DayHours {
        val s = snapshot ?: return DayHours.READY
        if (!hoursOnDemand) return DayHours.READY
        val end = date.plusDays(1).atStartOfDay(s.timeZone).toInstant()
        return when {
            s.hoursCover(end) -> DayHours.READY
            hourRequests.loadingUntil?.let { !it.isBefore(end) } == true -> DayHours.LOADING
            hourRequests.failedUntil?.let { !it.isBefore(end) } == true -> DayHours.FAILED
            else -> DayHours.NEEDED
        }
    }
}

/** Tells the widget layer that cached data changed. Implemented in :widget. */
fun interface WeatherUpdateListener {
    suspend fun onWeatherUpdated()
}
