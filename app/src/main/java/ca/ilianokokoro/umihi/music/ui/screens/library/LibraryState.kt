package ca.ilianokokoro.umihi.music.ui.screens.library

import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.PlaylistInfo


data class LibraryState(
    val screenState: ScreenState = ScreenState.Loading,
    val isRefreshing: Boolean = false,
    val playlists: List<PlaylistInfo> = emptyList(),
)

sealed class ScreenState {
    data class Success(val library: Library) : ScreenState()
    data object LoggedOut : ScreenState()
    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
