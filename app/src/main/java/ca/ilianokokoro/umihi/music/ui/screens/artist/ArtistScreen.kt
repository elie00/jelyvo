package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import android.os.Build
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import ca.ilianokokoro.umihi.music.ui.components.studio.ARTIST_ACTION_GAP
import ca.ilianokokoro.umihi.music.ui.components.studio.ARTIST_SIDE_BUTTON
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleIconButton
import ca.ilianokokoro.umihi.music.ui.components.studio.PlayCircle
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import ca.ilianokokoro.umihi.music.ui.navigation.supportsBrowsingContextPane
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPageTheme
import ca.ilianokokoro.umihi.music.ui.theme.rememberArtworkPalette
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

/** The photograph's proportions, and how much of the page it may take. */
private const val ARTIST_PHOTO_RATIO = 0.95f
private const val ARTIST_PHOTO_MAX_HEIGHT = 0.6f
private val HEADER_DROP = 44.dp
private val ARTIST_HEADER_LIFT = 64.dp
private val HEADER_GUTTER = 24.dp
private val SOFT_FOOT_BLUR = 48.dp
/** Where the blurred copy starts to show and where it has fully taken over. */
private val SOFT_FOOT_MASK = Brush.verticalGradient(
    0.55f to Color.Transparent,
    0.85f to Color.Black,
)

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

    val palette = rememberArtworkPalette((state as? ArtistState.Success)?.page?.artist?.thumbnailHref)
    ArtworkPageTheme(palette, pageColor = palette.wash) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            // Always there, so going back never depends on the hero still being on screen.
            CenterAlignedTopAppBar(
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
                    val artHeight = minOf(maxWidth / ARTIST_PHOTO_RATIO, maxHeight * ARTIST_PHOTO_MAX_HEIGHT)
                    if (!showContextPane) {
                        ArtistPhotoBackground(
                            photo = page.artist.thumbnailHref,
                            wash = palette.wash,
                            artHeight = artHeight,
                            listState = listState,
                            modifier = Modifier.matchParentSize(),
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
                            // The photograph is behind the list. This only reserves its room and sets the name on it.
                            item(key = "hero", contentType = "hero") {
                                ArtistHeader(name = page.artist.name, artHeight = artHeight)
                            }
                            item(key = "hero-actions", contentType = "hero-actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        ARTIST_ACTION_GAP,
                                        Alignment.CenterHorizontally,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (page.songs.isNotEmpty()) {
                                        CircleIconButton(
                                            icon = Icons.Rounded.Shuffle,
                                            contentDescription = stringResource(R.string.shuffle),
                                            onClick = shuffleAll,
                                            size = ARTIST_SIDE_BUTTON,
                                        )
                                        PlayCircle(
                                            onClick = playAll,
                                            description = stringResource(R.string.play),
                                        )
                                    }
                                    subscriptionChannelId?.let {
                                        CircleIconButton(
                                            icon = if (isSubscribed) {
                                                Icons.Rounded.Star
                                            } else {
                                                Icons.Rounded.StarBorder
                                            },
                                            contentDescription = stringResource(
                                                if (isSubscribed) {
                                                    R.string.artist_following
                                                } else {
                                                    R.string.artist_follow
                                                }
                                            ),
                                            onClick = artistViewModel::toggleSubscribed,
                                            size = ARTIST_SIDE_BUTTON,
                                        )
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
                            val bio = page.artist.description?.takeIf { it.isNotBlank() }
                            val subscribers = page.artist.subscriberCount?.takeIf { it.isNotBlank() }
                            if (bio != null || subscribers != null) {
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
                                        subscribers?.let { StatChip(it) }
                                        bio?.let {
                                            Text(
                                            text = it,
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
    }

/**
 * Everything on an artist page that is colour rather than words: the photograph with its foot
 * dissolved into the page. It follows the scroll instead of being scrolled, so the list keeps the
 * gesture, and it is read in a placement block, so a scroll moves it without recomposing anything.
 */
@Composable
internal fun ArtistPhotoBackground(
    photo: Any?,
    wash: Color,
    artHeight: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset {
                    val top = if (listState.firstVisibleItemIndex == 0) {
                        -listState.firstVisibleItemScrollOffset
                    } else {
                        // Far enough up that no part of it comes back down.
                        -artHeight.roundToPx() * 2
                    }
                    IntOffset(0, top)
                },
        ) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // The same picture blurred over the lower half, so it loses its detail before its colour:
            // a merge rather than a fade to a flat tint. Blur needs API 31; below that the fade alone.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        // Offscreen, so the mask cuts the blurred result and not each draw under it.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(SOFT_FOOT_MASK, blendMode = BlendMode.DstIn)
                        }
                        .blur(SOFT_FOOT_BLUR, BlurredEdgeTreatment.Rectangle),
                )
            }
            // Ends on the page colour at full strength, and eased: a gradient that changes slope at a
            // stop shows a line at that stop, however close the colours either side of it are.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.65f to wash.copy(alpha = 0.30f),
                            0.82f to wash.copy(alpha = 0.72f),
                            0.94f to wash.copy(alpha = 0.95f),
                            1.00f to wash,
                        )
                    ),
            )
        }
    }
}

/** Reserves the photograph's room in the list and sets the name, centred, on its foot. */
@Composable
internal fun ArtistHeader(name: String, artHeight: Dp) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(artHeight + HEADER_DROP - ARTIST_HEADER_LIFT)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
        )
    }
}

/** A figure about the artist, in a small pill. YouTube's text already says what it counts. */
@Composable
private fun StatChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
