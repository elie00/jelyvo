package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.SearchResult
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

    suspend fun search(query: String): SearchResult {
        val json = YoutubeApiClient.search(query)
        return SearchResult(
            songs = YoutubeDataExtractor.extractSearchResults(json),
            continuation = YoutubeDataExtractor.extractContinuationToken(json),
        )
    }

    suspend fun searchMore(continuationToken: String): SearchResult {
        val json = YoutubeApiClient.searchContinuation(continuationToken)
        return SearchResult(
            songs = YoutubeDataExtractor.extractSearchContinuationSongs(json),
            continuation = YoutubeDataExtractor.extractContinuationToken(json),
        )
    }
}