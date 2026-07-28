package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.models.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsBottomSheet(
    song: Song,
    audioInfo: PlaybackAudioInfo,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalContext.current.resources
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.about_this_song),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            DetailRow(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.top_result_artist),
                value = song.artist,
                onClick = onOpenArtist.takeIf { song.artist.isNotBlank() },
            )
            song.albumName?.takeIf(String::isNotBlank)?.let { album ->
                DetailRow(
                    icon = Icons.Rounded.Album,
                    label = stringResource(R.string.top_result_album),
                    value = album,
                    onClick = onOpenAlbum,
                )
            }
            val quality = audioInfo.getDisplayLabel(resources)
            if (quality.isNotBlank()) {
                DetailRow(
                    icon = Icons.Rounded.Audiotrack,
                    label = stringResource(R.string.audio_quality_title),
                    value = quality,
                )
            }
            DetailRow(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.source),
                value = if (song.isLocal) {
                    stringResource(R.string.on_device)
                } else {
                    stringResource(R.string.youtube_music)
                },
            )
            Text(
                text = stringResource(R.string.credits_availability_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
    )
}
