package com.brkckr.parkv3.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brkckr.parkv3.data.local.ParkDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scenario 10 on a device: a database written by an installed version keeps its favorites
 * when opened by the current app configuration (no destructive fallback), and the exported
 * schema matches the entities. Add a case here for every new schema version and migration.
 */
@RunWith(AndroidJUnit4::class)
class ParkDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ParkDatabase::class.java)

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun exportedSchemaMatchesEntities() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    @Test
    fun favoritesSurviveOpeningAnExistingDatabase() = runBlocking {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO favorites (parkId, nameSnapshot, districtSnapshot, addedAtMillis) VALUES (3068, '15 Temmuz', 'ÜMRANİYE', 1)")
            db.execSQL(
                "INSERT INTO parks (id, name, district, latitude, longitude, openState, capacity, emptyCapacity, workHours, " +
                    "parkType, freeTime, lastSeenSyncId, lastSeenAtMillis, missingSinceMillis) " +
                    "VALUES (3068, '15 Temmuz', 'ÜMRANİYE', 41.0246, 29.0915, 'CLOSED', 1029, 629, '24 Saat', 'KAPALI OTOPARK', 15, 1, 1, NULL)",
            )
        }

        val database = ParkDatabase.create(context, TEST_DB)
        try {
            val dao = database.parkDao()
            assertThat(dao.observeFavoriteIds().first()).containsExactly(3068)
            assertThat(dao.getPark(3068)?.capacity).isEqualTo(1029)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
