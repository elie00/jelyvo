package ca.ilianokokoro.umihi.music.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.containsNormalized
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.materialu.MaterialUButton
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistCard
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.DockScrollToTopEffect
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar

@Composable
internal fun OnDeviceTab(
    songs: List<Song>,
    songsLoaded: Boolean,
    onLoadLocalSongs: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var requested by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requested = true
        hasPermission = granted
        if (granted) onLoadLocalSongs()
    }

    // Request the audio permission the first time the section is opened; load once granted.
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            if (!songsLoaded) onLoadLocalSongs()
        } else if (!requested) {
            requested = true
            launcher.launch(permission)
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    if (requested) R.string.local_audio_permission_denied
                    else R.string.local_audio_permission_rationale
                ),
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(
                onClick = { launcher.launch(permission) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
        return
    }

    if (songsLoaded && songs.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_local_songs))
        return
    }

    if (!songsLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingAnimation()
        }
        return
    }

    val sourceName = stringResource(R.string.on_device)

    val listState = rememberLazyListState()
    DockScrollToTopEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = LocalLibraryHeaderHeight.current + 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        item(key = "play-shuffle") {
            PlayShuffleRow(
                onPlay = {
                    onOpenPlayer()
                    PlayerManager.playQueue(
                        songs.map { it.mediaItem },
                        sourceName = sourceName
                    )
                },
                onShuffle = {
                    onOpenPlayer()
                    PlayerManager.playQueue(
                        songs.shuffled().map { it.mediaItem },
                        sourceName = sourceName
                    )
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        items(items = songs, key = { it.youtubeId }) { song ->
            LocalSongListItem(
                song = song,
                songs = songs,
                onOpenPlayer = onOpenPlayer,
            )
        }
    }
}

@Composable
internal fun LocalSongListItem(
    song: Song,
    songs: List<Song>,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val sourceName = stringResource(R.string.on_device)

    SongListItem(
        song = song,
        onPress = {
            // Queue the whole on-device list, starting at the tapped track.
            onOpenPlayer()
            PlayerManager.playQueue(
                songs.map { it.mediaItem },
                songs.indexOf(song),
                sourceName = sourceName
            )
        },
        playNext = { PlayerManager.addNext(song, context) },
        addToQueue = { PlayerManager.addToQueue(song, context) },
        startRadio = { PlayerManager.startRadio(song, context) },
    )
}

@Composable
internal fun PlaylistsTab(
    playlists: List<PlaylistInfo>,
    localPlaylists: List<LocalPlaylist>,
    localPlaylistCovers: Map<String, List<String>>,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onLocalPlaylistPressed: (id: String) -> Unit,
    onSongDroppedOnLocalPlaylist: (playlistId: String, song: Song) -> Unit,
    onCreatePlaylist: (() -> Unit)?,
    onImportPlaylist: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    DockScrollToTopEffect(gridState)

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = LocalLibraryHeaderHeight.current + 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = PageGutter,
            end = PageGutter,
        ),
    ) {
        playlistItems(
            playlists = playlists,
            localPlaylists = localPlaylists,
            localPlaylistCovers = localPlaylistCovers,
            onPlaylistPressed = onPlaylistPressed,
            onLocalPlaylistPressed = onLocalPlaylistPressed,
            onSongDroppedOnLocalPlaylist = onSongDroppedOnLocalPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onImportPlaylist = onImportPlaylist,
        )
    }
}

/**
 * Import/create buttons plus the on-device and account playlist sections. Shared between the
 * dedicated Playlists view and the stacked all-sections view.
 */
internal fun LazyGridScope.playlistItems(
    playlists: List<PlaylistInfo>,
    localPlaylists: List<LocalPlaylist>,
    localPlaylistCovers: Map<String, List<String>>,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onLocalPlaylistPressed: (id: String) -> Unit,
    onSongDroppedOnLocalPlaylist: (playlistId: String, song: Song) -> Unit,
    onCreatePlaylist: (() -> Unit)?,
    onImportPlaylist: () -> Unit,
) {
    item(key = "playlist-actions", span = { GridItemSpan(maxLineSpan) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            MaterialUButton(
                onClick = onImportPlaylist,
                icon = Icons.Rounded.FileOpen,
                text = stringResource(R.string.import_playlist)
            )
            // Creating a playlist writes to the connected account, so it is only offered
            // when signed in (null callback while logged out).
            if (onCreatePlaylist != null) {
                MaterialUButton(
                    onClick = onCreatePlaylist,
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    text = stringResource(R.string.create_playlist)
                )
            }
        }
    }

    if (localPlaylists.isNotEmpty()) {
        item(
            key = "header-on-device-playlists",
            contentType = "header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = stringResource(R.string.on_device_playlists),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(
            items = localPlaylists,
            key = { "local-playlist-${it.id}" },
            contentType = { "playlist" },
        ) { local ->
            PlaylistCard(
                playlistInfo = PlaylistInfo(id = local.id, title = local.name),
                onClicked = { onLocalPlaylistPressed(local.id) },
                coverUris = localPlaylistCovers[local.id].orEmpty(),
                onSongDropped = { song ->
                    onSongDroppedOnLocalPlaylist(local.id, song)
                },
            )
        }
    }

    if (playlists.isNotEmpty()) {
        item(
            key = "header-account-playlists",
            contentType = "header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = stringResource(R.string.account_playlists),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    itemsIndexed(
        items = playlists,
        key = { index, playlist ->
            ComposeHelper.getLazyKey(
                playlist,
                playlist.id,
                index
            )
        },
        contentType = { _, _ -> "playlist" },
    ) { _, playlist ->
        PlaylistCard(
            playlistInfo = playlist,
            onClicked = { onPlaylistPressed(playlist) }
        )
    }
}

@Composable
internal fun LikedSongsTab(
    songs: List<Song>,
    searchQuery: String,
    debouncedSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_liked_songs))
        return
    }

    val sourceName = stringResource(R.string.liked_songs)
    val focusManager = LocalFocusManager.current

    // Accent- and case-insensitive local filter on title and artist.
    val filteredSongs = remember(songs, debouncedSearchQuery) {
        if (debouncedSearchQuery.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                song.title.containsNormalized(debouncedSearchQuery) ||
                        song.artist.containsNormalized(debouncedSearchQuery)
            }
        }
    }

    val listState = rememberLazyListState()
    DockScrollToTopEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = LocalLibraryHeaderHeight.current + 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        item(key = "search-bar") {
            SearchBar(
                modifier = Modifier.padding(bottom = 8.dp),
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                onSearch = {},
                onClear = { onSearchQueryChange("") },
                focusManager = focusManager,
            )
        }

        if (filteredSongs.isEmpty()) {
            item(key = "no-results") {
                Text(
                    text = stringResource(R.string.no_results),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        } else {
            item(key = "play-shuffle") {
                PlayShuffleRow(
                    onPlay = {
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            filteredSongs.map { it.mediaItem },
                            sourceName = sourceName
                        )
                    },
                    onShuffle = {
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            filteredSongs.shuffled().map { it.mediaItem },
                            sourceName = sourceName
                        )
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            items(items = filteredSongs, key = { it.youtubeId }) { song ->
                LikedSongListItem(
                    song = song,
                    songs = filteredSongs,
                    onOpenPlayer = onOpenPlayer,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
            }
        }
    }
}

@Composable
internal fun LikedSongListItem(
    song: Song,
    songs: List<Song>,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    val sourceName = stringResource(R.string.liked_songs)

    SongListItem(
        song = song,
        onPress = {
            // Queue the whole liked-songs list, starting at the tapped one.
            onOpenPlayer()
            PlayerManager.playQueue(
                songs.map { it.mediaItem },
                songs.indexOf(song),
                sourceName = sourceName
            )
        },
        playNext = { PlayerManager.addNext(song, context) },
        addToQueue = { PlayerManager.addToQueue(song, context) },
        startRadio = { PlayerManager.startRadio(song, context) },
        onViewArtist = {
            song.artistId?.let { id -> onOpenArtist(id, song.artist) }
        },
        onViewAlbum = {
            song.albumId?.let { id ->
                onOpenAlbum(id, song.albumName ?: "", song.thumbnailHref)
            }
        },
        initialLikeStatus = true,
    )
}

@Composable
internal fun SavedAlbumsTab(
    albums: List<SearchItem.Album>,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_saved_albums))
        return
    }

    val gridState = rememberLazyGridState()
    DockScrollToTopEffect(gridState)

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = LocalLibraryHeaderHeight.current + 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = PageGutter,
            end = PageGutter,
        ),
    ) {
        items(items = albums, key = { it.id }) { album ->
            LibraryAlbumCard(
                album = album,
                onClick = { onOpenAlbum(album.id, album.title, album.thumbnail) },
            )
        }
    }
}

@Composable
internal fun LibraryAlbumCard(
    album: SearchItem.Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        SquareImage(
            uri = album.thumbnail,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.subtitle.isNotBlank()) {
            Text(
                text = album.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun FollowedArtistsTab(
    artists: List<SearchItem.Artist>,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_followed_artists))
        return
    }

    val listState = rememberLazyListState()
    DockScrollToTopEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = LocalLibraryHeaderHeight.current + 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        items(items = artists, key = { it.id }) { artist ->
            ArtistListItem(artist = artist, onOpenArtist = onOpenArtist)
        }
    }
}

@Composable
internal fun ArtistListItem(
    artist: SearchItem.Artist,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(artist.name, style = MaterialTheme.typography.titleMedium) },
        leadingContent = {
            SquareImage(
                uri = artist.thumbnail,
                modifier = Modifier.size(56.dp),
                cornerRadius = 28.dp,
            )
        },
        modifier = Modifier.clickable { onOpenArtist(artist.id, artist.name) },
    )
}

@Composable
private fun EmptyTabMessage(text: String) {
    // Scrollable so the surrounding PullToRefreshBox still allows refreshing an empty tab.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}
