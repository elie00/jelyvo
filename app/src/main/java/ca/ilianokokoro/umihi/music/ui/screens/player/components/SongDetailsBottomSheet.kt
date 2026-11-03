package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.song.SongThumbnail
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetActionRow
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetHeader

@Composable
fun SongDetailsBottomSheet(
    song: Song,
    audioInfo: PlaybackAudioInfo,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.about_this_song),
    ) {
        SongDetailsContent(
            song = song,
            audioInfo = audioInfo,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
        )
    }
}

@Composable
internal fun SongDetailsContent(
    song: Song,
    audioInfo: PlaybackAudioInfo,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    val resources = LocalResources.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
    ) {
        SheetHeader(
            title = song.title,
            subtitle = null,
            artwork = { SongThumbnail(song = song, size = 52.dp) },
            modifier = Modifier.padding(bottom = 6.dp),
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
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    SheetActionRow(
        icon = icon,
        label = value,
        description = label,
        onClick = onClick,
    )
}
