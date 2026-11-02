package ca.ilianokokoro.umihi.music.desktop

import ca.ilianokokoro.umihi.music.desktop.theme.dmSansFontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeMusicSearch
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeStreamResolver
import ca.ilianokokoro.umihi.music.desktop.account.DesktopAccount
import ca.ilianokokoro.umihi.music.desktop.account.checkAccount
import ca.ilianokokoro.umihi.music.desktop.playback.JavaFxAudioEngine
import ca.ilianokokoro.umihi.music.desktop.theme.nunitoFontFamily
import ca.ilianokokoro.umihi.music.desktop.ui.AccountScreen
import ca.ilianokokoro.umihi.music.desktop.ui.CollectionScreen
import ca.ilianokokoro.umihi.music.desktop.ui.PlayerBar
import ca.ilianokokoro.umihi.music.desktop.ui.Screen
import ca.ilianokokoro.umihi.music.desktop.ui.SearchScreen
import ca.ilianokokoro.umihi.music.desktop.ui.tracks
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import kotlinx.coroutines.launch

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Jelyvo",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
    ) {
        JelyvoDesktopApp()
    }
}

@Composable
private fun JelyvoDesktopApp() {
    val engine = remember { JavaFxAudioEngine(NewPipeStreamResolver(preferMuxedProgressive = true)) }
    val scope = rememberCoroutineScope()
    val playback by engine.state.collectAsState()

    var screen by remember { mutableStateOf<Screen>(Screen.Search) }

    val account = remember { DesktopAccount() }
    val session by account.session.collectAsState()
    var accountBusy by remember { mutableStateOf(false) }
    var accountCheck by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { account.restore() }

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

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            searching = true
            searchError = null
            runCatching { NewPipeMusicSearch.search(query, filter) }
                .onSuccess { results = it }
                .onFailure { searchError = it.message ?: it::class.simpleName }
            searching = false
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
        screen = target
        collectionTracks = emptyList()
        collectionError = null
        collectionLoading = true
        scope.launch {
            runCatching { load() }
                .onSuccess { (tracks, artwork, subtitle) ->
                    collectionTracks = tracks
                    collectionArtwork = artwork
                    collectionSubtitle = subtitle
                }
                .onFailure { collectionError = it.message ?: it::class.simpleName }
            collectionLoading = false
        }
    }

    UmihiMusicTheme(darkTheme = true, fontFamily = nunitoFontFamily, studioFontFamily = dmSansFontFamily) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        when (val current = screen) {
                            Screen.Search -> SearchScreen(
                                query = query,
                                onQueryChange = { query = it },
                                filter = filter,
                                onFilterChange = { filter = it; runSearch() },
                                onSubmit = ::runSearch,
                                loading = searching,
                                error = searchError,
                                results = results,
                                playingTrackId = playback.currentTrack?.id,
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
                                        accountCheck = if (account.signIn(header)) {
                                            "Enregistré dans le Trousseau."
                                        } else {
                                            "Impossible d'écrire dans le Trousseau."
                                        }
                                        accountBusy = false
                                    }
                                },
                                onSignOut = {
                                    scope.launch {
                                        accountBusy = true
                                        account.signOut()
                                        accountCheck = null
                                        accountBusy = false
                                    }
                                },
                                onCheck = {
                                    val current = session ?: return@AccountScreen
                                    scope.launch {
                                        accountBusy = true
                                        accountCheck = checkAccount(current)
                                        accountBusy = false
                                    }
                                },
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
                            )
                        }
                    }

                    HorizontalDivider()
                    PlayerBar(engine = engine)
                }
            }
        }
    }
}
