package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicVideo
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.managers.AbLoop
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song

/**
 * Overflow menu for the full-screen player. Only exposes actions that are not
 * already reachable from the player screen itself (radio, album, share, equalizer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    song: Song,
    onOpenAlbum: (Song) -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayVideoVersion: () -> Unit,
    abLoop: AbLoop?,
    onToggleAbLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { changeVisibility(false) },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )

            PlayerMenuItem(
                icon = Icons.Rounded.Radio,
                text = stringResource(R.string.start_radio),
                onClick = {
                    PlayerManager.startRadio(song, context)
                    changeVisibility(false)
                },
            )

            if (!song.isLocal) {
                PlayerMenuItem(
                    icon = Icons.Rounded.NotInterested,
                    text = stringResource(R.string.not_interested),
                    onClick = {
                        NotInterestedStore.hide(song, context)
                        changeVisibility(false)
                    },
                )
                PlayerMenuItem(
                    icon = Icons.Rounded.MusicVideo,
                    text = stringResource(R.string.play_video_version),
                    onClick = {
                        onPlayVideoVersion()
                        changeVisibility(false)
                    },
                )
            }

            if (song.albumId != null) {
                PlayerMenuItem(
                    icon = Icons.Rounded.Album,
                    text = stringResource(R.string.view_album),
                    onClick = {
                        changeVisibility(false)
                        onOpenAlbum(song)
                    },
                )
            }

            PlayerMenuItem(
                icon = Icons.Rounded.Share,
                text = stringResource(R.string.share),
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(
                                R.string.share_song_text,
                                song.title,
                                song.artist,
                                song.youtubeUrl
                            )
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                    changeVisibility(false)
                },
            )

            PlayerMenuItem(
                icon = Icons.Rounded.Repeat,
                text = when {
                    abLoop == null -> stringResource(R.string.ab_loop)
                    abLoop.pointBMs == null -> stringResource(R.string.ab_loop_a_set)
                    else -> stringResource(R.string.ab_loop_active)
                },
                tint = if (abLoop != null) MaterialTheme.colorScheme.primary else null,
                onClick = onToggleAbLoop,
            )

            PlayerMenuItem(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.equalizer_title),
                onClick = {
                    changeVisibility(false)
                    onOpenEqualizer()
                },
            )
        }
    }
}

@Composable
private fun PlayerMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    ListItem(
        headlineContent = { Text(text, color = tint ?: Color.Unspecified) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: LocalContentColor.current,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
