package ca.ilianokokoro.umihi.music.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.key.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.PlaybackQueue
import ca.ilianokokoro.umihi.music.core.domain.QueueEntry
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.youtubePlaylistUrl
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeMusicSearch
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeStreamResolver
import ca.ilianokokoro.umihi.music.desktop.account.DesktopAccount
import ca.ilianokokoro.umihi.music.desktop.account.checkAccount
import ca.ilianokokoro.umihi.music.desktop.account.likedAccountTracks
import ca.ilianokokoro.umihi.music.desktop.playback.JavaFxAudioEngine
import ca.ilianokokoro.umihi.music.desktop.theme.nunitoFontFamily
import ca.ilianokokoro.umihi.music.desktop.library.DesktopLibrary
import ca.ilianokokoro.umihi.music.desktop.library.DesktopPlaylist
import ca.ilianokokoro.umihi.music.desktop.ui.LibraryScreen
import ca.ilianokokoro.umihi.music.desktop.ui.QueueScreen
import ca.ilianokokoro.umihi.music.desktop.ui.PlaylistNameDialog
import ca.ilianokokoro.umihi.music.desktop.ui.AccountScreen
import ca.ilianokokoro.umihi.music.desktop.ui.CollectionScreen
import ca.ilianokokoro.umihi.music.desktop.ui.PlayerBar
import ca.ilianokokoro.umihi.music.desktop.ui.Screen
import ca.ilianokokoro.umihi.music.desktop.ui.SearchScreen
import ca.ilianokokoro.umihi.music.desktop.ui.tracks
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Jelyvo",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
    ) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(860, 580) }
        JelyvoDesktopApp(onQuit = ::exitApplication)
    }
}

@Composable
private fun JelyvoDesktopApp(onQuit: () -> Unit) {
    val engine = remember { JavaFxAudioEngine(NewPipeStreamResolver(preferMuxedProgressive = true)) }
    val scope = rememberCoroutineScope()
    DisposableEffect(engine) { onDispose { engine.release() } }
    val playback by engine.state.collectAsState()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val queue by engine.queue.collectAsState()
    val library = remember { DesktopLibrary() }
    val libraryState by library.state.collectAsState()
    var libraryReady by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var savePlaylist by remember { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<DesktopPlaylist?>(null) }
    var deletePlaylist by remember { mutableStateOf<DesktopPlaylist?>(null) }
    var clearHistory by remember { mutableStateOf(false) }
    fun libraryAction(action: suspend () -> Unit) {
        scope.launch {
            try { action(); libraryError = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { libraryError = "La bibliothèque locale n'a pas pu être enregistrée. Vos données existantes sont conservées." }
        }
    }
    LaunchedEffect(Unit) {
        try { library.restore(); libraryReady = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { libraryError = "Impossible de lire la bibliothèque locale. Le fichier existant est conservé." }
    }
    var recordedSlot by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playback.isPlaying, queue.current?.entryId, libraryReady) {
        val entry = queue.current
        if (libraryReady && playback.isPlaying && entry != null && entry.entryId != recordedSlot) {
            recordedSlot = entry.entryId
            libraryAction { library.recordPlay(entry.track) }
        }
    }

    val account = remember { DesktopAccount() }
    val session by account.session.collectAsState()
    var accountBusy by remember { mutableStateOf(false) }
    var accountCheck by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { account.restore() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { accountCheck = "Trousseau indisponible. Réessayez depuis l'écran Compte." }
    }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CatalogSearchFilter.SONGS) }
    var results by remember { mutableStateOf(emptyList<CatalogSearchItem>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var collectionTracks by remember { mutableStateOf(emptyList<Track>()) }
    var collectionArtwork by remember { mutableStateOf("") }
    var collectionSubtitle by remember { mutableStateOf("") }
    var collectionLoading by remember { mutableStateOf(false) }
    var collectionError by remember { mutableStateOf<String?>(null) }

    var searchJob by remember { mutableStateOf<Job?>(null) }
    var collectionJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(session) {
        if (screen == Screen.AccountTracks) {
            collectionJob?.cancel()
            collectionTracks = emptyList()
            screen = Screen.Account
        }
    }

    fun runSearch() {
        if (query.isBlank()) return
        val submittedQuery = query.trim()
        val submittedFilter = filter
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            searchError = null
            results = emptyList()
            try {
                val found = NewPipeMusicSearch.search(submittedQuery, submittedFilter)
                ensureActive()
                results = found
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { searchError = "Recherche indisponible. Réessayez." }
            finally { if (isActive) searching = false }
        }
    }

    fun play(tracks: List<Track>, index: Int) {
        if (index !in tracks.indices) return
        scope.launch {
            engine.load(
                PlaybackQueue(
                    entries = tracks.mapIndexed { position, track ->
                        QueueEntry(entryId = "${track.id}#$position", track = track)
                    },
                ),
                index,
            )
        }
    }

    /** Album and artist pages load the same way; only the catalog call differs. */
    fun openCollection(target: Screen, load: suspend () -> Triple<List<Track>, String, String>) {
        collectionJob?.cancel()
        screen = target
        collectionArtwork = ""
        collectionSubtitle = ""
        collectionTracks = emptyList()
        collectionError = null
        collectionLoading = true
        collectionJob = scope.launch {
            try {
                val (tracks, artwork, subtitle) = load()
                ensureActive()
                collectionTracks = tracks
                collectionArtwork = artwork
                collectionSubtitle = subtitle
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { collectionError = "Collection indisponible. Réessayez." }
            finally { if (isActive) collectionLoading = false }
        }
    }

    UmihiMusicTheme(darkTheme = true, fontFamily = nunitoFontFamily) {
        if (savePlaylist || renamePlaylist != null) PlaylistNameDialog(
            initialName = renamePlaylist?.name.orEmpty(),
            onDismiss = { savePlaylist = false; renamePlaylist = null },
            onSave = { name ->
                val renaming = renamePlaylist
                val tracks = queue.entries.map { it.track }
                libraryAction {
                    if (renaming != null) library.renamePlaylist(renaming.id, name)
                    else library.savePlaylist(name, tracks)
                }
                savePlaylist = false; renamePlaylist = null
            },
        )
        if (deletePlaylist != null || clearHistory) AlertDialog(
            onDismissRequest = { deletePlaylist = null; clearHistory = false },
            title = { Text(if (clearHistory) "Effacer l'historique ?" else "Supprimer cette playlist ?") },
            confirmButton = { TextButton(onClick = {
                val deleting = deletePlaylist
                libraryAction { if (deleting != null) library.deletePlaylist(deleting.id) else library.clearHistory() }
                deletePlaylist = null; clearHistory = false
            }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deletePlaylist = null; clearHistory = false }) { Text("Annuler") } },
        )
        Surface(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || (!event.isMetaPressed && !event.isCtrlPressed)) false
            else when (event.key) {
                Key.Q, Key.W -> { onQuit(); true }
                Key.Spacebar -> { if (playback.isPlaying) engine.pause() else engine.play(); true }
                Key.DirectionRight -> { engine.skipNext(); true }
                Key.DirectionLeft -> { engine.skipPrevious(); true }
                Key.One -> { screen = Screen.Home; true }
                Key.Two -> { screen = Screen.Search; true }
                Key.Three -> { screen = Screen.Library; true }
                Key.Four -> { screen = Screen.Queue; true }
                else -> false
            }
        }) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    NavigationRailItem(selected = screen is Screen.Home, onClick = { screen = Screen.Home }, icon = { Text("⌂") }, label = { Text("Accueil") })
                    NavigationRailItem(selected = screen is Screen.Library, onClick = { screen = Screen.Library }, icon = { Text("♫") }, label = { Text("Bibliothèque") })
                    NavigationRailItem(selected = screen is Screen.Queue, onClick = { screen = Screen.Queue }, icon = { Text("≡") }, label = { Text("File") })
                    NavigationRailItem(
                        selected = screen is Screen.Search,
                        onClick = { screen = Screen.Search },
                        icon = { Text("⌕", style = MaterialTheme.typography.headlineSmall) },
                        label = { Text("Recherche") },
                    )
                    NavigationRailItem(
                        selected = screen is Screen.Account,
                        onClick = { screen = Screen.Account },
                        icon = { Text("◍", style = MaterialTheme.typography.headlineSmall) },
                        label = { Text("Compte") },
                    )
                }
                VerticalDivider()

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                    Column(modifier = Modifier.weight(1f)) {
                        when (val current = screen) {
                            Screen.Home, Screen.Library -> LibraryScreen(
                                library = libraryState, playingTrackId = playback.currentTrack?.id,
                                onPlay = ::play, onEnqueue = engine::enqueue,
                                onRename = { renamePlaylist = it }, onDelete = { deletePlaylist = it },
                                onClearHistory = { clearHistory = true }, home = current == Screen.Home,
                            )
                            Screen.Queue -> QueueScreen(queue, engine::seekToIndex, engine::remove, engine::move, { savePlaylist = true })
                            Screen.Search -> SearchScreen(
                                query = query,
                                onQueryChange = { query = it },
                                filter = filter,
                                onFilterChange = { filter = it; runSearch() },
                                onSubmit = {
                                    val playlistUrl = youtubePlaylistUrl(query)
                                    if (playlistUrl == null) runSearch()
                                    else openCollection(Screen.Album(playlistUrl, "Playlist YouTube")) {
                                        val page = NewPipeMusicSearch.album(playlistUrl)
                                        Triple(page.tracks, page.artworkUrl, page.title)
                                    }
                                },
                                loading = searching,
                                error = searchError,
                                results = results,
                                playingTrackId = playback.currentTrack?.id,
                                onEnqueue = engine::enqueue,
                                onPlayTrack = { index ->
                                    val songs = results.tracks()
                                    val clicked = results[index]
                                    if (clicked is CatalogSearchItem.TrackItem) {
                                        play(songs, songs.indexOf(clicked.track))
                                    }
                                },
                                onOpenAlbum = { album ->
                                    openCollection(Screen.Album(album.id, album.title)) {
                                        val page = NewPipeMusicSearch.album(album.id)
                                        Triple(page.tracks, page.artworkUrl, page.artistName)
                                    }
                                },
                                onOpenArtist = { artist ->
                                    openCollection(Screen.Artist(artist.id, artist.name)) {
                                        val page = NewPipeMusicSearch.artist(artist.id)
                                        Triple(page.allTracks, page.artworkUrl, "Artiste")
                                    }
                                },
                            )

                            Screen.Account -> AccountScreen(
                                session = session,
                                checkResult = accountCheck,
                                busy = accountBusy,
                                onSignIn = { header ->
                                    scope.launch {
                                        accountBusy = true
                                        try {
                                            accountCheck = if (account.signIn(header)) "Enregistré dans le Trousseau."
                                                else "En-tête incomplet ou Trousseau indisponible."
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { accountCheck = "Trousseau indisponible." }
                                        finally { accountBusy = false }
                                    }
                                },
                                onSignOut = {
                                    scope.launch {
                                        accountBusy = true
                                        try {
                                            accountCheck = if (account.signOut()) null else "La session n'a pas pu être supprimée du Trousseau. Réessayez."
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { accountCheck = "Trousseau indisponible." }
                                        finally { accountBusy = false }
                                    }
                                },
                                onOpenLikes = {
                                    val current = session ?: return@AccountScreen
                                    openCollection(Screen.AccountTracks) {
                                        Triple(likedAccountTracks(current), "", "Compte YouTube")
                                    }
                                },
                                onCheck = {
                                    val current = session ?: return@AccountScreen
                                    scope.launch {
                                        accountBusy = true
                                        try { accountCheck = checkAccount(current) }
                                        finally { accountBusy = false }
                                    }
                                },
                            )

                            Screen.AccountTracks -> CollectionScreen(
                                title = "Titres aimés sur YouTube", subtitle = collectionSubtitle, artworkUri = "",
                                tracks = collectionTracks, loading = collectionLoading, error = collectionError,
                                playingTrackId = playback.currentTrack?.id, onBack = { screen = Screen.Account },
                                onPlayTrack = { play(collectionTracks, it) }, onEnqueue = engine::enqueue,
                            )

                            is Screen.Album -> CollectionScreen(
                                title = current.title,
                                subtitle = collectionSubtitle,
                                artworkUri = collectionArtwork,
                                tracks = collectionTracks,
                                loading = collectionLoading,
                                error = collectionError,
                                playingTrackId = playback.currentTrack?.id,
                                onBack = { screen = Screen.Search },
                                onPlayTrack = { play(collectionTracks, it) },
                                onEnqueue = engine::enqueue,
                            )

                            is Screen.Artist -> CollectionScreen(
                                title = current.name,
                                subtitle = collectionSubtitle,
                                artworkUri = collectionArtwork,
                                tracks = collectionTracks,
                                loading = collectionLoading,
                                error = collectionError,
                                playingTrackId = playback.currentTrack?.id,
                                onBack = { screen = Screen.Search },
                                onPlayTrack = { play(collectionTracks, it) },
                                onEnqueue = engine::enqueue,
                            )
                        }
                    }

                    HorizontalDivider()
                    PlayerBar(engine = engine,
                        favorite = libraryState.favorites.any { it.id == playback.currentTrack?.id },
                        onFavorite = { track -> libraryAction { library.toggleFavorite(track) } },
                        onQueue = { screen = Screen.Queue },
                    )
                }
            }
        }
    }
}
