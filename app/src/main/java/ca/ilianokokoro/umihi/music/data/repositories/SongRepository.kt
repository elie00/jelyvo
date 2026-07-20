package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.datasources.SongDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.SearchResult
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SongRepository {
    private val songDataSource = SongDataSource()

    fun search(
        query: String,
        filter: SearchFilter = SearchFilter.Songs,
    ): Flow<ApiResult<SearchResult>> {
        return flow {
            emit(ApiResult.Loading)
            emit(ApiResult.Success(songDataSource.search(query, filter)))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }

    suspend fun searchMore(
        continuationToken: String,
        filter: SearchFilter = SearchFilter.Songs,
    ): SearchResult =
        withContext(Dispatchers.IO) {
            songDataSource.searchMore(continuationToken, filter)
        }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        songDataSource.suggestions(query)
    }

    fun getSongInfo(songId: String): Flow<ApiResult<Song>> {
        return flow {
            emit(ApiResult.Loading)
            emit(ApiResult.Success(songDataSource.getSongInfo(songId)))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }
}
