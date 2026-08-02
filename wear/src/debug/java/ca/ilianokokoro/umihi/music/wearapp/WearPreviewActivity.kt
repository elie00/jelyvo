package ca.ilianokokoro.umihi.music.wearapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.MaterialTheme
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import ca.ilianokokoro.umihi.music.wear.WearQueueItem

/** Debug-only visual fixture used to verify every screen without a paired phone. */
class WearPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                JelyvoWearApp(
                    state = WearUiState(
                        playback = previewPlayback,
                        phoneConnected = true,
                        loading = false,
                    ),
                    isAmbient = false,
                    actions = WearPlayerActions(
                        previous = {}, togglePlayback = {}, next = {}, seekBack = {},
                        seekForward = {}, toggleLike = {}, toggleShuffle = {}, cycleRepeat = {},
                        volumeDown = {}, volumeUp = {}, playQueueIndex = {}, clearError = {},
                    ),
                )
            }
        }
    }
}

private val previewPlayback = WearPlaybackSnapshot(
    mediaId = "preview",
    title = "Blinding Lights",
    artist = "The Weeknd",
    artworkUri = "",
    positionMs = 86_000L,
    durationMs = 200_000L,
    isPlaying = true,
    hasPrevious = true,
    hasNext = true,
    isLiked = true,
    shuffleEnabled = true,
    repeatMode = 2,
    deviceVolume = 9,
    maxDeviceVolume = 15,
    queueIndex = 1,
    queue = listOf(
        WearQueueItem("1", "Save Your Tears", "The Weeknd", ""),
        WearQueueItem("preview", "Blinding Lights", "The Weeknd", ""),
        WearQueueItem("3", "Starboy", "The Weeknd, Daft Punk", ""),
        WearQueueItem("4", "Die For You", "The Weeknd", ""),
    ),
    updatedAtMs = System.currentTimeMillis(),
)
