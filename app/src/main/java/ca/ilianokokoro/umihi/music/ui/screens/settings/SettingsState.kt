package ca.ilianokokoro.umihi.music.ui.screens.settings

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.StorageCategory
import ca.ilianokokoro.umihi.music.core.StorageUsage
import ca.ilianokokoro.umihi.music.models.UmihiSettings

@Immutable
data class SettingsState(
    val screenState: ScreenState = ScreenState.Loading,
    val showDownloadDeleteConfirm: Boolean = false,
    val showThemeColorDialog: Boolean = false,
    val showThemePresetDialog: Boolean = false,
    val showProfileDialog: Boolean = false,
    val showAudioQualityDialog: Boolean = false,
    val showSleepTimerDefaultDialog: Boolean = false,
    val showRestoreConfirm: Boolean = false,
    val backupInProgress: Boolean = false,
    val notInterestedCount: Int = 0,
    val showNotInterestedClearConfirm: Boolean = false,
    val showStorageDialog: Boolean = false,
    // null while sizes are being measured ("calculating…" placeholder).
    val storageUsage: StorageUsage? = null,
    // Category awaiting the purge confirmation dialog.
    val pendingStoragePurge: StorageCategory? = null,
)

@Immutable
sealed class ScreenState {
    data class Success(
        val settings: UmihiSettings,
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
