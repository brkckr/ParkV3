package com.brkckr.parkv3.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * schemas match the entities. Add a case here for every new schema version and migration.
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
    fun exportedSchemasMatchEntitiesAfterEveryMigration() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true).close()
    }

    @Test
    fun version2DropsOnlyTheOpenStateColumn() {
        helper.createDatabase(TEST_DB, 1).use(::insertVersion1Rows)

        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            val columns = db.query("PRAGMA table_info(parks)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertThat(columns).doesNotContain("openState")
            assertThat(columns).containsAtLeast("id", "name", "capacity", "emptyCapacity", "workHours", "lastSeenSyncId")

            db.query("SELECT name, capacity, emptyCapacity, workHours FROM parks WHERE id = 3068").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("15 Temmuz")
                assertThat(cursor.getInt(1)).isEqualTo(1029)
                assertThat(cursor.getInt(2)).isEqualTo(629)
                assertThat(cursor.getString(3)).isEqualTo("24 Saat")
            }
            db.query("SELECT parkId FROM favorites").use { cursor ->
                assertThat(cursor.count).isEqualTo(1)
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(3068)
            }
        }
    }

    @Test
    fun favoritesSurviveOpeningAVersion1Database() = runBlocking {
        helper.createDatabase(TEST_DB, 1).use(::insertVersion1Rows)

        val database = ParkDatabase.create(context, TEST_DB)
        try {
            val dao = database.parkDao()
            assertThat(dao.observeFavoriteIds().first()).containsExactly(3068)
            assertThat(dao.getPark(3068)?.capacity).isEqualTo(1029)
        } finally {
            database.close()
        }
    }

    /** Rows as version 1 stored them, including the since-dropped `openState` column. */
    private fun insertVersion1Rows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO favorites (parkId, nameSnapshot, districtSnapshot, addedAtMillis) VALUES (3068, '15 Temmuz', 'ÜMRANİYE', 1)")
        db.execSQL(
            "INSERT INTO parks (id, name, district, latitude, longitude, openState, capacity, emptyCapacity, workHours, " +
                "parkType, freeTime, lastSeenSyncId, lastSeenAtMillis, missingSinceMillis) " +
                "VALUES (3068, '15 Temmuz', 'ÜMRANİYE', 41.0246, 29.0915, 'CLOSED', 1029, 629, '24 Saat', 'KAPALI OTOPARK', 15, 1, 1, NULL)",
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
