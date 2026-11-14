package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.data.db.PlaceDao
import dev.antomanc.ameki.core.data.db.SnapshotDao
import dev.antomanc.ameki.core.data.db.SnapshotEntity
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The only way weather enters the app. Widgets and screens read the cache; network calls happen
 * only in [refresh], called by the worker, pull-to-refresh, or when a place is first opened.
 */
class WeatherRepository(
    private val placeDao: PlaceDao,
    private val snapshotDao: SnapshotDao,
    private val settings: SettingsRepository,
    private val sources: SourceRegistry,
    private val places: PlaceRepository,
    private val listeners: () -> List<WeatherUpdateListener> = { emptyList() },
    private val clock: () -> Instant = Instant::now,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** Weather for every saved place, in display order. */
    val all: Flow<List<PlaceWeather>> = combine(
        places.places, snapshotDao.observeAll(), settings.settings,
    ) { placeList, snapshots, s ->
        val byPlace = snapshots.associateBy { it.placeId }
        placeList.map { place -> toPlaceWeather(place, byPlace[place.id], s.source) }
    }

    suspend fun get(placeId: Long): PlaceWeather? {
        val place = placeDao.byId(placeId)?.toModel() ?: return null
        return toPlaceWeather(place, snapshotDao.get(placeId), settings.current().source)
    }

    private fun toPlaceWeather(place: dev.antomanc.ameki.core.model.Place, e: SnapshotEntity?, source: SourceId): PlaceWeather {
        val snapshot = e?.let { decode(it) }?.let { valid(it, source) }
        return PlaceWeather(
            place = place,
            snapshot = snapshot,
            lastAttemptAt = e?.lastAttemptAt?.let(Instant::ofEpochSecond),
            error = e?.lastError?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
        )
    }

    private fun decode(e: SnapshotEntity): WeatherSnapshot? =
        runCatching { json.decodeFromString(WeatherSnapshot.serializer(), e.json) }.getOrNull()

    /** Drop data from another source and parts past their retention limit. */
    private fun valid(s: WeatherSnapshot, source: SourceId): WeatherSnapshot? {
        if (s.source != source) return null
        val now = clock()
        val keep = s.fetchedAt.filter { (part, at) ->
            Duration.between(at, now) < RefreshPolicy.maxAge(s.source, part)
        }.keys
        return s.retain(keep).takeIf { keep.isNotEmpty() }
    }

    /**
     * Refresh one place. Without [force], only parts that are due are fetched (that is what
     * keeps Google usage low). Returns the error, or null on success.
     */
    suspend fun refresh(placeId: Long, force: Boolean = false, notify: Boolean = true): SyncError? {
        val result = mutex.withLock { refreshLocked(placeId, force) }
        if (notify) notifyListeners()
        return result
    }

    /** Refresh every place worth refreshing; used by the background worker. */
    suspend fun refreshActive(widgetPlaceIds: Set<Long?>): Map<Long, SyncError?> {
        val all = placeDao.all()
        val s = settings.current()
        val wanted = buildSet {
            addAll(places.recentlyOpened())
            s.selectedPlaceId?.let(::add)
            widgetPlaceIds.forEach { id -> if (id != null) add(id) else all.firstOrNull()?.let { add(it.id) } }
            if (isEmpty()) all.firstOrNull()?.let { add(it.id) }
        }
        val results = mutex.withLock { wanted.associateWith { refreshLocked(it, force = false) } }
        purgeExpired()
        notifyListeners()
        return results
    }

    private suspend fun refreshLocked(placeId: Long, force: Boolean): SyncError? {
        val s = settings.current()
        var placeEntity = placeDao.byId(placeId) ?: return null
        if (placeEntity.isCurrentLocation) {
            if (!places.updateCurrentLocation()) return record(placeId, s, null, SyncError.NO_LOCATION)
            placeEntity = placeDao.byId(placeId) ?: return null
        }
        val source = sources[s.source]
        val existing = snapshotDao.get(placeId)?.let(::decode)?.let { valid(it, s.source) }
        val parts = if (force) {
            DataPart.entries.filterTo(mutableSetOf()) { it.capability in source.capabilities }
        } else {
            RefreshPolicy.dueParts(s.source, existing, s.refreshIntervalMinutes, clock(), source.capabilities)
        }
        if (parts.isEmpty()) return null

        return try {
            val fresh = source.fetch(placeEntity.toModel().point.coarse(), language(s), parts)
            val merged = existing?.mergedWith(fresh) ?: fresh
            record(placeId, s, merged, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            record(placeId, s, existing, SyncError.from(e))
        }
    }

    private suspend fun record(placeId: Long, s: AppSettings, snapshot: WeatherSnapshot?, error: SyncError?): SyncError? {
        snapshotDao.upsert(
            SnapshotEntity(
                placeId = placeId,
                sourceId = s.source.name,
                json = snapshot?.let { json.encodeToString(WeatherSnapshot.serializer(), it) }
                    ?: snapshotDao.get(placeId)?.json ?: EMPTY,
                lastAttemptAt = clock().epochSecond,
                lastError = error?.name,
            ),
        )
        return error
    }

    /**
     * Make sure hourly data reaches [until] (the end of a day the user opened). Free for
     * sources that already return every hour; for Google it fetches only the extra pages.
     * Returns the error, or null when the hours are there.
     */
    suspend fun ensureHourlyUntil(placeId: Long, until: Instant): SyncError? = mutex.withLock {
        val s = settings.current()
        val source = sources[s.source]
        val existing = snapshotDao.get(placeId)?.let(::decode)?.let { valid(it, s.source) }
        val lastHour = existing?.hourly?.lastOrNull()?.time
        if (lastHour != null && !lastHour.isBefore(until.minusSeconds(3600))) return@withLock null
        val place = placeDao.byId(placeId) ?: return@withLock null
        val hours = (Duration.between(clock(), until).toHours() + 2).toInt()
        try {
            val extra = source.fetchHours(place.toModel().point.coarse(), language(s), hours) ?: return@withLock null
            val merged = existing?.mergedWith(extra) ?: extra
            record(placeId, s, merged, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncError.from(e)
        }
    }

    /** Physically delete expired data (required by Google's caching terms, tidy for the rest). */
    suspend fun purgeExpired() {
        val source = settings.current().source
        snapshotDao.all().forEach { e ->
            val s = decode(e) ?: return@forEach
            val kept = valid(s, source)
            if (kept != s) {
                snapshotDao.upsert(e.copy(json = kept?.let { json.encodeToString(WeatherSnapshot.serializer(), it) } ?: EMPTY))
            }
        }
    }

    /** Switching source: old data is meaningless for the new one (and Google's must go). */
    suspend fun clearAll() {
        snapshotDao.clear()
        notifyListeners()
    }

    private suspend fun notifyListeners() = listeners().forEach { runCatching { it.onWeatherUpdated() } }

    private fun language(s: AppSettings): String = s.dataLanguage ?: Locale.getDefault().toLanguageTag()

    private companion object {
        const val EMPTY = ""
    }
}
