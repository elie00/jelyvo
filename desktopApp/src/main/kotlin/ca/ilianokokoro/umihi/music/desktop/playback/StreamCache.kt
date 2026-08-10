package ca.ilianokokoro.umihi.music.desktop.playback

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * On-disk copy of a track's audio, because the desktop engine cannot stream YouTube's fragmented
 * MP4 directly: JavaFX Media rejects the remote URL outright but plays the same bytes from a file.
 *
 * The cost is that playback waits for the download; the benefit is that a replayed track starts
 * instantly and the cache is the natural place for offline support later.
 */
class StreamCache(private val directory: File = defaultDirectory()) {

    suspend fun localFile(track: Track, url: String): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, "${track.id}.m4a")
        if (target.isFile && target.length() > 0L) return@withContext target

        // Download beside the target and rename, so an interrupted run never leaves a truncated
        // file that later looks like a complete cache hit.
        val partial = File(directory, "${track.id}.m4a.part")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", YoutubeApiConstants.USER_AGENT)
            .header("Range", "bytes=0-")
            .build()

        UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Stream download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Stream download returned no body")
            body.byteStream().use { input -> partial.outputStream().use(input::copyTo) }
        }

        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("Could not finalize cached stream for ${track.id}")
        }
        target
    }

    private companion object {
        fun defaultDirectory(): File = File(
            System.getProperty("user.home"),
            "Library/Caches/ca.ilianokokoro.umihi.music.desktop/streams",
        )
    }
}
