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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem

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
                                    uri = albumCover.ifBlank {
                                        page.songs.firstOrNull()?.thumbnailHref ?: ""
                                    },
                                    modifier = Modifier.size(180.dp)
                                )
                                Text(
                                    text = page.name,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        items(items = page.songs, key = { it.youtubeId }) { rawSong ->
                            // Album tracks share the album cover, so fill in blank thumbnails.
                            val song = if (rawSong.thumbnailHref.isBlank() && albumCover.isNotBlank()) {
                                rawSong.copy(thumbnailHref = albumCover)
                            } else {
                                rawSong
                            }
                            SongListItem(
                                song = song,
                                onPress = {
                                    onOpenPlayer()
                                    PlayerManager.playSong(song)
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
