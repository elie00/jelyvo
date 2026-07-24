package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    currentSong: Song,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean,
    progressFraction: Float,
) {
    val controlsInteractionSources = List(2) { ComposeHelper.rememberInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.primaryContainer,
                0.30f
            )
        ),
        // Flat: the mini player sits welded on the navigation bar's surface, no shadow between.
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        LinearWavyProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth(),
            amplitude = { if (isPlaying) 1f else 0f },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = currentSong,
                contentKey = { it.youtubeId },
                transitionSpec = {
                    (
                        fadeIn(tween(180)) +
                            slideInVertically(tween(180)) { height -> height / 8 }
                        ) togetherWith fadeOut(tween(90))
                },
                label = "miniPlayerTrack",
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareImage(
                        uri = it.thumbnailPath ?: it.thumbnailHref,
                        modifier = artworkModifier.size(50.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = it.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }

            ButtonGroup(
                overflowIndicator = {},
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                customItem(
                    {
                        FilledIconToggleButton(
                            enabled = !isLoading,
                            checked = isPlaying && !isLoading,
                            onCheckedChange = {
                                if (!isLoading) {
                                    onPlayPause()
                                }
                            },
                            shapes = IconButtonDefaults.toggleableShapes()
                                .copy(checkedShape = IconButtonDefaults.shapes().shape),
                            interactionSource = controlsInteractionSources[0],
                            modifier = Modifier.animateWidth(
                                interactionSource = controlsInteractionSources[0]
                            )
                        ) {
                            AnimatedContent(
                                targetState = when {
                                    isLoading -> MiniPlayerTransportState.Loading
                                    isPlaying -> MiniPlayerTransportState.Playing
                                    else -> MiniPlayerTransportState.Paused
                                },
                                transitionSpec = {
                                    (
                                        fadeIn(tween(120)) +
                                            scaleIn(tween(120), initialScale = 0.92f)
                                        ) togetherWith fadeOut(tween(80))
                                },
                                label = "miniPlayerTransport",
                            ) { state ->
                                if (state == MiniPlayerTransportState.Loading) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                    )
                                } else {
                                    val icon = if (state == MiniPlayerTransportState.Playing) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(
                                            if (state == MiniPlayerTransportState.Playing) {
                                                R.string.pause
                                            } else {
                                                R.string.play
                                            }
                                        ),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    {}
                )

                customItem(
                    {
                        FilledIconButton(
                            onClick = onSkipNext,
                            shapes = IconButtonDefaults.shapes(),
                            interactionSource = controlsInteractionSources[1],
                            modifier = Modifier.animateWidth(
                                interactionSource = controlsInteractionSources[1]
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.next)
                            )
                        }
                    },
                    {}
                )
            }
        }
    }
}

private enum class MiniPlayerTransportState {
    Loading,
    Playing,
    Paused,
}
