package ca.ilianokokoro.umihi.music.ui.screens.settings

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.backup.BackupManager
import ca.ilianokokoro.umihi.music.core.managers.VersionManager
import ca.ilianokokoro.umihi.music.core.spotify.SpotifyConstants
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.dialog.ConfirmDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.AudioQualityDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.ThemeColorDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.audioQualityLabel
import ca.ilianokokoro.umihi.music.ui.components.dialog.themeColorLabel
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.BooleanSettingItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsSection
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SliderSettingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@Composable
fun SettingsScreen(
    openAuthScreen: () -> Unit,
    openEqualizerScreen: () -> Unit,
    openSpotifyImport: () -> Unit,
    openListeningStats: () -> Unit,
    application: Application,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(application))
) {
    val uiState = settingsViewModel.uiState.collectAsStateWithLifecycle().value

    // Refresh when returning to the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.getSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FadingStatusBarWrapper { statusBarHeight ->
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp)
        ) { paddingValues ->
            when (val screenState = uiState.screenState) {
                ScreenState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingAnimation()
                    }
                }

                is ScreenState.Error -> {
                    ErrorMessage(
                        ex = screenState.exception,
                        onRetry = settingsViewModel::getSettings
                    )
                }

                is ScreenState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = statusBarHeight,
                                bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING + paddingValues.calculateBottomPadding()
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Tapping the title repeatedly toggles the special language easter egg
                        // (previously triggered from the bottom bar's Settings tab).
                        val titleClickCount = remember { mutableIntStateOf(0) }
                        val context = LocalContext.current
                        val resources = LocalResources.current
                        val scope = rememberCoroutineScope()
                        val datastoreRepository = remember { DatastoreRepository(context) }
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                titleClickCount.intValue++
                                if (titleClickCount.intValue == Constants.Locale.Special.CLICK_QUANTITY) {
                                    titleClickCount.intValue = 0
                                    scope.launch {
                                        val oldSettings = datastoreRepository.settings.first()
                                        datastoreRepository.save(
                                            PreferenceKeys.USE_SPECIAL_LANGUAGE,
                                            !oldSettings.useSpecialLanguage
                                        )

                                        Toast.makeText(
                                            context,
                                            if (oldSettings.useSpecialLanguage) {
                                                resources.getString(R.string.special_language_disabled)
                                            } else {
                                                resources.getString(R.string.special_language_enabled)
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                        (context as Activity).recreate()
                                    }
                                }
                            }
                        )


                        SettingsSection(
                            title = stringResource(R.string.app_info)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.current_version),
                                subtitle = VersionManager.getVersionName(),
                                leadingIcon = Icons.Outlined.Info,
                                onClick = { }
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.account)
                        ) {
                            if (settingsViewModel.isLoggedIn()) {
                                SettingsItem(
                                    title = stringResource(R.string.log_out),
                                    subtitle = stringResource(R.string.logged_in_message),
                                    leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                                    onClick = settingsViewModel::logOut
                                )
                            } else {
                                SettingsItem(
                                    title = stringResource(R.string.log_in),
                                    subtitle = stringResource(R.string.logged_out_message),
                                    leadingIcon = Icons.AutoMirrored.Outlined.Login,
                                    onClick = openAuthScreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                title = stringResource(R.string.clear_login_info),
                                subtitle = stringResource(R.string.clear_login_message),
                                leadingIcon = Icons.Outlined.Delete,
                                onClick = settingsViewModel::clearLogins
                            )
                            // No client id compiled in -> the OAuth flow can only fail, so do not
                            // advertise it. See SpotifyConstants.
                            if (SpotifyConstants.IS_CONFIGURED) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SettingsItem(
                                    title = stringResource(R.string.import_from_spotify_title),
                                    subtitle = stringResource(R.string.import_from_spotify_subtitle),
                                    leadingIcon = Icons.Outlined.LibraryMusic,
                                    onClick = openSpotifyImport
                                )
                            }
                        }

                        SettingsSection(
                            title = stringResource(R.string.appearance)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.theme_color_title),
                                subtitle = stringResource(
                                    R.string.theme_color_subtitle,
                                    themeColorLabel(screenState.settings.themeColor)
                                ),
                                leadingIcon = Icons.Outlined.Palette,
                                onClick = {
                                    settingsViewModel.updateShowThemeColorDialog(true)
                                }
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.general)
                        ) {
                            BooleanSettingItem(
                                title = stringResource(R.string.show_podcast_playlist_title),
                                subtitle = stringResource(R.string.show_podcast_playlist_description),
                                leadingIcon = Icons.AutoMirrored.Outlined.FeaturedPlayList,
                                value = screenState.settings.showPodcastPlaylist,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.SHOW_PODCAST_PLAYLIST,
                                        it
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.keep_screen_on_title),
                                subtitle = stringResource(R.string.keep_screen_on_title_description),
                                leadingIcon = Icons.Outlined.StayCurrentPortrait,
                                value = screenState.settings.keepScreenOn,
                                onToggle = settingsViewModel::updateKeepScreenOnSetting
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.send_playback_data_title),
                                subtitle = stringResource(R.string.send_playback_data_description),
                                leadingIcon = Icons.Outlined.History,
                                value = screenState.settings.sendPlaybackData,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.SEND_PLAYBACK_DATA,
                                        it
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.notify_new_releases_title),
                                subtitle = stringResource(R.string.notify_new_releases_description),
                                leadingIcon = Icons.Outlined.NotificationsActive,
                                value = screenState.settings.notifyNewReleases,
                                onToggle = settingsViewModel::updateNotifyNewReleasesSetting
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.playback)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.listening_stats),
                                subtitle = stringResource(R.string.listening_stats_subtitle),
                                leadingIcon = Icons.Outlined.Leaderboard,
                                onClick = openListeningStats,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.autoplay_title),
                                subtitle = stringResource(R.string.autoplay_subtitle),
                                leadingIcon = Icons.Outlined.Radio,
                                value = screenState.settings.autoplay,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.AUTOPLAY,
                                        it
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                title = stringResource(R.string.not_interested_title),
                                subtitle = if (uiState.notInterestedCount == 0) {
                                    stringResource(R.string.not_interested_empty)
                                } else {
                                    pluralStringResource(
                                        R.plurals.not_interested_count,
                                        uiState.notInterestedCount,
                                        uiState.notInterestedCount,
                                    )
                                },
                                leadingIcon = Icons.Outlined.NotInterested,
                                onClick = {
                                    if (uiState.notInterestedCount > 0) {
                                        settingsViewModel.updateShowNotInterestedClearConfirm(true)
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.enable_audio_offload),
                                subtitle = stringResource(R.string.audio_offload_subtitle),
                                leadingIcon = Icons.Outlined.Memory,
                                value = screenState.settings.useAudioOffload,
                                onToggle = settingsViewModel::updateAudioOffloadSetting
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val crossfadeSeconds = screenState.settings.crossfadeSeconds
                            SliderSettingItem(
                                title = stringResource(R.string.crossfade_title),
                                subtitle = if (crossfadeSeconds <= 0) {
                                    stringResource(R.string.crossfade_disabled)
                                } else {
                                    stringResource(R.string.crossfade_seconds, crossfadeSeconds)
                                },
                                leadingIcon = Icons.Outlined.Waves,
                                value = crossfadeSeconds,
                                valueRange = 0..12,
                                onValueChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.CROSSFADE_SECONDS,
                                        it
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                title = stringResource(R.string.equalizer_title),
                                subtitle = stringResource(R.string.equalizer_subtitle),
                                leadingIcon = Icons.Outlined.GraphicEq,
                                onClick = openEqualizerScreen
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.data_and_storage)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.audio_quality_title),
                                subtitle = stringResource(
                                    R.string.audio_quality_subtitle,
                                    audioQualityLabel(screenState.settings.audioQuality),
                                ),
                                leadingIcon = Icons.Outlined.HighQuality,
                                onClick = {
                                    settingsViewModel.updateShowAudioQualityDialog(true)
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.data_saver_title),
                                subtitle = stringResource(R.string.data_saver_description),
                                leadingIcon = Icons.Outlined.DataSaverOn,
                                value = screenState.settings.dataSaverOnMetered,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.DATA_SAVER_ON_METERED,
                                        it,
                                    )
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BooleanSettingItem(
                                title = stringResource(R.string.download_on_metered_title),
                                subtitle = stringResource(R.string.download_on_metered_description),
                                leadingIcon = Icons.Outlined.CloudDownload,
                                value = screenState.settings.downloadOnMetered,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.DOWNLOAD_ON_METERED,
                                        it
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                title = stringResource(R.string.delete_downloads),
                                subtitle = stringResource(R.string.clear_data_message),
                                leadingIcon = Icons.Outlined.Delete,
                                onClick = {
                                    settingsViewModel.updateShowDownloadDeleteConfirm(true)
                                }
                            )
                        }

                        val backupLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/json")
                        ) { uri ->
                            uri?.let { settingsViewModel.backupData(it) }
                        }
                        val restoreLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            uri?.let { settingsViewModel.requestRestore(it) }
                        }
                        SettingsSection(
                            title = stringResource(R.string.backup_restore_section)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.backup_data_title),
                                subtitle = stringResource(R.string.backup_data_subtitle),
                                leadingIcon = Icons.Outlined.Backup,
                                onClick = {
                                    if (!uiState.backupInProgress) {
                                        backupLauncher.launch(BackupManager.SUGGESTED_FILE_NAME)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                title = stringResource(R.string.restore_data_title),
                                subtitle = stringResource(R.string.restore_data_subtitle),
                                leadingIcon = Icons.Outlined.SettingsBackupRestore,
                                onClick = {
                                    if (!uiState.backupInProgress) {
                                        restoreLauncher.launch(arrayOf("application/json"))
                                    }
                                }
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.smart_offline)
                        ) {
                            BooleanSettingItem(
                                title = stringResource(R.string.auto_download_title),
                                subtitle = stringResource(R.string.auto_download_description),
                                leadingIcon = Icons.Outlined.CloudSync,
                                value = screenState.settings.autoDownload,
                                onToggle = settingsViewModel::updateAutoDownloadSetting
                            )
                            if (screenState.settings.autoDownload) {
                                Spacer(modifier = Modifier.height(4.dp))
                                BooleanSettingItem(
                                    title = stringResource(R.string.auto_download_liked_title),
                                    subtitle = stringResource(R.string.auto_download_liked_description),
                                    leadingIcon = Icons.Outlined.FavoriteBorder,
                                    value = screenState.settings.autoDownloadLiked,
                                    onToggle = {
                                        settingsViewModel.updateSetting(
                                            PreferenceKeys.AUTO_DOWNLOAD_LIKED,
                                            it
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BooleanSettingItem(
                                    title = stringResource(R.string.auto_download_wifi_only_title),
                                    subtitle = stringResource(R.string.auto_download_wifi_only_description),
                                    leadingIcon = Icons.Outlined.Wifi,
                                    value = screenState.settings.autoDownloadWifiOnly,
                                    onToggle = settingsViewModel::updateAutoDownloadWifiOnlySetting
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val quotaGb = screenState.settings.autoDownloadQuotaGb
                                SliderSettingItem(
                                    title = stringResource(R.string.auto_download_quota_title),
                                    subtitle = stringResource(
                                        R.string.auto_download_quota_subtitle,
                                        quotaGb
                                    ),
                                    leadingIcon = Icons.Outlined.Storage,
                                    value = quotaGb,
                                    valueRange = Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_MIN..
                                            Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_MAX,
                                    onValueChange = {
                                        settingsViewModel.updateSetting(
                                            PreferenceKeys.AUTO_DOWNLOAD_QUOTA_GB,
                                            it
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.showThemeColorDialog) {
                            ThemeColorDialog(
                                selectedOption = screenState.settings.themeColor,
                                onChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.THEME_COLOR,
                                        it.toString()
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowThemeColorDialog(false)
                                }
                            )
                        } else if (uiState.showAudioQualityDialog) {
                            AudioQualityDialog(
                                selectedOption = screenState.settings.audioQuality,
                                onChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.AUDIO_QUALITY,
                                        it.toString(),
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowAudioQualityDialog(false)
                                },
                            )
                        } else if (uiState.showDownloadDeleteConfirm) {
                            ConfirmDialog(
                                title = stringResource(R.string.download_clear_confirm_title),
                                text = stringResource(R.string.download_clear_confirm_text),
                                onConfirm = {
                                    settingsViewModel.clearDownloads()
                                    settingsViewModel.updateShowDownloadDeleteConfirm(false)
                                },
                                onDismiss = {
                                    settingsViewModel.updateShowDownloadDeleteConfirm(false)
                                }
                            )
                        } else if (uiState.showRestoreConfirm) {
                            ConfirmDialog(
                                title = stringResource(R.string.restore_confirm_title),
                                text = stringResource(R.string.restore_confirm_text),
                                onConfirm = settingsViewModel::confirmRestore,
                                onDismiss = {
                                    settingsViewModel.updateShowRestoreConfirm(false)
                                }
                            )
                        } else if (uiState.showNotInterestedClearConfirm) {
                            ConfirmDialog(
                                title = stringResource(
                                    R.string.not_interested_clear_confirm_title
                                ),
                                text = stringResource(
                                    R.string.not_interested_clear_confirm_text
                                ),
                                onConfirm = {
                                    settingsViewModel.clearNotInterested()
                                    settingsViewModel.updateShowNotInterestedClearConfirm(false)
                                },
                                onDismiss = {
                                    settingsViewModel.updateShowNotInterestedClearConfirm(false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
