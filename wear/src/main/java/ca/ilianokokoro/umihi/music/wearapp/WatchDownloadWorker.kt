package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit

class WatchDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val song = inputData.toSong() ?: return@withContext Result.failure()
        val store = WatchLibraryStore(applicationContext)
        if (store.audioFile(song.videoId).exists()) {
            store.addDownload(song)
            return@withContext Result.success()
        }
        val target = store.audioFile(song.videoId)
        val partial = File(target.parentFile, "${song.videoId}.partial")
        runCatching {
            val directUrl = WatchYoutubeClient().resolveAudioUrl(song.videoId)
            val request = Request.Builder().url(directUrl).build()
            HTTP_CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement HTTP ${response.code}")
                val body = response.body ?: throw IOException("Flux vide")
                partial.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            store.addDownload(song)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                partial.delete()
                if (runAttemptCount < 2) Result.retry() else Result.failure(
                    Data.Builder().putString(KEY_ERROR, it.message).build(),
                )
            },
        )
    }

    companion object {
        const val TAG = "jelyvo_watch_download"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ARTWORK = "artwork"
        const val KEY_DURATION = "duration"
        const val KEY_EXPLICIT = "explicit"
        const val KEY_ERROR = "error"
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()

        fun enqueue(context: Context, song: WatchSong, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<WatchDownloadWorker>()
                .setInputData(song.toData())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                        )
                        .build(),
                )
                .addTag(TAG)
                .addTag("$TAG:${song.videoId}")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$TAG:${song.videoId}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

private fun WatchSong.toData(): Data = Data.Builder()
    .putString(WatchDownloadWorker.KEY_VIDEO_ID, videoId)
    .putString(WatchDownloadWorker.KEY_TITLE, title)
    .putString(WatchDownloadWorker.KEY_ARTIST, artist)
    .putString(WatchDownloadWorker.KEY_ARTWORK, artworkUrl)
    .putString(WatchDownloadWorker.KEY_DURATION, durationText)
    .putBoolean(WatchDownloadWorker.KEY_EXPLICIT, isExplicit)
    .build()

private fun Data.toSong(): WatchSong? = WatchSong(
    videoId = getString(WatchDownloadWorker.KEY_VIDEO_ID) ?: return null,
    title = getString(WatchDownloadWorker.KEY_TITLE).orEmpty(),
    artist = getString(WatchDownloadWorker.KEY_ARTIST).orEmpty(),
    artworkUrl = getString(WatchDownloadWorker.KEY_ARTWORK).orEmpty(),
    durationText = getString(WatchDownloadWorker.KEY_DURATION).orEmpty(),
    isExplicit = getBoolean(WatchDownloadWorker.KEY_EXPLICIT, false),
)
