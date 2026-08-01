package ca.ilianokokoro.umihi.music.wear

import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ca.ilianokokoro.umihi.music.services.PlaybackService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives transport commands from the paired Jelyvo Wear OS application. */
class WearPlaybackBridgeService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearPlaybackContract.COMMAND_PATH) return
        val command = messageEvent.data.toString(Charsets.UTF_8)
        withController { controller ->
            when (command) {
                WearPlaybackContract.COMMAND_PLAY -> controller.play()
                WearPlaybackContract.COMMAND_PAUSE -> controller.pause()
                WearPlaybackContract.COMMAND_TOGGLE -> {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }
                WearPlaybackContract.COMMAND_PREVIOUS -> {
                    if (controller.currentPosition > RESTART_TRACK_THRESHOLD_MS) {
                        controller.seekTo(0L)
                    } else {
                        controller.seekToPreviousMediaItem()
                    }
                }
                WearPlaybackContract.COMMAND_NEXT -> controller.seekToNextMediaItem()
                WearPlaybackContract.COMMAND_REQUEST_STATE -> Unit
                else -> return@withController
            }
            WearPlaybackStatePublisher(applicationContext).publish(controller)
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java),
        )
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }
                    .onFailure { Log.w(TAG, "Unable to connect to phone playback session", it) }
                    .getOrNull()
                    ?: return@addListener
                try {
                    action(controller)
                } finally {
                    controller.release()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private companion object {
        const val TAG = "WearPlaybackBridge"
        const val RESTART_TRACK_THRESHOLD_MS = 5_000L
    }
}
