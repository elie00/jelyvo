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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

private enum class RootMode { CHOOSER, WATCH, PHONE }

data class StandaloneActions(
    val setQuery: (String) -> Unit,
    val search: () -> Unit,
    val play: (WatchSong, List<WatchSong>) -> Unit,
    val togglePlayback: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val toggleFavorite: () -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
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
        RootMode.WATCH -> StandaloneScreen(standaloneState, standaloneActions)
        RootMode.PHONE -> JelyvoWearApp(remoteState, isAmbient, remoteActions)
    }
}

@Composable
private fun ModeChooser(hasLocalPlayback: Boolean, onWatch: () -> Unit, onPhone: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WatchBackground),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Image(
                painterResource(R.drawable.ic_jelyvo_watch),
                contentDescription = null,
                modifier = Modifier.size(57.dp).background(WatchCream, CircleShape).padding(3.dp),
            )
            Text("Jelyvo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Choisir un lecteur", color = WatchMuted, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
        }
        item {
            ModeButton(
                icon = Icons.Filled.Devices,
                title = if (hasLocalPlayback) "Reprendre sur la montre" else "Écouter sur la montre",
                subtitle = "Wi‑Fi ou LTE • sans téléphone",
                emphasized = true,
                onClick = onWatch,
            )
        }
        item {
            ModeButton(
                icon = Icons.Filled.PhoneAndroid,
                title = "Piloter le téléphone",
                subtitle = "Télécommande Jelyvo",
                emphasized = false,
                onClick = onPhone,
            )
        }
    }
}

@Composable
private fun ModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (emphasized) WatchPlum else WatchSurface)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(37.dp).background(if (emphasized) WatchCream else WatchSurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (emphasized) WatchPlum else WatchCoral)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = WatchMuted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun StandaloneScreen(state: StandaloneUiState, actions: StandaloneActions) {
    var showPlayer by rememberSaveable { mutableStateOf(state.currentSong != null) }
    BackHandler(enabled = showPlayer) { showPlayer = false }
    Box(Modifier.fillMaxSize().background(WatchBackground)) {
        if (showPlayer && state.currentSong != null) {
            LocalPlayer(state, actions, onBrowse = { showPlayer = false })
        } else {
            StandaloneBrowser(state, actions, onOpenPlayer = { showPlayer = true })
        }
        state.error?.let { error ->
            Text(
                error,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .background(Color(0xFF8E2634), RoundedCornerShape(16.dp))
                    .clickable(onClick = actions.clearError)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun StandaloneBrowser(
    state: StandaloneUiState,
    actions: StandaloneActions,
    onOpenPlayer: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val displayed = when {
        state.searchPerformed -> state.results
        state.favorites.isNotEmpty() -> state.favorites
        else -> state.recents
    }
    val heading = when {
        state.searchPerformed -> "Résultats"
        state.favorites.isNotEmpty() -> "Titres aimés"
        else -> "Écoutés récemment"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().focusable().onRotaryScrollEvent { event ->
            scope.launch { listState.scrollBy(event.verticalScrollPixels) }
            true
        },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Sur la montre", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Lecture autonome", color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
        }
        if (state.currentSong != null) {
            item {
                SongRow(state.currentSong, trailing = if (state.isPlaying) "Ⅱ" else "▶", onClick = onOpenPlayer)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WatchSurfaceHigh)
                    .clickable { focusRequester.requestFocus(); keyboard?.show() }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = WatchCoral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = actions.setQuery,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = SolidColor(WatchCoral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); actions.search() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { input ->
                        if (state.query.isBlank()) Text("Rechercher un titre", color = WatchMuted, fontSize = 11.sp)
                        input()
                    },
                )
                Box(
                    Modifier.size(29.dp).background(WatchPlum, CircleShape).clickable {
                        keyboard?.hide(); actions.search()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        if (state.searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LibraryMusic, null, tint = WatchMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(heading, color = WatchMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (displayed.isEmpty()) {
                item {
                    Text(
                        if (state.searchPerformed) "Aucun titre trouvé" else "Recherchez votre premier titre",
                        color = WatchMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }
            }
            items(displayed, key = WatchSong::videoId) { song ->
                SongRow(song, trailing = song.durationText) {
                    actions.play(song, displayed)
                    onOpenPlayer()
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: WatchSong, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WatchSurface)
            .clickable(onClick = onClick).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(39.dp).clip(CircleShape).background(WatchSurfaceHigh), contentAlignment = Alignment.Center) {
            AsyncImage(song.artworkUrl.takeIf(String::isNotBlank), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (song.artworkUrl.isBlank()) Icon(Icons.Filled.MusicNote, null, tint = WatchMuted)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = WatchMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LocalPlayer(state: StandaloneUiState, actions: StandaloneActions, onBrowse: () -> Unit) {
    val song = state.currentSong ?: return
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SUR LA MONTRE", color = WatchCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onBrowse))
        Spacer(Modifier.height(4.dp))
        AsyncImage(
            song.artworkUrl.takeIf(String::isNotBlank), null,
            Modifier.size(86.dp).clip(CircleShape).background(WatchSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = WatchMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
            val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(WatchCoral, CircleShape))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatStandaloneTime(state.positionMs), color = WatchMuted, fontSize = 8.sp)
            Text(formatStandaloneTime(state.durationMs), color = WatchMuted, fontSize = 8.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerCircle(Icons.Filled.SkipPrevious, state.currentIndex > 0, actions.previous, 37)
            PlayerCircle(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                state.controllerReady, actions.togglePlayback, 50, emphasized = true)
            PlayerCircle(Icons.Filled.SkipNext, state.currentIndex < state.queueSize - 1, actions.next, 37)
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerCircle(Icons.Filled.Replay10, true, actions.seekBack, 31)
            PlayerCircle(if (state.currentIsFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                true, actions.toggleFavorite, 31, active = state.currentIsFavorite)
            PlayerCircle(Icons.Filled.Forward10, true, actions.seekForward, 31)
            PlayerCircle(Icons.Filled.Shuffle, true, actions.toggleShuffle, 31, active = state.shuffleEnabled)
            PlayerCircle(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                true, actions.cycleRepeat, 31, active = state.repeatMode != Player.REPEAT_MODE_OFF)
        }
    }
}

@Composable
private fun PlayerCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    emphasized: Boolean = false,
    active: Boolean = false,
) {
    Box(
        Modifier.size(size.dp).background(
            when {
                !enabled -> Color.White.copy(alpha = .05f)
                emphasized -> WatchCream
                active -> WatchPlum
                else -> WatchSurfaceHigh
            }, CircleShape,
        ).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (emphasized) WatchPlum else Color.White, modifier = Modifier.size((size * .48f).dp))
    }
}

private fun formatStandaloneTime(milliseconds: Long): String {
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
