package dev.antomanc.ameki.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, SnapshotEntity::class, WidgetConfigEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AmekiDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun snapshots(): SnapshotDao
    abstract fun widgets(): WidgetDao
}
