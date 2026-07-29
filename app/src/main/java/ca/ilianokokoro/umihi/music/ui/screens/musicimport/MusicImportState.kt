package ca.ilianokokoro.umihi.music.ui.screens.musicimport

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.importing.ExternalMusicPlaylist
import ca.ilianokokoro.umihi.music.models.importing.MusicImportProgress

@Immutable
data class MusicImportState(
    val link: String = "",
    val playlist: ExternalMusicPlaylist? = null,
    val screenState: MusicImportScreenState = MusicImportScreenState.Ready,
)

sealed interface MusicImportScreenState {
    data object Ready : MusicImportScreenState
    data object Resolving : MusicImportScreenState
    data object Preview : MusicImportScreenState
    data class Importing(val progress: MusicImportProgress) : MusicImportScreenState
    data class Complete(val progress: MusicImportProgress) : MusicImportScreenState
    data class Error(val exception: Exception) : MusicImportScreenState
}
