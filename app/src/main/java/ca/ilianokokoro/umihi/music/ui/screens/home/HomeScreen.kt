package ca.ilianokokoro.umihi.music.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.ProfileAvatar
import ca.ilianokokoro.umihi.music.ui.components.home.ForYouMixesRow
import ca.ilianokokoro.umihi.music.ui.components.home.HomeFeedSectionRow
import ca.ilianokokoro.umihi.music.ui.components.home.HomeShortcutGrid
import ca.ilianokokoro.umihi.music.ui.navigation.DockScrollToTopEffect
import ca.ilianokokoro.umihi.music.ui.components.home.RecentlyPlayedRow
import ca.ilianokokoro.umihi.music.ui.components.home.ResumeListeningRow
import java.time.LocalTime

@Composable
fun HomeScreen(
    onSettingsButtonPress: () -> Unit,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    onExplorePress: () -> Unit = {},
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
                            // Sampled once per composition of the tab; the greeting and section
                            // order only need to be right when the screen is (re)opened.
                            val currentHour = remember { LocalTime.now().hour }

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
                                        top = paddingValues.calculateTopPadding() + statusBarHeight + 6.dp,
                                        bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                                        end = 12.dp,
                                        start = 12.dp
                                    )

                                ) {
                                    item(
                                        key = "settings-header",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        HomeHeader(
                                            greeting = greetingForHour(currentHour),
                                            profileName = uiState.profileName,
                                            profileAvatarPath = uiState.profileAvatarPath,
                                            onSettingsButtonPress = onSettingsButtonPress,
                                        )
                                    }
                                    if (uiState.shortcuts.isNotEmpty()) {
                                        // Spotify-style shortcut tiles, always above the carousels.
                                        item(
                                            key = "shortcuts",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            HomeShortcutGrid(
                                                songs = uiState.shortcuts,
                                                onSongClick = { PlayerManager.playSong(it) },
                                            )
                                        }
                                    }
                                    homeCarouselOrder(currentHour).forEach { carousel ->
                                        when (carousel) {
                                            HomeCarousel.RESUME -> if (uiState.resumeItems.isNotEmpty()) {
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
                                                        },
                                                        onRemoveFromHistory = {
                                                            homeViewModel.removeFromHistory(it.song.youtubeId)
                                                        }
                                                    )
                                                }
                                            }

                                            HomeCarousel.RECENT -> if (uiState.recentlyPlayed.isNotEmpty()) {
                                                item(
                                                    key = "recently-played",
                                                    span = { GridItemSpan(maxLineSpan) },
                                                ) {
                                                    RecentlyPlayedRow(
                                                        songs = uiState.recentlyPlayed,
                                                        onSongClick = { PlayerManager.playSong(it) },
                                                        onRemoveFromHistory = {
                                                            homeViewModel.removeFromHistory(it.youtubeId)
                                                        }
                                                    )
                                                }
                                            }

                                            HomeCarousel.FOR_YOU -> if (uiState.forYouMixes.isNotEmpty()) {
                                                item(
                                                    key = "for-you-mixes",
                                                    span = { GridItemSpan(maxLineSpan) },
                                                ) {
                                                    // Resolved here so the click lambda stays plain.
                                                    val mixTitles = mapOf(
                                                        HomeMixKind.REPLAY to stringResource(R.string.home_mix_replay),
                                                        HomeMixKind.REDISCOVER to stringResource(R.string.home_mix_rediscover),
                                                    )
                                                    ForYouMixesRow(
                                                        mixes = uiState.forYouMixes,
                                                        onMixClick = { mix ->
                                                            PlayerManager.playQueue(
                                                                mix.songs.map { it.mediaItem },
                                                                sourceName = mixTitles[mix.kind],
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (newReleases.isNotEmpty()) {
                                        item(
                                            key = "new-releases",
                                            contentType = "feed-section",
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
                                            contentType = "feed-section",
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

                    ScreenState.LoggedOut -> LoggedOutHome(
                        statusBarHeight = statusBarHeight,
                        onSettingsButtonPress = onSettingsButtonPress,
                        onExplorePress = onExplorePress,
                    )

                    ScreenState.Loading -> MusicSkeleton(MusicSkeletonLayout.Home)
                    is ScreenState.Error -> ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = homeViewModel::getHomeFeed
                    )

                }
            }
        }
    }

}

@Composable
private fun HomeHeader(
    greeting: HomeGreeting,
    profileName: String,
    profileAvatarPath: String,
    onSettingsButtonPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(
            name = profileName,
            avatarPath = profileAvatarPath,
            size = 44.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            val greetingBase = stringResource(
                when (greeting) {
                    HomeGreeting.MORNING -> R.string.home_greeting_morning
                    HomeGreeting.AFTERNOON -> R.string.home_greeting_afternoon
                    HomeGreeting.EVENING -> R.string.home_greeting_evening
                }
            )
            val greetingText = if (profileName.isNotBlank()) {
                "$greetingBase $profileName"
            } else {
                greetingBase
            }
            Text(
                text = greetingText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onSettingsButtonPress) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }
}

@Composable
private fun LoggedOutHome(
    statusBarHeight: androidx.compose.ui.unit.Dp,
    onSettingsButtonPress: () -> Unit,
    onExplorePress: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusBarHeight),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 92.dp, y = (-112).dp)
                .size(280.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-96).dp, y = 100.dp)
                .size(240.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f),
        ) {}

        FilledTonalIconButton(
            onClick = onSettingsButtonPress,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 32.dp)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(34.dp),
                color = Color(0xFFFFF8ED),
                shadowElevation = 8.dp,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_jelyvo_foreground),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(6.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.log_in_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Button(
                    onClick = onSettingsButtonPress,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        text = stringResource(R.string.log_in),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }

                FilledTonalButton(
                    onClick = onExplorePress,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        text = stringResource(R.string.explore_without_account),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
        }
    }
}
