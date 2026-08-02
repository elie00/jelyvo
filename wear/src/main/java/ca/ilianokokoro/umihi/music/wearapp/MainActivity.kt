package ca.ilianokokoro.umihi.music.wearapp

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
    private val viewModel: WearPlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val ambientManager = rememberAmbientModeManager()
            CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
                MaterialTheme {
                    JelyvoWearApp(
                        state = state,
                        isAmbient = ambientManager.currentAmbientMode is AmbientMode.Ambient,
                        actions = WearPlayerActions(
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
                    )
                }
            }
        }
    }
}
