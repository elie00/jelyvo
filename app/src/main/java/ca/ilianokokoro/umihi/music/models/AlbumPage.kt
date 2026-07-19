package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumPage(
    val name: String,
    val songs: List<Song> = emptyList(),
)
