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
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    private val onNextExpiry: (Instant?) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** Weather for every saved place, in display order. */
    val all: Flow<List<PlaceWeather>> = combine(
        places.places, snapshotDao.observeAll(), settings.settings, expiryTicks(),
    ) { placeList, snapshots, s, _ ->
        val byPlace = snapshots.associateBy { it.placeId }
        placeList.map { place -> toPlaceWeather(place, byPlace[place.id], s.source) }
    }

    suspend fun get(placeId: Long): PlaceWeather? {
        val place = placeDao.byId(placeId)?.toModel() ?: return null
        return toPlaceWeather(place, snapshotDao.get(placeId), settings.current().source)
    }

    private fun toPlaceWeather(place: dev.antomanc.ameki.core.model.Place, e: SnapshotEntity?, source: SourceId): PlaceWeather {
        val snapshot = e?.let { decode(it) }?.let { valid(it, source, place.point.coarse(), e.pointOrNull()) }
        return PlaceWeather(
            place = place,
            snapshot = snapshot,
            lastAttemptAt = e?.lastAttemptAt?.let(Instant::ofEpochSecond),
            error = e?.lastError?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
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
        val now = clock()
        val keep = s.fetchedAt.filter { (part, at) ->
            Duration.between(at, now) < RefreshPolicy.maxAge(s.source, part)
        }.keys
        return s.retain(keep).takeIf { keep.isNotEmpty() }
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
                val next = entities.mapNotNull { e -> decode(e)?.let { nextExpiry(it, now) } }.minOrNull()
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
            if (!places.updateCurrentLocation()) {
                val stillHere = placeEntity.toModel().point.coarse()
                return record(placeId, s, null, SyncError.NO_LOCATION, stillHere)
            }
            placeEntity = placeDao.byId(placeId) ?: return null
        }
        // The point this refresh is for: a move must never mix with the previous place's data.
        val point = placeEntity.toModel().point.coarse()
        val source = sources[s.source]
        val existingEntity = snapshotDao.get(placeId)
        val existing = existingEntity?.let(::decode)
            ?.let { valid(it, s.source, point, existingEntity.pointOrNull()) }
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
     * Make sure hourly data reaches [until] (the end of a day the user opened). Free for
     * sources that already return every hour; for Google it fetches only the extra pages.
     * Returns the error, or null when the hours are there.
     */
    suspend fun ensureHourlyUntil(placeId: Long, until: Instant): SyncError? = mutex.withLock {
        val s = settings.current()
        val source = sources[s.source]
        val place = placeDao.byId(placeId) ?: return@withLock null
        val point = place.toModel().point.coarse()
        val existingEntity = snapshotDao.get(placeId)
        val existing = existingEntity?.let(::decode)
            ?.let { valid(it, s.source, point, existingEntity.pointOrNull()) }
        val lastHour = existing?.hourly?.lastOrNull()?.time
        if (lastHour != null && !lastHour.isBefore(until.minusSeconds(3600))) return@withLock null
        val hours = (Duration.between(clock(), until).toHours() + 2).toInt()
        try {
            val extra = source.fetchHours(point, language(s), hours) ?: return@withLock null
            val merged = existing?.mergedWith(extra) ?: extra
            record(placeId, s, merged, null, point)
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
        onNextExpiry(snapshotDao.all().mapNotNull { decode(it)?.let { snapshot -> nextExpiry(snapshot, now) } }.minOrNull())
    }

    private fun language(s: AppSettings): String = s.dataLanguage ?: Locale.getDefault().toLanguageTag()

    private companion object {
        const val EMPTY = ""
    }
}

/** When the next cached part crosses its retention limit; internal so the timer logic is testable. */
internal fun nextExpiry(s: WeatherSnapshot, now: Instant): Instant? = s.fetchedAt.entries
    .filter { (part, at) -> at.plus(RefreshPolicy.maxAge(s.source, part)).isAfter(now) }
    .flatMap { (part, at) -> buildList {
        add(at.plus(RefreshPolicy.maxAge(s.source, part)))
        add(at.plus(RefreshPolicy.weatherFreshness(s.source, part)))
        if (part == DataPart.CURRENT) s.current?.let { add(it.time.plus(RefreshPolicy.weatherFreshness(s.source, part))) }
        add(now.atZone(s.timeZone).toLocalDate().plusDays(1).atStartOfDay(s.timeZone).toInstant())
    } }
    .filter { it.isAfter(now) }
    .minOrNull()
