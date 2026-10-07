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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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
        val token = inputData.getString(KEY_TOKEN) ?: return@withContext Result.failure()
        if (token != store.downloadToken(song.videoId)) return@withContext Result.failure()
        val target = store.audioFile(song.videoId)
        if (store.registerExistingDownload(song, token)) return@withContext Result.success()
        val partial = File.createTempFile("${song.videoId}-", ".partial", target.parentFile)
        try {
            val directUrl = WatchYoutubeClient().resolveAudioUrl(song.videoId)
            val request = Request.Builder().url(directUrl).build()
            HTTP_CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement HTTP ${response.code}")
                val body = response.body ?: throw IOException("Flux vide")
                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_DOWNLOAD_BYTES) throw IOException("Titre trop volumineux")
                            output.write(buffer, 0, count)
                        }
                        if (total == 0L || (body.contentLength() >= 0 && total != body.contentLength())) {
                            throw IOException("Téléchargement incomplet")
                        }
                    }
                }
            }
            coroutineContext.ensureActive()
            if (store.publishDownload(song, partial, token)) Result.success() else Result.failure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure(
                Data.Builder().putString(KEY_ERROR, "Téléchargement impossible").build(),
            )
        } finally { partial.delete() }
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
        const val KEY_TOKEN = "download_token"
        private const val MAX_DOWNLOAD_BYTES = 128L * 1024 * 1024
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()

        fun enqueue(context: Context, song: WatchSong, wifiOnly: Boolean) {
            if (!isWatchVideoId(song.videoId)) return
            val request = OneTimeWorkRequestBuilder<WatchDownloadWorker>()
                .setInputData(Data.Builder().putAll(song.toData()).putString(KEY_TOKEN, WatchLibraryStore(context).downloadToken(song.videoId)).build())
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
                ExistingWorkPolicy.KEEP,
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
    videoId = getString(WatchDownloadWorker.KEY_VIDEO_ID)?.takeIf(::isWatchVideoId) ?: return null,
    title = getString(WatchDownloadWorker.KEY_TITLE).orEmpty(),
    artist = getString(WatchDownloadWorker.KEY_ARTIST).orEmpty(),
    artworkUrl = getString(WatchDownloadWorker.KEY_ARTWORK).orEmpty(),
    durationText = getString(WatchDownloadWorker.KEY_DURATION).orEmpty(),
    isExplicit = getBoolean(WatchDownloadWorker.KEY_EXPLICIT, false),
)
