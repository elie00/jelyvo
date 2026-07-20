package ca.ilianokokoro.umihi.music.ui.screens.library

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.dialog.PlaylistCreationDialog
import ca.ilianokokoro.umihi.music.ui.components.materialu.MaterialUButton
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistCard
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel

private enum class LibraryTab {
    Playlists,
    LikedSongs,
    Albums,
    Artists,
}

/**
 * Top-level "Library" destination: the connected account's collection, split into playlists,
 * liked songs, saved albums and followed artists.
 */
@Composable
fun LibraryScreen(
    sharedViewModel: SharedViewModel,
    onSettingsButtonPress: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    application: Application,
    libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(application = application)
    ),
) {
    val uiState = libraryViewModel.uiState.collectAsStateWithLifecycle().value
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.Playlists) }
    var createPlaylistOpen by remember { mutableStateOf(false) }

    val deletedPlaylistIds by sharedViewModel.deletedPlaylistIds.collectAsState()
    val playlistRefreshNeeded by sharedViewModel.playlistRefreshNeeded.collectAsState()

    LaunchedEffect(deletedPlaylistIds, playlistRefreshNeeded) {
        when {
            playlistRefreshNeeded -> {
                libraryViewModel.refreshPlaylists()
                sharedViewModel.consumePlaylistRefresh()
                sharedViewModel.consumeDeletedPlaylists()
            }

            deletedPlaylistIds.isNotEmpty() -> {
                libraryViewModel.removePlaylistsFromList(deletedPlaylistIds)
                sharedViewModel.consumeDeletedPlaylists()
            }
        }
    }

    FadingStatusBarWrapper { statusBarHeight ->
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding() + statusBarHeight),
            ) {
                when (uiState.screenState) {
                    is ScreenState.Success -> {
                        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                            LibraryTab.entries.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = {
                                        Text(
                                            stringResource(
                                                when (tab) {
                                                    LibraryTab.Playlists -> R.string.playlists
                                                    LibraryTab.LikedSongs -> R.string.liked_songs
                                                    LibraryTab.Albums -> R.string.saved_albums
                                                    LibraryTab.Artists -> R.string.followed_artists
                                                }
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = libraryViewModel::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LibraryContent(
                                library = uiState.screenState.library,
                                playlists = uiState.playlists,
                                selectedTab = selectedTab,
                                onOpenPlayer = onOpenPlayer,
                                onPlaylistPressed = onPlaylistPressed,
                                onCreatePlaylist = { createPlaylistOpen = true },
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtist = onOpenArtist,
                            )
                        }
                    }

                    ScreenState.LoggedOut -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(R.string.log_in_message),
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = onSettingsButtonPress,
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }

                    ScreenState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingAnimation()
                    }

                    is ScreenState.Error -> ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = libraryViewModel::load
                    )
                }

                if (createPlaylistOpen) {
                    PlaylistCreationDialog(
                        onClose = { createPlaylistOpen = false },
                        onConfirm = { title, description, privacy ->
                            libraryViewModel.createPlaylist(title, description, privacy)
                            createPlaylistOpen = false
                        })
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    library: Library,
    playlists: List<PlaylistInfo>,
    selectedTab: LibraryTab,
    onOpenPlayer: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    when (selectedTab) {
        LibraryTab.Playlists -> PlaylistsTab(
            playlists = playlists,
            onPlaylistPressed = onPlaylistPressed,
            onCreatePlaylist = onCreatePlaylist,
        )

        LibraryTab.LikedSongs -> LikedSongsTab(
            songs = library.likedSongs,
            onOpenPlayer = onOpenPlayer,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
        )

        LibraryTab.Albums -> SavedAlbumsTab(
            albums = library.savedAlbums,
            onOpenAlbum = onOpenAlbum,
        )

        LibraryTab.Artists -> FollowedArtistsTab(
            artists = library.followedArtists,
            onOpenArtist = onOpenArtist,
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistInfo>,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
        ),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.End) {
                MaterialUButton(
                    onClick = onCreatePlaylist,
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    text = stringResource(R.string.create_playlist)
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
            }
        ) { _, playlist ->
            PlaylistCard(
                playlistInfo = playlist,
                onClicked = { onPlaylistPressed(playlist) }
            )
        }
    }
}

@Composable
private fun LikedSongsTab(
    songs: List<Song>,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    val context = LocalContext.current

    if (songs.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_liked_songs))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
        ),
    ) {
        item(key = "play-shuffle") {
            PlayShuffleRow(
                onPlay = {
                    onOpenPlayer()
                    PlayerManager.playQueue(songs.map { it.mediaItem })
                },
                onShuffle = {
                    onOpenPlayer()
                    PlayerManager.playQueue(songs.shuffled().map { it.mediaItem })
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        items(items = songs, key = { it.youtubeId }) { song ->
            SongListItem(
                song = song,
                onPress = {
                    // Queue the whole liked-songs list, starting at the tapped one.
                    onOpenPlayer()
                    PlayerManager.playQueue(
                        songs.map { it.mediaItem },
                        songs.indexOf(song)
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
            )
        }
    }
}

@Composable
private fun SavedAlbumsTab(
    albums: List<SearchItem.Album>,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_saved_albums))
        return
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
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
private fun LibraryAlbumCard(
    album: SearchItem.Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SquareImage(
            uri = album.thumbnail,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.subtitle.isNotBlank()) {
            Text(
                text = album.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FollowedArtistsTab(
    artists: List<SearchItem.Artist>,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.no_followed_artists))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        items(items = artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = { Text(artist.name) },
                leadingContent = {
                    SquareImage(
                        uri = artist.thumbnail,
                        modifier = Modifier.size(64.dp),
                        cornerRadius = 32.dp,
                    )
                },
                modifier = Modifier.clickable { onOpenArtist(artist.id, artist.name) },
            )
        }
    }
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
