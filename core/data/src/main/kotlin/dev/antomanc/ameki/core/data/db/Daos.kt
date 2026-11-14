package dev.antomanc.ameki.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY isCurrentLocation DESC, sortOrder, id")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY isCurrentLocation DESC, sortOrder, id")
    suspend fun all(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun byId(id: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE isCurrentLocation = 1 LIMIT 1")
    suspend fun currentLocation(): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(place: PlaceEntity): Long

    @Upsert
    suspend fun upsert(place: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE places SET lastOpenedAt = :at WHERE id = :id")
    suspend fun markOpened(id: Long, at: Long)

    @Query("UPDATE places SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM places")
    suspend fun maxOrder(): Int

    @Transaction
    suspend fun reorder(ids: List<Long>) = ids.forEachIndexed { i, id -> setOrder(id, i) }
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE placeId = :placeId")
    fun observe(placeId: Long): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshots")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE placeId = :placeId")
    suspend fun get(placeId: Long): SnapshotEntity?

    @Query("SELECT * FROM snapshots")
    suspend fun all(): List<SnapshotEntity>

    @Upsert
    suspend fun upsert(entity: SnapshotEntity)

    @Query("DELETE FROM snapshots")
    suspend fun clear()
}

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widgets")
    fun observeAll(): Flow<List<WidgetConfigEntity>>

    @Query("SELECT * FROM widgets")
    suspend fun all(): List<WidgetConfigEntity>

    @Query("SELECT * FROM widgets WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widgets WHERE appWidgetId = :id")
    fun observe(id: Int): Flow<WidgetConfigEntity?>

    @Upsert
    suspend fun upsert(config: WidgetConfigEntity)

    @Query("DELETE FROM widgets WHERE appWidgetId IN (:ids)")
    suspend fun delete(ids: List<Int>)
}
