package dev.antomanc.nembo.core.data.sync

import dev.antomanc.nembo.core.model.Place
import dev.antomanc.nembo.core.model.WeatherSnapshot
import dev.antomanc.nembo.provider.api.ProviderException
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
            is ProviderException.Network -> NETWORK
            is ProviderException.InvalidApiKey -> if (e.message == "No API key") MISSING_KEY else INVALID_KEY
            is ProviderException.AccessDenied -> ACCESS_DENIED
            is ProviderException.QuotaExceeded -> QUOTA
            is ProviderException.Unsupported -> UNSUPPORTED
            else -> UNKNOWN
        }
    }
}

/** What the UI and widgets render for one place. */
data class PlaceWeather(
    val place: Place,
    /** Already filtered: expired parts and data from another provider are gone. */
    val snapshot: WeatherSnapshot?,
    val lastAttemptAt: Instant?,
    val error: SyncError?,
) {
    val hasData: Boolean get() = snapshot?.current != null || snapshot?.daily?.isNotEmpty() == true
}

/** Tells the widget layer that cached data changed. Implemented in :widget. */
fun interface WeatherUpdateListener {
    suspend fun onWeatherUpdated()
}
