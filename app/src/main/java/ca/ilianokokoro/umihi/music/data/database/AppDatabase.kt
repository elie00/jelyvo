package ca.ilianokokoro.umihi.music.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalHistoryDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalPlaylistDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalPlayerQueueDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalSongDataSource
import ca.ilianokokoro.umihi.music.data.datasources.local.VersionDataSource
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
        PlayerQueueState::class,
    ],
    version = Constants.Database.VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songRepository(): LocalSongDataSource
    abstract fun playlistRepository(): LocalPlaylistDataSource
    abstract fun versionRepository(): VersionDataSource
    abstract fun historyRepository(): LocalHistoryDataSource
    abstract fun playerQueueRepository(): LocalPlayerQueueDataSource

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
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration(dropAllTables = true)
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
    }
}
