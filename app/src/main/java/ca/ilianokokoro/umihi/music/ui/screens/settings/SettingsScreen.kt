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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.backup.BackupManager
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.core.managers.VersionManager
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FrostedPageBar
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.ProfileAvatar
import ca.ilianokokoro.umihi.music.ui.components.dialog.AudioQualityDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.ConfirmDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.SleepTimerDefaultDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.ThemeColorDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.ThemePresetDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.VideoQualityDialog
import ca.ilianokokoro.umihi.music.ui.components.dialog.audioQualityLabel
import ca.ilianokokoro.umihi.music.ui.components.dialog.themeColorLabel
import ca.ilianokokoro.umihi.music.ui.components.dialog.themePresetLabel
import ca.ilianokokoro.umihi.music.ui.components.dialog.videoQualityLabel
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.BooleanSettingItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.JamDialog
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.LastFmDialog
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.ListenBrainzDialog
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsRowDivider
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsSection
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SliderSettingItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.StorageDialog
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.storageCategoryLabel
import ca.ilianokokoro.umihi.music.ui.theme.ThemePreset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


/** How far the page scrolls before the bar's glass and title are fully in. */
private val SETTINGS_TITLE_COLLAPSE_DISTANCE = 56.dp

@Composable
fun SettingsScreen(
    openAuthScreen: () -> Unit,
    openEqualizerScreen: () -> Unit,
    openMusicImport: () -> Unit,
    openListeningStats: () -> Unit,
    openRecoDebug: () -> Unit,
    openDownloadManager: () -> Unit,
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

    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val collapseDistancePx = remember(density) {
        with(density) { SETTINGS_TITLE_COLLAPSE_DISTANCE.toPx() }
    }
    val collapseProgress by remember(collapseDistancePx) {
        derivedStateOf { (scrollState.value / collapseDistancePx).coerceIn(0f, 1f) }
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
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
                    Box(Modifier.padding(top = statusBarHeight)) {
                        ErrorMessage(
                            ex = screenState.exception,
                            onRetry = settingsViewModel::getSettings,
                        )
                    }
                }

                is ScreenState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                            .verticalScroll(scrollState)
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = statusBarHeight + 8.dp,
                                bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING + paddingValues.calculateBottomPadding()
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable(
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
                            title = stringResource(R.string.profile)
                        ) {
                            ProfileItem(
                                name = screenState.settings.profileName,
                                avatarPath = screenState.settings.profileAvatarPath,
                                onClick = {
                                    settingsViewModel.updateShowProfileDialog(true)
                                }
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
                                    onClick = {
                                        settingsViewModel.updateShowLogOutConfirm(true)
                                    }
                                )
                            } else {
                                SettingsItem(
                                    title = stringResource(R.string.log_in),
                                    subtitle = stringResource(R.string.logged_out_message),
                                    leadingIcon = Icons.AutoMirrored.Outlined.Login,
                                    onClick = openAuthScreen
                                )
                            }
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.clear_login_info),
                                subtitle = stringResource(R.string.clear_login_message),
                                leadingIcon = Icons.Outlined.Delete,
                                onClick = settingsViewModel::clearLogins
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.music_import_title),
                                subtitle = stringResource(R.string.music_import_settings_subtitle),
                                leadingIcon = Icons.Outlined.LibraryMusic,
                                onClick = openMusicImport
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.appearance)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.theme_preset_title),
                                subtitle = themePresetLabel(screenState.settings.themePreset),
                                leadingIcon = Icons.Outlined.Brush,
                                onClick = {
                                    settingsViewModel.updateShowThemePresetDialog(true)
                                }
                            )
                            // L'accent n'a de sens que sous le theme Jelyvo : les autres presets
                            // imposent leurs propres couleurs.
                            if (screenState.settings.themePreset ==
                                ThemePreset.Jelyvo
                            ) {
                                SettingsRowDivider()
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
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.prefer_fastest_refresh_title),
                                subtitle = stringResource(R.string.prefer_fastest_refresh_description),
                                leadingIcon = Icons.Outlined.Speed,
                                value = screenState.settings.preferFastestRefresh,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.PREFER_FASTEST_REFRESH,
                                        it
                                    )
                                }
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.keep_screen_on_title),
                                subtitle = stringResource(R.string.keep_screen_on_title_description),
                                leadingIcon = Icons.Outlined.StayCurrentPortrait,
                                value = screenState.settings.keepScreenOn,
                                onToggle = settingsViewModel::updateKeepScreenOnSetting
                            )
                            SettingsRowDivider()
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
                            SettingsRowDivider()
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
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.download_manager_title),
                                subtitle = stringResource(R.string.download_manager_subtitle),
                                leadingIcon = Icons.Outlined.CloudDownload,
                                onClick = openDownloadManager,
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.reco_debug_title),
                                subtitle = stringResource(R.string.reco_debug_subtitle),
                                leadingIcon = Icons.AutoMirrored.Outlined.Rule,
                                onClick = openRecoDebug,
                            )
                            SettingsRowDivider()
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
                            SettingsRowDivider()
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
                                enabled = uiState.notInterestedCount > 0,
                                onClick = {
                                    settingsViewModel.updateShowNotInterestedClearConfirm(true)
                                },
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.enable_audio_offload),
                                subtitle = stringResource(R.string.audio_offload_subtitle),
                                leadingIcon = Icons.Outlined.Memory,
                                value = screenState.settings.useAudioOffload,
                                onToggle = settingsViewModel::updateAudioOffloadSetting
                            )
                            SettingsRowDivider()
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
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.automix_title),
                                subtitle = stringResource(R.string.automix_description),
                                leadingIcon = Icons.Outlined.AutoAwesome,
                                value = screenState.settings.automix,
                                onToggle = {
                                    settingsViewModel.updateSetting(PreferenceKeys.AUTOMIX, it)
                                }
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.skip_silence_title),
                                subtitle = stringResource(R.string.skip_silence_description),
                                leadingIcon = Icons.Outlined.FastForward,
                                value = screenState.settings.skipSilence,
                                onToggle = {
                                    settingsViewModel.updateSetting(PreferenceKeys.SKIP_SILENCE, it)
                                }
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.smart_transitions_title),
                                subtitle = stringResource(R.string.smart_transitions_description),
                                leadingIcon = Icons.Outlined.Waves,
                                value = screenState.settings.smartTransitions,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.SMART_TRANSITIONS,
                                        it
                                    )
                                }
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.normalize_volume_title),
                                subtitle = stringResource(R.string.normalize_volume_description),
                                leadingIcon = Icons.AutoMirrored.Outlined.VolumeUp,
                                value = screenState.settings.normalizeVolume,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.NORMALIZE_VOLUME,
                                        it
                                    )
                                }
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.resume_on_audio_connect_title),
                                subtitle = stringResource(R.string.resume_on_audio_connect_description),
                                leadingIcon = Icons.Outlined.Headphones,
                                value = screenState.settings.resumeOnAudioConnect,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.RESUME_ON_AUDIO_CONNECT,
                                        it
                                    )
                                }
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.sleep_timer_default_title),
                                subtitle = stringResource(
                                    R.string.minutes,
                                    screenState.settings.sleepTimerDefaultMinutes
                                ),
                                leadingIcon = Icons.Outlined.Timer,
                                onClick = {
                                    settingsViewModel.updateShowSleepTimerDefaultDialog(true)
                                }
                            )
                            SettingsRowDivider()
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
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.video_quality_title),
                                subtitle = stringResource(
                                    R.string.video_quality_subtitle,
                                    videoQualityLabel(screenState.settings.videoQuality),
                                ),
                                leadingIcon = Icons.Outlined.HighQuality,
                                onClick = {
                                    settingsViewModel.updateShowVideoQualityDialog(true)
                                },
                            )
                            SettingsRowDivider()
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
                            SettingsRowDivider()
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
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.storage_title),
                                subtitle = stringResource(R.string.storage_subtitle),
                                leadingIcon = Icons.Outlined.Storage,
                                onClick = {
                                    settingsViewModel.updateShowStorageDialog(true)
                                }
                            )
                            SettingsRowDivider()
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
                                subtitle = if (uiState.backupInProgress) {
                                    stringResource(R.string.backup_in_progress)
                                } else {
                                    stringResource(R.string.backup_data_subtitle)
                                },
                                leadingIcon = Icons.Outlined.Backup,
                                enabled = !uiState.backupInProgress,
                                onClick = {
                                    backupLauncher.launch(BackupManager.SUGGESTED_FILE_NAME)
                                }
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.restore_data_title),
                                subtitle = if (uiState.backupInProgress) {
                                    stringResource(R.string.backup_in_progress)
                                } else {
                                    stringResource(R.string.restore_data_subtitle)
                                },
                                leadingIcon = Icons.Outlined.SettingsBackupRestore,
                                enabled = !uiState.backupInProgress,
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json"))
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
                                SettingsRowDivider()
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
                                SettingsRowDivider()
                                BooleanSettingItem(
                                    title = stringResource(R.string.auto_download_wifi_only_title),
                                    subtitle = stringResource(R.string.auto_download_wifi_only_description),
                                    leadingIcon = Icons.Outlined.Wifi,
                                    value = screenState.settings.autoDownloadWifiOnly,
                                    onToggle = settingsViewModel::updateAutoDownloadWifiOnlySetting
                                )
                                SettingsRowDivider()
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

                        SettingsSection(
                            title = stringResource(R.string.jam_title)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.jam_title),
                                subtitle = (uiState.jamStatus as? JamStatus.InParty)?.let {
                                    stringResource(R.string.jam_share_code, it.code)
                                } ?: stringResource(R.string.jam_subtitle),
                                leadingIcon = Icons.Outlined.Headphones,
                                onClick = { settingsViewModel.updateShowJamDialog(true) }
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.scrobbling_section)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.listenbrainz_title),
                                subtitle = stringResource(
                                    if (uiState.listenBrainzConnected) {
                                        R.string.listenbrainz_connected
                                    } else {
                                        R.string.listenbrainz_subtitle
                                    }
                                ),
                                leadingIcon = Icons.Outlined.GraphicEq,
                                onClick = {
                                    settingsViewModel.updateShowListenBrainzDialog(true)
                                }
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.lastfm_title),
                                subtitle = stringResource(
                                    if (uiState.lastFmConnected) {
                                        R.string.lastfm_connected
                                    } else {
                                        R.string.lastfm_subtitle
                                    }
                                ),
                                leadingIcon = Icons.Outlined.GraphicEq,
                                onClick = {
                                    settingsViewModel.updateShowLastFmDialog(true)
                                }
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.updates)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.check_for_updates),
                                subtitle = stringResource(R.string.check_update_setting_description),
                                leadingIcon = Icons.Outlined.Update,
                                onClick = settingsViewModel::checkForUpdates
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = stringResource(R.string.auto_update_title),
                                subtitle = stringResource(R.string.auto_update_subtitle),
                                leadingIcon = Icons.Outlined.Autorenew,
                                value = screenState.settings.updateChecking,
                                onToggle = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.AUTO_UPDATE,
                                        it
                                    )
                                }
                            )
                        }

                        // Le bloc version vit en bas de page, comme la plupart des apps : c'est une
                        // information de reference, pas un reglage.
                        SettingsSection(
                            title = stringResource(R.string.app_info)
                        ) {
                            SettingsItem(
                                title = stringResource(R.string.current_version),
                                subtitle = VersionManager.getVersionName(),
                                leadingIcon = Icons.Outlined.Info,
                                enabled = false,
                                onClick = { }
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = stringResource(R.string.stability_report_title),
                                subtitle = if (uiState.stabilityReport != null) {
                                    stringResource(R.string.stability_report_available)
                                } else if (uiState.playbackFailureCount > 0) {
                                    stringResource(
                                        R.string.stability_report_playback_failures,
                                        uiState.playbackFailureCount,
                                    )
                                } else {
                                    stringResource(R.string.stability_report_empty)
                                },
                                leadingIcon = Icons.Outlined.BugReport,
                                onClick = settingsViewModel::shareStabilityReport,
                            )
                        }

                        // Signature du fork.
                        Text(
                            text = stringResource(R.string.settings_signature),
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 6.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )

                        if (uiState.showThemePresetDialog) {
                            ThemePresetDialog(
                                selectedOption = screenState.settings.themePreset,
                                onChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.THEME_PRESET,
                                        it.toString()
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowThemePresetDialog(false)
                                }
                            )
                        } else if (uiState.showProfileDialog) {
                            ProfileEditDialog(
                                initialName = screenState.settings.profileName,
                                avatarPath = screenState.settings.profileAvatarPath,
                                onPickAvatar = settingsViewModel::updateProfileAvatar,
                                onSaveName = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.PROFILE_NAME,
                                        it.trim()
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowProfileDialog(false)
                                }
                            )
                        } else if (uiState.showListenBrainzDialog) {
                            ListenBrainzDialog(
                                connected = uiState.listenBrainzConnected,
                                connecting = uiState.listenBrainzConnecting,
                                errorMessage = uiState.listenBrainzError,
                                onConnect = settingsViewModel::connectListenBrainz,
                                onDisconnect = settingsViewModel::disconnectListenBrainz,
                                onClose = {
                                    settingsViewModel.updateShowListenBrainzDialog(false)
                                },
                            )
                        } else if (uiState.showJamDialog) {
                            JamDialog(
                                status = uiState.jamStatus,
                                savedServerUrl = uiState.jamServerUrl,
                                onCreate = settingsViewModel::createJam,
                                onJoin = settingsViewModel::joinJam,
                                onLeave = settingsViewModel::leaveJam,
                                onKick = settingsViewModel::kickJamMember,
                                onSetHostOnlyControl = settingsViewModel::setJamHostOnlyControl,
                                onClose = { settingsViewModel.updateShowJamDialog(false) },
                            )
                        } else if (uiState.showLastFmDialog) {
                            LastFmDialog(
                                connected = uiState.lastFmConnected,
                                busy = uiState.lastFmBusy,
                                errorMessage = uiState.lastFmError,
                                authorizeUrl = uiState.lastFmAuthorizeUrl,
                                onRequestApproval = settingsViewModel::requestLastFmApproval,
                                onApproved = settingsViewModel::finishLastFmApproval,
                                onDisconnect = settingsViewModel::disconnectLastFm,
                                onClose = {
                                    settingsViewModel.updateShowLastFmDialog(false)
                                },
                            )
                        } else if (uiState.showThemeColorDialog) {
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
                        } else if (uiState.showVideoQualityDialog) {
                            VideoQualityDialog(
                                selectedOption = screenState.settings.videoQuality,
                                onChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.VIDEO_QUALITY,
                                        it.toString(),
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowVideoQualityDialog(false)
                                },
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
                        } else if (uiState.showSleepTimerDefaultDialog) {
                            SleepTimerDefaultDialog(
                                selectedMinutes = screenState.settings.sleepTimerDefaultMinutes,
                                onChange = {
                                    settingsViewModel.updateSetting(
                                        PreferenceKeys.SLEEP_TIMER_DEFAULT_MINUTES,
                                        it,
                                    )
                                },
                                onClose = {
                                    settingsViewModel.updateShowSleepTimerDefaultDialog(false)
                                },
                            )
                        } else if (uiState.pendingStoragePurge != null) {
                            ConfirmDialog(
                                title = stringResource(R.string.storage_purge_confirm_title),
                                text = stringResource(
                                    R.string.storage_purge_confirm_text,
                                    storageCategoryLabel(uiState.pendingStoragePurge),
                                ),
                                onConfirm = settingsViewModel::confirmStoragePurge,
                                onDismiss = settingsViewModel::dismissStoragePurge,
                            )
                        } else if (uiState.showStorageDialog) {
                            StorageDialog(
                                usage = uiState.storageUsage,
                                onPurge = settingsViewModel::requestStoragePurge,
                                onClose = {
                                    settingsViewModel.updateShowStorageDialog(false)
                                },
                            )
                        } else if (uiState.showLogOutConfirm) {
                            ConfirmDialog(
                                title = stringResource(R.string.log_out_confirm_title),
                                text = stringResource(R.string.log_out_confirm_text),
                                onConfirm = settingsViewModel::logOut,
                                onDismiss = {
                                    settingsViewModel.updateShowLogOutConfirm(false)
                                }
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

        FrostedPageBar(
            title = stringResource(R.string.settings),
            hazeState = hazeState,
            visibility = collapseProgress,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Ligne de la section Profil : avatar rond (ou silhouette par defaut) + nom choisi. L'identite
 * Google n'est pas modifiable ; ce profil est purement local et n'affecte que l'affichage.
 */
@Composable
private fun ProfileItem(
    name: String,
    avatarPath: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileAvatar(name = name, avatarPath = avatarPath, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { stringResource(R.string.profile_name_placeholder) },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.profile_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Edition du profil local : nom libre + choix d'avatar via le photo picker systeme (aucune
 * permission requise). L'image est copiee en interne par le ViewModel, l'URI du picker n'etant
 * valable que le temps de la session.
 */
@Composable
private fun ProfileEditDialog(
    initialName: String,
    avatarPath: String,
    onPickAvatar: (android.net.Uri) -> Unit,
    onSaveName: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onPickAvatar)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(text = stringResource(R.string.profile_edit_title))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            pickAvatar.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                ) {
                    ProfileAvatar(name = name, avatarPath = avatarPath, size = 96.dp)
                }
                Text(
                    text = stringResource(R.string.profile_avatar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveName(name)
                    onClose()
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClose,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
