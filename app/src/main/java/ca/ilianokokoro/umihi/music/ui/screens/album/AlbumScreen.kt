package ca.ilianokokoro.umihi.music.ui.screens.album

import android.app.Application
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.groupSongsByDisc
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.BrowsingContextPane
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButton
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButtonAction
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.localizedApiSectionTitle
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.components.song.SongSelectionActionBar
import ca.ilianokokoro.umihi.music.ui.components.song.isTrackArtistRedundant
import ca.ilianokokoro.umihi.music.ui.components.song.updateSongSelection
import ca.ilianokokoro.umihi.music.ui.navigation.supportsBrowsingContextPane
import ca.ilianokokoro.umihi.music.ui.screens.album.components.AlbumMenuBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.album.components.LocalPlaylistPickerDialog
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily
import kotlin.math.roundToInt

/**
 * Distance de defilement au bout de laquelle la barre superieure est totalement opaque.
 * Calee sur la hauteur de la pochette pour que le titre de la barre prenne le relais
 * juste au moment ou celui de l'en-tete disparait.
 */
private val ALBUM_TOP_BAR_COLLAPSE_DISTANCE = 200.dp

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
    val albumShareTemplate = stringResource(R.string.album_share_text)
    val isLoggedIn by albumViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isAlbumSaved by albumViewModel.isAlbumSaved.collectAsStateWithLifecycle()
    val downloadedIds by albumViewModel.downloadedIds.collectAsStateWithLifecycle()
    val localPlaylists by albumViewModel.localPlaylists.collectAsStateWithLifecycle()
    var showAlbumMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var selectedSongIds by remember(albumId) { mutableStateOf(emptySet<String>()) }
    var selectionAnchorId by remember(albumId) { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseDistancePx = remember(density) {
        with(density) { ALBUM_TOP_BAR_COLLAPSE_DISTANCE.toPx() }
    }
    // Progression 0f..1f du repliement de l'en-tete. derivedStateOf evite de recomposer
    // tout l'ecran a chaque pixel defile : seule la barre relit cette valeur.
    val collapseProgress by remember(collapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    // Le nom charge depuis la page remplace celui passe en argument des qu'il est disponible.
    val topBarTitle = (state as? AlbumState.Success)
        ?.page?.name?.ifBlank { albumName }
        ?: albumName

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            // Barre collante : le bouton retour reste toujours atteignable, le fond et le
            // titre n'apparaissent qu'au fur et a mesure du defilement.
            val containerColor = MaterialTheme.colorScheme.surfaceContainer
                .copy(alpha = collapseProgress)
            TopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(
                            fontFamily = nunitoFontFamily
                        ),
                        modifier = Modifier.alpha(collapseProgress)
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    scrolledContainerColor = containerColor,
                ),
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            when (state) {
                is AlbumState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        MusicSkeleton(MusicSkeletonLayout.SongList)
                    }
                }

                is AlbumState.Error -> {
                    Box(modifier = Modifier.padding(paddingValues)) {
                        ErrorMessage(ex = state.exception, onRetry = albumViewModel::load)
                    }
                }

                is AlbumState.Success -> {
                    val page = state.page
                    val showContextPane = supportsBrowsingContextPane(
                        availableWidthDp = maxWidth.value.roundToInt(),
                        availableHeightDp = maxHeight.value.roundToInt(),
                    )
                    // Prefer the high-res cover from the browse response, fall back to the passed one.
                    val cover = page.cover.ifBlank { albumCover }
                    // Album rows rely on their surrounding page for album identity and often for
                    // artwork. Carry both into MediaItem extras so the player can navigate back.
                    val resolvedAlbumName = page.name.ifBlank { albumName }
                    val playableSongs = page.songs.map { song ->
                        song.withAlbumContext(
                            albumId = albumId,
                            albumName = resolvedAlbumName,
                            artworkHref = cover,
                        )
                    }
                    val selectedSongs = playableSongs.filter {
                        it.youtubeId in selectedSongIds
                    }
                    // Un album multi-CD est rendu disque par disque, la numerotation repartant a 1
                    // sur chacun (comme Spotify). Un seul groupe = aucun en-tete, affichage a plat.
                    val discs = remember(playableSongs) { groupSongsByDisc(playableSongs) }
                    val showDiscHeaders = discs.size > 1
                    val playSongs: (List<Song>, Int) -> Unit = { list, startIndex ->
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            list.map { it.mediaItem },
                            startIndex,
                            sourceName = page.name.ifBlank { albumName },
                            sourceId = albumId,
                            sourceType = QueueSourceType.Album,
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
                    Row(modifier = Modifier.fillMaxSize()) {
                    if (showContextPane) {
                        BrowsingContextPane(
                            modifier = Modifier.padding(
                                top = paddingValues.calculateTopPadding()
                            ),
                        ) {
                            AlbumContextHeader(
                                coverUri = cover.ifBlank {
                                    page.songs.firstOrNull()?.thumbnailHref ?: ""
                                },
                                name = resolvedAlbumName,
                                artist = page.artist,
                                artistId = page.artistId,
                                metadataLine = metadataLine,
                                description = page.description,
                                artworkSize = 220.dp,
                                hasSongs = playableSongs.isNotEmpty(),
                                isLoggedIn = isLoggedIn,
                                canSaveAlbum = page.playlistId != null,
                                isAlbumSaved = isAlbumSaved,
                                onOpenArtist = onOpenArtist,
                                onToggleAlbumSaved = albumViewModel::toggleAlbumSaved,
                                onPlay = { playSongs(playableSongs, 0) },
                                onShuffle = { playSongs(playableSongs.shuffled(), 0) },
                                onStartRadio = {
                                    onOpenPlayer()
                                    PlayerManager.startRadio(playableSongs.first(), context)
                                },
                                onDownload = {
                                    albumViewModel.downloadAlbum(playableSongs, cover)
                                },
                                onMore = { showAlbumMenu = true },
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        state = listState,
                        // Le contenu defile SOUS la barre superieure : sa hauteur est reportee
                        // dans le contentPadding plutot que sur le conteneur.
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                            start = 8.dp,
                            end = 8.dp
                        )
                    ) {
                        if (!showContextPane) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AlbumContextHeader(
                                        coverUri = cover.ifBlank {
                                            page.songs.firstOrNull()?.thumbnailHref ?: ""
                                        },
                                        name = resolvedAlbumName,
                                        artist = page.artist,
                                        artistId = page.artistId,
                                        metadataLine = metadataLine,
                                        description = "",
                                        artworkSize = 180.dp,
                                        hasSongs = playableSongs.isNotEmpty(),
                                        isLoggedIn = isLoggedIn,
                                        canSaveAlbum = page.playlistId != null,
                                        isAlbumSaved = isAlbumSaved,
                                        onOpenArtist = onOpenArtist,
                                        onToggleAlbumSaved =
                                            albumViewModel::toggleAlbumSaved,
                                        onPlay = { playSongs(playableSongs, 0) },
                                        onShuffle = {
                                            playSongs(playableSongs.shuffled(), 0)
                                        },
                                        onStartRadio = {
                                            onOpenPlayer()
                                            PlayerManager.startRadio(
                                                playableSongs.first(),
                                                context,
                                            )
                                        },
                                        onDownload = {
                                            albumViewModel.downloadAlbum(playableSongs, cover)
                                        },
                                        onMore = { showAlbumMenu = true },
                                    )
                                }
                            }
                        }

                        // Decalage de chaque disque dans la file complete : les groupes suivent
                        // l'ordre de playableSongs, la lecture reste donc celle de l'album entier.
                        var discStart = 0
                        discs.forEach { disc ->
                            val firstIndex = discStart
                            discStart += disc.songs.size

                            if (showDiscHeaders) {
                                item(
                                    key = "album-disc-${disc.number}",
                                    contentType = "disc-header"
                                ) {
                                    Text(
                                        text = stringResource(R.string.disc_number, disc.number),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = 8.dp,
                                            top = 12.dp,
                                            bottom = 4.dp
                                        )
                                    )
                                }
                            }

                            itemsIndexed(
                                items = disc.songs,
                                key = { _, song -> song.youtubeId },
                                contentType = { _, _ -> "song" }) { index, song ->
                                SongListItem(
                                    song = song,
                                    onPress = {
                                        // Queue the whole album, starting at the tapped track.
                                        playSongs(playableSongs, firstIndex + index)
                                    },
                                    playNext = { PlayerManager.addNext(song, context) },
                                    addToQueue = { PlayerManager.addToQueue(song, context) },
                                    startRadio = { PlayerManager.startRadio(song, context) },
                                    // Featured artists differ from the album artist, so the row
                                    // needs its own link; the album entry would just point here.
                                    onViewArtist = song.artistId?.let { artistId ->
                                        { onOpenArtist(artistId, song.artist) }
                                    },
                                    download = {
                                        albumViewModel.downloadSong(
                                            song,
                                            playableSongs,
                                            page.cover,
                                        )
                                    },
                                    // Numerotation propre a chaque disque : la position dans le
                                    // groupe fait foi, le numero fourni par YouTube servant surtout
                                    // a decouper les disques.
                                    trackNumber = index + 1,
                                    hideArtist = isTrackArtistRedundant(song.artist, page.artist),
                                    // Album rows come from the network: the offline marker is
                                    // resolved against the shared in-memory Set of downloaded ids.
                                    isDownloaded = song.youtubeId in downloadedIds,
                                    isSelected = song.youtubeId in selectedSongIds,
                                    selectionActive = selectedSongIds.isNotEmpty(),
                                    onSelectionClick = { modifiers ->
                                        val update = updateSongSelection(
                                            orderedIds = playableSongs.map(Song::youtubeId),
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
                        }

                        if (page.songs.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.no_results),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        if (!showContextPane && page.description.isNotBlank()) {
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
                            item(key = "album-section-title-$index", contentType = "section-title") {
                                Text(
                                    text = localizedApiSectionTitle(section.title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                            item(key = "album-section-row-$index", contentType = "section-row") {
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

                    SongSelectionActionBar(
                        songs = selectedSongs,
                        newPlaylistName = resolvedAlbumName,
                        onPlay = {
                            playSongs(selectedSongs, 0)
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

                    if (showAlbumMenu) {
                        val shareTitle = page.name.ifBlank { albumName }
                        AlbumMenuBottomSheet(
                            albumName = shareTitle,
                            albumArtist = page.artist,
                            onShare = {
                                // "Title • Artist\nlink" ; l'artiste saute si le header n'en a pas.
                                val header = listOf(shareTitle, page.artist)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        albumShareTemplate.format(
                                            header,
                                            "${YoutubeApiConstants.ORIGIN}/browse/$albumId"
                                        )
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                                showAlbumMenu = false
                            },
                            onAddToPlaylist = {
                                showAlbumMenu = false
                                showPlaylistPicker = true
                            },
                            onDismiss = { showAlbumMenu = false },
                        )
                    }

                    if (showPlaylistPicker) {
                        LocalPlaylistPickerDialog(
                            playlists = localPlaylists,
                            onPick = { playlist ->
                                albumViewModel.addAlbumToLocalPlaylist(playlist, playableSongs)
                                showPlaylistPicker = false
                            },
                            onCreateNew = {
                                albumViewModel.createLocalPlaylistFromAlbum(
                                    name = page.name.ifBlank { albumName },
                                    songs = playableSongs,
                                )
                                showPlaylistPicker = false
                            },
                            onDismiss = { showPlaylistPicker = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AlbumContextHeader(
    coverUri: String,
    name: String,
    artist: String,
    artistId: String?,
    metadataLine: String,
    description: String,
    artworkSize: Dp,
    hasSongs: Boolean,
    isLoggedIn: Boolean,
    canSaveAlbum: Boolean,
    isAlbumSaved: Boolean,
    onOpenArtist: (String, String) -> Unit,
    onToggleAlbumSaved: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit,
) {
    SquareImage(
        uri = coverUri,
        modifier = Modifier.size(artworkSize),
    )
    Text(
        text = name,
        style = MaterialTheme.typography.headlineSmallEmphasized.copy(
            fontFamily = nunitoFontFamily
        ),
        textAlign = TextAlign.Center,
    )
    if (artist.isNotBlank()) {
        Text(
            text = artist,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = if (artistId != null) {
                Modifier.clickable { onOpenArtist(artistId, artist) }
            } else {
                Modifier
            },
        )
    }
    if (metadataLine.isNotBlank()) {
        Text(
            text = metadataLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (hasSongs) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoggedIn && canSaveAlbum) {
                FilledIconToggleButton(
                    checked = isAlbumSaved,
                    onCheckedChange = { onToggleAlbumSaved() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = if (isAlbumSaved) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = stringResource(
                            if (isAlbumSaved) {
                                R.string.remove_album_from_library
                            } else {
                                R.string.save_album_to_library
                            }
                        ),
                    )
                }
            }
            PlaySplitButton(
                onPlay = onPlay,
                menuActions = listOf(
                    PlaySplitButtonAction(
                        label = stringResource(R.string.shuffle),
                        icon = Icons.Rounded.Shuffle,
                        onClick = onShuffle,
                    ),
                    PlaySplitButtonAction(
                        label = stringResource(R.string.start_radio),
                        icon = Icons.Rounded.Radio,
                        onClick = onStartRadio,
                    ),
                    PlaySplitButtonAction(
                        label = stringResource(R.string.download),
                        icon = Icons.Rounded.Download,
                        onClick = onDownload,
                    ),
                ),
            )
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more),
                )
            }
        }
    }
    if (description.isNotBlank()) {
        var expanded by remember(description) { mutableStateOf(false) }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize(),
        )
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
