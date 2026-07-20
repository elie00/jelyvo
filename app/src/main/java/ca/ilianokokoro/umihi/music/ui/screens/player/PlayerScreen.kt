package ca.ilianokokoro.umihi.music.ui.screens.player

import android.app.Application
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.screens.player.components.LyricsPanel
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerControls
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerMenuBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.QueueBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SleepTimerBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SpeedSelectorBottomSheet
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPlayerTheme
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (Song) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    application: Application,
    playerViewModel: PlayerViewModel = viewModel(
        factory =
            PlayerViewModel.Factory(application = application)
    )
) {
    val uiState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    val playbackData = PlayerManager.audioInfo.collectAsStateWithLifecycle().value
    val orientation = LocalConfiguration.current.orientation
    val currentSong = uiState.queue.getOrNull(uiState.currentIndex)

    // Close the screen in resumed with an empty queue
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.queue.isEmpty() && currentSong == null) {
                onBack()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ArtworkPlayerTheme(
        artworkUri = currentSong?.thumbnailPath ?: currentSong?.thumbnailHref,
    ) {
    Scaffold(
        modifier = Modifier.padding(
            start = 8.dp,
            end = 8.dp,
            bottom = 10.dp
        )
    ) { paddingValues ->
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Column(
                modifier = modifier
                    .background(playerBackgroundBrush())
                    .fillMaxSize()
                    .padding(paddingValues),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                ArtworkOrLyrics(
                    currentSong = currentSong,
                    uiState = uiState,
                    onToggle = playerViewModel::toggleLyricsVisibility,
                    artworkModifier = artworkModifier,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SongInfo(
                        song = currentSong,
                        isLoggedIn = uiState.isLoggedIn,
                        isLiked = uiState.isLiked,
                        isLiking = uiState.isLiking,
                        onToggleLike = playerViewModel::toggleLike,
                        onArtistClick = { playerViewModel.openArtist(onOpenArtist) },
                        onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                    )
                    PlayerControls(
                        isPlaying = uiState.isPlaying,
                        isLoading = uiState.isLoading,
                        progress = uiState.playbackProgress,
                        onSeek = playerViewModel::seek,
                        onSeekPlayer = playerViewModel::seekPlayer,
                        onUpdateSeekBarHeldState = playerViewModel::updateSeekBarHeldState,
                        onOpenQueue = {
                            playerViewModel.setQueueVisibility(true)
                        },
                        onOpenSleepTimer = {
                            playerViewModel.setSleepTimerSheetVisibility(true)
                        },
                        onOpenSpeedSelector = {
                            playerViewModel.setSpeedSelectorVisibility(true)
                        },
                        playbackSpeed = uiState.playbackSpeed,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        audioInfo = playbackData,
                    )
                }
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Row(
                modifier = modifier
                    .background(playerBackgroundBrush())
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    ArtworkOrLyrics(
                        currentSong = currentSong,
                        uiState = uiState,
                        onToggle = playerViewModel::toggleLyricsVisibility,
                        artworkModifier = artworkModifier,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    SongInfo(
                        song = currentSong,
                        isLoggedIn = uiState.isLoggedIn,
                        isLiked = uiState.isLiked,
                        isLiking = uiState.isLiking,
                        onToggleLike = playerViewModel::toggleLike,
                        onArtistClick = { playerViewModel.openArtist(onOpenArtist) },
                        onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                    )

                    PlayerControls(
                        isPlaying = uiState.isPlaying,
                        isLoading = uiState.isLoading,
                        progress = uiState.playbackProgress,
                        onSeek = playerViewModel::seek,
                        onSeekPlayer = playerViewModel::seekPlayer,
                        onUpdateSeekBarHeldState = playerViewModel::updateSeekBarHeldState,
                        onOpenQueue = {
                            playerViewModel.setQueueVisibility(true)
                        },
                        onOpenSleepTimer = {
                            playerViewModel.setSleepTimerSheetVisibility(true)
                        },
                        onOpenSpeedSelector = {
                            playerViewModel.setSpeedSelectorVisibility(true)
                        },
                        playbackSpeed = uiState.playbackSpeed,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        audioInfo = playbackData,
                    )
                }
            }

        }
    }


    if (uiState.isSpeedSelectorShown) {
        SpeedSelectorBottomSheet(
            changeVisibility = playerViewModel::setSpeedSelectorVisibility,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = playerViewModel::setPlaybackSpeed,
        )
    }

    if (uiState.isQueueModalShown) {
        QueueBottomSheet(
            changeVisibility = playerViewModel::setQueueVisibility,
            songs = uiState.queue,
            currentIndex = uiState.currentIndex
        )
    }

    if (uiState.isPlayerMenuShown && currentSong != null) {
        PlayerMenuBottomSheet(
            changeVisibility = playerViewModel::setPlayerMenuVisibility,
            song = currentSong,
            onOpenAlbum = onOpenAlbum,
            onOpenEqualizer = onOpenEqualizer,
        )
    }

    if (uiState.isSleepTimerModalShown) {
        SleepTimerBottomSheet(
            changeVisibility = playerViewModel::setSleepTimerSheetVisibility,
            activeRemainingSeconds = uiState.sleepTimerRemainingSeconds,
            onStartTimer = playerViewModel::startSleepTimer,
            onStartEndOfSong = playerViewModel::startSleepTimerEndOfSong,
            onCancelTimer = playerViewModel::cancelSleepTimer,
        )
    }
    }
}

/**
 * Soft vertical gradient from the artwork-derived container tones down to the player surface,
 * keeping text contrast intact since only surface-level tones are blended.
 */
@Composable
private fun playerBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        0f to lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.35f),
        1f to colorScheme.surfaceContainerLow,
    )
}

@Composable
private fun ArtworkOrLyrics(
    currentSong: Song?,
    uiState: PlayerState,
    onToggle: () -> Unit,
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(onClick = onToggle)) {
        if (uiState.areLyricsShown) {
            LyricsPanel(
                lyricsState = uiState.lyrics,
                currentPositionMs = uiState.playbackProgress.position.toLong(),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Thumbnail(
                href = (currentSong?.thumbnailPath ?: currentSong?.thumbnailHref).toString(),
                artworkModifier = artworkModifier,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun Thumbnail(
    href: String,
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        val size = minOf(maxWidth, maxHeight)

        AnimatedContent(
            targetState = href,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY)
                ).togetherWith(
                    fadeOut(
                        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY)
                    )
                )
            }
        ) { targetState ->
            SquareImage(
                uri = targetState,
                modifier = artworkModifier.size(size)
            )
        }
    }
}


@Composable
fun SongInfo(
    song: Song?,
    isLoggedIn: Boolean = false,
    isLiked: Boolean = false,
    isLiking: Boolean = false,
    onToggleLike: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = song?.title ?: "",
                style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = nunitoFontFamily),
                modifier = Modifier.basicMarquee()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = song != null, onClick = onArtistClick)
            ) {
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee()
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_artist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            if (isLoggedIn) {
                FilledIconToggleButton(
                    checked = isLiked,
                    onCheckedChange = {
                        if (isLiking) {
                            return@FilledIconToggleButton
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onToggleLike()
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = IconButtonDefaults.filledIconToggleButtonColors().checkedContainerColor,
                        checkedContentColor = IconButtonDefaults.filledIconToggleButtonColors().checkedContentColor,
                    ),
                ) {
                    Icon(
                        imageVector = if (isLiked) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (isLiked) {
                            stringResource(R.string.unlike)
                        } else {
                            stringResource(R.string.like)
                        }
                    )
                }
            }

            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more)
                )
            }
        }
    }
}
