package com.brkckr.parkv3.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

/**
 * Schema changes must ship a migration; destructive fallback is deliberately not enabled
 * because it would delete favorites (docs/adr/0002). Schemas are exported to app/schemas.
 */
@Database(
    entities = [ParkEntity::class, ParkDetailEntity::class, FavoriteEntity::class, SyncStateEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = ParkDatabase.DropOpenState::class)],
)
abstract class ParkDatabase : RoomDatabase() {
    abstract fun parkDao(): ParkDao

    /** v2: the source's isOpen is no longer stored (docs/adr/0014). Favorites are untouched. */
    @DeleteColumn(tableName = "parks", columnName = "openState")
    class DropOpenState : AutoMigrationSpec

    companion object {
        const val NAME = "parkv3.db"

        /** Production configuration, shared with the instrumented migration test. */
        fun create(context: Context, name: String = NAME): ParkDatabase =
            Room.databaseBuilder(context, ParkDatabase::class.java, name).build()
    }
}
