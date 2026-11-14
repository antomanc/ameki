package dev.antomanc.ameki.provider.api

import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.ProviderId
import dev.antomanc.ameki.core.model.WeatherSnapshot

/**
 * A weather data source. Implementations map their own responses onto the domain model and
 * declare what they support; the UI hides whatever is missing. Adding a provider means adding
 * a module that implements this and registering it in :core:data.
 */
interface WeatherProvider {
    val id: ProviderId
    val capabilities: Set<Capability>

    /**
     * Fetch [parts] for [location]. Only the requested parts are filled, and
     * [WeatherSnapshot.fetchedAt] lists exactly the parts returned. Units are always canonical
     * (see the model). [language] is a BCP-47 tag for provider texts.
     *
     * @throws ProviderException on any failure.
     */
    suspend fun fetch(
        location: GeoPoint,
        language: String,
        parts: Set<DataPart> = DataPart.entries.toSet(),
    ): WeatherSnapshot
}

/** Why a fetch failed, in terms the UI can act on. */
sealed class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connectivity, timeout, DNS: try again later. */
    class Network(cause: Throwable) : ProviderException("Network error: ${cause.message}", cause)

    /** Key missing or rejected. The user has to fix it in settings. */
    class InvalidApiKey(message: String) : ProviderException(message)

    /** API not enabled, billing off, key restricted to other APIs or apps. */
    class AccessDenied(message: String) : ProviderException(message)

    /** Quota or rate limit hit. */
    class QuotaExceeded(message: String) : ProviderException(message)

    /** Provider has no data for this place (e.g. outside coverage). */
    class Unsupported(message: String) : ProviderException(message)

    /** Anything else: 5xx, malformed response. */
    class Unknown(message: String, cause: Throwable? = null) : ProviderException(message, cause)
}
