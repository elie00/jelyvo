package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong

data class LocalPlaylistState(
    val loading: Boolean = true,
    val name: String = "",
    val songs: List<LocalPlaylistSong> = emptyList(),
    val notFound: Boolean = false,
)
