package ca.ilianokokoro.umihi.music.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration16To17CreatesUsablePlayEventsTable() {
        helper.createDatabase(TEST_DB, 16).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            17,
            true,
            migrationStartingAt(16),
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO play_events (
                    youtubeId, playedAt, durationListenedMs, trackDurationMs,
                    completionRatio, endReason, hourOfDay, dayOfWeek,
                    audioOutput, sourceContext
                ) VALUES ('track', 1000, 900, 1000, 0.9, 'COMPLETED', 12, 3, NULL, 'daily-mix')
                """.trimIndent()
            )

            database.query("SELECT youtubeId, sourceContext FROM play_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("track", cursor.getString(0))
                assertEquals("daily-mix", cursor.getString(1))
            }
        }
    }

    @Test
    fun migration17To18AddsSourceIdWithoutLosingQueueState() {
        helper.createDatabase(TEST_DB, 17).use(::insertQueueState)

        helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            migrationStartingAt(17),
        ).use { database ->
            database.query(
                "SELECT sourceName, sourceId, currentIndex, positionMs FROM player_queue_state"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Daily mix", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals(2, cursor.getInt(2))
                assertEquals(42_000L, cursor.getLong(3))
            }
        }
    }

    @Test
    fun migration16To18ExecutesTheWholeRecentChain() {
        helper.createDatabase(TEST_DB, 16).use(::insertQueueState)

        helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            *AppDatabase.MIGRATIONS,
        ).use { database ->
            database.query("SELECT sourceId, itemsJson FROM player_queue_state").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("[]", cursor.getString(1))
            }
            database.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='play_events'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        }
    }

    private fun migrationStartingAt(version: Int) =
        AppDatabase.MIGRATIONS.single { it.startVersion == version }

    private fun insertQueueState(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO player_queue_state (
                id, itemsJson, currentIndex, positionMs, repeatMode,
                shuffleEnabled, sourceName, isRadio
            ) VALUES (1, '[]', 2, 42000, 0, 0, 'Daily mix', 0)
            """.trimIndent()
        )
    }

    private companion object {
        const val TEST_DB = "jelyvo-migration-test"
    }
}
