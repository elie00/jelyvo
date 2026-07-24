package ca.ilianokokoro.umihi.music.data.database

import ca.ilianokokoro.umihi.music.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The database builder ends in `fallbackToDestructiveMigration(dropAllTables = true)`, so a version
 * bump shipped without its migration wipes every user's library at runtime instead of failing
 * anywhere visible. This asserts the registered migrations form an unbroken chain up to
 * [Constants.Database.VERSION], turning that silent wipe into a red test.
 */
class AppDatabaseMigrationsTest {

    @Test
    fun `migrations form an unbroken chain up to the current version`() {
        val migrations = AppDatabase.MIGRATIONS
        val byStartVersion = migrations.associateBy { it.startVersion }

        for (version in migrations.minOf { it.startVersion } until Constants.Database.VERSION) {
            val migration = byStartVersion[version]
            assertNotNull("No migration registered for $version -> ${version + 1}", migration)
            assertEquals(version + 1, migration!!.endVersion)
        }
    }
}
