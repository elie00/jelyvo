package ca.ilianokokoro.umihi.music.wearapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

private enum class RootMode { CHOOSER, WATCH, PHONE }
private enum class WatchRoute { HOME, SEARCH, LIBRARY, DOWNLOADS, SETTINGS, LIST, PLAYER }

data class StandaloneActions(
    val setQuery: (String) -> Unit,
    val search: () -> Unit,
    val refreshHome: () -> Unit,
    val play: (WatchSong, List<WatchSong>) -> Unit,
    val playQueueIndex: (Int) -> Unit,
    val togglePlayback: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val toggleFavorite: () -> Unit,
    val toggleDownload: (WatchSong) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val clearDownloads: () -> Unit,
    val setSmartDownloadLimit: (Int) -> Unit,
    val toggleWifiOnly: () -> Unit,
    val toggleRestrictedMode: () -> Unit,
    val clearError: () -> Unit,
)

@Composable
fun JelyvoWatchRoot(
    remoteState: WearUiState,
    standaloneState: StandaloneUiState,
    isAmbient: Boolean,
    remoteActions: WearPlayerActions,
    standaloneActions: StandaloneActions,
) {
    var mode by rememberSaveable { mutableStateOf(RootMode.CHOOSER) }
    BackHandler(enabled = mode != RootMode.CHOOSER) { mode = RootMode.CHOOSER }
    when (mode) {
        RootMode.CHOOSER -> ModeChooser(
            hasLocalPlayback = standaloneState.currentSong != null,
            onWatch = { mode = RootMode.WATCH },
            onPhone = { mode = RootMode.PHONE },
        )
        RootMode.WATCH -> StandaloneExperience(standaloneState, standaloneActions)
        RootMode.PHONE -> JelyvoWearApp(remoteState, isAmbient, remoteActions)
    }
}

@Composable
private fun ModeChooser(hasLocalPlayback: Boolean, onWatch: () -> Unit, onPhone: () -> Unit) {
    WearList(verticalPadding = 19) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painterResource(R.drawable.ic_jelyvo_watch),
                    null,
                    Modifier.size(57.dp).background(WatchCream, CircleShape).padding(3.dp),
                )
                Text("Jelyvo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Choisir un lecteur", color = WatchMuted, fontSize = 10.sp)
                Spacer(Modifier.height(5.dp))
            }
        }
        item {
            NavigationCard(
                Icons.Filled.Devices,
                if (hasLocalPlayback) "Reprendre sur la montre" else "Écouter sur la montre",
                "Wi‑Fi, LTE ou hors connexion",
                emphasized = true,
                onClick = onWatch,
            )
        }
        item {
            NavigationCard(Icons.Filled.PhoneAndroid, "Piloter le téléphone", "Télécommande Jelyvo", onClick = onPhone)
        }
    }
}

@Composable
private fun StandaloneExperience(state: StandaloneUiState, actions: StandaloneActions) {
    var route by rememberSaveable { mutableStateOf(WatchRoute.HOME) }
    var listTitle by rememberSaveable { mutableStateOf("") }
    var selectedSongs by remember { mutableStateOf(emptyList<WatchSong>()) }
    var returnRoute by rememberSaveable { mutableStateOf(WatchRoute.HOME) }

    fun openPlayer() { returnRoute = route; route = WatchRoute.PLAYER }
    fun openList(title: String, songs: List<WatchSong>) {
        listTitle = title; selectedSongs = songs; route = WatchRoute.LIST
    }

    BackHandler(enabled = route != WatchRoute.HOME) {
        route = if (route == WatchRoute.PLAYER) returnRoute else WatchRoute.HOME
    }
    Box(Modifier.fillMaxSize().background(WatchBackground)) {
        when (route) {
            WatchRoute.HOME -> WatchHome(
                state,
                actions,
                onNavigate = { route = it },
                onPlayer = ::openPlayer,
                onList = ::openList,
            )
            WatchRoute.SEARCH -> SearchScreen(state, actions, ::openPlayer)
            WatchRoute.LIBRARY -> LibraryScreen(state, onNavigate = { route = it }, onList = ::openList)
            WatchRoute.DOWNLOADS -> DownloadsScreen(state, actions, ::openPlayer)
            WatchRoute.SETTINGS -> SettingsScreen(state, actions)
            WatchRoute.LIST -> SongListScreen(listTitle, selectedSongs, state, actions, ::openPlayer)
            WatchRoute.PLAYER -> LocalPlayer(state, actions, onBrowse = { route = returnRoute })
        }
        state.error?.let { error ->
            Text(
                error,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .background(ErrorSurface, RoundedCornerShape(15.dp))
                    .clickable(onClick = actions.clearError)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun WatchHome(
    state: StandaloneUiState,
    actions: StandaloneActions,
    onNavigate: (WatchRoute) -> Unit,
    onPlayer: () -> Unit,
    onList: (String, List<WatchSong>) -> Unit,
) {
    WearList {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_jelyvo_watch), null, Modifier.size(31.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Jelyvo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("sur ta montre", color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        state.currentSong?.let { song ->
            item { NowPlayingCard(song, state.isPlaying, onPlayer, actions.togglePlayback) }
        }
        item {
            NavigationCard(Icons.Filled.Search, "Rechercher", "Titres, artistes et albums", emphasized = true) {
                onNavigate(WatchRoute.SEARCH)
            }
        }
        item {
            NavigationCard(
                if (state.downloads.isEmpty()) Icons.Filled.Download else Icons.Filled.DownloadDone,
                "Téléchargements",
                if (state.downloads.isEmpty()) "Écouter sans réseau" else "${state.downloads.size} titre(s) hors ligne",
            ) { onNavigate(WatchRoute.DOWNLOADS) }
        }
        if (state.homeLoading) {
            item { LoadingBlock() }
        } else if (state.homeSongs.isNotEmpty()) {
            item { SectionHeading("Pour toi", "Actualisé depuis Jelyvo") }
            item {
                ArtworkCarousel(state.homeSongs.take(8)) { song ->
                    actions.play(song, state.homeSongs); onPlayer()
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item { SectionHeading("Musique aimée", "${state.favorites.size} titre(s)") }
            items(state.favorites.take(2), key = { "fav:${it.videoId}" }) { song ->
                SongRow(song, song.durationText) { actions.play(song, state.favorites); onPlayer() }
            }
            item { SmallMoreButton("Tout afficher") { onList("Musique aimée", state.favorites) } }
        }
        if (state.recents.isNotEmpty()) {
            item { SectionHeading("Écoutés récemment", null) }
            items(state.recents.take(3), key = { "recent:${it.videoId}" }) { song ->
                SongRow(song, song.durationText) { actions.play(song, state.recents); onPlayer() }
            }
        }
        item { SectionHeading("Ta musique", null) }
        item { NavigationCard(Icons.Filled.LibraryMusic, "Bibliothèque", "Favoris, artistes et historique") { onNavigate(WatchRoute.LIBRARY) } }
        item { NavigationCard(Icons.Filled.Settings, "Paramètres", "Réseau et téléchargements") { onNavigate(WatchRoute.SETTINGS) } }
    }
}

@Composable
private fun SearchScreen(state: StandaloneUiState, actions: StandaloneActions, onPlayer: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    WearList {
        item { ScreenTitle("Rechercher", "Catalogue Jelyvo") }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(WatchSurfaceHigh)
                    .clickable { focusRequester.requestFocus(); keyboard?.show() }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = WatchCoral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                BasicTextField(
                    state.query,
                    actions.setQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = SolidColor(WatchCoral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); actions.search() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { field ->
                        if (state.query.isBlank()) Text("Titre ou artiste", color = WatchMuted, fontSize = 11.sp)
                        field()
                    },
                )
                PlayerCircle(Icons.Filled.Search, true, { keyboard?.hide(); actions.search() }, 30, active = true)
            }
        }
        if (state.searching) item { LoadingBlock() }
        else if (state.searchPerformed && state.results.isEmpty()) item { EmptyMessage("Aucun titre trouvé", "Essaie un autre artiste ou titre") }
        else items(state.results, key = WatchSong::videoId) { song ->
            SongRow(song, song.durationText) { actions.play(song, state.results); onPlayer() }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: StandaloneUiState,
    onNavigate: (WatchRoute) -> Unit,
    onList: (String, List<WatchSong>) -> Unit,
) {
    val allSongs = (state.favorites + state.recents + state.downloads).distinctBy(WatchSong::videoId)
    val artists = allSongs.distinctBy { it.artist.lowercase() }
    WearList {
        item { ScreenTitle("Bibliothèque", "Ta musique Jelyvo") }
        item { NavigationCard(Icons.Filled.History, "Écoutés récemment", "${state.recents.size} titre(s)") { onList("Écoutés récemment", state.recents) } }
        item { NavigationCard(Icons.Filled.Favorite, "Titres aimés", "${state.favorites.size} titre(s)") { onList("Titres aimés", state.favorites) } }
        item { NavigationCard(Icons.Filled.DownloadDone, "Hors connexion", "${state.downloads.size} titre(s)") { onNavigate(WatchRoute.DOWNLOADS) } }
        item { NavigationCard(Icons.Filled.MusicNote, "Tous les titres", "${allSongs.size} titre(s)") { onList("Tous les titres", allSongs) } }
        item { NavigationCard(Icons.Filled.Person, "Artistes", "${artists.size} artiste(s)") { onList("Artistes", artists) } }
        item { NavigationCard(Icons.Filled.Album, "Mix Jelyvo", "Recommandations actualisées") { onList("Mix Jelyvo", state.homeSongs) } }
        item { NavigationCard(Icons.Filled.Podcasts, "Podcasts", "Bientôt disponible", enabled = false) {} }
    }
}

@Composable
private fun DownloadsScreen(state: StandaloneUiState, actions: StandaloneActions, onPlayer: () -> Unit) {
    WearList {
        item { ScreenTitle("Téléchargements", "Lecture sans connexion") }
        if (state.downloadingIds.isNotEmpty()) {
            item { StatusCard(Icons.Filled.Download, "Téléchargement en cours", "${state.downloadingIds.size} titre(s)") }
        }
        item {
            NavigationCard(
                Icons.Filled.DownloadDone,
                "Téléchargements intelligents",
                if (state.smartDownloadLimit == 0) "Désactivés" else "Jusqu’à ${state.smartDownloadLimit} titres",
                emphasized = state.smartDownloadLimit > 0,
            ) {
                val options = listOf(0, 10, 25, 50)
                val index = options.indexOf(state.smartDownloadLimit).coerceAtLeast(0)
                actions.setSmartDownloadLimit(options[(index + 1) % options.size])
            }
        }
        if (state.downloads.isEmpty()) {
            item { EmptyMessage("Aucun titre téléchargé", "Télécharge un morceau depuis le lecteur") }
        } else {
            items(state.downloads, key = WatchSong::videoId) { song ->
                SongRow(song, "✓") { actions.play(song, state.downloads); onPlayer() }
            }
            item { NavigationCard(Icons.Filled.DeleteOutline, "Tout supprimer", "Libérer le stockage") { actions.clearDownloads() } }
        }
    }
}

@Composable
private fun SettingsScreen(state: StandaloneUiState, actions: StandaloneActions) {
    WearList {
        item { ScreenTitle("Paramètres", "Jelyvo Watch") }
        item {
            NavigationCard(
                if (state.wifiOnly) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                "Télécharger en Wi‑Fi",
                if (state.wifiOnly) "Activé" else "Wi‑Fi ou LTE",
                emphasized = state.wifiOnly,
                onClick = actions.toggleWifiOnly,
            )
        }
        item {
            NavigationCard(
                Icons.Filled.Download,
                "Téléchargements intelligents",
                if (state.smartDownloadLimit == 0) "Désactivés" else "${state.smartDownloadLimit} titres maximum",
            ) {
                val options = listOf(0, 10, 25, 50)
                val index = options.indexOf(state.smartDownloadLimit).coerceAtLeast(0)
                actions.setSmartDownloadLimit(options[(index + 1) % options.size])
            }
        }
        item {
            NavigationCard(
                Icons.Filled.FavoriteBorder,
                "Mode restreint",
                if (state.restrictedMode) "Activé" else "Désactivé",
                emphasized = state.restrictedMode,
                onClick = actions.toggleRestrictedMode,
            )
        }
        item { StatusCard(Icons.Filled.Devices, "Lecture autonome", "Wi‑Fi, LTE et fichiers hors ligne") }
        item { StatusCard(Icons.Filled.PhoneAndroid, "Mode téléphone", "Disponible depuis l’écran de départ") }
        item { Text("Jelyvo 2.5.0", color = WatchMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
    }
}

@Composable
private fun SongListScreen(
    title: String,
    songs: List<WatchSong>,
    state: StandaloneUiState,
    actions: StandaloneActions,
    onPlayer: () -> Unit,
) {
    WearList {
        item { ScreenTitle(title, "${songs.size} titre(s)") }
        if (songs.isEmpty()) item { EmptyMessage("Cette liste est vide", "Les titres apparaîtront après écoute") }
        else items(songs, key = WatchSong::videoId) { song ->
            SongRow(song, when {
                song.videoId in state.downloadingIds -> "…"
                song.videoId in state.downloads.map(WatchSong::videoId) -> "✓"
                else -> song.durationText
            }) { actions.play(song, songs); onPlayer() }
        }
    }
}

@Composable
private fun LocalPlayer(state: StandaloneUiState, actions: StandaloneActions, onBrowse: () -> Unit) {
    val song = state.currentSong ?: return
    val pagerState = rememberPagerState(pageCount = { 3 })
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> LocalPlayerMain(state, song, actions, onBrowse)
                1 -> LocalPlayerControls(state, song, actions)
                else -> LocalQueue(state, actions)
            }
        }
        PagerDots(3, pagerState.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

@Composable
private fun LocalPlayerMain(state: StandaloneUiState, song: WatchSong, actions: StandaloneActions, onBrowse: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("sur la montre", color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onBrowse))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            AsyncImage(song.artworkUrl.takeIf(String::isNotBlank), null, Modifier.fillMaxSize().clip(RoundedCornerShape(25.dp)).background(WatchSurfaceHigh), contentScale = ContentScale.Crop)
            if (song.artworkUrl.isBlank()) Icon(Icons.Filled.MusicNote, null, tint = WatchMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = WatchMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        ProgressLine(state.positionMs, state.durationMs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs), color = WatchMuted, fontSize = 8.sp)
            Text(formatTime(state.durationMs), color = WatchMuted, fontSize = 8.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PlayerCircle(Icons.Filled.SkipPrevious, state.currentIndex > 0, actions.previous, 38)
            PlayerCircle(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, state.controllerReady, actions.togglePlayback, 51, emphasized = true)
            PlayerCircle(Icons.Filled.SkipNext, state.currentIndex < state.queueSize - 1, actions.next, 38)
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun LocalPlayerControls(state: StandaloneUiState, song: WatchSong, actions: StandaloneActions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle("Commandes", song.title)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlItem(Icons.Filled.Replay10, "−10 s", actions.seekBack)
            ControlItem(if (state.currentIsFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (state.currentIsFavorite) "Aimé" else "Aimer", actions.toggleFavorite, state.currentIsFavorite)
            ControlItem(Icons.Filled.Forward10, "+10 s", actions.seekForward)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlItem(
                if (state.currentIsDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                when {
                    song.videoId in state.downloadingIds -> "En cours"
                    state.currentIsDownloaded -> "Hors ligne"
                    else -> "Télécharger"
                },
                { actions.toggleDownload(song) },
                state.currentIsDownloaded,
            )
            ControlItem(Icons.Filled.Shuffle, "Aléatoire", actions.toggleShuffle, state.shuffleEnabled)
            ControlItem(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "Un titre"; Player.REPEAT_MODE_ALL -> "Tout"; else -> "Boucle" },
                actions.cycleRepeat,
                state.repeatMode != Player.REPEAT_MODE_OFF,
            )
        }
    }
}

@Composable
private fun LocalQueue(state: StandaloneUiState, actions: StandaloneActions) {
    WearList {
        item { ScreenTitle("File d’attente", "${state.queue.size} titre(s)") }
        itemsIndexed(state.queue, key = { index, song -> "$index:${song.videoId}" }) { index, song ->
            SongRow(song, if (index == state.currentIndex) "●" else "") { actions.playQueueIndex(index) }
        }
    }
}

@Composable
private fun WearList(
    verticalPadding: Int = 16,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().focusable().onRotaryScrollEvent { event ->
            scope.launch { listState.scrollBy(event.verticalScrollPixels) }; true
        },
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = verticalPadding.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun NavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (emphasized) 26.dp else 21.dp))
            .background(if (emphasized) WatchPlum else WatchSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = if (emphasized) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(if (emphasized) WatchCream else WatchSurfaceHigh, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (emphasized) WatchPlum else if (enabled) WatchCoral else WatchMuted, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else WatchMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = WatchMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NowPlayingCard(song: WatchSong, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(WatchSurfaceHigh).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(song.artworkUrl.takeIf(String::isNotBlank), null, Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(WatchSurface), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = WatchMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerCircle(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, true, onToggle, 34, active = true)
    }
}

@Composable
private fun ArtworkCarousel(songs: List<WatchSong>, onClick: (WatchSong) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
        items(songs, key = WatchSong::videoId) { song ->
            Box(
                Modifier.size(width = 105.dp, height = 72.dp).clip(RoundedCornerShape(24.dp))
                    .background(WatchSurface).clickable { onClick(song) },
            ) {
                AsyncImage(song.artworkUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)))
                Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    Text(song.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = .8f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: WatchSong, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(WatchSurface).clickable(onClick = onClick).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(14.dp)).background(WatchSurfaceHigh), contentAlignment = Alignment.Center) {
            AsyncImage(song.artworkUrl.takeIf(String::isNotBlank), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (song.artworkUrl.isBlank()) Icon(Icons.Filled.MusicNote, null, tint = WatchMuted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = WatchMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable private fun SectionHeading(title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, start = 3.dp, end = 3.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        subtitle?.let { Text(it, color = WatchMuted, fontSize = 8.sp) }
    }
}

@Composable private fun StatusCard(icon: ImageVector, title: String, subtitle: String) {
    NavigationCard(icon, title, subtitle, enabled = false) {}
}

@Composable private fun EmptyMessage(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.MusicNote, null, tint = WatchCoral, modifier = Modifier.size(26.dp))
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(subtitle, color = WatchMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

@Composable private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(27.dp)) }
}

@Composable private fun SmallMoreButton(label: String, onClick: () -> Unit) {
    Text(label, color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp))
}

@Composable private fun ProgressLine(position: Long, duration: Long) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
        val fraction = if (duration > 0) position.toFloat() / duration else 0f
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(WatchCoral, CircleShape))
    }
}

@Composable private fun PagerDots(count: Int, page: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(if (page == index) 5.dp else 3.dp).background(if (page == index) Color.White else Color.White.copy(alpha = .3f), CircleShape))
        }
    }
}

@Composable
private fun ControlItem(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerCircle(icon, true, onClick, 42, active = active)
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) WatchCoral else WatchMuted, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun PlayerCircle(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    emphasized: Boolean = false,
    active: Boolean = false,
) {
    Box(
        Modifier.size(size.dp).background(
            when { !enabled -> Color.White.copy(alpha = .05f); emphasized -> WatchCream; active -> WatchPlum; else -> WatchSurfaceHigh },
            CircleShape,
        ).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (emphasized) WatchPlum else Color.White, modifier = Modifier.size((size * .48f).dp))
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(Locale.getDefault(), seconds / 60, seconds % 60)
}

private val WatchBackground = Color(0xFF151110)
private val WatchMuted = Color(0xFFDCC1BC)
private val WatchCoral = Color(0xFFF47B67)
private val WatchPlum = Color(0xFF74304F)
private val WatchSurface = Color(0xFF221B1A)
private val WatchSurfaceHigh = Color(0xFF2D2523)
private val WatchCream = Color(0xFFFFF8ED)
private val ErrorSurface = Color(0xFF8E2634)
