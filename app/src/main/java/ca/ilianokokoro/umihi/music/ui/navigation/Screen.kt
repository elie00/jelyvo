package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import kotlinx.serialization.Serializable

@Serializable
data object HomeScreenKey : NavKey

/**
 * [initialQuery] seeds the field and runs the search on arrival, so other screens can hand off a
 * term. The bottom bar and the shortcut always push the query-less instance, which is what makes
 * tab identity stable: a seeded search is a distinct entry stacked on top of the tab.
 */
@Serializable
data class SearchScreenKey(val initialQuery: String? = null) : NavKey

@Serializable
data object LibraryScreenKey : NavKey

@Serializable
data object SettingsScreenKey : NavKey

@Serializable
data object ListeningStatsScreenKey : NavKey

@Serializable
data object RecoDebugScreenKey : NavKey

@Serializable
data class PlaylistScreenKey(val playlistInfo: PlaylistInfo) : NavKey

@Serializable
data class LocalPlaylistScreenKey(val playlistId: String) : NavKey

@Serializable
data object AuthScreenKey : NavKey

@Serializable
data object EqualizerScreenKey : NavKey

@Serializable
data object MusicImportScreenKey : NavKey

@Serializable
data object DownloadManagerScreenKey : NavKey

@Serializable
data class ArtistScreenKey(val artistId: String, val artistName: String) : NavKey

@Serializable
data class ArtistReleasesScreenKey(
    val title: String,
    val browseId: String,
    val params: String = "",
) : NavKey

@Serializable
data class AlbumScreenKey(
    val albumId: String,
    val albumName: String,
    val albumCover: String = "",
) : NavKey

@Serializable
data class MoodCategoryScreenKey(
    val browseId: String,
    val params: String,
    val title: String,
) : NavKey


data class ScreenUiConfig(
    val showBottomBar: Boolean = true,
    val showMiniPlayer: Boolean = true,
    val selectedTab: NavKey? = null
)

@Composable
fun rememberScreenUiConfig(current: NavKey): ScreenUiConfig {
    return remember(current) {
        when (current) {
            HomeScreenKey -> ScreenUiConfig(
                selectedTab = HomeScreenKey
            )

            is SearchScreenKey -> ScreenUiConfig(
                selectedTab = SearchScreenKey()
            )

            LibraryScreenKey -> ScreenUiConfig(
                selectedTab = LibraryScreenKey
            )

            SettingsScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            ListeningStatsScreenKey -> ScreenUiConfig(showBottomBar = false)

            RecoDebugScreenKey -> ScreenUiConfig(showBottomBar = false)

            is PlaylistScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            is LocalPlaylistScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            AuthScreenKey -> ScreenUiConfig(
                showBottomBar = false,
                showMiniPlayer = false
            )

            EqualizerScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            MusicImportScreenKey -> ScreenUiConfig(
                showBottomBar = false,
                showMiniPlayer = false
            )

            DownloadManagerScreenKey -> ScreenUiConfig(showBottomBar = false)

            is ArtistScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            is ArtistReleasesScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            is AlbumScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            is MoodCategoryScreenKey -> ScreenUiConfig(
                showBottomBar = false
            )

            else -> ScreenUiConfig()
        }
    }
}
