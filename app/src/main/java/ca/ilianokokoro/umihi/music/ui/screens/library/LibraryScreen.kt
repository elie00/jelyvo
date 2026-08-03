package ca.ilianokokoro.umihi.music.ui.screens.library

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import ca.ilianokokoro.umihi.music.extensions.containsNormalized
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.dialog.PlaylistCreationDialog
import ca.ilianokokoro.umihi.music.ui.components.materialu.MaterialUButton
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistCard
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.DockScrollToTopEffect
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar

private enum class LibraryTab(@StringRes val labelRes: Int) {
    Playlists(R.string.playlists),
    LikedSongs(R.string.liked_songs),
    Albums(R.string.saved_albums),
    Artists(R.string.followed_artists),
    OnDevice(R.string.on_device),
}

/**
 * Sections that need no YouTube account. Shown as the only available chips while logged out so
 * on-device playback and local playlists stay reachable without signing in.
 */
private val LOCAL_LIBRARY_TABS = listOf(LibraryTab.Playlists, LibraryTab.OnDevice)

/** How many songs a stacked section previews before its "show all" button. */
private const val SECTION_SONG_PREVIEW_COUNT = 5

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
    onLocalPlaylistPressed: (id: String) -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    application: Application,
    libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(application = application)
    ),
) {
    val uiState = libraryViewModel.uiState.collectAsStateWithLifecycle().value

    // -1 means no filter chip selected: every section is shown stacked in one list.
    var selectedTabOrdinal by rememberSaveable { mutableIntStateOf(-1) }
    val selectedTab = LibraryTab.entries.getOrNull(selectedTabOrdinal)
    var createPlaylistOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { libraryViewModel.importPlaylist(it) }
    }

    uiState.importMessage?.let { messageRes ->
        LaunchedEffect(messageRes) {
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
            libraryViewModel.consumeImportMessage()
        }
    }
    uiState.playlistDropMessage?.let { messageRes ->
        LaunchedEffect(messageRes) {
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
            libraryViewModel.consumePlaylistDropMessage()
        }
    }

    val deletedPlaylistIds by sharedViewModel.deletedPlaylistIds.collectAsStateWithLifecycle()
    val playlistRefreshNeeded by sharedViewModel.playlistRefreshNeeded.collectAsStateWithLifecycle()

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
                        // Spotify-style filter chips: selecting a chip replaces the current
                        // filter, tapping the X (or the chip again) clears it.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LibraryTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedTabOrdinal = if (selected) -1 else tab.ordinal
                                    },
                                    label = {
                                        Text(
                                            stringResource(tab.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.clear_filter),
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        }
                                    } else {
                                        null
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
                                localPlaylists = uiState.localPlaylists,
                                localPlaylistCovers = uiState.localPlaylistCovers,
                                localSongs = uiState.localSongs,
                                localSongsLoaded = uiState.localSongsLoaded,
                                onLoadLocalSongs = libraryViewModel::loadLocalSongs,
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTabOrdinal = it.ordinal },
                                onOpenPlayer = onOpenPlayer,
                                onPlaylistPressed = onPlaylistPressed,
                                onLocalPlaylistPressed = onLocalPlaylistPressed,
                                onSongDroppedOnLocalPlaylist =
                                    libraryViewModel::addDroppedSongToLocalPlaylist,
                                onCreatePlaylist = { createPlaylistOpen = true },
                                onImportPlaylist = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtist = onOpenArtist,
                                likedSongsSearchQuery = uiState.likedSongsSearchQuery,
                                debouncedLikedSongsSearchQuery = uiState.debouncedLikedSongsSearchQuery,
                                onLikedSongsSearchQueryChange = libraryViewModel::onLikedSongsSearchQueryChange,
                            )
                        }
                    }

                    ScreenState.LoggedOut -> {
                        // Logged out: no account content, but on-device playback and local
                        // playlists need no sign-in, so expose a reduced library restricted to
                        // the local chips, with a banner prompting sign-in for account content.
                        val localSelectedTab = selectedTab?.takeIf { it in LOCAL_LIBRARY_TABS }

                        LoggedOutBanner(onSettingsButtonPress = onSettingsButtonPress)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LOCAL_LIBRARY_TABS.forEach { tab ->
                                val selected = localSelectedTab == tab
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedTabOrdinal = if (selected) -1 else tab.ordinal
                                    },
                                    label = {
                                        Text(
                                            stringResource(tab.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.clear_filter),
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }

                        LibraryContent(
                            library = Library(),
                            playlists = emptyList(),
                            localPlaylists = uiState.localPlaylists,
                            localPlaylistCovers = uiState.localPlaylistCovers,
                            localSongs = uiState.localSongs,
                            localSongsLoaded = uiState.localSongsLoaded,
                            onLoadLocalSongs = libraryViewModel::loadLocalSongs,
                            selectedTab = localSelectedTab,
                            onSelectTab = { selectedTabOrdinal = it.ordinal },
                            onOpenPlayer = onOpenPlayer,
                            onPlaylistPressed = onPlaylistPressed,
                            onLocalPlaylistPressed = onLocalPlaylistPressed,
                            onSongDroppedOnLocalPlaylist =
                                libraryViewModel::addDroppedSongToLocalPlaylist,
                            // Creating an account playlist requires sign-in.
                            onCreatePlaylist = null,
                            onImportPlaylist = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist,
                            likedSongsSearchQuery = uiState.likedSongsSearchQuery,
                            debouncedLikedSongsSearchQuery = uiState.debouncedLikedSongsSearchQuery,
                            onLikedSongsSearchQueryChange = libraryViewModel::onLikedSongsSearchQueryChange,
                        )
                    }

                    ScreenState.Loading -> {
                        val hasLocalSnapshot = uiState.playlists.isNotEmpty() ||
                            uiState.localPlaylists.isNotEmpty() || uiState.localSongs.isNotEmpty()
                        if (hasLocalSnapshot) {
                            // Never cover useful on-device or persisted playlist data with a
                            // full-screen skeleton while account sections refresh.
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            LibraryContent(
                                library = Library(),
                                playlists = uiState.playlists,
                                localPlaylists = uiState.localPlaylists,
                                localPlaylistCovers = uiState.localPlaylistCovers,
                                localSongs = uiState.localSongs,
                                localSongsLoaded = uiState.localSongsLoaded,
                                onLoadLocalSongs = libraryViewModel::loadLocalSongs,
                                selectedTab = null,
                                onSelectTab = { selectedTabOrdinal = it.ordinal },
                                onOpenPlayer = onOpenPlayer,
                                onPlaylistPressed = onPlaylistPressed,
                                onLocalPlaylistPressed = onLocalPlaylistPressed,
                                onSongDroppedOnLocalPlaylist =
                                    libraryViewModel::addDroppedSongToLocalPlaylist,
                                onCreatePlaylist = { createPlaylistOpen = true },
                                onImportPlaylist = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtist = onOpenArtist,
                                likedSongsSearchQuery = uiState.likedSongsSearchQuery,
                                debouncedLikedSongsSearchQuery =
                                    uiState.debouncedLikedSongsSearchQuery,
                                onLikedSongsSearchQueryChange =
                                    libraryViewModel::onLikedSongsSearchQueryChange,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MusicSkeleton(MusicSkeletonLayout.Library)
                            }
                        }
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

/** Compact sign-in prompt shown above the reduced logged-out library. */
@Composable
private fun LoggedOutBanner(onSettingsButtonPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_logged_out_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = onSettingsButtonPress,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.log_in))
        }
    }
}

@Composable
private fun LibraryContent(
    library: Library,
    playlists: List<PlaylistInfo>,
    localPlaylists: List<LocalPlaylist>,
    localPlaylistCovers: Map<String, List<String>>,
    localSongs: List<Song>,
    localSongsLoaded: Boolean,
    onLoadLocalSongs: () -> Unit,
    selectedTab: LibraryTab?,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onLocalPlaylistPressed: (id: String) -> Unit,
    onSongDroppedOnLocalPlaylist: (playlistId: String, song: Song) -> Unit,
    onCreatePlaylist: (() -> Unit)?,
    onImportPlaylist: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    likedSongsSearchQuery: String,
    debouncedLikedSongsSearchQuery: String,
    onLikedSongsSearchQueryChange: (String) -> Unit,
) {
    when (selectedTab) {
        null -> AllSectionsContent(
            library = library,
            playlists = playlists,
            localPlaylists = localPlaylists,
            localPlaylistCovers = localPlaylistCovers,
            localSongs = localSongs,
            localSongsLoaded = localSongsLoaded,
            onLoadLocalSongs = onLoadLocalSongs,
            onSelectTab = onSelectTab,
            onOpenPlayer = onOpenPlayer,
            onPlaylistPressed = onPlaylistPressed,
            onLocalPlaylistPressed = onLocalPlaylistPressed,
            onSongDroppedOnLocalPlaylist = onSongDroppedOnLocalPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onImportPlaylist = onImportPlaylist,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
        )

        LibraryTab.Playlists -> PlaylistsTab(
            playlists = playlists,
            localPlaylists = localPlaylists,
            localPlaylistCovers = localPlaylistCovers,
            onPlaylistPressed = onPlaylistPressed,
            onLocalPlaylistPressed = onLocalPlaylistPressed,
            onSongDroppedOnLocalPlaylist = onSongDroppedOnLocalPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onImportPlaylist = onImportPlaylist,
        )

        LibraryTab.LikedSongs -> LikedSongsTab(
            songs = library.likedSongs,
            searchQuery = likedSongsSearchQuery,
            debouncedSearchQuery = debouncedLikedSongsSearchQuery,
            onSearchQueryChange = onLikedSongsSearchQueryChange,
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

        LibraryTab.OnDevice -> OnDeviceTab(
            songs = localSongs,
            songsLoaded = localSongsLoaded,
            onLoadLocalSongs = onLoadLocalSongs,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

/**
 * No chip selected: every library section stacked in a single scrollable grid, with a preview
 * of the song-based sections and a "show all" shortcut that selects the matching chip.
 */
@Composable
private fun AllSectionsContent(
    library: Library,
    playlists: List<PlaylistInfo>,
    localPlaylists: List<LocalPlaylist>,
    localPlaylistCovers: Map<String, List<String>>,
    localSongs: List<Song>,
    localSongsLoaded: Boolean,
    onLoadLocalSongs: () -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onLocalPlaylistPressed: (id: String) -> Unit,
    onSongDroppedOnLocalPlaylist: (playlistId: String, song: Song) -> Unit,
    onCreatePlaylist: (() -> Unit)?,
    onImportPlaylist: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    val context = LocalContext.current

    // Load on-device songs for the stacked view when audio access is already granted; the
    // dedicated "On device" chip owns requesting the permission.
    LaunchedEffect(localSongsLoaded) {
        if (!localSongsLoaded) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                onLoadLocalSongs()
            }
        }
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
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
        ),
    ) {
        sectionHeader(key = "header-playlists", titleRes = R.string.playlists)
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

        if (library.likedSongs.isNotEmpty()) {
            sectionHeader(key = "header-liked-songs", titleRes = R.string.liked_songs)
            items(
                items = library.likedSongs.take(SECTION_SONG_PREVIEW_COUNT),
                key = { "liked-${it.youtubeId}" },
                contentType = { "song" },
                span = { GridItemSpan(maxLineSpan) },
            ) { song ->
                LikedSongListItem(
                    song = song,
                    songs = library.likedSongs,
                    onOpenPlayer = onOpenPlayer,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
            }
            showAllItem(
                key = "show-all-liked-songs",
                itemCount = library.likedSongs.size,
                onShowAll = { onSelectTab(LibraryTab.LikedSongs) },
            )
        }

        if (library.savedAlbums.isNotEmpty()) {
            sectionHeader(key = "header-albums", titleRes = R.string.saved_albums)
            items(
                items = library.savedAlbums,
                key = { "album-${it.id}" },
                contentType = { "album" },
            ) { album ->
                LibraryAlbumCard(
                    album = album,
                    onClick = { onOpenAlbum(album.id, album.title, album.thumbnail) },
                )
            }
        }

        if (library.followedArtists.isNotEmpty()) {
            sectionHeader(key = "header-artists", titleRes = R.string.followed_artists)
            items(
                items = library.followedArtists,
                key = { "artist-${it.id}" },
                contentType = { "artist" },
                span = { GridItemSpan(maxLineSpan) },
            ) { artist ->
                ArtistListItem(artist = artist, onOpenArtist = onOpenArtist)
            }
        }

        if (localSongsLoaded && localSongs.isNotEmpty()) {
            sectionHeader(key = "header-on-device", titleRes = R.string.on_device)
            items(
                items = localSongs.take(SECTION_SONG_PREVIEW_COUNT),
                key = { "local-${it.youtubeId}" },
                contentType = { "song" },
                span = { GridItemSpan(maxLineSpan) },
            ) { song ->
                LocalSongListItem(
                    song = song,
                    songs = localSongs,
                    onOpenPlayer = onOpenPlayer,
                )
            }
            showAllItem(
                key = "show-all-on-device",
                itemCount = localSongs.size,
                onShowAll = { onSelectTab(LibraryTab.OnDevice) },
            )
        }
    }
}

private fun LazyGridScope.sectionHeader(key: String, @StringRes titleRes: Int) {
    item(key = key, contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** "Show all" shortcut shown when a stacked section previews only part of its content. */
private fun LazyGridScope.showAllItem(key: String, itemCount: Int, onShowAll: () -> Unit) {
    if (itemCount <= SECTION_SONG_PREVIEW_COUNT) {
        return
    }
    item(key = key, contentType = "show-all", span = { GridItemSpan(maxLineSpan) }) {
        TextButton(onClick = onShowAll) {
            Text(stringResource(R.string.show_all))
        }
    }
}

@Composable
private fun OnDeviceTab(
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
private fun LocalSongListItem(
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
private fun PlaylistsTab(
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
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
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
private fun LazyGridScope.playlistItems(
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
private fun LikedSongsTab(
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
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            start = 8.dp,
            end = 8.dp,
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
private fun LikedSongListItem(
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
private fun SavedAlbumsTab(
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

    val listState = rememberLazyListState()
    DockScrollToTopEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        items(items = artists, key = { it.id }) { artist ->
            ArtistListItem(artist = artist, onOpenArtist = onOpenArtist)
        }
    }
}

@Composable
private fun ArtistListItem(
    artist: SearchItem.Artist,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
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
