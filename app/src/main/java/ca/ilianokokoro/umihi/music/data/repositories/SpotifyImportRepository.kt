package ca.ilianokokoro.umihi.music.data.repositories

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyImportTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Progress snapshot emitted while importing Spotify tracks.
 *
 * - [matched]  : YouTube [Song]s resolved so far (playable/downloadable in the app).
 * - [failures] : Spotify tracks that could not be matched on YouTube.
 */
@Immutable
data class ImportProgress(
    val total: Int,
    val processed: Int = 0,
    val matched: List<Song> = emptyList(),
    val failures: List<SpotifyImportTrack> = emptyList(),
    val isComplete: Boolean = false,
) {
    val matchedCount: Int get() = matched.size
    val failureCount: Int get() = failures.size
}

/**
 * Orchestrates a Spotify -> YouTube import. Each Spotify track is matched on YouTube by
 * searching "artist(s) title" via [SongRepository.search] and taking the best (first) result.
 * Matching failures are collected, never thrown, so one bad track cannot abort the import.
 */
class SpotifyImportRepository(
    private val songRepository: SongRepository = SongRepository(),
) {
    fun importTracks(tracks: List<SpotifyImportTrack>): Flow<ImportProgress> = flow {
        // Initial snapshot so the UI can render the total immediately.
        emit(ImportProgress(total = tracks.size, isComplete = tracks.isEmpty()))

        val matched = mutableListOf<Song>()
        val failures = mutableListOf<SpotifyImportTrack>()

        tracks.forEachIndexed { index, track ->
            val bestMatch = matchTrack(track)
            if (bestMatch != null) {
                matched += bestMatch
            } else {
                failures += track
            }

            emit(
                ImportProgress(
                    total = tracks.size,
                    processed = index + 1,
                    matched = matched.toList(),
                    failures = failures.toList(),
                    isComplete = index == tracks.lastIndex,
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun matchTrack(track: SpotifyImportTrack): Song? {
        val query = track.youtubeSearchQuery
        if (query.isBlank()) return null

        return try {
            // SongRepository.search emits Loading then a terminal Success/Error.
            val result = songRepository.search(query)
                .first { it !is ApiResult.Loading }

            when (result) {
                is ApiResult.Success -> result.data.firstOrNull()
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
