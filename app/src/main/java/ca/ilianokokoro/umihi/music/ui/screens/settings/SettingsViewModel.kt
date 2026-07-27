package ca.ilianokokoro.umihi.music.ui.screens.settings

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ExoCache
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.core.StorageCategory
import ca.ilianokokoro.umihi.music.core.StorageInspector
import ca.ilianokokoro.umihi.music.core.backup.BackupManager
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.ScreenAwakeManager
import ca.ilianokokoro.umihi.music.core.workers.NewReleasesScheduler
import ca.ilianokokoro.umihi.music.core.workers.OfflineSyncScheduler
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SettingsState())
    val uiState = _uiState.asStateFlow()


    private val _application = application
    private val datastoreRepository = DatastoreRepository(application)
    private val downloadRepository = DownloadRepository(application)

    fun logOut() {
        viewModelScope.launch {
            datastoreRepository.logOut()
            getSettings()
        }
    }

    fun getSettings() {
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            val notInterestedCount = datastoreRepository.notInterested.first().size
            _uiState.update {
                _uiState.value.copy(
                    screenState = ScreenState.Success(settings = settings),
                    notInterestedCount = notInterestedCount,
                )
            }
        }
    }

    fun clearLogins() {
        viewModelScope.launch {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            logOut()
            Toast.makeText(
                _application,
                _application.getString(R.string.login_info_cleared),
                Toast.LENGTH_LONG
            ).show()

        }
    }

    fun updateShowThemeColorDialog(value: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    showThemeColorDialog = value
                )
            }
        }
    }

    fun updateShowThemePresetDialog(value: Boolean) {
        _uiState.update {
            it.copy(showThemePresetDialog = value)
        }
    }

    fun updateShowProfileDialog(value: Boolean) {
        _uiState.update {
            it.copy(showProfileDialog = value)
        }
    }

    /**
     * Copie l'image choisie dans le stockage interne (les URI du photo picker ne sont pas
     * persistants) sous un nom horodate — un nouveau fichier a chaque changement, pour que le
     * cache d'images ne resserve jamais l'ancien avatar — puis enregistre le chemin.
     */
    fun updateProfileAvatar(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(_application.filesDir, "profile").apply { mkdirs() }
                val target = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
                _application.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@launch
                val previous = datastoreRepository.getSettings().profileAvatarPath
                datastoreRepository.save(
                    DatastoreRepository.PreferenceKeys.PROFILE_AVATAR,
                    target.absolutePath
                )
                if (previous.isNotBlank()) {
                    File(previous).delete()
                }
            } catch (e: Exception) {
                LogHelper.printe("Failed to save profile avatar", exception = e)
            }
        }
    }

    fun updateShowAudioQualityDialog(value: Boolean) {
        _uiState.update {
            it.copy(showAudioQualityDialog = value)
        }
    }

    fun updateShowSleepTimerDefaultDialog(value: Boolean) {
        _uiState.update {
            it.copy(showSleepTimerDefaultDialog = value)
        }
    }

    fun updateShowStorageDialog(value: Boolean) {
        _uiState.update {
            it.copy(showStorageDialog = value, pendingStoragePurge = null)
        }
        if (value) {
            refreshStorageUsage()
        }
    }

    fun requestStoragePurge(category: StorageCategory) {
        _uiState.update {
            it.copy(pendingStoragePurge = category)
        }
    }

    fun dismissStoragePurge() {
        _uiState.update {
            it.copy(pendingStoragePurge = null)
        }
    }

    /** Re-measures every category size; the dialog shows "calculating…" while `null`. */
    private fun refreshStorageUsage() {
        _uiState.update { it.copy(storageUsage = null) }
        viewModelScope.launch {
            val usage = StorageInspector.measure(_application)
            _uiState.update { it.copy(storageUsage = usage) }
        }
    }

    @OptIn(UnstableApi::class)
    fun confirmStoragePurge() {
        val category = _uiState.value.pendingStoragePurge ?: return
        _uiState.update { it.copy(pendingStoragePurge = null, storageUsage = null) }
        viewModelScope.launch {
            when (category) {
                StorageCategory.Images -> {
                    val imageLoader = SingletonImageLoader.get(_application)
                    imageLoader.memoryCache?.clear()
                    withContext(Dispatchers.IO) {
                        imageLoader.diskCache?.clear()
                    }
                }

                StorageCategory.Downloads -> {
                    // Same cleanup as the "delete all downloads" entry below.
                    downloadRepository.cancelAllWorks()
                    AppDatabase.clearDownloads(_application)
                    ExoCache(_application).clear()
                    UmihiHelper.getDownloadDirectory(context = _application)
                        .deleteRecursively()
                }

                StorageCategory.ApiCache -> withContext(Dispatchers.IO) {
                    ResponseCache.clear()
                }

                // Measured only: purging the database would wipe playlists and history.
                StorageCategory.Database -> return@launch
            }
            showToast(R.string.storage_purged)
            refreshStorageUsage()
        }
    }

    fun updateShowDownloadDeleteConfirm(value: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    showDownloadDeleteConfirm = value
                )
            }
        }
    }

    fun updateShowNotInterestedClearConfirm(value: Boolean) {
        _uiState.update {
            it.copy(showNotInterestedClearConfirm = value)
        }
    }

    fun clearNotInterested() {
        viewModelScope.launch {
            datastoreRepository.clearNotInterested()
            showToast(R.string.not_interested_cleared)
            getSettings()
        }
    }


    @OptIn(UnstableApi::class)
    fun clearDownloads() {
        viewModelScope.launch {
            downloadRepository.cancelAllWorks()
            AppDatabase.clearDownloads(_application)
            ExoCache(_application).clear()
            UmihiHelper.getDownloadDirectory(context = _application)
                .deleteRecursively()
            Toast.makeText(
                _application,
                _application.getString(R.string.downloads_cleared),
                Toast.LENGTH_LONG
            ).show()
        }
    }


    fun updateAudioOffloadSetting(value: Boolean) {
        PlayerManager.setAudioOffloadEnabled(value)
        updateSetting(
            DatastoreRepository.PreferenceKeys.USE_AUDIO_OFFLOAD,
            value
        )
    }

    fun updateKeepScreenOnSetting(value: Boolean) {
        ScreenAwakeManager.setKeepScreenOn(value)
        updateSetting(
            DatastoreRepository.PreferenceKeys.KEEP_SCREEN_ON,
            value
        )
    }

    fun updateNotifyNewReleasesSetting(value: Boolean) {
        if (value) {
            NewReleasesScheduler.schedule(_application)
        } else {
            NewReleasesScheduler.cancel(_application)
        }
        updateSetting(
            DatastoreRepository.PreferenceKeys.NOTIFY_NEW_RELEASES,
            value
        )
    }


    private fun autoDownloadWifiOnly(): Boolean {
        val state = _uiState.value.screenState
        return (state as? ScreenState.Success)?.settings?.autoDownloadWifiOnly ?: true
    }

    fun updateAutoDownloadSetting(value: Boolean) {
        if (value) {
            val wifiOnly = autoDownloadWifiOnly()
            OfflineSyncScheduler.schedule(_application, wifiOnly)
            // Kick off an immediate sync so enabling the feature does not wait a full period.
            OfflineSyncScheduler.triggerNow(_application, wifiOnly)
        } else {
            OfflineSyncScheduler.cancel(_application)
        }
        updateSetting(
            DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD,
            value
        )
    }

    fun updateAutoDownloadWifiOnlySetting(value: Boolean) {
        // Reschedule so the changed network constraint takes effect when the feature is enabled.
        val state = _uiState.value.screenState
        if ((state as? ScreenState.Success)?.settings?.autoDownload == true) {
            OfflineSyncScheduler.reschedule(_application, value)
        }
        updateSetting(
            DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD_WIFI_ONLY,
            value
        )
    }

    fun updateShowRestoreConfirm(value: Boolean) {
        _uiState.update {
            it.copy(showRestoreConfirm = value)
        }
        if (!value) {
            pendingRestoreUri = null
        }
    }

    /** Uri picked through ACTION_OPEN_DOCUMENT, kept until the user confirms the restore. */
    private var pendingRestoreUri: Uri? = null

    fun requestRestore(uri: Uri) {
        pendingRestoreUri = uri
        _uiState.update {
            it.copy(showRestoreConfirm = true)
        }
    }

    fun backupData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupInProgress = true) }
            try {
                BackupManager.exportToUri(_application, uri)
                showToast(R.string.backup_success)
            } catch (e: Exception) {
                LogHelper.printe("Backup export failed", exception = e)
                showToast(R.string.backup_error)
            } finally {
                _uiState.update { it.copy(backupInProgress = false) }
            }
        }
    }

    fun confirmRestore() {
        val uri = pendingRestoreUri
        updateShowRestoreConfirm(false)
        if (uri == null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(backupInProgress = true) }
            try {
                BackupManager.restoreFromUri(_application, uri)
                showToast(R.string.restore_success)
            } catch (e: Exception) {
                LogHelper.printe("Backup restore failed", exception = e)
                showToast(R.string.restore_error)
            } finally {
                _uiState.update { it.copy(backupInProgress = false) }
                // The restored preferences must be reflected by the screen right away.
                getSettings()
            }
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(
            _application,
            _application.getString(messageRes),
            Toast.LENGTH_LONG
        ).show()
    }

    fun isLoggedIn(): Boolean {
        val state = _uiState.value.screenState
        if (state !is ScreenState.Success) {
            return false
        }
        return !state.settings.cookies.isEmpty()
    }

    fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            datastoreRepository.save(
                key,
                value
            )
            getSettings()
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(application)
            }
        }
    }
}
