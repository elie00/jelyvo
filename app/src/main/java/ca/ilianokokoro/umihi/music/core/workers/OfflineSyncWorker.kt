package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.DownloadHelper
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.datasources.LibraryDataSource
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodic "smart offline" sync. When enabled it downloads the account's selected sources (the
 * liked-songs playlist) that are not on device yet, reusing the same [DownloadHelper] pipeline as
 * the manual download workers, then enforces the storage quota by purging the least-recent
 * auto-downloaded tracks.
 *
 * Auto-downloaded tracks are persisted as plain [Song] rows with no playlist cross-reference. That
 * both surfaces them in the "Downloaded" library view (via getDownloadedSongs) and marks them as
 * purge-eligible: manual downloads always own a cross-reference and are therefore never reclaimed.
 */
class OfflineSyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val datastoreRepository = DatastoreRepository(appContext)
    private val libraryDataSource = LibraryDataSource()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()

    override suspend fun doWork(): Result {
        return try {
            val settings = datastoreRepository.getSettings()

            // Double guard: the schedule is cancelled when the toggle is turned off, but a run
            // already enqueued could still fire.
            if (!settings.autoDownload) {
                return Result.success()
            }

            // Download the missing liked songs (only reachable on an authenticated library browse).
            if (settings.autoDownloadLiked && !settings.cookies.isEmpty()) {
                val liked = libraryDataSource.retrieveLikedSongs(settings)

                val missing = liked
                    .distinctBy { it.youtubeId }
                    .filter { localSongRepository.getSong(it.youtubeId)?.downloaded != true }
                    .take(MAX_DOWNLOADS_PER_RUN)

                if (missing.isNotEmpty()) {
                    downloadSongs(missing)
                }
            }

            // Enforce the storage quota regardless of the source, so shrinking the quota reclaims
            // space on the next run even when there is nothing new to download.
            enforceQuota(settings.autoDownloadQuotaGb)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(message = "Offline sync failed", exception = e)
            Result.retry()
        }
    }

    private suspend fun downloadSongs(songs: List<Song>) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
        songs.map { song ->
            async {
                semaphore.withPermit {
                    try {
                        val audioPath = DownloadHelper.downloadAudio(appContext, song)
                            ?: return@withPermit
                        val thumbnailPath = DownloadHelper.downloadImage(
                            appContext,
                            song.thumbnailHref,
                            song.youtubeId
                        )

                        localSongRepository.create(
                            song.copy(
                                audioFilePath = audioPath,
                                thumbnailPath = thumbnailPath?.path,
                            )
                        )
                        printd("Offline sync downloaded ${song.title}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A single failed track must not abort the whole run; it is retried on the
                        // next periodic sync.
                        printe(message = "Offline sync failed for ${song.youtubeId}", exception = e)
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Deletes the oldest auto-downloaded tracks (by audio file mtime) until the download folder is
     * back under [quotaGb]. The currently playing track, when detectable, is spared. Manual
     * downloads are never touched (they are excluded by [getUnlinkedDownloadedSongs]).
     */
    private suspend fun enforceQuota(quotaGb: Int) = withContext(Dispatchers.IO) {
        val audioDir = UmihiHelper.getDownloadDirectory(
            appContext,
            Constants.Downloads.AUDIO_FILES_FOLDER
        )
        val thumbnailDir = UmihiHelper.getDownloadDirectory(
            appContext,
            Constants.Downloads.THUMBNAILS_FOLDER
        )

        val quotaBytes = quotaGb.toLong() * BYTES_PER_GB
        var totalBytes = directorySize(audioDir) + directorySize(thumbnailDir)
        if (totalBytes <= quotaBytes) {
            return@withContext
        }

        val nowPlayingId = currentlyPlayingId()

        val candidates = localSongRepository.getUnlinkedDownloadedSongs()
            .filter { it.youtubeId != nowPlayingId }
            .sortedBy { File(audioDir, "${it.youtubeId}.webm").lastModified() }

        val deletedIds = mutableListOf<String>()
        for (song in candidates) {
            if (totalBytes <= quotaBytes) {
                break
            }
            val audioFile = File(audioDir, "${song.youtubeId}.webm")
            val thumbnailFile = File(thumbnailDir, "${song.youtubeId}.jpg")

            totalBytes -= audioFile.length() + thumbnailFile.length()
            audioFile.delete()
            thumbnailFile.delete()
            deletedIds.add(song.youtubeId)
        }

        if (deletedIds.isNotEmpty()) {
            localSongRepository.deleteByIds(deletedIds)
            printd("Offline quota purge removed ${deletedIds.size} tracks")
        }
    }

    private fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** youtubeId of the track playing right now, or null. Read on the MediaController's thread. */
    private suspend fun currentlyPlayingId(): String? = withContext(Dispatchers.Main) {
        runCatching { PlayerManager.currentController?.currentMediaItem?.mediaId }.getOrNull()
    }

    companion object {
        // Cap the work per run: a large liked library would otherwise download for a long time on
        // a single background execution. The remainder is picked up on the next periodic run.
        private const val MAX_DOWNLOADS_PER_RUN = 15
        private const val MAX_CONCURRENT_DOWNLOADS = 4
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
    }
}
