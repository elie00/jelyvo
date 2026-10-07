package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.Track

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: CatalogSearchFilter,
    onFilterChange: (CatalogSearchFilter) -> Unit,
    onSubmit: () -> Unit,
    loading: Boolean,
    error: String?,
    results: List<CatalogSearchItem>,
    playingTrackId: String?,
    onPlayTrack: (Int) -> Unit,
    onEnqueue: (Track) -> Unit,
    onOpenAlbum: (CatalogSearchItem.Album) -> Unit,
    onOpenArtist: (CatalogSearchItem.Artist) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Rechercher sur YouTube Music") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSubmit, enabled = !loading && query.isNotBlank()) { Text("Chercher") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SEARCH_FILTERS.forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { onFilterChange(value) },
                    label = { Text(label) },
                )
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                results.isEmpty() -> Text(
                    text = "Aucun résultat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(results, key = { index, item -> "${item.id}#$index" }) { index, item ->
                        when (item) {
                            is CatalogSearchItem.TrackItem -> TrackRow(
                                track = item.track,
                                playing = playingTrackId == item.track.id,
                                onClick = { onPlayTrack(index) },
                                onEnqueue = { onEnqueue(item.track) },
                            )

                            is CatalogSearchItem.Album -> AlbumRow(
                                title = item.title,
                                subtitle = item.subtitle,
                                artworkUri = item.artworkUrl,
                                onClick = { onOpenAlbum(item) },
                            )

                            is CatalogSearchItem.Artist -> ArtistRow(
                                name = item.name,
                                artworkUri = item.artworkUrl,
                                onClick = { onOpenArtist(item) },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** The track slice of a result list, so a click can load a queue of songs only. */
fun List<CatalogSearchItem>.tracks(): List<Track> =
    filterIsInstance<CatalogSearchItem.TrackItem>().map { it.track }
