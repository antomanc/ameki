package dev.antomanc.ameki.source.api

import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherSnapshot

/**
 * A weather data source. Implementations map their own responses onto the domain model and
 * declare what they support; the UI hides whatever is missing. Adding a source means adding
 * a module that implements this and registering it in :core:data.
 */
interface WeatherSource {
    val id: SourceId
    val capabilities: Set<Capability>

    /**
     * Fetch [parts] for [location]. Only the requested parts are filled, and
     * [WeatherSnapshot.fetchedAt] lists exactly the parts returned. Units are always canonical
     * (see the model). [language] is a BCP-47 tag for source texts.
     *
     * @throws SourceException on any failure.
     */
    suspend fun fetch(
        location: GeoPoint,
        language: String,
        parts: Set<DataPart> = DataPart.entries.toSet(),
    ): WeatherSnapshot

    /**
     * Hourly forecast for the next [hours] hours, for sources whose regular refresh returns
     * fewer (Google pages by 24 and each page is a billed call). Null when the source has
     * nothing more than [fetch] already gives.
     */
    suspend fun fetchHours(location: GeoPoint, language: String, hours: Int): WeatherSnapshot? = null
}

/** Why a fetch failed, in terms the UI can act on. */
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connectivity, timeout, DNS: try again later. */
    class Network(cause: Throwable) : SourceException("Network error: ${cause.message}", cause)

    /** Key missing or rejected. The user has to fix it in settings. */
    class InvalidApiKey(message: String) : SourceException(message)

    /** API not enabled, billing off, key restricted to other APIs or apps. */
    class AccessDenied(message: String) : SourceException(message)

    /** Quota or rate limit hit. */
    class QuotaExceeded(message: String) : SourceException(message)

    /** Source has no data for this place (e.g. outside coverage). */
    class Unsupported(message: String) : SourceException(message)

    /** Anything else: 5xx, malformed response. */
    class Unknown(message: String, cause: Throwable? = null) : SourceException(message, cause)
}
