package ca.ilianokokoro.umihi.music.ui.screens.album

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
                        PlayerManager.playQueue(list.map { it.mediaItem }, startIndex)
                    }
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
                    }
                }
            }
        }
    }
}
