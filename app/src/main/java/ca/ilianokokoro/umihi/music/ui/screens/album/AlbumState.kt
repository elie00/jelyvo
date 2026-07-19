package ca.ilianokokoro.umihi.music.ui.screens.album

import ca.ilianokokoro.umihi.music.models.AlbumPage

sealed class AlbumState {
    data object Loading : AlbumState()
    data class Success(val page: AlbumPage) : AlbumState()
    data class Error(val exception: Exception) : AlbumState()
}
