package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.domain.PlaybackQueue
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.desktop.library.DesktopPlaylist
import ca.ilianokokoro.umihi.music.desktop.library.LibrarySnapshot

@Composable
fun LibraryScreen(
    library: LibrarySnapshot,
    playingTrackId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onEnqueue: (Track) -> Unit,
    onRename: (DesktopPlaylist) -> Unit,
    onDelete: (DesktopPlaylist) -> Unit,
    onClearHistory: () -> Unit,
    home: Boolean = false,
) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text(if (home) "Accueil" else "Bibliothèque locale", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        Text("Enregistrée sur ce Mac", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        if (!home) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Favoris", "Historique", "Playlists").forEachIndexed { index, label ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
            }
        }
        val tracks = if (home || tab == 1) library.recents else library.favorites
        if (!home && tab == 1 && tracks.isNotEmpty()) TextButton(onClick = onClearHistory) { Text("Effacer l'historique") }
        LazyColumn(Modifier.weight(1f)) {
            if (!home && tab == 2) {
                if (library.playlists.isEmpty()) item {
                    Text("Enregistrez votre file de lecture pour créer une playlist.", Modifier.padding(20.dp))
                }
                items(library.playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPlay(playlist.tracks, 0) }, modifier = Modifier.weight(1f)) {
                            Text("${playlist.name} · ${playlist.tracks.size} titres")
                        }
                        TextButton(onClick = { onRename(playlist) }) { Text("Renommer") }
                        TextButton(onClick = { onDelete(playlist) }) { Text("Supprimer") }
                    }
                }
            } else {
                if (tracks.isEmpty()) item {
                    Text(if (home) "Recherchez un titre pour commencer. Vos dernières écoutes apparaîtront ici." else "Aucun titre pour le moment.", Modifier.padding(20.dp))
                }
                itemsIndexed(tracks, key = { index, track -> "${track.id}#$index" }) { index, track ->
                    TrackRow(track, playingTrackId == track.id, { onPlay(tracks, index) }, { onEnqueue(track) })
                }
            }
        }
    }
}

@Composable
fun QueueScreen(queue: PlaybackQueue, onPlay: (Int) -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("File de lecture", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = onSave, enabled = queue.entries.isNotEmpty()) { Text("Enregistrer en playlist") }
        }
        if (queue.entries.isEmpty()) Text("Ajoutez des titres depuis la recherche ou une collection.", Modifier.padding(20.dp))
        LazyColumn {
            itemsIndexed(queue.entries, key = { _, entry -> entry.entryId }) { index, entry ->
                Column {
                    TrackRow(entry.track, index == queue.currentIndex, { onPlay(index) })
                    Row(Modifier.padding(start = 16.dp)) {
                        TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("Monter") }
                        TextButton(onClick = { onMove(index, index + 1) }, enabled = index < queue.entries.lastIndex) { Text("Descendre") }
                        TextButton(onClick = { onRemove(index) }) { Text("Retirer") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun PlaylistNameDialog(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nom de la playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
