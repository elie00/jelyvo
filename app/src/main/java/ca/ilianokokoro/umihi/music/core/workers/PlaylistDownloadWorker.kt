package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.DownloadHelper
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotificationManager
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.cancellation.CancellationException

class PlaylistDownloadWorker(
    private val appContext: Context,
    private val params: WorkerParameters
) :
    CoroutineWorker(appContext, params) {

    private val playlistRepository = AppDatabase.getInstance(appContext).playlistRepository()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()
    private val songRepository = SongRepository()

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun doWork(): Result {
        val playlistId = params.inputData.getString(PLAYLIST_KEY)
            ?: return Result.failure()

        val playlist = playlistRepository.getPlaylistById(playlistId)
            ?: return Result.failure()

        return try {
            val totalSongs = playlist.songs.size
            val downloadedIds = localSongRepository.getDownloadedSongs()
                .mapTo(mutableSetOf()) { it.youtubeId }
            val pendingSongs = playlist.songs.filterNot { it.youtubeId in downloadedIds }
            val downloadedSongs = AtomicInt(totalSongs - pendingSongs.size)
            val failedSongs = AtomicInt(0)

            setProgress(
                workDataOf(
                    DownloadWorkContract.KEY_COMPLETED to downloadedSongs.load(),
                    DownloadWorkContract.KEY_TOTAL to totalSongs,
                    DownloadWorkContract.KEY_FAILED to 0,
                    DownloadWorkContract.KEY_TITLE to playlist.info.title,
                ),
            )

            NotificationManager.showPlaylistDownloadProgress(
                appContext,
                playlist,
                downloadedSongs.load(),
                totalSongs
            )

            val playlistImage = DownloadHelper.downloadImage(
                appContext,
                playlist.info.coverHref,
                playlist.info.id
            )

            playlistRepository.insertPlaylist(
                playlist.info.copy(
                    coverPath = playlistImage?.path
                )
            )

            val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)

            coroutineScope {
                pendingSongs.map { song ->
                    async {
                        semaphore.withPermit {
                            try {
                                val fullSongData = songRepository
                                    .getSongInfo(song.youtubeId)
                                    .firstOrNull { it is ApiResult.Success } as? ApiResult.Success
                                    ?: throw IllegalStateException(
                                        "Could not fetch song info for ${song.youtubeId}"
                                    )

                                val fullSong = fullSongData.data

                                val audioPath = DownloadHelper.downloadAudio(appContext, song)

                                // The player response only knows the video thumbnail (16:9 for
                                // clips); keep the square art captured with the playlist, if any.
                                val thumbnailPath = DownloadHelper.downloadImage(
                                    appContext,
                                    song.thumbnailHref.ifBlank { fullSong.thumbnailHref },
                                    song.youtubeId
                                )

                                val updatedSong = song.copy(
                                    thumbnailPath = thumbnailPath?.path,
                                    audioFilePath = audioPath,
                                )

                                localSongRepository.create(updatedSong)

                                val downloaded = downloadedSongs.incrementAndFetch()
                                setProgress(
                                    workDataOf(
                                        DownloadWorkContract.KEY_COMPLETED to downloaded,
                                        DownloadWorkContract.KEY_TOTAL to totalSongs,
                                        DownloadWorkContract.KEY_FAILED to failedSongs.load(),
                                        DownloadWorkContract.KEY_TITLE to playlist.info.title,
                                    ),
                                )
                                if (downloaded < totalSongs) {
                                    NotificationManager.showPlaylistDownloadProgress(
                                        appContext,
                                        playlist,
                                        downloaded,
                                        totalSongs
                                    )
                                }
                            } catch (e: CancellationException) {
                                printd("Song download canceled ${song.title}")
                                throw e
                            } catch (e: Exception) {
                                val failed = failedSongs.incrementAndFetch()
                                setProgress(
                                    workDataOf(
                                        DownloadWorkContract.KEY_COMPLETED to downloadedSongs.load(),
                                        DownloadWorkContract.KEY_TOTAL to totalSongs,
                                        DownloadWorkContract.KEY_FAILED to failed,
                                        DownloadWorkContract.KEY_TITLE to playlist.info.title,
                                    ),
                                )
                                NotificationManager.showSongDownloadFailed(
                                    appContext,
                                    song
                                )

                                printe(
                                    message = "Error downloading song: ${song.title}",
                                    exception = e
                                )
                            }
                        }
                    }
                }.awaitAll()
            }

            if (failedSongs.load() == 0) {
                NotificationManager.showPlaylistDownloadSuccess(appContext, playlist)
                printd("Playlist download complete")
            } else {
                NotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                printd("Playlist download partial: ${failedSongs.load()} failure(s)")
            }

            Result.success(
                workDataOf(
                    DownloadWorkContract.KEY_COMPLETED to downloadedSongs.load(),
                    DownloadWorkContract.KEY_TOTAL to totalSongs,
                    DownloadWorkContract.KEY_FAILED to failedSongs.load(),
                ),
            )
        } catch (_: CancellationException) {
            NotificationManager.showPlaylistDownloadCanceled(appContext, playlist)
            printd("Playlist download canceled ${playlist.info.title}")
            Result.failure()
        } catch (e: Exception) {
            NotificationManager.showPlaylistDownloadFailure(appContext, playlist)
            printe(message = e.toString(), exception = e)
            Result.failure()
        }
    }


    companion object {
        const val PLAYLIST_KEY = "playlist"
    }
}
