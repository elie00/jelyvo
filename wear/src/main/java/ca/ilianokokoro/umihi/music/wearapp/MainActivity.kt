package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLocale.wrap(newBase))
    }

    private val viewModel: WearPlaybackViewModel by viewModels()
    private val standaloneViewModel: StandalonePlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val standaloneState by standaloneViewModel.state.collectAsStateWithLifecycle()
            val ambientManager = rememberAmbientModeManager()
            CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
                MaterialTheme {
                    JelyvoWatchRoot(
                        remoteState = state,
                        standaloneState = standaloneState,
                        isAmbient = ambientManager.currentAmbientMode is AmbientMode.Ambient,
                        remoteActions = WearPlayerActions(
                            previous = viewModel::previous,
                            togglePlayback = viewModel::togglePlayback,
                            next = viewModel::next,
                            seekBack = viewModel::seekBack,
                            seekForward = viewModel::seekForward,
                            toggleLike = viewModel::toggleLike,
                            toggleShuffle = viewModel::toggleShuffle,
                            cycleRepeat = viewModel::cycleRepeat,
                            volumeDown = viewModel::volumeDown,
                            volumeUp = viewModel::volumeUp,
                            playQueueIndex = viewModel::playQueueIndex,
                            clearError = viewModel::clearError,
                        ),
                        standaloneActions = StandaloneActions(
                            setQuery = standaloneViewModel::setQuery,
                            search = standaloneViewModel::search,
                            refreshHome = standaloneViewModel::refreshHome,
                            play = standaloneViewModel::play,
                            playQueueIndex = standaloneViewModel::playQueueIndex,
                            togglePlayback = standaloneViewModel::togglePlayback,
                            previous = standaloneViewModel::previous,
                            next = standaloneViewModel::next,
                            seekBack = standaloneViewModel::seekBack,
                            seekForward = standaloneViewModel::seekForward,
                            toggleFavorite = standaloneViewModel::toggleFavorite,
                            toggleDownload = standaloneViewModel::toggleDownload,
                            toggleShuffle = standaloneViewModel::toggleShuffle,
                            cycleRepeat = standaloneViewModel::cycleRepeat,
                            clearDownloads = standaloneViewModel::clearDownloads,
                            setSmartDownloadLimit = standaloneViewModel::setSmartDownloadLimit,
                            toggleWifiOnly = standaloneViewModel::toggleWifiOnly,
                            toggleRestrictedMode = standaloneViewModel::toggleRestrictedMode,
                            clearError = standaloneViewModel::clearError,
                            resumeFromPhone = { snapshot ->
                                standaloneViewModel.resumeFromPhone(snapshot, viewModel::pause)
                            },
                            continueOnPhone = {
                                standaloneViewModel.handoffToPhone()?.let { handoff ->
                                    viewModel.continueOnPhone(
                                        handoff,
                                        standaloneViewModel::pausePlayback,
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
}
