package ca.ilianokokoro.umihi.music.services

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchFilter
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class UmihiMediaLibraryCallback(
    private val service: PlaybackService,
    private val serviceScope: CoroutineScope,
    private val datastoreRepository: DatastoreRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) : MediaLibrarySession.Callback {
    private val database = AppDatabase.getInstance(service.applicationContext)
    private val localPlaylists = database.playlistRepository()
    private val localSongs = database.songRepository()
    private val historyRepository = HistoryRepository(service.applicationContext)
    private val searchResults = ConcurrentHashMap<String, List<MediaItem>>()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val commands =
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_GET_TIMELINE)
                .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(commands)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                MediaItem.Builder()
                    .setMediaId(Constants.ExoPlayer.Library.ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(service.getString(R.string.app_name))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build(),
                params
            )
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        serviceScope.launch {
            try {
                val result = when {
                    parentId == Constants.ExoPlayer.Library.ROOT_ID -> {
                        listOf(
                            browsableFolder(
                                Constants.ExoPlayer.Library.RECENT_ROOT,
                                service.getString(R.string.recently_played),
                                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                            ),
                            browsableFolder(
                                Constants.ExoPlayer.Library.TOP_TRACKS_ROOT,
                                service.getString(R.string.top_tracks),
                                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                            ),
                            browsableFolder(
                                Constants.ExoPlayer.Library.DOWNLOADS_ROOT,
                                service.getString(R.string.downloads),
                                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                            ),
                            browsableFolder(
                                Constants.ExoPlayer.Library.PLAYLIST_ROOT,
                                service.getString(R.string.playlists),
                                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                            ),
                        )
                    }

                    parentId == Constants.ExoPlayer.Library.PLAYLIST_ROOT -> {
                        val remote = try {
                            val result = playlistRepository
                                .retrieveAll(datastoreRepository.settings.first())
                                .first { it !is ApiResult.Loading }
                            (result as? ApiResult.Success)?.data.orEmpty()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val local = localPlaylists.getAll().map { it.info }
                        (remote + local)
                            .distinctBy { it.id }
                            .map(PlaylistInfo::toBrowsableMediaItem)
                    }

                    parentId in collectionRootIds -> {
                        val songs = collectionSongs(parentId)
                        if (songs.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(
                                playCollectionMediaItem(parentId),
                                shuffleCollectionMediaItem(parentId),
                            ) + songs.map { it.mediaItem }
                        }
                    }

                    parentId.startsWith(Constants.ExoPlayer.Library.PLAYLIST_PREFIX) -> {
                        val playlistId =
                            parentId.removePrefix(Constants.ExoPlayer.Library.PLAYLIST_PREFIX)

                        val playlist = Playlist(
                            PlaylistInfo(id = playlistId)
                        )

                        val remoteResult = try {
                            playlistRepository
                                .retrieveOne(playlist, datastoreRepository.settings.first())
                                .first { it !is ApiResult.Loading }
                        } catch (_: Exception) {
                            null
                        }
                        val resolvedPlaylist = (remoteResult as? ApiResult.Success)?.data
                            ?: localPlaylists.getPlaylistById(playlistId)

                        if (resolvedPlaylist != null) {
                            listOf(
                                playPlaylistMediaItem(playlistId),
                                shufflePlaylistMediaItem(playlistId)
                            ) + resolvedPlaylist.songs.map { song ->
                                song.mediaItem
                            }
                        } else {
                            emptyList()
                        }
                    }

                    else -> emptyList()
                }

                future.set(
                    LibraryResult.ofItemList(
                        result,
                        params
                    )
                )
            } catch (_: Exception) {
                future.set(
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                )
            }
        }

        return future
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        val future = SettableFuture.create<LibraryResult<Void>>()

        serviceScope.launch {
            try {
                val results = searchMediaItems(query).take(MAX_SEARCH_RESULTS)
                if (searchResults.size >= MAX_CACHED_SEARCHES) {
                    searchResults.clear()
                }
                searchResults[query.normalizedSearchKey()] = results
                session.notifySearchResultChanged(browser, query, results.size, params)
                future.set(LibraryResult.ofVoid(params))
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }

        return future
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        serviceScope.launch {
            try {
                val results = searchResults[query.normalizedSearchKey()]
                    ?: searchMediaItems(query).take(MAX_SEARCH_RESULTS).also {
                        searchResults[query.normalizedSearchKey()] = it
                    }
                future.set(
                    LibraryResult.ofItemList(
                        paginateMediaItems(results, page, pageSize),
                        params,
                    )
                )
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }

        return future
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

        serviceScope.launch {
            try {
                val resolvedItems = mediaItems.flatMap { item ->
                    resolveMediaItemForPlayback(item)
                }

                val resolvedStartIndex = when {
                    resolvedItems.isEmpty() -> C.INDEX_UNSET
                    startIndex == C.INDEX_UNSET -> 0
                    else -> startIndex.coerceIn(0, resolvedItems.lastIndex)
                }

                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        resolvedItems,
                        resolvedStartIndex,
                        startPositionMs.coerceAtLeast(0L)
                    )
                )
            } catch (e: Exception) {
                future.setException(e)
            }
        }

        return future
    }

    private suspend fun resolveMediaItemForPlayback(
        mediaItem: MediaItem
    ): List<MediaItem> {
        mediaItem.requestMetadata.searchQuery
            ?.takeIf(String::isNotBlank)
            ?.let { return searchMediaItems(it).take(VOICE_PLAY_QUEUE_SIZE) }

        val mediaId = mediaItem.mediaId

        if (mediaId.startsWith(Constants.ExoPlayer.Library.PLAY_PLAYLIST_PREFIX)) {
            val playlistId = mediaId.removePrefix(Constants.ExoPlayer.Library.PLAY_PLAYLIST_PREFIX)
            return resolvePlaylistSongs(
                playlistId = playlistId,
                shuffle = false
            )
        }

        if (mediaId.startsWith(Constants.ExoPlayer.Library.SHUFFLE_PLAYLIST_PREFIX)) {
            val playlistId =
                mediaId.removePrefix(Constants.ExoPlayer.Library.SHUFFLE_PLAYLIST_PREFIX)
            return resolvePlaylistSongs(
                playlistId = playlistId,
                shuffle = true
            )
        }

        if (mediaId.startsWith(Constants.ExoPlayer.Library.PLAY_COLLECTION_PREFIX)) {
            return resolveCollectionSongs(
                mediaId.removePrefix(Constants.ExoPlayer.Library.PLAY_COLLECTION_PREFIX),
                shuffle = false,
            )
        }

        if (mediaId.startsWith(Constants.ExoPlayer.Library.SHUFFLE_COLLECTION_PREFIX)) {
            return resolveCollectionSongs(
                mediaId.removePrefix(Constants.ExoPlayer.Library.SHUFFLE_COLLECTION_PREFIX),
                shuffle = true,
            )
        }

        return listOf(
            resolveSongMediaItem(mediaItem)
        )
    }

    private suspend fun searchMediaItems(query: String): List<MediaItem> {
        if (query.isBlank()) {
            return collectionSongs(Constants.ExoPlayer.Library.RECENT_ROOT)
                .take(VOICE_PLAY_QUEUE_SIZE)
                .map { it.mediaItem }
        }

        val result = songRepository.search(query, SearchFilter.Songs)
            .first { it !is ApiResult.Loading }
        return (result as? ApiResult.Success)
            ?.data
            ?.songs
            .orEmpty()
            .map { it.mediaItem }
    }

    private suspend fun resolvePlaylistSongs(
        playlistId: String,
        shuffle: Boolean
    ): List<MediaItem> {
        val result = playlistRepository.retrieveOne(
            playlist = Playlist(
                PlaylistInfo(id = playlistId)
            ),
            settings = datastoreRepository.settings.first()
        ).firstOrNull { result ->
            result is ApiResult.Success
        }

        val playlist = (result as? ApiResult.Success)?.data
            ?: localPlaylists.getPlaylistById(playlistId)
            ?: return emptyList()

        val songs = if (shuffle) playlist.songs.shuffled() else playlist.songs

        return songs.map { song ->
            song.mediaItem
        }
    }

    private suspend fun resolveSongMediaItem(
        mediaItem: MediaItem
    ): MediaItem {
        val mediaId = mediaItem.mediaId

        if (mediaItem.localConfiguration?.uri != null &&
            mediaItem.mediaMetadata.title != null
        ) {
            return mediaItem
        }

        // On-device track: the playable content:// URI is dropped when the item crosses the
        // controller→session boundary, so rebuild it from the metadata extras instead of trying
        // to resolve a (non-existent) YouTube stream.
        val extras = mediaItem.mediaMetadata.extras
        if (extras?.getBoolean(Constants.ExoPlayer.SongMetadata.IS_LOCAL) == true) {
            val localUri = extras.getString(Constants.ExoPlayer.SongMetadata.LOCAL_URI)
            if (!localUri.isNullOrBlank()) {
                return mediaItem.buildUpon().setUri(localUri).build()
            }
        }

        val songResult = songRepository.getSongInfo(mediaId)
            .firstOrNull { result ->
                result is ApiResult.Success
            }

        if (songResult is ApiResult.Success) {
            return songResult.data.mediaItem
        }

        return mediaItem.buildUpon()
            .setUri("${Constants.YoutubeApi.YOUTUBE_URL_PREFIX}$mediaId")
            .build()
    }

    private fun playPlaylistMediaItem(
        playlistId: String
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId("${Constants.ExoPlayer.Library.PLAY_PLAYLIST_PREFIX}$playlistId")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(service.getString(R.string.play))
                    .setArtworkUri(drawableUri(androidx.media3.session.R.drawable.media3_icon_play))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()
    }

    private suspend fun collectionSongs(collectionId: String) = when (collectionId) {
        Constants.ExoPlayer.Library.RECENT_ROOT -> historyRepository.recentSongs(50).first()
        Constants.ExoPlayer.Library.DOWNLOADS_ROOT -> localSongs.getDownloadedSongs()
        Constants.ExoPlayer.Library.TOP_TRACKS_ROOT -> {
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            historyRepository.topTracks(monthStart, 25).first().map { it.toSong() }
        }
        else -> emptyList()
    }

    private suspend fun resolveCollectionSongs(
        collectionId: String,
        shuffle: Boolean,
    ): List<MediaItem> {
        val songs = collectionSongs(collectionId).let { if (shuffle) it.shuffled() else it }
        return songs.map { it.mediaItem }
    }

    private fun browsableFolder(id: String, title: String, mediaType: Int) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    private fun playCollectionMediaItem(collectionId: String) =
        collectionActionMediaItem(
            mediaId = "${Constants.ExoPlayer.Library.PLAY_COLLECTION_PREFIX}$collectionId",
            title = service.getString(R.string.play),
            icon = androidx.media3.session.R.drawable.media3_icon_play,
        )

    private fun shuffleCollectionMediaItem(collectionId: String) =
        collectionActionMediaItem(
            mediaId = "${Constants.ExoPlayer.Library.SHUFFLE_COLLECTION_PREFIX}$collectionId",
            title = service.getString(R.string.shuffle),
            icon = androidx.media3.session.R.drawable.media3_icon_shuffle_on,
        )

    private fun collectionActionMediaItem(mediaId: String, title: String, icon: Int) =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(drawableUri(icon))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()

    private fun shufflePlaylistMediaItem(
        playlistId: String
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId("${Constants.ExoPlayer.Library.SHUFFLE_PLAYLIST_PREFIX}$playlistId")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(service.getString(R.string.shuffle))
                    .setArtworkUri(drawableUri(androidx.media3.session.R.drawable.media3_icon_shuffle_on))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()
    }

    private fun drawableUri(
        drawableResId: Int
    ): Uri {
        return Uri.Builder()
            .scheme("android.resource")
            .authority(service.packageName)
            .appendPath(drawableResId.toString())
            .build()
    }

    private companion object {
        const val MAX_CACHED_SEARCHES = 20
        const val MAX_SEARCH_RESULTS = 50
        const val VOICE_PLAY_QUEUE_SIZE = 20

        val collectionRootIds = setOf(
            Constants.ExoPlayer.Library.RECENT_ROOT,
            Constants.ExoPlayer.Library.DOWNLOADS_ROOT,
            Constants.ExoPlayer.Library.TOP_TRACKS_ROOT,
        )
    }
}

internal fun paginateMediaItems(
    items: List<MediaItem>,
    page: Int,
    pageSize: Int,
): List<MediaItem> {
    if (page < 0 || pageSize <= 0 || items.isEmpty()) return emptyList()

    val fromIndex = (page.toLong() * pageSize.toLong())
        .coerceAtMost(items.size.toLong())
        .toInt()
    val toIndex = (fromIndex.toLong() + pageSize.toLong())
        .coerceAtMost(items.size.toLong())
        .toInt()
    return items.subList(fromIndex, toIndex)
}

private fun String.normalizedSearchKey(): String = trim().lowercase()
