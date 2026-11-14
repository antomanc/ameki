package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.data.db.PlaceDao
import dev.antomanc.ameki.core.data.db.PlaceEntity
import dev.antomanc.ameki.core.data.location.DeviceLocation
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.provider.api.PlaceSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.math.abs

class PlaceRepository(
    private val dao: PlaceDao,
    private val search: PlaceSearch,
    private val device: DeviceLocation,
    private val clock: () -> Instant = Instant::now,
) {
    val places: Flow<List<Place>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<Place> = dao.all().map { it.toModel() }

    suspend fun get(id: Long): Place? = dao.byId(id)?.toModel()

    suspend fun search(query: String, language: String) = search.search(query, language)

    suspend fun add(result: PlaceSearchResult): Long {
        val existing = dao.all().firstOrNull {
            !it.isCurrentLocation && abs(it.latitude - result.point.latitude) < 0.01 && abs(it.longitude - result.point.longitude) < 0.01
        }
        if (existing != null) return existing.id
        return dao.insert(
            PlaceEntity(
                name = result.name, region = result.region, country = result.country,
                latitude = result.point.latitude, longitude = result.point.longitude,
                timeZone = result.timeZone, sortOrder = dao.maxOrder() + 1,
                lastOpenedAt = clock().epochSecond,
            ),
        )
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun markOpened(id: Long) = dao.markOpened(id, clock().epochSecond)

    /** Turn the "current location" entry on: it is created even before the first fix arrives. */
    suspend fun enableCurrentLocation(fallbackName: String): Long {
        dao.currentLocation()?.let { return it.id }
        val fix = device.locate()
        return dao.insert(
            PlaceEntity(
                name = fix?.let { device.placeName(it)?.first } ?: fallbackName,
                region = null, country = null,
                latitude = fix?.latitude ?: 0.0, longitude = fix?.longitude ?: 0.0,
                timeZone = null, isCurrentLocation = true, sortOrder = -1,
                lastOpenedAt = clock().epochSecond,
            ),
        )
    }

    suspend fun disableCurrentLocation() {
        dao.currentLocation()?.let { dao.delete(it.id) }
    }

    /**
     * Refresh the current-location entry's coordinates. Returns true when it has a usable
     * position (either new or previously stored).
     */
    suspend fun updateCurrentLocation(): Boolean {
        val entry = dao.currentLocation() ?: return false
        val fix: GeoPoint? = device.locate()
        if (fix == null) return entry.latitude != 0.0 || entry.longitude != 0.0
        val moved = abs(fix.latitude - entry.latitude) > 0.01 || abs(fix.longitude - entry.longitude) > 0.01
        if (moved) {
            val name = device.placeName(fix)
            dao.upsert(
                entry.copy(
                    latitude = fix.latitude, longitude = fix.longitude,
                    name = name?.first ?: entry.name, region = name?.second,
                ),
            )
        }
        return true
    }

    fun hasLocationPermission() = device.hasPermission()

    /** Ids opened within [days] days: the ones worth refreshing in the background. */
    suspend fun recentlyOpened(days: Long = 3): Set<Long> {
        val since = clock().minusSeconds(days * 86_400).epochSecond
        return dao.all().filter { (it.lastOpenedAt ?: 0) >= since }.mapTo(mutableSetOf()) { it.id }
    }
}
