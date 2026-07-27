package ca.ilianokokoro.umihi.music.core

import android.content.Context
import ca.ilianokokoro.umihi.music.Umihi
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Storage category shown (and purgeable) in the storage dialog. */
enum class StorageCategory {
    /** Coil's on-disk artwork cache. */
    Images,

    /** Downloaded audio files plus the ExoPlayer stream cache. */
    Downloads,

    /** The Room database (playlists, history, queue). Measured only — never purged from here. */
    Database,

    /** The on-disk mirror of API responses (home feed bootstrap). */
    ApiCache,
}

/** Sizes in bytes per [StorageCategory], measured on the IO dispatcher. */
data class StorageUsage(
    val bytes: Map<StorageCategory, Long>,
) {
    fun bytesFor(category: StorageCategory): Long = bytes[category] ?: 0L
}

/**
 * Measures how much disk space each app data category occupies. Everything walks the filesystem,
 * so callers must stay off the main thread — [measure] enforces the IO dispatcher itself.
 */
object StorageInspector {

    suspend fun measure(context: Context): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            mapOf(
                StorageCategory.Images to
                        directorySizeBytes(File(context.cacheDir, Umihi.IMAGE_CACHE_DIR)),
                StorageCategory.Downloads to
                        directorySizeBytes(UmihiHelper.getDownloadDirectory(context)) +
                        directorySizeBytes(
                            File(context.cacheDir, Constants.ExoPlayer.Cache.NAME)
                        ),
                StorageCategory.Database to databaseSizeBytes(context),
                StorageCategory.ApiCache to ResponseCache.diskUsageBytes(),
            )
        )
    }

    /** Recursive size of [dir]; 0 when it does not exist. Never throws. */
    fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return runCatching {
            dir.walkBottomUp().filter(File::isFile).sumOf(File::length)
        }.getOrDefault(0L)
    }

    /** Main database file plus its SQLite -wal / -shm side files. */
    private fun databaseSizeBytes(context: Context): Long {
        val dbFile = context.getDatabasePath(Constants.Database.NAME)
        return listOf(
            dbFile,
            File(dbFile.parentFile, "${dbFile.name}-wal"),
            File(dbFile.parentFile, "${dbFile.name}-shm"),
        ).sumOf { if (it.isFile) it.length() else 0L }
    }
}
