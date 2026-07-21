package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.ResumeItem
import ca.ilianokokoro.umihi.music.models.Song
import androidx.compose.runtime.Immutable


@Immutable
data class HomeState(
    val screenState: ScreenState = ScreenState.Loading,
    val isRefreshing: Boolean = false,
    val recentlyPlayed: List<Song> = emptyList(),
    val resumeItems: List<ResumeItem> = emptyList()
)

@Immutable
sealed class ScreenState {
    data class LoggedIn(
        val homeSections: List<HomeFeedSection> = emptyList(),
        val newReleases: List<HomeFeedItem.Album> = emptyList(),
    ) : ScreenState()

    data object LoggedOut : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
