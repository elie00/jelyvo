package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    application: Application,
    artistViewModel: ArtistViewModel = viewModel(
        factory = ArtistViewModel.Factory(application, artistId, artistName)
    ),
) {
    val state = artistViewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is ArtistState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingAnimation()
                    }
                }

                is ArtistState.Error -> {
                    ErrorMessage(ex = state.exception, onRetry = artistViewModel::load)
                }

                is ArtistState.Success -> {
                    val page = state.page
                    var showAllSongs by rememberSaveable(page.artist.channelUrl) {
                        mutableStateOf(false)
                    }
                    var bioExpanded by rememberSaveable(page.artist.channelUrl) {
                        mutableStateOf(false)
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
                                    uri = page.artist.thumbnailHref,
                                    cornerRadius = 0.dp,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(MaterialShapes.Cookie12Sided.toShape())
                                )
                                Text(
                                    text = page.artist.name,
                                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                        fontFamily = nunitoFontFamily
                                    )
                                )
                                page.artist.subscriberCount?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                page.artist.description?.takeIf { it.isNotBlank() }?.let { bio ->
                                    // Bios run to several paragraphs; collapsed by default so they
                                    // never push the music below the fold.
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (bioExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .clickable { bioExpanded = !bioExpanded }
                                    )
                                }
                                if (page.songs.isNotEmpty()) {
                                    PlayShuffleRow(
                                        onPlay = {
                                            onOpenPlayer()
                                            PlayerManager.playQueue(
                                                page.songs.map { it.mediaItem },
                                                sourceName = page.artist.name
                                            )
                                        },
                                        onShuffle = {
                                            onOpenPlayer()
                                            PlayerManager.playQueue(
                                                page.songs.shuffled().map { it.mediaItem },
                                                sourceName = page.artist.name
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        page.sections.forEachIndexed { index, section ->
                            item(key = "section-title-$index", contentType = "section-title") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            item(key = "section-row-$index", contentType = "section-row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    items(items = section.albums, key = { it.browseId }) { album ->
                                        AlbumCard(
                                            thumbnail = album.thumbnail,
                                            title = album.title,
                                            onClick = {
                                                onOpenAlbum(album.browseId, album.title, album.thumbnail)
                                            }
                                        )
                                    }
                                    items(items = section.videos, key = { it.youtubeId }) { video ->
                                        AlbumCard(
                                            thumbnail = video.thumbnailHref,
                                            title = video.title,
                                            onClick = {
                                                onOpenPlayer()
                                                PlayerManager.playSong(video)
                                            }
                                        )
                                    }
                                    items(
                                        items = section.artists,
                                        key = { it.channelUrl }) { related ->
                                        RelatedArtistCard(
                                            thumbnail = related.thumbnailHref,
                                            name = related.name,
                                            onClick = {
                                                onOpenArtist(related.channelUrl, related.name)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // The top shelf is the artist's most-streamed tracks, in the API's ranking
                        // and carrying play counts. "Show all" swaps in the full discography, which
                        // is ordered differently and has no counts — so it is a choice, not a
                        // silent upgrade.
                        val hasMore = page.songs.size > page.topSongs.size
                        val shownSongs = if (showAllSongs || page.topSongs.isEmpty()) {
                            page.songs
                        } else {
                            page.topSongs
                        }

                        if (shownSongs.isNotEmpty()) {
                            item(key = "songs-title", contentType = "section-title") {
                                Text(
                                    text = stringResource(
                                        if (showAllSongs || page.topSongs.isEmpty()) {
                                            R.string.all_songs
                                        } else {
                                            R.string.popular
                                        }
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                        }

                        items(
                            items = shownSongs,
                            key = { it.youtubeId },
                            contentType = { "song" }) { song ->
                            SongListItem(
                                song = song,
                                onPress = {
                                    // Queue what is on screen, starting at the tapped one.
                                    onOpenPlayer()
                                    PlayerManager.playQueue(
                                        shownSongs.map { it.mediaItem },
                                        shownSongs.indexOf(song),
                                        sourceName = page.artist.name
                                    )
                                },
                                playNext = { PlayerManager.addNext(song, context) },
                                addToQueue = { PlayerManager.addToQueue(song, context) },
                                startRadio = { PlayerManager.startRadio(song, context) },
                            )
                        }

                        if (hasMore && page.topSongs.isNotEmpty()) {
                            item(key = "songs-toggle", contentType = "songs-toggle") {
                                TextButton(
                                    onClick = { showAllSongs = !showAllSongs },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            if (showAllSongs) {
                                                R.string.show_less
                                            } else {
                                                R.string.show_all
                                            }
                                        )
                                    )
                                }
                            }
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

/** A "Fans might also like" card: circular artwork, the way both YT Music and Spotify show people. */
@Composable
private fun RelatedArtistCard(
    thumbnail: String,
    name: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SquareImage(
            uri = thumbnail,
            cornerRadius = 0.dp,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumCard(
    thumbnail: String,
    title: String,
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
    }
}
