package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Builds a "radio" / autoplay continuation for a seed song from the seed's own YouTube Music
 * station ("RDAMVM" + videoId), so the suggestions stay in the seed's register instead of the
 * generic recommendations a plain related-streams lookup returns.
 */
class RadioDataSource {
    private val songDataSource = SongDataSource()

    suspend fun getRelatedSongs(seed: Song): List<Song> = withContext(Dispatchers.IO) {
        val response = YoutubeApiClient.next(
            videoId = seed.youtubeId,
            playlistId = RADIO_PLAYLIST_PREFIX + seed.youtubeId,
        )
        val related = YoutubeDataExtractor.extractRadioSongs(response)
            .filter { it.youtubeId != seed.youtubeId }
            .distinctBy { it.youtubeId }

        enrichSongs(related)
            .distinctBy { it.youtubeId }
            .filter { it.youtubeId != seed.youtubeId }
    }

    /**
     * Radio items can be the music-video version of a track, with a 16:9 thumbnail and the clip's
     * audio. Swap each one for its album-audio counterpart when it exists; otherwise just recover
     * the square art from the player info. One extra request per song, bounded, and a failure keeps
     * the item as-is.
     */
    private suspend fun enrichSongs(songs: List<Song>): List<Song> = coroutineScope {
        val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)
        songs.map { song ->
            async {
                semaphore.withPermit {
                    runCatching { songDataSource.getAudioCounterpart(song.youtubeId) }.getOrNull()
                        ?: enrichThumbnail(song)
                }
            }
        }.awaitAll()
    }

    private suspend fun enrichThumbnail(song: Song): Song {
        val betterThumbnail = runCatching {
            songDataSource.getSongInfo(song.youtubeId).thumbnailHref
        }.getOrDefault("")
        return if (betterThumbnail.isBlank()) {
            song
        } else {
            song.copy(thumbnailHref = betterThumbnail)
        }
    }

    companion object {
        private const val RADIO_PLAYLIST_PREFIX = "RDAMVM"
    }
}
