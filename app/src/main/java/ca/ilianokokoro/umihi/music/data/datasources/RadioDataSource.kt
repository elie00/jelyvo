package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/**
 * Builds a "radio" / autoplay continuation for a seed song using NewPipe's related streams.
 * Relies on the already-initialized NewPipe service (see MainActivity.initNewPipe).
 */
class RadioDataSource {
    private val songDataSource = SongDataSource()

    suspend fun getRelatedSongs(seed: Song): List<Song> = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, seed.youtubeUrl)
        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
            .filter { it.youtubeId != seed.youtubeId }
            .distinctBy { it.youtubeId }

        enrichThumbnails(related)
    }

    /**
     * NewPipe related items only expose the 16:9 video thumbnail. Fetch each track's player info
     * to recover the square art when it exists — one extra request per song, bounded, and a
     * failure just keeps the video thumbnail.
     */
    private suspend fun enrichThumbnails(songs: List<Song>): List<Song> = coroutineScope {
        val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)
        songs.map { song ->
            async {
                semaphore.withPermit {
                    val betterThumbnail = runCatching {
                        songDataSource.getSongInfo(song.youtubeId).thumbnailHref
                    }.getOrDefault("")
                    if (betterThumbnail.isBlank()) {
                        song
                    } else {
                        song.copy(thumbnailHref = betterThumbnail)
                    }
                }
            }
        }.awaitAll()
    }

    private fun StreamInfoItem.toSongOrNull(): Song? {
        val videoId = YoutubeDataExtractor.extractYouTubeVideoId(url) ?: return null
        return Song(
            youtubeId = videoId,
            title = name.orEmpty(),
            artist = uploaderName.orEmpty(),
            duration = if (duration > 0) formatDuration(duration) else "",
            thumbnailHref = thumbnails.lastOrNull()?.url.orEmpty(),
        )
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
