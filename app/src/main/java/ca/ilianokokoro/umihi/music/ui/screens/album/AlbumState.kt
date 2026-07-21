package ca.ilianokokoro.umihi.music.ui.screens.album

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.AlbumPage

@Immutable
sealed class AlbumState {
    data object Loading : AlbumState()
    data class Success(val page: AlbumPage) : AlbumState()
    data class Error(val exception: Exception) : AlbumState()
}
