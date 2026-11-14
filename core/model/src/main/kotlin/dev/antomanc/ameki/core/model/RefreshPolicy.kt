package dev.antomanc.ameki.core.model

import java.time.Duration

/**
 * How often each part of the forecast is refreshed and how long it may be kept, per provider.
 * The worker, the cache and the call estimate in settings all read from here, so they can't
 * disagree.
 */
object RefreshPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 30

    /** WorkManager won't run periodic work more often than every 15 minutes. */
    fun intervalOptions(provider: ProviderId): List<Int> = when (provider) {
        // Google's terms allow caching current and hourly data for one hour at most, so a
        // longer interval would leave widgets with nothing to show between refreshes.
        ProviderId.GOOGLE -> listOf(15, 30, 60)
        ProviderId.OPEN_METEO -> listOf(15, 30, 60, 120, 180)
    }

    fun clampInterval(provider: ProviderId, minutes: Int): Int {
        val options = intervalOptions(provider)
        return options.lastOrNull { it <= minutes } ?: options.first()
    }

    /** How often a part needs fetching, given the user's interval. */
    fun partInterval(provider: ProviderId, part: DataPart, baseMinutes: Int): Duration = when (provider) {
        ProviderId.OPEN_METEO -> Duration.ofMinutes(baseMinutes.toLong()) // one call returns everything
        ProviderId.GOOGLE -> when (part) {
            DataPart.CURRENT, DataPart.HOURLY -> Duration.ofMinutes(baseMinutes.toLong())
            DataPart.ALERTS -> Duration.ofMinutes(maxOf(baseMinutes, 60).toLong())
            DataPart.DAILY -> Duration.ofMinutes(maxOf(baseMinutes, 180).toLong())
        }
    }

    /**
     * Maximum age before a cached part must be deleted. For Google these come from the Google
     * Maps Platform Service Specific Terms (Weather API caching table).
     */
    fun maxAge(provider: ProviderId, part: DataPart): Duration = when (provider) {
        ProviderId.GOOGLE -> when (part) {
            DataPart.CURRENT, DataPart.HOURLY, DataPart.ALERTS -> Duration.ofHours(1)
            DataPart.DAILY -> Duration.ofHours(24)
        }
        ProviderId.OPEN_METEO -> Duration.ofDays(7)
    }

    /** Small slack so a refresh scheduled "every 30 min" isn't skipped for being 29 min old. */
    val tolerance: Duration = Duration.ofMinutes(5)

    /** Parts of [snapshot] that are due for a refresh at [now]. */
    fun dueParts(
        provider: ProviderId,
        snapshot: WeatherSnapshot?,
        baseMinutes: Int,
        now: java.time.Instant,
    ): Set<DataPart> = DataPart.entries.filterTo(mutableSetOf()) { part ->
        val fetched = snapshot?.takeIf { it.provider == provider }?.fetchedAt?.get(part)
            ?: return@filterTo true
        Duration.between(fetched, now) >= partInterval(provider, part, baseMinutes).minus(tolerance)
    }

    /** Google calls made for one refresh of these parts. Hourly is one 24-hour page. */
    fun googleCalls(parts: Set<DataPart>): Int = parts.size

    /**
     * Estimated Google Weather API calls in a 30-day month for [places] places refreshed every
     * [baseMinutes]. Free tier is [GOOGLE_FREE_CALLS_PER_MONTH].
     */
    fun estimateGoogleCallsPerMonth(places: Int, baseMinutes: Int): Int {
        val minutesPerMonth = 30 * 24 * 60
        val perPlace = DataPart.entries.sumOf { part ->
            minutesPerMonth / partInterval(ProviderId.GOOGLE, part, baseMinutes).toMinutes().toInt()
        }
        return perPlace * places
    }

    const val GOOGLE_FREE_CALLS_PER_MONTH = 10_000
}
