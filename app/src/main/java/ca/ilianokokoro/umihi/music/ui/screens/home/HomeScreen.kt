package ca.ilianokokoro.umihi.music.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.home.HomeFeedSectionRow
import ca.ilianokokoro.umihi.music.ui.navigation.DockScrollToTopEffect
import ca.ilianokokoro.umihi.music.ui.components.home.RecentlyPlayedRow
import ca.ilianokokoro.umihi.music.ui.components.home.ResumeListeningRow

@Composable
fun HomeScreen(
    onSettingsButtonPress: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    application: Application,
    homeViewModel: HomeViewModel = viewModel(
        factory =
            HomeViewModel.Factory(application = application)
    )

) {
    val uiState = homeViewModel.uiState.collectAsStateWithLifecycle().value

    FadingStatusBarWrapper { statusBarHeight ->
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (uiState.screenState) {
                    is ScreenState.LoggedIn -> {
                        val homeSections = uiState.screenState.homeSections
                        val newReleases = uiState.screenState.newReleases

                        if (homeSections.isEmpty() && newReleases.isEmpty() && uiState.recentlyPlayed.isEmpty() && uiState.resumeItems.isEmpty()) {
                            Text(
                                stringResource(R.string.home_empty),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            val gridState = rememberLazyGridState()
                            DockScrollToTopEffect(gridState)

                            PullToRefreshBox(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = homeViewModel::refresh
                            ) {
                                LazyVerticalGrid(
                                    modifier = Modifier.fillMaxSize(),
                                    state = gridState,
                                    columns = GridCells.Adaptive(minSize = 150.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(
                                        top = paddingValues.calculateTopPadding() + statusBarHeight + 8.dp,
                                        bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                                        end = 8.dp,
                                        start = 8.dp
                                    )

                                ) {
                                    item(
                                        key = "settings-header",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            IconButton(onClick = onSettingsButtonPress) {
                                                Icon(
                                                    Icons.Outlined.Settings,
                                                    contentDescription = stringResource(R.string.settings)
                                                )
                                            }
                                        }
                                    }
                                    if (uiState.resumeItems.isNotEmpty()) {
                                        item(
                                            key = "resume-listening",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            ResumeListeningRow(
                                                items = uiState.resumeItems,
                                                onResume = {
                                                    PlayerManager.resumeSong(
                                                        it.song,
                                                        it.positionMs,
                                                        it.durationMs,
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (uiState.recentlyPlayed.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            RecentlyPlayedRow(
                                                songs = uiState.recentlyPlayed,
                                                onSongClick = { PlayerManager.playSong(it) }
                                            )
                                        }
                                    }
                                    if (newReleases.isNotEmpty()) {
                                        item(
                                            key = "new-releases",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            HomeFeedSectionRow(
                                                section = HomeFeedSection(
                                                    title = stringResource(R.string.new_releases_from_artists),
                                                    items = newReleases,
                                                ),
                                                onPlay = PlayerManager::playSong,
                                                onOpenPlaylist = { onPlaylistPressed(it.playlist) },
                                                onOpenAlbum = {
                                                    onOpenAlbum(it.browseId, it.title, it.thumbnail)
                                                },
                                                onOpenArtist = {
                                                    onOpenArtist(it.browseId, it.name)
                                                },
                                            )
                                        }
                                    }
                                    homeSections.forEach { section ->
                                        item(
                                            key = "home-${section.title}",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            HomeFeedSectionRow(
                                                section = section,
                                                onPlay = PlayerManager::playSong,
                                                onOpenPlaylist = { onPlaylistPressed(it.playlist) },
                                                onOpenAlbum = {
                                                    onOpenAlbum(it.browseId, it.title, it.thumbnail)
                                                },
                                                onOpenArtist = {
                                                    onOpenArtist(it.browseId, it.name)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ScreenState.LoggedOut -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.log_in_message),
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = onSettingsButtonPress,
                            shapes = ButtonDefaults.shapes()
                        )
                        {
                            Text(stringResource(R.string.open_settings))
                        }
                    }

                    ScreenState.Loading -> LoadingAnimation()
                    is ScreenState.Error -> ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = homeViewModel::getHomeFeed
                    )

                }
            }
        }
    }

}
