package dev.antomanc.ameki.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    /** Rounded to ~1 km: enough for weather, and avoids sending a precise position anywhere. */
    fun coarse(): GeoPoint = GeoPoint(round2(latitude), round2(longitude))

    /** Same place for weather purposes: within about a kilometre. */
    fun isNear(other: GeoPoint, tolerance: Double = 0.01): Boolean =
        kotlin.math.abs(latitude - other.latitude) <= tolerance &&
            kotlin.math.abs(longitude - other.longitude) <= tolerance

    private fun round2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}

/** A place the user follows. [isCurrentLocation] places get their coordinates from the device. */
data class Place(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val point: GeoPoint,
    val timeZone: String?,
    val isCurrentLocation: Boolean = false,
    val sortOrder: Int = 0,
    /** The city name at the first rename: the personal label is [name], this keeps the real one. */
    val originalName: String? = null,
) {
    val subtitle: String? get() = buildList {
        if (originalName != null && originalName != name) add(originalName)
        addAll(listOfNotNull(region, country))
    }.distinct().joinToString(", ").ifEmpty { null }
}

/** Result of a city search, before it is saved. */
data class PlaceSearchResult(
    val name: String,
    val region: String?,
    val country: String?,
    val point: GeoPoint,
    val timeZone: String?,
)
