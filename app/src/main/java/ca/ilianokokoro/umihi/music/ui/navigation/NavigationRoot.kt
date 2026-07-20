package ca.ilianokokoro.umihi.music.ui.navigation

import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.ui.components.miniplayer.MiniPlayerWrapper
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import ca.ilianokokoro.umihi.music.core.spotify.SpotifyAuthHelper
import ca.ilianokokoro.umihi.music.core.spotify.SpotifyPkce
import ca.ilianokokoro.umihi.music.ui.screens.album.AlbumScreen
import ca.ilianokokoro.umihi.music.ui.screens.artist.ArtistScreen
import ca.ilianokokoro.umihi.music.ui.screens.auth.AuthScreen
import ca.ilianokokoro.umihi.music.ui.screens.equalizer.EqualizerScreen
import ca.ilianokokoro.umihi.music.ui.screens.home.HomeScreen
import ca.ilianokokoro.umihi.music.ui.screens.player.PlayerScreen
import ca.ilianokokoro.umihi.music.ui.screens.playlist.PlaylistScreen
import ca.ilianokokoro.umihi.music.ui.screens.search.SearchScreen
import ca.ilianokokoro.umihi.music.ui.screens.settings.SettingsScreen
import ca.ilianokokoro.umihi.music.ui.screens.stats.ListeningStatsScreen
import ca.ilianokokoro.umihi.music.ui.screens.spotifyimport.SpotifyImportScreen
import androidx.core.net.toUri


@Composable
fun NavigationRoot(modifier: Modifier = Modifier) {
    val sharedViewModel: SharedViewModel = viewModel()
    val backStack = rememberNavBackStack(HomeScreenKey)
    val app = LocalContext.current.applicationContext as Application
    val currentScreen = backStack.last()
    val screenConfig = rememberScreenUiConfig(currentScreen)

    var showFullPlayer by remember { mutableStateOf(false) }
    var bottomBarHeightPixels by remember { mutableIntStateOf(0) }
    val bottomBarHeightDp = with(LocalDensity.current) { bottomBarHeightPixels.toDp() }
    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = showFullPlayer,
        transitionSpec = {
            fadeIn(tween(Constants.Animation.NAVIGATION_DURATION)) togetherWith
                fadeOut(tween(Constants.Animation.NAVIGATION_DURATION))
        },
        label = "miniPlayerToFullPlayer",
    ) { fullPlayer ->
        val artworkModifier = with(this@SharedTransitionLayout) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(PlayerSharedElement.Artwork),
                animatedVisibilityScope = this@AnimatedContent,
            )
        }

        if (fullPlayer) {
            PlayerScreen(
                onBack = { showFullPlayer = false },
                onOpenArtist = { song ->
                    song.artistId?.let { id ->
                        showFullPlayer = false
                        backStack.add(ArtistScreenKey(id, song.artist))
                    }
                },
                artworkModifier = artworkModifier,
                modifier = Modifier.fillMaxSize(),
                application = app,
            )
        } else {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->

        val miniPlayerBottomPadding by animateDpAsState(
            targetValue = if (!screenConfig.showBottomBar) {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            } else {
                bottomBarHeightDp
            },
            animationSpec = tween(Constants.Animation.NAVIGATION_DURATION)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            NavDisplay(
                modifier = Modifier
                    .fillMaxSize(),
                backStack = backStack,
                onBack = backStack::safePop,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = {
                    (scaleIn(
                        animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                        initialScale = 0.85f
                    ) +
                            fadeIn(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION))) togetherWith
                            (scaleOut(
                                animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                                targetScale = 1.1f
                            ) +
                                    fadeOut(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION)))
                },
                popTransitionSpec = {
                    (scaleIn(
                        animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                        initialScale = 1.1f
                    ) +
                            fadeIn(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION))) togetherWith
                            (scaleOut(
                                animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                                targetScale = 0.85f
                            ) +
                                    fadeOut(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION)))
                },
                predictivePopTransitionSpec = {
                    (scaleIn(
                        animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                        initialScale = 1.1f
                    ) +
                            fadeIn(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION))) togetherWith
                            (scaleOut(
                                animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                                targetScale = 0.85f
                            ) +
                                    fadeOut(animationSpec = tween(Constants.Animation.NAVIGATION_DURATION)))
                },
                entryProvider = { key ->
                    when (key) {

                        is HomeScreenKey -> NavEntry(key) {
                            HomeScreen(
                                sharedViewModel = sharedViewModel,
                                onSettingsButtonPress = { backStack.add(SettingsScreenKey) },
                                onPlaylistPressed = { playlist ->
                                    backStack.add(PlaylistScreenKey(playlistInfo = playlist))
                                },
                                onOpenAlbum = { id, name, cover ->
                                    backStack.add(AlbumScreenKey(id, name, cover))
                                },
                                onOpenArtist = { id, name ->
                                    backStack.add(ArtistScreenKey(id, name))
                                },
                                application = app
                            )
                        }

                        is SettingsScreenKey -> NavEntry(key) {
                            SettingsScreen(
                                openAuthScreen = { backStack.add(AuthScreenKey) },
                                openEqualizerScreen = { backStack.add(EqualizerScreenKey) },
                                openSpotifyImport = { backStack.add(SpotifyImportScreenKey) },
                                openListeningStats = { backStack.add(ListeningStatsScreenKey) },
                                application = app
                            )
                        }

                        is ListeningStatsScreenKey -> NavEntry(key) {
                            ListeningStatsScreen(
                                application = app,
                                onBack = backStack::safePop,
                                onOpenPlayer = { showFullPlayer = true },
                            )
                        }

                        is EqualizerScreenKey -> NavEntry(key) {
                            EqualizerScreen(
                                onBack = backStack::safePop,
                                application = app
                            )
                        }

                        is SpotifyImportScreenKey -> NavEntry(key) {
                            val context = LocalContext.current
                            SpotifyImportScreen(
                                application = app,
                                onBack = backStack::safePop,
                                onStartAuth = {
                                    val pkce = SpotifyPkce.generate()
                                    val url = SpotifyAuthHelper.buildAuthorizationUrl(
                                        pkce.codeChallenge,
                                        "umihi_state"
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                },
                            )
                        }

                        is PlaylistScreenKey -> NavEntry(key) {
                            PlaylistScreen(
                                sharedViewModel = sharedViewModel,
                                playlistInfo = key.playlistInfo,
                                onBack = backStack::safePop,
                                onOpenPlayer = { showFullPlayer = true },
                                onOpenArtist = { song -> song.artistId?.let { id -> backStack.add(ArtistScreenKey(id, song.artist)) } },
                                onOpenAlbum = { id, name, cover -> backStack.add(AlbumScreenKey(id, name, cover)) },
                                application = app
                            )
                        }

                        is AuthScreenKey -> NavEntry(key) {
                            AuthScreen(
                                onBack = backStack::safePop,
                                sharedViewModel = sharedViewModel,
                                application = app
                            )
                        }

                        is SearchScreenKey -> NavEntry(key) {
                            SearchScreen(
                                application = app,
                                onOpenArtist = { song -> song.artistId?.let { id -> backStack.add(ArtistScreenKey(id, song.artist)) } },
                                onOpenAlbum = { id, name, cover -> backStack.add(AlbumScreenKey(id, name, cover)) },
                            )
                        }

                        is ArtistScreenKey -> NavEntry(key) {
                            ArtistScreen(
                                artistId = key.artistId,
                                artistName = key.artistName,
                                onBack = backStack::safePop,
                                onOpenPlayer = { showFullPlayer = true },
                                onOpenAlbum = { id, name, cover ->
                                    backStack.add(AlbumScreenKey(id, name, cover))
                                },
                                application = app
                            )
                        }

                        is AlbumScreenKey -> NavEntry(key) {
                            AlbumScreen(
                                albumId = key.albumId,
                                albumName = key.albumName,
                                albumCover = key.albumCover,
                                onBack = backStack::safePop,
                                onOpenPlayer = { showFullPlayer = true },
                                application = app
                            )
                        }

                        else -> throw RuntimeException(
                            app.getString(
                                R.string.invalid_navkey,
                                key
                            )
                        )
                    }
                }
            )

            MiniPlayerWrapper(
                showMiniPlayer = screenConfig.showMiniPlayer,
                onMiniPlayerPressed = { showFullPlayer = true },
                artworkModifier = artworkModifier,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = miniPlayerBottomPadding)
            )


            AnimatedVisibility(
                visible = screenConfig.showBottomBar,
                enter = slideInVertically(
                    animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    animationSpec = tween(Constants.Animation.NAVIGATION_DURATION),
                    targetOffsetY = { it }
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomNavigationBar(
                    currentTab = screenConfig.selectedTab,
                    onTabSelected = { key ->
                        if (backStack.last() != key) {
                            backStack.add(key)
                        }
                    },
                    modifier = Modifier.onSizeChanged { bottomBarHeightPixels = it.height }
                )
            }
        }

    }
        }
    }
    }
}

private enum class PlayerSharedElement { Artwork }


fun NavBackStack<NavKey>.safePop() {
    if (this.size > 1) {
        this.removeLastOrNull()
    } else {
        printe("Backstack Pop was called unsafely")
    }
}
