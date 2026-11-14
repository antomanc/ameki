package dev.antomanc.nembo.core.data

import dev.antomanc.nembo.core.data.db.PlaceDao
import dev.antomanc.nembo.core.data.db.SnapshotDao
import dev.antomanc.nembo.core.data.db.SnapshotEntity
import dev.antomanc.nembo.core.data.settings.SettingsRepository
import dev.antomanc.nembo.core.data.sync.PlaceWeather
import dev.antomanc.nembo.core.data.sync.SyncError
import dev.antomanc.nembo.core.data.sync.WeatherUpdateListener
import dev.antomanc.nembo.core.model.AppSettings
import dev.antomanc.nembo.core.model.DataPart
import dev.antomanc.nembo.core.model.ProviderId
import dev.antomanc.nembo.core.model.RefreshPolicy
import dev.antomanc.nembo.core.model.WeatherSnapshot
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
    private val providers: ProviderRegistry,
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
        placeList.map { place -> toPlaceWeather(place, byPlace[place.id], s.provider) }
    }

    suspend fun get(placeId: Long): PlaceWeather? {
        val place = placeDao.byId(placeId)?.toModel() ?: return null
        return toPlaceWeather(place, snapshotDao.get(placeId), settings.current().provider)
    }

    private fun toPlaceWeather(place: dev.antomanc.nembo.core.model.Place, e: SnapshotEntity?, provider: ProviderId): PlaceWeather {
        val snapshot = e?.let { decode(it) }?.let { valid(it, provider) }
        return PlaceWeather(
            place = place,
            snapshot = snapshot,
            lastAttemptAt = e?.lastAttemptAt?.let(Instant::ofEpochSecond),
            error = e?.lastError?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
        )
    }

    private fun decode(e: SnapshotEntity): WeatherSnapshot? =
        runCatching { json.decodeFromString(WeatherSnapshot.serializer(), e.json) }.getOrNull()

    /** Drop data from another provider and parts past their retention limit. */
    private fun valid(s: WeatherSnapshot, provider: ProviderId): WeatherSnapshot? {
        if (s.provider != provider) return null
        val now = clock()
        val keep = s.fetchedAt.filter { (part, at) ->
            Duration.between(at, now) < RefreshPolicy.maxAge(s.provider, part)
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
        val provider = providers[s.provider]
        val existing = snapshotDao.get(placeId)?.let(::decode)?.let { valid(it, s.provider) }
        val parts = if (force) {
            DataPart.entries.toSet()
        } else {
            RefreshPolicy.dueParts(s.provider, existing, s.refreshIntervalMinutes, clock())
        }.filterTo(mutableSetOf()) { it != DataPart.ALERTS || dev.antomanc.nembo.core.model.Capability.ALERTS in provider.capabilities }
        if (parts.isEmpty()) return null

        return try {
            val fresh = provider.fetch(placeEntity.toModel().point.coarse(), language(s), parts)
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
                providerId = s.provider.name,
                json = snapshot?.let { json.encodeToString(WeatherSnapshot.serializer(), it) }
                    ?: snapshotDao.get(placeId)?.json ?: EMPTY,
                lastAttemptAt = clock().epochSecond,
                lastError = error?.name,
            ),
        )
        return error
    }

    /** Physically delete expired data (required by Google's caching terms, tidy for the rest). */
    suspend fun purgeExpired() {
        val provider = settings.current().provider
        snapshotDao.all().forEach { e ->
            val s = decode(e) ?: return@forEach
            val kept = valid(s, provider)
            if (kept != s) {
                snapshotDao.upsert(e.copy(json = kept?.let { json.encodeToString(WeatherSnapshot.serializer(), it) } ?: EMPTY))
            }
        }
    }

    /** Switching provider: old data is meaningless for the new one (and Google's must go). */
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
