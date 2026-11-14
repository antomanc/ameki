package dev.antomanc.ameki.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.Place

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String?,
    val isCurrentLocation: Boolean = false,
    val sortOrder: Int = 0,
    /** Epoch seconds; places not opened for a while and not on a widget are not refreshed. */
    val lastOpenedAt: Long? = null,
) {
    fun toModel() = Place(
        id = id, name = name, region = region, country = country,
        point = GeoPoint(latitude, longitude), timeZone = timeZone,
        isCurrentLocation = isCurrentLocation, sortOrder = sortOrder,
    )
}

/** Last known weather for a place, stored as JSON: one row per place, replaced on refresh. */
@Entity(
    tableName = "snapshots",
    foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = ForeignKey.CASCADE)],
)
data class SnapshotEntity(
    @PrimaryKey val placeId: Long,
    val sourceId: String,
    val json: String,
    /** Epoch seconds of the last attempt, successful or not. */
    val lastAttemptAt: Long?,
    /** Name of a SyncError, null when the last attempt succeeded. */
    val lastError: String?,
)

@Entity(
    tableName = "widgets",
    indices = [Index("placeId")],
)
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    /** Null follows the first place in the list (the current location, when enabled). */
    val placeId: Long?,
    val style: String,
    val backgroundOpacity: Float,
)
