package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.core.managers.LikeManager
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.normalizedForSearch
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem
import kotlinx.coroutines.launch

/** Fraction of the row width the finger must cross before a swipe commits to its action. */
private const val SWIPE_TRIGGER_FRACTION = 0.33f

/** Same width as [SongThumbnail]'s default so numbered and thumbnailed rows stay aligned. */
private val TRACK_NUMBER_SLOT_SIZE = 60.dp

/**
 * A track row. [trackNumber] and [hideArtist] switch the row to its "album page" look: the
 * thumbnail becomes the track number (every row would otherwise repeat the same cover) and the
 * artist drops out of the subtitle when it merely repeats the album artist.
 */
@Composable
fun SongListItem(
    song: Song,
    onPress: () -> Unit,
    playNext: () -> Unit,
    addToQueue: () -> Unit,
    startRadio: () -> Unit,
    modifier: Modifier = Modifier,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    download: (() -> Unit)? = null,
    trackNumber: Int? = null,
    hideArtist: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(song.youtubeId) {
        PlayerManager.prewarmStream(context, song)
    }

    // Observed per row rather than threaded down from each screen: only the visible rows are
    // composed, and this way every list using SongListItem marks the playing track for free.
    val controller by PlayerManager.controllerState.collectAsStateWithLifecycle()
    val currentMediaId = ComposeHelper.rememberCurrentMediaId(controller)
    val isPlaying = ComposeHelper.rememberIsPlaying(controller)
    val isCurrentSong = currentMediaId != null && currentMediaId == song.youtubeId

    val containerColor by animateColorAsState(
        targetValue = if (isCurrentSong) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "songItemContainer",
    )
    val isLoggedIn by remember(context) { LikeManager.isLoggedIn(context) }
        .collectAsStateWithLifecycle(initialValue = false)

    val haptics = LocalHapticFeedback.current
    val swipeScope = rememberCoroutineScope()
    val swipeState = rememberSwipeToDismissBoxState(
        SwipeToDismissBoxValue.Settled,
        { distance: Float -> distance * SWIPE_TRIGGER_FRACTION },
    )
    val isSwiping = swipeState.dismissDirection != SwipeToDismissBoxValue.Settled

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        enableDismissFromStartToEnd = isLoggedIn,
        backgroundContent = {
            SwipeActionBackground(direction = swipeState.dismissDirection)
        },
        onDismiss = { direction ->
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            when (songSwipeAction(direction, isLoggedIn)) {
                SongSwipeAction.AddToLiked -> LikeManager.addToLiked(song, context)
                SongSwipeAction.AddToQueue -> addToQueue()
                null -> Unit
            }
            swipeScope.launch { swipeState.reset() }
        },
    ) {
        ListItem(
            modifier = Modifier
                .background(
                    if (isSwiping) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    }
                )
                .combinedClickable(onClick = onPress, onLongClick = { expanded = true }),
        leadingContent = {
            if (trackNumber != null) {
                // On an album page the number replaces the cover, which every row would repeat.
                // The playing track yields the number to the indicator, as YT Music does.
                Box(
                    modifier = Modifier.size(TRACK_NUMBER_SLOT_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCurrentSong) {
                        NowPlayingIndicator(isPlaying = isPlaying)
                    } else {
                        Text(
                            text = trackNumber.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                SongThumbnail(song = song)
            }
        },
        trailingContent = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more)
                )
            }
            MaterialUDropdown(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MaterialUDropdownItem(
                    leadingIcon = Icons.Rounded.PlayCircleOutline,
                    text = stringResource(R.string.play_next),
                    onClick = {
                        playNext()
                        expanded = false
                    }
                )
                MaterialUDropdownItem(
                    leadingIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    text = stringResource(R.string.add_to_queue),
                    onClick = {
                        addToQueue()
                        expanded = false
                    }
                )
                MaterialUDropdownItem(
                    leadingIcon = Icons.Rounded.Radio,
                    text = stringResource(R.string.start_radio),
                    onClick = {
                        startRadio()
                        expanded = false
                    }
                )
                if (!song.isLocal) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.NotInterested,
                        text = stringResource(R.string.not_interested),
                        onClick = {
                            NotInterestedStore.hide(song, context)
                            expanded = false
                        }
                    )
                }
                if (isLoggedIn) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.FavoriteBorder,
                        text = stringResource(R.string.add_to_liked),
                        onClick = {
                            LikeManager.addToLiked(song, context)
                            expanded = false
                        }
                    )
                }
                if (onViewArtist != null && song.artistId != null) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Person,
                        text = stringResource(R.string.view_artist),
                        onClick = {
                            onViewArtist()
                            expanded = false
                        }
                    )
                }
                if (onViewAlbum != null && song.albumId != null) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Album,
                        text = stringResource(R.string.view_album),
                        onClick = {
                            onViewAlbum()
                            expanded = false
                        }
                    )
                }
                if (download != null && !song.downloaded) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Download,
                        text = stringResource(R.string.download),
                        onClick = {
                            download()
                            expanded = false
                        }
                    )
                }
            }
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (song.downloaded) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = Icons.Rounded.DownloadForOffline,
                        contentDescription = null
                    )
                }

                Text(
                    text = songSubtitle(song, hideArtist = hideArtist),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Numbered rows already show the indicator in place of the number.
                if (isCurrentSong && trackNumber == null) {
                    NowPlayingIndicator(isPlaying = isPlaying)
                }
                Text(
                    text = song.title,
                    color = if (isCurrentSong) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Unspecified
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee()
                )
                if (song.isExplicit) {
                    ExplicitBadge()
                }
            }
        },
        )
    }
}

/**
 * Subtitle line of a track row: "Artist • 3:45" for songs, "Artist • 1.2M views • 3:45" for video
 * rows, "Artist • 45M plays • 3:45" on an artist page. Blank/absent parts are skipped so the
 * separators never dangle. [hideArtist] drops the leading artist on an album page, where it would
 * just repeat the album artist.
 */
@Composable
internal fun songSubtitle(song: Song, hideArtist: Boolean = false): String {
    val separator = stringResource(R.string.dot)
    return listOfNotNull(
        song.artist.takeIf { !hideArtist }?.takeIf(String::isNotBlank),
        song.viewCount.takeIf { song.isVideo }?.takeIf(String::isNotBlank),
        song.playCount?.takeIf(String::isNotBlank),
        song.duration.takeIf(String::isNotBlank),
    ).joinToString(" $separator ")
}

/**
 * Whether a track's artist merely repeats the one already shown in the album header, in which case
 * the row hides it. Comparison is trimmed, case- and accent-insensitive; a featuring credit
 * ("Artist & Guest") differs and stays visible. A blank album artist hides nothing.
 */
internal fun isTrackArtistRedundant(trackArtist: String, albumArtist: String): Boolean {
    if (albumArtist.isBlank()) return false

    return trackArtist.trim().normalizedForSearch() == albumArtist.trim().normalizedForSearch()
}

/** Which action a settled swipe on a song row runs. */
internal enum class SongSwipeAction { AddToLiked, AddToQueue }

/**
 * Maps a settled swipe to its action using logical directions, so the gesture remains correct in
 * RTL layouts. Liking is unavailable while signed out, matching the overflow menu.
 */
internal fun songSwipeAction(
    direction: SwipeToDismissBoxValue,
    isLoggedIn: Boolean,
): SongSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd -> SongSwipeAction.AddToLiked.takeIf { isLoggedIn }
    SwipeToDismissBoxValue.EndToStart -> SongSwipeAction.AddToQueue
    SwipeToDismissBoxValue.Settled -> null
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return

    val isLike = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isLike) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
            .padding(horizontal = 24.dp),
        contentAlignment = if (isLike) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = if (isLike) {
                Icons.Rounded.Favorite
            } else {
                Icons.AutoMirrored.Rounded.PlaylistPlay
            },
            contentDescription = null,
            tint = if (isLike) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}
