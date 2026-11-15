package dev.antomanc.ameki.core.model

import java.time.Duration

/**
 * How often each part of the forecast is refreshed and how long it may be kept, per source.
 * The worker, the cache and the call estimate in settings all read from here, so they can't
 * disagree.
 */
object RefreshPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 30

    /** WorkManager won't run periodic work more often than every 15 minutes. */
    fun intervalOptions(source: SourceId): List<Int> = when (source) {
        // Google's terms allow caching current and hourly data for one hour at most, so a
        // longer interval would leave widgets with nothing to show between refreshes.
        SourceId.GOOGLE -> listOf(15, 30, 60)
        SourceId.OPEN_METEO -> listOf(15, 30, 60, 120, 180)
    }

    fun clampInterval(source: SourceId, minutes: Int): Int {
        val options = intervalOptions(source)
        return options.lastOrNull { it <= minutes } ?: options.first()
    }

    /** How often a part needs fetching, given the user's interval. */
    fun partInterval(source: SourceId, part: DataPart, baseMinutes: Int): Duration = when (source) {
        SourceId.OPEN_METEO -> Duration.ofMinutes(baseMinutes.toLong()) // one call returns everything
        SourceId.GOOGLE -> when (part) {
            DataPart.CURRENT, DataPart.HOURLY -> Duration.ofMinutes(baseMinutes.toLong())
            DataPart.ALERTS -> Duration.ofMinutes(maxOf(baseMinutes, 60).toLong())
            DataPart.DAILY -> Duration.ofMinutes(maxOf(baseMinutes, 180).toLong())
            DataPart.NOWCAST -> Duration.ofMinutes(baseMinutes.toLong())
        }
    }

    /**
     * Maximum age before a cached part must be deleted. For Google these come from the Google
     * Maps Platform Service Specific Terms (Weather API caching table).
     */
    fun maxAge(source: SourceId, part: DataPart): Duration = when (source) {
        SourceId.GOOGLE -> when (part) {
            DataPart.CURRENT, DataPart.HOURLY, DataPart.ALERTS, DataPart.NOWCAST -> Duration.ofHours(1)
            DataPart.DAILY -> Duration.ofHours(24)
        }
        SourceId.OPEN_METEO -> Duration.ofDays(7)
    }

    /**
     * How long a part still describes the weather it claims to. [maxAge] is retention: offline
     * we may keep a current reading much longer, but after this it must not look like "now".
     */
    fun weatherFreshness(source: SourceId, part: DataPart): Duration {
        val weather = when (part) {
            DataPart.CURRENT, DataPart.NOWCAST -> Duration.ofHours(2)
            DataPart.HOURLY -> Duration.ofHours(6)
            DataPart.DAILY -> Duration.ofHours(24)
            DataPart.ALERTS -> Duration.ofHours(1)
        }
        return minOf(weather, maxAge(source, part))
    }

    /** Small slack so a refresh scheduled "every 30 min" isn't skipped for being 29 min old. */
    val tolerance: Duration = Duration.ofMinutes(5)

    /** Parts of [snapshot] that are due for a refresh at [now]. */
    fun dueParts(
        source: SourceId,
        snapshot: WeatherSnapshot?,
        baseMinutes: Int,
        now: java.time.Instant,
        capabilities: Set<Capability> = Capability.entries.toSet(),
    ): Set<DataPart> = DataPart.entries.filter { it.capability in capabilities }.filterTo(mutableSetOf()) { part ->
        val fetched = snapshot?.takeIf { it.source == source }?.fetchedAt?.get(part)
            ?: return@filterTo true
        Duration.between(fetched, now) >= partInterval(source, part, baseMinutes).minus(tolerance)
    }

    /**
     * Estimated Google Weather API calls in a 30-day month for [places] places refreshed every
     * [baseMinutes]: background refreshes only, one call per part (hourly is one 24-hour page).
     * Opening a later day adds its extra hour pages on top, which depends on the user.
     * Free tier is [GOOGLE_FREE_CALLS_PER_MONTH].
     */
    fun estimateGoogleCallsPerMonth(places: Int, baseMinutes: Int): Int {
        val minutesPerMonth = 30 * 24 * 60
        // Google has no nowcast, so it costs nothing.
        val perPlace = DataPart.entries.filter { it != DataPart.NOWCAST }.sumOf { part ->
            minutesPerMonth / partInterval(SourceId.GOOGLE, part, baseMinutes).toMinutes().toInt()
        }
        return perPlace * places
    }

    const val GOOGLE_FREE_CALLS_PER_MONTH = 10_000
}
