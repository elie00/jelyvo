package ca.ilianokokoro.umihi.music.ui.screens.playlist

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.SongSortButton
import ca.ilianokokoro.umihi.music.ui.components.playlist.SpecialPlaylistCover
import ca.ilianokokoro.umihi.music.ui.components.playlist.specialPlaylistCover
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import ca.ilianokokoro.umihi.music.ui.screens.playlist.components.PlaylistHeader
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPageTheme
import ca.ilianokokoro.umihi.music.ui.theme.rememberArtworkPalette

import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun PlaylistScreen(
    sharedViewModel: SharedViewModel,
    playlistInfo: PlaylistInfo,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    application: Application,
    playlistViewModel: PlaylistViewModel = viewModel(
        factory =
            PlaylistViewModel.Factory(
                playlistInfo = playlistInfo,
                sharedViewModel = sharedViewModel,
                application = application
            )
    )

) {
    val uiState = playlistViewModel.uiState.collectAsStateWithLifecycle().value
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { playlistViewModel.exportPlaylist(context, it) }
    }

    LaunchedEffect(uiState.showingSearch) {
        if (uiState.showingSearch) {
            focusRequester.requestFocus()
        }
    }

    ArtworkPageTheme(rememberArtworkPalette(playlistInfo.coverPath ?: playlistInfo.coverHref)) {
    Box(Modifier.fillMaxSize()) {

        val hazeState = remember { HazeState() }
        Scaffold(topBar = {
            if (uiState.showingSearch) {
                CenterAlignedTopAppBar(
                    colors = frostedTopAppBarColors(),
                    modifier = modifier.frosted(hazeState),
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        FilledIconButton(
                            onClick = playlistViewModel::hideSearch,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    title = {
                        SearchBar(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth(),
                            value = uiState.searchQuery,
                            onValueChange = playlistViewModel::onSearchQueryChange,
                            onSearch = {
                                focusRequester.freeFocus()
                            },
                            onClear = { playlistViewModel.onSearchQueryChange("") },
                            focusManager = focusManager,
                            focusRequester = focusRequester,
                        )
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    colors = frostedTopAppBarColors(),
                    modifier = Modifier.frosted(hazeState),
                    title = {
                        val displayTitle = if (specialPlaylistCover(playlistInfo.id) == SpecialPlaylistCover.Liked) {
                            stringResource(R.string.liked_songs)
                        } else {
                            playlistInfo.title
                        }
                        Text(
                            displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        SongSortButton(
                            sort = uiState.sort,
                            onSortChange = playlistViewModel::onSortChange,
                        )
                        FilledIconButton(
                            onClick = { exportLauncher.launch("${playlistInfo.title}.json") },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Upload,
                                contentDescription = stringResource(R.string.export_playlist)
                            )
                        }
                        FilledIconButton(
                            onClick = playlistViewModel::showSearch,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }
                )
            }
        }) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
            ) {

                if (uiState.screenState is ScreenState.Error) {
                    // The success branches consume paddingValues themselves; without it here the
                    // full-size error message starts under the top bar and gets clipped.
                    ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = playlistViewModel::getPlaylistInfo,
                        modifier = Modifier.padding(paddingValues),
                    )
                } else {
                    val playlistInfo: Playlist = when (uiState.screenState) {
                        is ScreenState.Loading -> {
                            Playlist(uiState.screenState.playlistInfo)
                        }

                        is ScreenState.Success -> {
                            uiState.screenState.playlist
                        }
                    }
                    val songs = playlistInfo.songs

                    // Hoisted out of the LazyColumn body: that lambda is not @Composable, so
                    // remember() cannot live inside it.
                    val visibleSongs = remember(songs, uiState.searchQuery, uiState.sort) {
                        uiState.visibleSongs
                    }

                    if (uiState.screenState is ScreenState.Loading || songs.isEmpty()) {

                        Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

                        PlaylistHeader(
                            onOpenPlayer = onOpenPlayer,
                            isDownloading = uiState.isDownloading,
                            onDownloadPlaylist = playlistViewModel::downloadPlaylist,
                            onShufflePlaylist = playlistViewModel::shufflePlaylist,
                            onPlayPlaylist = playlistViewModel::playPlaylist,
                            onDeleteDownloadPlaylist = playlistViewModel::deleteLocalPlaylist,
                            onDeletePlaylist = { playlistViewModel.deletePlaylist(onBack) },
                            onCancelDownload = playlistViewModel::cancelDownload,
                            playlist = playlistInfo
                        )

                        if (uiState.screenState is ScreenState.Loading) {
                            MusicSkeleton(MusicSkeletonLayout.SongList)
                        } else {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = modifier
                                    .fillMaxSize()
                            ) {
                                Text(
                                    stringResource(R.string.empty_playlist),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                    } else {
                        val pullToRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = playlistViewModel::refreshPlaylistInfo,
                            state = pullToRefreshState,
                            // Drawn below the bar, which would otherwise hide it.
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    state = pullToRefreshState,
                                    isRefreshing = uiState.isRefreshing,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = paddingValues.calculateTopPadding()),
                                )
                            },
                            modifier = modifier
                                .fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = modifier.fillMaxSize().hazeSource(hazeState),
                                contentPadding = PaddingValues(bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING)
                            ) {
                                item { Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding())) }

                                item {
                                    PlaylistHeader(
                                        onOpenPlayer = onOpenPlayer,
                                        isDownloading = uiState.isDownloading,
                                        onDownloadPlaylist = playlistViewModel::downloadPlaylist,
                                        onShufflePlaylist = playlistViewModel::shufflePlaylist,
                                        onPlayPlaylist = playlistViewModel::playPlaylist,
                                        onDeleteDownloadPlaylist = playlistViewModel::deleteLocalPlaylist,
                                        onDeletePlaylist = { playlistViewModel.deletePlaylist(onBack) },
                                        onCancelDownload = playlistViewModel::cancelDownload,
                                        playlist = playlistInfo
                                    )
                                }

                                if (uiState.searchQuery.isNotBlank() && visibleSongs.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.no_results),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp)
                                        )
                                    }
                                }

                                items(
                                    items = visibleSongs,
                                    key = { song ->
                                        song.uid
                                    }
                                ) { song ->
                                    SongListItem(song, onPress = {
                                        onOpenPlayer()
                                        playlistViewModel.playPlaylist(song)
                                    }, playNext = {
                                        PlayerManager.addNext(song, application)
                                    }, addToQueue = {
                                        PlayerManager.addToQueue(
                                            song,
                                            application
                                        )
                                    }, startRadio = {
                                        PlayerManager.startRadio(song, application)
                                    }, onViewArtist = {
                                        onOpenArtist(song)
                                    }, onViewAlbum = {
                                        song.albumId?.let { id ->
                                            onOpenAlbum(id, song.albumName ?: "", song.thumbnailHref)
                                        }
                                    }, download = {
                                        playlistViewModel.downloadSong(song)
                                    }, initialLikeStatus = true.takeIf {
                                        playlistInfo.info.isLikedSongsPlaylist
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }

    }
    }
}
