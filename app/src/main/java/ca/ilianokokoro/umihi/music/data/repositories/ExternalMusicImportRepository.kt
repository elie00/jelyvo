package ca.ilianokokoro.umihi.music.data.repositories

import android.app.Application
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.extensions.durationToSeconds
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import ca.ilianokokoro.umihi.music.models.importing.ExternalMusicPlaylist
import ca.ilianokokoro.umihi.music.models.importing.MusicImportProgress
import ca.ilianokokoro.umihi.music.models.importing.MusicImportTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ExternalMusicImportRepository(
    application: Application,
    private val songRepository: SongRepository = SongRepository(),
) {
    private val localPlaylistRepository = LocalPlaylistRepository(application)

    fun importPlaylist(playlist: ExternalMusicPlaylist): Flow<MusicImportProgress> = flow {
        emit(MusicImportProgress(playlistName = playlist.name, total = playlist.tracks.size))

        val matched = mutableListOf<Song>()
        val failures = mutableListOf<MusicImportTrack>()
        playlist.tracks.forEachIndexed { index, track ->
            val match = matchTrack(track)
            if (match != null) matched += match else failures += track

            val complete = index == playlist.tracks.lastIndex
            val playlistId = if (complete && matched.isNotEmpty()) {
                localPlaylistRepository.createFromTracks(
                    name = playlist.name,
                    tracks = matched.map { it.toTrack() },
                )
            } else {
                null
            }
            emit(
                MusicImportProgress(
                    playlistName = playlist.name,
                    total = playlist.tracks.size,
                    processed = index + 1,
                    matched = matched.toList(),
                    failures = failures.toList(),
                    createdPlaylistId = playlistId,
                    isComplete = complete,
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun matchTrack(track: MusicImportTrack): Song? {
        if (track.searchQuery.isBlank()) return null
        return try {
            val result = songRepository.search(track.searchQuery)
                .first { it !is ApiResult.Loading }
            val candidates = (result as? ApiResult.Success)?.data?.songs.orEmpty()
            chooseBestImportMatch(track, candidates)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}

internal fun chooseBestImportMatch(
    track: MusicImportTrack,
    candidates: List<Song>,
): Song? {
    val wantedTitle = normalizeImportText(track.title)
    val wantedArtist = normalizeImportText(track.artist)
    if (wantedTitle.isBlank()) return null

    return candidates
        .map { candidate ->
            val candidateTitle = normalizeImportText(candidate.title)
            val candidateArtist = normalizeImportText(candidate.artist)
            var score = when {
                candidateTitle == wantedTitle -> 12
                candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> 7
                else -> titleTokenScore(wantedTitle, candidateTitle)
            }
            if (wantedArtist.isNotBlank()) {
                score += when {
                    candidateArtist == wantedArtist -> 8
                    candidateArtist.contains(wantedArtist) ||
                        wantedArtist.contains(candidateArtist) -> 5
                    else -> titleTokenScore(wantedArtist, candidateArtist).coerceAtMost(3)
                }
            }
            val wantedSeconds = track.durationMs / 1_000L
            val candidateSeconds = candidate.duration.durationToSeconds().toLong()
            if (wantedSeconds > 0 && candidateSeconds > 0) {
                score += when (kotlin.math.abs(wantedSeconds - candidateSeconds)) {
                    in 0..4 -> 3
                    in 5..12 -> 1
                    else -> -2
                }
            }
            candidate to score
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second >= MINIMUM_MATCH_SCORE }
        ?.first
}

private fun normalizeImportText(value: String): String =
    java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""\([^)]*(official|audio|video|lyrics?)[^)]*\)"""), " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

private fun titleTokenScore(first: String, second: String): Int {
    val firstTokens = first.split(' ').filter(String::isNotBlank).toSet()
    val secondTokens = second.split(' ').filter(String::isNotBlank).toSet()
    if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0
    return ((firstTokens.intersect(secondTokens).size * 6f) / firstTokens.size)
        .toInt()
        .coerceAtMost(6)
}

private const val MINIMUM_MATCH_SCORE = 10
