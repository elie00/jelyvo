package ca.ilianokokoro.umihi.music.data.repositories

import android.app.Application
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.localizedCacheKey
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.datasources.PlaylistDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Privacy
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.Uuid

class PlaylistRepository(application: Application) {
    private val playlistDataSource = PlaylistDataSource()
    private val localPlaylistDataSource = AppDatabase.getInstance(application).playlistRepository()
    private val localSongDataSource = AppDatabase.getInstance(application).songRepository()

    fun retrieveAll(settings: UmihiSettings): Flow<ApiResult<List<PlaylistInfo>>> {
        return cachedApiFlow(
            key = allCacheKey(settings),
            diskSerializer = ListSerializer(PlaylistInfo.serializer()),
        ) {
            try {
                playlistDataSource.retrieveAll(settings)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                // On the first ever launch there may be no response mirror yet. Room still gives
                // downloaded playlists a useful offline fallback; otherwise let cachedApiFlow
                // retain the previously painted response.
                printe(message = "retrieveAll failed, using local cache", exception = e)
                val localPlaylists = localPlaylistDataSource.getAll().map { it.info }
                if (localPlaylists.isNotEmpty()) localPlaylists else throw e
            }
        }
    }

    fun retrieveOne(
        playlist: Playlist,
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<Playlist>> {
        if (playlist.info.id == Constants.Downloads.DOWNLOADED_PLAYLIST_ID) {
            return flow {
                val downloadedSongs = localSongDataSource.getDownloadedSongs()
                emit(ApiResult.Success(Playlist(info = playlist.info, songs = downloadedSongs)))
            }.flowOn(Dispatchers.IO)
        }

        return cachedApiFlow(
            key = oneCacheKey(settings, playlist.info),
            useCache = useCache,
            diskSerializer = Playlist.serializer(),
        ) {
            try {
                val remotePlaylist = playlistDataSource.retrieveOne(playlist, settings)
                val localPlaylist = localPlaylistDataSource.getPlaylistById(playlist.info.id)
                mergeWithLocal(remotePlaylist, localPlaylist)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                printe(message = "retrieveOne failed, using local cache", exception = e)
                val localPlaylist = localPlaylistDataSource.getPlaylistById(playlist.info.id)
                if (localPlaylist != null) {
                    localPlaylist.copy(songs = localPlaylist.songs.filter { it.downloaded })
                } else {
                    throw e.toException()
                }
            }
        }
    }

    fun create(
        title: String,
        description: String,
        privacy: Privacy,
        settings: UmihiSettings
    ): Flow<ApiResult<PlaylistInfo?>> {
        return flow {
            emit(ApiResult.Loading)
            emit(
                ApiResult.Success(
                    playlistDataSource.create(title, description, privacy, settings)
                )
            )
        }.flowOn(Dispatchers.IO)
    }

    fun delete(
        playlist: PlaylistInfo,
        settings: UmihiSettings
    ): Flow<ApiResult<Unit>> {
        return flow {
            emit(ApiResult.Loading)
            emit(ApiResult.Success(playlistDataSource.delete(playlist, settings)))
        }.flowOn(Dispatchers.IO)
    }

    private fun mergeWithLocal(remotePlaylist: Playlist, localPlaylist: Playlist?): Playlist {
        if (localPlaylist == null) {
            return remotePlaylist
        }
        val localMap = localPlaylist.songs.associateBy { it.youtubeId }
        val mergedSongs = remotePlaylist.songs.map { remoteSong ->
            localMap[remoteSong.youtubeId]?.copy(uid = Uuid.random().toString()) ?: remoteSong
        }
        return remotePlaylist.copy(songs = mergedSongs)
    }

    companion object {
        fun allCacheKey(settings: UmihiSettings): String =
            localizedCacheKey("playlists:${settings.dataSyncId.orEmpty()}")

        fun oneCacheKey(settings: UmihiSettings, info: PlaylistInfo): String {
            val id = if (info.isLikedSongsPlaylist) {
                Constants.YoutubeApi.Browse.LIKED_SONGS_BROWSE_ID
            } else {
                info.id
            }
            return localizedCacheKey("playlist:${settings.dataSyncId.orEmpty()}:$id")
        }

        fun invalidateLikedCache(settings: UmihiSettings) {
            ResponseCache.remove(
                oneCacheKey(
                    settings,
                    PlaylistInfo(id = Constants.YoutubeApi.Browse.LIKED_SONGS_BROWSE_ID),
                )
            )
        }
    }
}
