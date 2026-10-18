package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.DownloadHelper
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotificationManager
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlin.coroutines.cancellation.CancellationException

class SongDownloadWorker(
    private val appContext: Context,
    private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val playlistRepository = AppDatabase.getInstance(appContext).playlistRepository()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()
    private val songRepository = SongRepository()

    override suspend fun doWork(): Result {
        val playlistId = params.inputData.getString(PLAYLIST_KEY)
            ?: return Result.failure()

        val songId = params.inputData.getString(SONG_KEY)
            ?: return Result.failure()

        val playlist = playlistRepository.getPlaylistById(playlistId)
            ?: return Result.failure()

        val song = localSongRepository.getSong(songId)
            ?: return Result.failure()

        setProgress(
            workDataOf(
                DownloadWorkContract.KEY_COMPLETED to 0,
                DownloadWorkContract.KEY_TOTAL to 1,
                DownloadWorkContract.KEY_FAILED to 0,
                DownloadWorkContract.KEY_TITLE to song.title,
            ),
        )

        return try {
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

            val fullSongData = songRepository
                .getSongInfo(song.youtubeId)
                .firstOrNull { it is ApiResult.Success } as? ApiResult.Success
                ?: throw IllegalStateException("Could not fetch song info for ${song.youtubeId}")

            val fullSong = fullSongData.data

            val audioPath = DownloadHelper.downloadAudio(appContext, song)

            // The player response only knows the video thumbnail (16:9 for clips); keep the
            // square art captured when the song was added, if any.
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

            NotificationManager.showSongDownloadSuccess(appContext, song)

            Result.success(
                workDataOf(
                    DownloadWorkContract.KEY_COMPLETED to 1,
                    DownloadWorkContract.KEY_TOTAL to 1,
                    DownloadWorkContract.KEY_FAILED to 0,
                ),
            )
        } catch (_: CancellationException) {
            printd("Song download canceled ${song.title}")
            Result.failure(
                workDataOf(
                    DownloadWorkContract.KEY_COMPLETED to 0,
                    DownloadWorkContract.KEY_TOTAL to 1,
                    DownloadWorkContract.KEY_FAILED to 1,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            NotificationManager.showSongDownloadFailed(appContext, song)

            printe(
                message = "Error downloading song: ${song.youtubeId}",
                exception = e
            )

            Result.failure(
                workDataOf(
                    DownloadWorkContract.KEY_COMPLETED to 0,
                    DownloadWorkContract.KEY_TOTAL to 1,
                    DownloadWorkContract.KEY_FAILED to 1,
                ),
            )
        }
    }

    companion object {
        const val PLAYLIST_KEY = "playlist"
        const val SONG_KEY = "song"
    }
}
