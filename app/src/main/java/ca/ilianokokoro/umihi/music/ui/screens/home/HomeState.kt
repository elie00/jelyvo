package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.Song


data class HomeState(
    val screenState: ScreenState = ScreenState.Loading,
    val isRefreshing: Boolean = false,
    val recentlyPlayed: List<Song> = emptyList()
)

sealed class ScreenState {
    data class LoggedIn(
        val playlistInfos: List<PlaylistInfo>,
        val homeSections: List<HomeFeedSection> = emptyList(),
    ) : ScreenState()

    data object LoggedOut
        : ScreenState()


    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
