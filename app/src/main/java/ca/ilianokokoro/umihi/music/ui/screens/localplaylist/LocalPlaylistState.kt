package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong

@Immutable
data class LocalPlaylistState(
    val loading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val songs: List<LocalPlaylistSong> = emptyList(),
    val notFound: Boolean = false,
)
