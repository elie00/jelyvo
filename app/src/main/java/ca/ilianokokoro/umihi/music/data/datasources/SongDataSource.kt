package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.SearchResult
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.Song

class SongDataSource {
    suspend fun getSongInfo(songId: String): Song {
        return YoutubeDataExtractor.extractSongInfo(
            YoutubeApiClient.getPlayerInfo(
                songId,
                //  fields = Constants.YoutubeApi.PlayerInfo.Fields.SONG_INFO
            )
        )
    }

    /**
     * Returns the album-audio version of a music video, or null when the track already is pure
     * audio (or has no linked audio version).
     */
    suspend fun getAudioCounterpart(songId: String): Song? {
        return YoutubeDataExtractor.extractAudioCounterpart(
            YoutubeApiClient.next(songId)
        )
    }

    suspend fun search(query: String, filter: SearchFilter, paramsOverride: String? = null): SearchResult {
        val json = YoutubeApiClient.search(query, filter, paramsOverride)
        // Only the unscoped tab carries a top-result card; keep its entity out of the list rows
        // so it is not shown twice.
        val topResult = if (filter == SearchFilter.All) {
            YoutubeDataExtractor.extractTopResult(json)
        } else {
            null
        }
        val items = YoutubeDataExtractor.extractSearchItems(json, filter)
        return SearchResult(
            items = if (topResult == null) items else items.filterNot { it.id == topResult.id },
            continuation = YoutubeDataExtractor.extractContinuationToken(json),
            topResult = topResult,
            spellCorrection = YoutubeDataExtractor.extractSpellCorrection(json),
        )
    }

    suspend fun searchMore(continuationToken: String, filter: SearchFilter): SearchResult {
        val json = YoutubeApiClient.searchContinuation(continuationToken)
        return SearchResult(
            items = YoutubeDataExtractor.extractSearchItems(json, filter),
            continuation = YoutubeDataExtractor.extractContinuationToken(json),
        )
    }

    suspend fun suggestions(query: String): List<String> =
        YoutubeDataExtractor.extractSearchSuggestions(
            YoutubeApiClient.searchSuggestions(query)
        )
}
