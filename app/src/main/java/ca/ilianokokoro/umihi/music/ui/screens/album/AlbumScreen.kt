package ca.ilianokokoro.umihi.music.ui.screens.album

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButton
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButtonAction
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily

@Composable
fun AlbumScreen(
    albumId: String,
    albumName: String,
    albumCover: String = "",
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    application: Application,
    albumViewModel: AlbumViewModel = viewModel(
        factory = AlbumViewModel.Factory(application, albumId, albumName)
    ),
) {
    val state = albumViewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is AlbumState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingAnimation()
                    }
                }

                is AlbumState.Error -> {
                    ErrorMessage(ex = state.exception, onRetry = albumViewModel::load)
                }

                is AlbumState.Success -> {
                    val page = state.page
                    // Prefer the high-res cover from the browse response, fall back to the passed one.
                    val cover = page.cover.ifBlank { albumCover }
                    // Album tracks share the cover; fill it in so playback/artwork isn't blank.
                    val playableSongs = page.songs.map { song ->
                        if (song.thumbnailHref.isBlank() && cover.isNotBlank()) {
                            song.copy(thumbnailHref = cover)
                        } else {
                            song
                        }
                    }
                    val playSongs: (List<Song>, Int) -> Unit = { list, startIndex ->
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            list.map { it.mediaItem },
                            startIndex,
                            sourceName = page.name.ifBlank { albumName }
                        )
                    }
                    // "12 songs • 41 min" computed locally when the header didn't provide it.
                    val totalMinutes = playableSongs.sumOf { song ->
                        song.duration.split(":")
                            .mapNotNull { part -> part.trim().toIntOrNull() }
                            .fold(0L) { acc, part -> acc * 60 + part }
                    } / 60
                    val fallbackSecondSubtitle = if (playableSongs.isEmpty()) "" else {
                        listOfNotNull(
                            stringResource(R.string.songs, playableSongs.size),
                            stringResource(R.string.short_minutes, totalMinutes)
                                .takeIf { totalMinutes > 0 },
                        ).joinToString(" • ")
                    }
                    // YT-Music-style metadata line, e.g. "Album • 2023 • 12 songs • 41 min".
                    val metadataLine = listOf(
                        page.subtitle,
                        page.secondSubtitle.ifBlank { fallbackSecondSubtitle },
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    // Album carousels ("Other versions"…) minus the album currently shown.
                    val sections = page.sections
                        .map { section ->
                            section.copy(albums = section.albums.filter { it.browseId != albumId })
                        }
                        .filter { it.albums.isNotEmpty() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                            start = 8.dp,
                            end = 8.dp
                        )
                    ) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    BackButton(onBack = onBack)
                                }
                                SquareImage(
                                    uri = cover.ifBlank {
                                        page.songs.firstOrNull()?.thumbnailHref ?: ""
                                    },
                                    modifier = Modifier.size(180.dp)
                                )
                                Text(
                                    text = page.name,
                                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                        fontFamily = nunitoFontFamily
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                if (page.artist.isNotBlank()) {
                                    val artistId = page.artistId
                                    Text(
                                        text = page.artist,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = if (artistId != null) {
                                            Modifier.clickable {
                                                onOpenArtist(artistId, page.artist)
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                                if (metadataLine.isNotBlank()) {
                                    Text(
                                        text = metadataLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (playableSongs.isNotEmpty()) {
                                    PlaySplitButton(
                                        onPlay = { playSongs(playableSongs, 0) },
                                        menuActions = listOf(
                                            PlaySplitButtonAction(
                                                label = stringResource(R.string.shuffle),
                                                icon = Icons.Rounded.Shuffle,
                                                onClick = { playSongs(playableSongs.shuffled(), 0) },
                                            ),
                                            PlaySplitButtonAction(
                                                label = stringResource(R.string.start_radio),
                                                icon = Icons.Rounded.Radio,
                                                onClick = {
                                                    onOpenPlayer()
                                                    PlayerManager.startRadio(playableSongs.first(), context)
                                                },
                                            ),
                                            PlaySplitButtonAction(
                                                label = stringResource(R.string.download),
                                                icon = Icons.Rounded.Download,
                                                onClick = {
                                                    albumViewModel.downloadAlbum(playableSongs, cover)
                                                },
                                            ),
                                        ),
                                    )
                                }
                            }
                        }

                        items(items = playableSongs, key = { it.youtubeId }) { song ->
                            SongListItem(
                                song = song,
                                onPress = {
                                    // Queue the whole album, starting at the tapped track.
                                    playSongs(playableSongs, playableSongs.indexOf(song))
                                },
                                playNext = { PlayerManager.addNext(song, context) },
                                addToQueue = { PlayerManager.addToQueue(song, context) },
                                startRadio = { PlayerManager.startRadio(song, context) },
                            )
                        }

                        if (page.songs.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.no_results),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        if (page.description.isNotBlank()) {
                            item(key = "album-description") {
                                var expanded by remember { mutableStateOf(false) }
                                Text(
                                    text = page.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                        .animateContentSize()
                                )
                            }
                        }

                        sections.forEachIndexed { index, section ->
                            item(key = "album-section-title-$index") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                            item(key = "album-section-row-$index") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 4.dp
                                    )
                                ) {
                                    items(
                                        items = section.albums,
                                        key = { it.browseId }) { album ->
                                        AlbumVersionCard(
                                            thumbnail = album.thumbnail,
                                            title = album.title,
                                            subtitle = album.subtitle,
                                            onClick = {
                                                onOpenAlbum(
                                                    album.browseId,
                                                    album.title,
                                                    album.thumbnail
                                                )
                                            }
                                        )
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

@Composable
private fun AlbumVersionCard(
    thumbnail: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SquareImage(
            uri = thumbnail,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
