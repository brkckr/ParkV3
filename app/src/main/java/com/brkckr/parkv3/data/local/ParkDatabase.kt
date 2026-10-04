package com.brkckr.parkv3.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema changes must ship a migration; destructive fallback is deliberately not enabled
 * because it would delete favorites (docs/adr/0002). Schemas are exported to app/schemas.
 */
@Database(
    entities = [ParkEntity::class, ParkDetailEntity::class, FavoriteEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ParkDatabase : RoomDatabase() {
    abstract fun parkDao(): ParkDao

    companion object {
        const val NAME = "parkv3.db"

        /** Production configuration, shared with the instrumented migration test. */
        fun create(context: Context, name: String = NAME): ParkDatabase =
            Room.databaseBuilder(context, ParkDatabase::class.java, name).build()
    }
}
