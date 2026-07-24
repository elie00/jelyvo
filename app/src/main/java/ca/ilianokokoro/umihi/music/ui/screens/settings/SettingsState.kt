package ca.ilianokokoro.umihi.music.ui.screens.settings

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.UmihiSettings

@Immutable
data class SettingsState(
    val screenState: ScreenState = ScreenState.Loading,
    val showDownloadDeleteConfirm: Boolean = false,
    val showThemeColorDialog: Boolean = false,
    val showRestoreConfirm: Boolean = false,
    val backupInProgress: Boolean = false

)

@Immutable
sealed class ScreenState {
    data class Success(
        val settings: UmihiSettings,
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}