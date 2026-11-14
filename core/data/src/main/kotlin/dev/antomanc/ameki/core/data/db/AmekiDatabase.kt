package dev.antomanc.ameki.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, SnapshotEntity::class, WidgetConfigEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AmekiDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun snapshots(): SnapshotDao
    abstract fun widgets(): WidgetDao
}
