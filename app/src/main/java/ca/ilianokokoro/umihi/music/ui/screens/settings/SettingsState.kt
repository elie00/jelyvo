package ca.ilianokokoro.umihi.music.ui.screens.settings

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.StorageCategory
import ca.ilianokokoro.umihi.music.core.StorageUsage
import ca.ilianokokoro.umihi.music.core.diagnostics.StabilityReport
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.models.UmihiSettings

@Immutable
data class SettingsState(
    val screenState: ScreenState = ScreenState.Loading,
    val showDownloadDeleteConfirm: Boolean = false,
    val showLogOutConfirm: Boolean = false,
    val showThemeColorDialog: Boolean = false,
    val showThemePresetDialog: Boolean = false,
    val showProfileDialog: Boolean = false,
    val showAudioQualityDialog: Boolean = false,
    val showVideoQualityDialog: Boolean = false,
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
    val stabilityReport: StabilityReport? = null,
    val showListenBrainzDialog: Boolean = false,
    val listenBrainzConnected: Boolean = false,
    val listenBrainzConnecting: Boolean = false,
    val listenBrainzError: Int? = null,
    val showLastFmDialog: Boolean = false,
    val lastFmConnected: Boolean = false,
    val lastFmBusy: Boolean = false,
    val lastFmError: Int? = null,
    // Set while the user is approving the app in their browser.
    val lastFmAuthorizeUrl: String? = null,
    val showJamDialog: Boolean = false,
    val jamStatus: JamStatus = JamStatus.Idle,
    val jamServerUrl: String = "",
)

@Immutable
sealed class ScreenState {
    data class Success(
        val settings: UmihiSettings,
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
