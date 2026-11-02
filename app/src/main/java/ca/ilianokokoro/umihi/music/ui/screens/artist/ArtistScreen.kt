package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.QueueSourceType
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.BrowsingContextPane
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.Shelf
import ca.ilianokokoro.umihi.music.ui.components.ShelfCard
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import ca.ilianokokoro.umihi.music.ui.components.localizedApiSectionTitle
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.components.song.SongSelectionActionBar
import ca.ilianokokoro.umihi.music.ui.components.song.updateSongSelection
import ca.ilianokokoro.umihi.music.ui.navigation.supportsBrowsingContextPane
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

/** How far the page scrolls before the bar's glass and title are fully in. */
private val ARTIST_TOP_BAR_COLLAPSE_DISTANCE = 240.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onOpenReleases: (title: String, browseId: String, params: String) -> Unit = { _, _, _ -> },
    application: Application,
    artistViewModel: ArtistViewModel = viewModel(
        factory = ArtistViewModel.Factory(application, artistId, artistName)
    ),
) {
    val state = artistViewModel.uiState.collectAsStateWithLifecycle().value
    val isSubscribed by artistViewModel.isSubscribed.collectAsStateWithLifecycle()
    val subscriptionChannelId by artistViewModel.subscriptionChannelId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseDistancePx = remember(density) {
        with(density) { ARTIST_TOP_BAR_COLLAPSE_DISTANCE.toPx() }
    }
    val collapseProgress by remember(collapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val hazeState = remember { HazeState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            // Always there, so going back never depends on the hero still being on screen.
            TopAppBar(
                modifier = Modifier.frosted(hazeState, visibility = collapseProgress),
                colors = frostedTopAppBarColors(),
                title = {
                    Text(
                        text = (state as? ArtistState.Success)?.page?.artist?.name?.ifBlank { artistName }
                            ?: artistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alpha(collapseProgress),
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            when (state) {
                is ArtistState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        MusicSkeleton(MusicSkeletonLayout.SongList)
                    }
                }

                is ArtistState.Error -> {
                    Box(modifier = Modifier.padding(paddingValues)) {
                        ErrorMessage(ex = state.exception, onRetry = artistViewModel::load)
                    }
                }

                is ArtistState.Success -> {
                    val page = state.page
                    val showContextPane = supportsBrowsingContextPane(
                        availableWidthDp = maxWidth.value.roundToInt(),
                        availableHeightDp = maxHeight.value.roundToInt(),
                    )
                    var showAllSongs by rememberSaveable(page.artist.channelUrl) {
                        mutableStateOf(false)
                    }
                    var bioExpanded by rememberSaveable(page.artist.channelUrl) {
                        mutableStateOf(false)
                    }
                    var selectedSongIds by remember(page.artist.channelUrl) {
                        mutableStateOf(emptySet<String>())
                    }
                    var selectionAnchorId by remember(page.artist.channelUrl) {
                        mutableStateOf<String?>(null)
                    }
                    // The top shelf is the artist's most-streamed tracks, in the API's ranking
                    // and carrying play counts. "Show all" swaps in the full discography, which
                    // is ordered differently and has no counts — so it is a choice, not a
                    // silent upgrade.
                    val hasMore = page.songs.size > page.topSongs.size
                    val ranked = !showAllSongs && page.topSongs.isNotEmpty()
                    val shownSongs = if (showAllSongs || page.topSongs.isEmpty()) {
                        page.songs
                    } else {
                        page.topSongs
                    }
                    val selectedSongs = shownSongs.filter {
                        it.youtubeId in selectedSongIds
                    }
                    val playAll = {
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            page.songs.map { it.mediaItem },
                            sourceName = page.artist.name,
                            sourceId = artistId,
                            sourceType = QueueSourceType.Artist,
                        )
                    }
                    val shuffleAll = {
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            page.songs.shuffled().map { it.mediaItem },
                            sourceName = page.artist.name,
                            sourceId = artistId,
                            sourceType = QueueSourceType.Artist,
                        )
                    }
                    Row(modifier = Modifier.fillMaxSize()) {
                    if (showContextPane) {
                        BrowsingContextPane(
                            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
                        ) {
                            ArtistContextHeader(
                                thumbnail = page.artist.thumbnailHref,
                                name = page.artist.name,
                                subscriberCount = page.artist.subscriberCount,
                                description = page.artist.description,
                                artworkSize = 220.dp,
                                bioExpanded = bioExpanded,
                                collapsedBioLines = 8,
                                onToggleBio = { bioExpanded = !bioExpanded },
                                hasSongs = page.songs.isNotEmpty(),
                                onPlay = playAll,
                                onShuffle = shuffleAll,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        state = listState,
                        // The hero runs under the bar and the status bar; everything else starts below them.
                        contentPadding = PaddingValues(
                            top = if (showContextPane) paddingValues.calculateTopPadding() + 8.dp else 0.dp,
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                        )
                    ) {
                        if (!showContextPane) {
                            // Spotify/YT Music-style hero: full-bleed photo with the name
                            // overlaid on a gradient, actions right below.
                            item(key = "hero", contentType = "hero") {
                                ArtistHero(
                                    thumbnail = page.artist.thumbnailHref,
                                    name = page.artist.name,
                                    subscriberCount = page.artist.subscriberCount,
                                )
                            }
                            item(key = "hero-actions", contentType = "hero-actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    subscriptionChannelId?.let {
                                        if (isSubscribed) {
                                            FilledTonalButton(onClick = artistViewModel::toggleSubscribed) {
                                                Text(stringResource(R.string.artist_following))
                                            }
                                        } else {
                                            OutlinedButton(onClick = artistViewModel::toggleSubscribed) {
                                                Text(stringResource(R.string.artist_follow))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (page.songs.isNotEmpty()) {
                                        FilledTonalIconButton(
                                            onClick = shuffleAll,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shuffle,
                                                contentDescription = stringResource(R.string.shuffle),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        FilledIconButton(
                                            onClick = playAll,
                                            modifier = Modifier.size(64.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = stringResource(R.string.play),
                                                modifier = Modifier.size(32.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // "Popular" first, like both references: it is what most visitors came for.
                        if (shownSongs.isNotEmpty()) {
                            item(key = "songs-title", contentType = "section-title") {
                                SectionTitle(
                                    text = stringResource(
                                        if (showAllSongs || page.topSongs.isEmpty()) {
                                            R.string.all_songs
                                        } else {
                                            R.string.popular
                                        }
                                    ),
                                )
                            }
                        }

                        itemsIndexed(
                            items = shownSongs,
                            key = { _, song -> song.youtubeId },
                            contentType = { _, _ -> "song" }) { index, song ->
                            SongListItem(
                                song = song,
                                rank = if (ranked) index + 1 else null,
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
                                // The artist entry would just point back to this page; the album
                                // of a popular track is the link that is actually missing here.
                                onViewAlbum = song.albumId?.let { albumId ->
                                    {
                                        onOpenAlbum(
                                            albumId,
                                            song.albumName.orEmpty(),
                                            song.thumbnailHref,
                                        )
                                    }
                                },
                                isSelected = song.youtubeId in selectedSongIds,
                                selectionActive = selectedSongIds.isNotEmpty(),
                                onSelectionClick = { modifiers ->
                                    val update = updateSongSelection(
                                        orderedIds = shownSongs.map(Song::youtubeId),
                                        selectedIds = selectedSongIds,
                                        anchorId = selectionAnchorId,
                                        clickedId = song.youtubeId,
                                        modifiers = modifiers,
                                    )
                                    selectedSongIds = update.selectedIds
                                    selectionAnchorId = update.anchorId
                                },
                            )
                        }

                        if (hasMore && page.topSongs.isNotEmpty()) {
                            item(key = "songs-toggle", contentType = "songs-toggle") {
                                TextButton(
                                    onClick = {
                                        showAllSongs = !showAllSongs
                                        selectedSongIds = emptySet()
                                        selectionAnchorId = null
                                    },
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

                        page.sections.forEachIndexed { index, section ->
                            item(key = "section-title-$index", contentType = "section-title") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SectionTitle(
                                        text = localizedApiSectionTitle(section.title),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (section.albums.isNotEmpty() &&
                                        section.moreBrowseId.isNotBlank()
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onOpenReleases(
                                                    section.title,
                                                    section.moreBrowseId,
                                                    section.moreParams,
                                                )
                                            },
                                        ) {
                                            Text(stringResource(R.string.show_all))
                                        }
                                    }
                                }
                            }
                            item(key = "section-row-$index", contentType = "section-row") {
                                Shelf(title = null) {
                                    items(items = section.albums, key = { it.browseId }) { album ->
                                        ShelfCard(
                                            title = album.title,
                                            subtitle = album.subtitle,
                                            artworkUri = album.thumbnail,
                                            onClick = {
                                                onOpenAlbum(album.browseId, album.title, album.thumbnail)
                                            }
                                        )
                                    }
                                    items(items = section.videos, key = { it.youtubeId }) { video ->
                                        ShelfCard(
                                            title = video.title,
                                            subtitle = "",
                                            artworkUri = video.thumbnailHref,
                                            onClick = {
                                                onOpenPlayer()
                                                PlayerManager.playSong(video)
                                            }
                                        )
                                    }
                                    items(
                                        items = section.artists,
                                        key = { it.channelUrl }) { related ->
                                        ShelfCard(
                                            title = related.name,
                                            subtitle = "",
                                            artworkUri = related.thumbnailHref,
                                            circular = true,
                                            onClick = {
                                                onOpenArtist(related.channelUrl, related.name)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // The biography closes the page, as Spotify's "About" card does; the
                        // context pane already shows it on wide layouts.
                        if (!showContextPane) {
                            page.artist.description?.takeIf { it.isNotBlank() }?.let { bio ->
                                item(key = "about", contentType = "about") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = PageGutter, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SectionTitle(
                                            text = stringResource(R.string.artist_about),
                                            padStart = 0.dp,
                                        )
                                        Text(
                                            text = bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { bioExpanded = !bioExpanded },
                                        )
                                    }
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

                    SongSelectionActionBar(
                        songs = selectedSongs,
                        newPlaylistName = page.artist.name,
                        onPlay = {
                            onOpenPlayer()
                            PlayerManager.playQueue(
                                selectedSongs.map { it.mediaItem },
                                sourceName = page.artist.name,
                                sourceId = artistId,
                                sourceType = QueueSourceType.Artist,
                            )
                        },
                        onAddToQueue = {
                            PlayerManager.addToQueue(selectedSongs, context)
                        },
                        onClear = {
                            selectedSongIds = emptySet()
                            selectionAnchorId = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top = paddingValues.calculateTopPadding() + 8.dp,
                                start = 12.dp,
                                end = 12.dp,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Full-width artist photo with the name overlaid on a bottom gradient, the way Spotify and
 * YT Music open an artist page. The gradient fades into the surface color so the photo blends
 * into the scrolling content below it.
 */
@Composable
internal fun ArtistHero(
    thumbnail: String,
    name: String,
    subscriberCount: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = PageGutter),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subscriberCount?.takeIf { it.isNotBlank() }?.let { count ->
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    padStart: Dp = PageGutter,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = padStart, top = 12.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.ArtistContextHeader(
    thumbnail: String,
    name: String,
    subscriberCount: String?,
    description: String?,
    artworkSize: Dp,
    bioExpanded: Boolean,
    collapsedBioLines: Int,
    onToggleBio: () -> Unit,
    hasSongs: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    SquareImage(
        uri = thumbnail,
        cornerRadius = 0.dp,
        modifier = Modifier
            .size(artworkSize)
            .clip(CircleShape),
    )
    Text(
        text = name,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
    )
    subscriberCount?.takeIf { it.isNotBlank() }?.let { count ->
        Text(
            text = count,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    description?.takeIf { it.isNotBlank() }?.let { bio ->
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (bioExpanded) Int.MAX_VALUE else collapsedBioLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleBio),
        )
    }
    if (hasSongs) {
        PlayShuffleRow(
            onPlay = onPlay,
            onShuffle = onShuffle,
        )
    }
}
