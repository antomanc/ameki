package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.data.db.PlaceDao
import dev.antomanc.ameki.core.data.db.SnapshotDao
import dev.antomanc.ameki.core.data.db.SnapshotEntity
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.HourRequests
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyExtension
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    private val onNextExpiry: (Instant?) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** Extra hours in flight or failed, per place. In memory: it describes this process's requests. */
    private val hourRequests = MutableStateFlow<Map<Long, HourRequests>>(emptyMap())

    /** Weather for every saved place, in display order. */
    val all: Flow<List<PlaceWeather>> = combine(
        places.places, snapshotDao.observeAll(), settings.settings, expiryTicks(), hourRequests,
    ) { placeList, snapshots, s, _, requests ->
        val byPlace = snapshots.associateBy { it.placeId }
        placeList.map { place -> toPlaceWeather(place, byPlace[place.id], s.source, requests[place.id]) }
    }

    suspend fun get(placeId: Long): PlaceWeather? {
        val place = placeDao.byId(placeId)?.toModel() ?: return null
        return toPlaceWeather(place, snapshotDao.get(placeId), settings.current().source, hourRequests.value[placeId])
    }

    private fun toPlaceWeather(
        place: dev.antomanc.ameki.core.model.Place,
        e: SnapshotEntity?,
        source: SourceId,
        requests: HourRequests?,
    ): PlaceWeather {
        val snapshot = e?.let { decode(it) }?.let { valid(it, source, place.point.coarse(), e.pointOrNull()) }
        return PlaceWeather(
            place = place,
            snapshot = snapshot,
            lastAttemptAt = e?.lastAttemptAt?.let(Instant::ofEpochSecond),
            error = e?.lastError?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
            hoursOnDemand = sources.ids.contains(source) && sources[source].fetchesHoursOnDemand,
            hourRequests = requests ?: HourRequests(),
        )
    }

    private fun decode(e: SnapshotEntity): WeatherSnapshot? =
        runCatching { json.decodeFromString(WeatherSnapshot.serializer(), e.json) }.getOrNull()

    /**
     * Drop data from another source, from another location, and parts past their retention limit.
     * Coordinates are compared only when both are known: old rows predate the stored point.
     */
    private fun valid(
        s: WeatherSnapshot,
        source: SourceId,
        placePoint: GeoPoint? = null,
        snapshotPoint: GeoPoint? = null,
    ): WeatherSnapshot? {
        if (s.source != source) return null
        if (placePoint != null && snapshotPoint != null && !placePoint.isNear(snapshotPoint)) return null
        return s.retainedAt(clock())
    }

    /** The stored snapshot for [placeId] that may still be used for [point]. */
    private suspend fun stored(placeId: Long, source: SourceId, point: GeoPoint): WeatherSnapshot? {
        val e = snapshotDao.get(placeId) ?: return null
        return decode(e)?.let { valid(it, source, point, e.pointOrNull()) }
    }

    /**
     * Re-emits exactly when the oldest cached part crosses its retention limit, so screens and
     * widgets stop showing expired data even when no refresh reaches the network.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun expiryTicks(): Flow<Unit> = snapshotDao.observeAll().flatMapLatest { entities ->
        flow {
            emit(Unit)
            while (true) {
                val now = clock()
                val next = entities.mapNotNull { e -> decode(e)?.nextChange(now) }.minOrNull()
                    ?: break
                delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000) + 50)
                emit(Unit)
            }
        }
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
    suspend fun refreshActive(widgetPlaceIds: Set<Long>): Map<Long, SyncError?> {
        val all = placeDao.all()
        val s = settings.current()
        val wanted = buildSet {
            addAll(places.recentlyOpened())
            s.selectedPlaceId?.let(::add)
            addAll(widgetPlaceIds)
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
            if (!places.updateCurrentLocation()) {
                val stillHere = placeEntity.toModel().point.coarse()
                return record(placeId, s, null, SyncError.NO_LOCATION, stillHere)
            }
            placeEntity = placeDao.byId(placeId) ?: return null
        }
        // The point this refresh is for: a move must never mix with the previous place's data.
        val point = placeEntity.toModel().point.coarse()
        val source = sources[s.source]
        val existing = stored(placeId, s.source, point)
        val parts = if (force) {
            DataPart.entries.filterTo(mutableSetOf()) { it.capability in source.capabilities }
        } else {
            RefreshPolicy.dueParts(s.source, existing, s.refreshIntervalMinutes, clock(), source.capabilities)
        }
        if (parts.isEmpty()) return null

        return try {
            val fresh = source.fetch(point, language(s), parts)
            val merged = existing?.mergedWith(fresh) ?: fresh
            record(placeId, s, merged, null, point)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            record(placeId, s, existing, SyncError.from(e), point)
        }
    }

    private suspend fun record(
        placeId: Long,
        s: AppSettings,
        snapshot: WeatherSnapshot?,
        error: SyncError?,
        point: GeoPoint? = null,
    ): SyncError? {
        val previous = snapshotDao.get(placeId)
        val previousPoint = previous?.pointOrNull()
        // On failure keep the old snapshot only if it belongs to the same place.
        val keepPrevious = snapshot == null &&
            (point == null || previousPoint == null || point.isNear(previousPoint))
        val encoded = when {
            snapshot != null -> json.encodeToString(WeatherSnapshot.serializer(), snapshot)
            keepPrevious -> previous?.json ?: EMPTY
            else -> EMPTY
        }
        snapshotDao.upsert(
            SnapshotEntity(
                placeId = placeId,
                sourceId = s.source.name,
                json = encoded,
                lastAttemptAt = clock().epochSecond,
                lastError = error?.name,
                latitude = point?.latitude ?: previous?.latitude?.takeIf { keepPrevious },
                longitude = point?.longitude ?: previous?.longitude?.takeIf { keepPrevious },
            ),
        )
        return error
    }

    /**
     * Make sure a day page has every hour of [date]. Free for sources that return every hour on
     * refresh; otherwise only the missing pages are fetched. Progress and failure reach the UI
     * through [PlaceWeather.dayHours], per place.
     */
    suspend fun loadDayHours(placeId: Long, date: LocalDate) {
        val s = settings.current()
        if (!sources[s.source].fetchesHoursOnDemand) return
        val zone = get(placeId)?.snapshot?.timeZone ?: return
        val until = date.plusDays(1).atStartOfDay(zone).toInstant()
        while (true) {
            val all = hourRequests.value
            val r = all[placeId] ?: HourRequests()
            // A request reaching at least this far is already running.
            if (r.loadingUntil != null && !r.loadingUntil.isBefore(until)) return
            if (hourRequests.compareAndSet(all, all + (placeId to r.copy(loadingUntil = until)))) break
        }
        var error: SyncError? = null
        var completed = false
        try {
            error = mutex.withLock { extendHoursLocked(placeId, until) }
            completed = true
        } finally {
            hourRequests.update { all ->
                val r = all[placeId] ?: HourRequests()
                all + (placeId to HourRequests(
                    // A longer request queued behind this one is still running.
                    loadingUntil = r.loadingUntil?.takeIf { it.isAfter(until) },
                    // Success covers every day up to this one; a failure marks only this day;
                    // a cancelled request proves nothing either way.
                    failed = when {
                        !completed -> r.failed
                        error != null -> r.failed + (date to error!!)
                        else -> r.failed.filterKeys { it.isAfter(date) }
                    },
                ))
            }
        }
    }

    private suspend fun extendHoursLocked(placeId: Long, until: Instant): SyncError? {
        val s = settings.current()
        val source = sources[s.source]
        val place = placeDao.byId(placeId) ?: return null
        val point = place.toModel().point.coarse()
        val existing = stored(placeId, s.source, point)
        if (existing?.hoursCover(until) == true) return null
        val hours = (Duration.between(clock(), until).toHours() + 2).toInt()
        return try {
            val fetched = source.fetchHours(point, language(s), hours)
            // An answer without hours must not replace the hours already stored.
            if (fetched.hourly.isEmpty()) return SyncError.UNKNOWN
            val extra = fetched.copy(
                hourlyExtension = HourlyExtension(fetchedAt = fetched.fetchedAt[DataPart.HOURLY] ?: clock(), requestedUntil = until),
            )
            record(placeId, s, existing?.mergedWith(extra) ?: extra, null, point)
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
            val placePoint = placeDao.byId(e.placeId)?.let { GeoPoint(it.latitude, it.longitude) }
            val kept = valid(s, source, placePoint, e.pointOrNull())
            if (kept != s) {
                snapshotDao.upsert(e.copy(json = kept?.let { json.encodeToString(WeatherSnapshot.serializer(), it) } ?: EMPTY))
            }
        }
    }

    /** Switching source: old data is meaningless for the new one (and Google's must go). */
    suspend fun clearAll() {
        snapshotDao.clear()
        hourRequests.value = emptyMap()
        notifyListeners()
    }

    /** Local-only expiry also runs when the network worker is waiting for connectivity. */
    suspend fun refreshCachedState() {
        purgeExpired()
        notifyListeners()
    }

    private suspend fun notifyListeners() {
        listeners().forEach { runCatching { it.onWeatherUpdated() } }
        val now = clock()
        onNextExpiry(snapshotDao.all().mapNotNull { decode(it)?.nextChange(now) }.minOrNull())
    }

    private fun language(s: AppSettings): String = s.dataLanguage ?: Locale.getDefault().toLanguageTag()

    private companion object {
        const val EMPTY = ""
    }
}
