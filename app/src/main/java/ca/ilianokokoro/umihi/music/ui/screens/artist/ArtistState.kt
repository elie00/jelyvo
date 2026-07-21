package ca.ilianokokoro.umihi.music.ui.screens.artist

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.ArtistPage

@Immutable
sealed class ArtistState {
    data object Loading : ArtistState()
    data class Success(val page: ArtistPage) : ArtistState()
    data class Error(val exception: Exception) : ArtistState()
}
