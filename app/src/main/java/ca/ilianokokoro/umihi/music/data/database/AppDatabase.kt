package ca.ilianokokoro.umihi.music.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalHistoryDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalPlayEventDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalPlaylistDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalPlayerQueueDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalSongDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalUserPlaylistDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.VersionDataSource
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.PlayEvent
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.PlaylistSongCrossRef
import ca.ilianokokoro.umihi.music.models.PlayerQueueState
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.Version

@Database(
    entities = [
        Song::class,
        PlaylistInfo::class,
        PlaylistSongCrossRef::class,
        Version::class,
        PlayHistoryEntry::class,
        PlayEvent::class,
        PlayerQueueState::class,
        LocalPlaylist::class,
    ],
    version = Constants.Database.VERSION,
    // Exported to app/schemas (see ksp room.schemaLocation) and committed: the JSON is the
    // baseline a future MigrationTestHelper diffs the next version against.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songRepository(): LocalSongDataSource
    abstract fun playlistRepository(): LocalPlaylistDataSource
    abstract fun versionRepository(): VersionDataSource
    abstract fun historyRepository(): LocalHistoryDataSource
    abstract fun playEventRepository(): LocalPlayEventDataSource
    abstract fun playerQueueRepository(): LocalPlayerQueueDataSource
    abstract fun localPlaylistRepository(): LocalUserPlaylistDataSource

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        suspend fun clearDownloads(context: Context) {
            val instance = getInstance(context)

            instance.songRepository().deleteAll()
            instance.playlistRepository().deleteAll()
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, Constants.Database.NAME
            )
                .addMigrations(*MIGRATIONS)
                // Versions 9+ contain user history, queues and local playlists and must always
                // migrate explicitly. Only pre-v9 installs, for which this fork has no schema or
                // migration chain, may use the legacy destructive fallback.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8)
                .build()

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `${Constants.Database.PLAYER_QUEUE_TABLE}` (
                        `id` INTEGER NOT NULL,
                        `itemsJson` TEXT NOT NULL,
                        `currentIndex` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `repeatMode` INTEGER NOT NULL,
                        `shuffleEnabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `${Constants.Database.SONGS_TABLE}` ADD COLUMN `loudnessDb` REAL")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `play_history_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `youtubeId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `thumbnailHref` TEXT NOT NULL,
                        `duration` TEXT NOT NULL,
                        `isExplicit` INTEGER NOT NULL,
                        `playedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `play_history_new`
                        (`youtubeId`, `title`, `artist`, `thumbnailHref`, `duration`, `isExplicit`, `playedAt`)
                    SELECT `youtubeId`, `title`, `artist`, `thumbnailHref`, `duration`, `isExplicit`, `playedAt`
                    FROM `play_history`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `play_history`")
                db.execSQL("ALTER TABLE `play_history_new` RENAME TO `play_history`")
                db.execSQL("CREATE INDEX `index_play_history_youtubeId` ON `play_history` (`youtubeId`)")
                db.execSQL("CREATE INDEX `index_play_history_playedAt` ON `play_history` (`playedAt`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `play_history` ADD COLUMN `positionMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `play_history` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `${Constants.Database.LOCAL_PLAYLISTS_TABLE}` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `songsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `${Constants.Database.PLAYER_QUEUE_TABLE}` ADD COLUMN `sourceName` TEXT")
                db.execSQL("ALTER TABLE `${Constants.Database.PLAYER_QUEUE_TABLE}` ADD COLUMN `isRadio` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable, like `loudnessDb` and `sourceName` above: a NOT NULL column would need a
                // DEFAULT here that the entity does not declare.
                db.execSQL("ALTER TABLE `${Constants.Database.LOCAL_PLAYLISTS_TABLE}` ADD COLUMN `description` TEXT")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `${Constants.Database.PLAY_EVENTS_TABLE}` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `youtubeId` TEXT NOT NULL,
                        `playedAt` INTEGER NOT NULL,
                        `durationListenedMs` INTEGER NOT NULL,
                        `trackDurationMs` INTEGER NOT NULL,
                        `completionRatio` REAL NOT NULL,
                        `endReason` TEXT NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `audioOutput` TEXT,
                        `sourceContext` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_youtubeId` ON `${Constants.Database.PLAY_EVENTS_TABLE}` (`youtubeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_playedAt` ON `${Constants.Database.PLAY_EVENTS_TABLE}` (`playedAt`)")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable, like `sourceName` above: the stable queue-source identifier stored
                // alongside the display name (e.g. "daily-mix").
                db.execSQL("ALTER TABLE `${Constants.Database.PLAYER_QUEUE_TABLE}` ADD COLUMN `sourceId` TEXT")
            }
        }

        // Declared last on purpose: companion properties initialize top-down, so hoisting this
        // above the migrations above would capture nulls. `internal` so the unit test can assert
        // the chain has no gap (a missing hop is silently swallowed by the destructive fallback).
        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
        )
    }
}
