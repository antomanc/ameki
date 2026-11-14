package dev.antomanc.ameki.core.data.sync

import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.source.api.SourceException
import java.time.Instant

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
            is SourceException.InvalidApiKey -> if (e.message == "No API key") MISSING_KEY else INVALID_KEY
            is SourceException.AccessDenied -> ACCESS_DENIED
            is SourceException.QuotaExceeded -> QUOTA
            is SourceException.Unsupported -> UNSUPPORTED
            else -> UNKNOWN
        }
    }
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
) {
    val hasData: Boolean get() = snapshot?.current != null || snapshot?.daily?.isNotEmpty() == true
}

/** Tells the widget layer that cached data changed. Implemented in :widget. */
fun interface WeatherUpdateListener {
    suspend fun onWeatherUpdated()
}
