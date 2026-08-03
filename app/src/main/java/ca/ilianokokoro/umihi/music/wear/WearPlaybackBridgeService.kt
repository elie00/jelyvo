package ca.ilianokokoro.umihi.music.wear

import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ca.ilianokokoro.umihi.music.core.managers.LikeManager
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.services.PlaybackService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives playback commands and acknowledges them to the paired Jelyvo Wear OS application. */
class WearPlaybackBridgeService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearPlaybackContract.COMMAND_PATH) return
        val command = messageEvent.data.toString(Charsets.UTF_8)
        if (command == WearPlaybackContract.COMMAND_TOGGLE_LIKE) {
            toggleLike(messageEvent.sourceNodeId, command)
            return
        }

        withController(
            onError = { sendResult(messageEvent.sourceNodeId, command, false, "phone_unavailable") },
        ) { controller ->
            val success = handlePlaybackCommand(controller, command)
            WearPlaybackStatePublisher(applicationContext).publish(controller)
            sendResult(
                nodeId = messageEvent.sourceNodeId,
                command = command,
                success = success,
                reason = if (success) "" else "unsupported_command",
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handlePlaybackCommand(controller: MediaController, command: String): Boolean {
        val queueIndex = WearPlaybackContract.queueIndexFromCommand(command)
        if (queueIndex != null) {
            if (queueIndex !in 0 until controller.mediaItemCount) return false
            controller.seekToDefaultPosition(queueIndex)
            controller.play()
            return true
        }

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
            WearPlaybackContract.COMMAND_SEEK_BACK -> controller.seekTo(
                (controller.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L),
            )
            WearPlaybackContract.COMMAND_SEEK_FORWARD -> controller.seekTo(
                (controller.currentPosition + SEEK_STEP_MS).coerceAtMost(
                    controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE,
                ),
            )
            WearPlaybackContract.COMMAND_TOGGLE_SHUFFLE -> {
                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            }
            WearPlaybackContract.COMMAND_CYCLE_REPEAT -> {
                controller.repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            WearPlaybackContract.COMMAND_VOLUME_UP -> controller.increaseDeviceVolume()
            WearPlaybackContract.COMMAND_VOLUME_DOWN -> controller.decreaseDeviceVolume()
            WearPlaybackContract.COMMAND_REQUEST_STATE -> Unit
            else -> return false
        }
        return true
    }

    private fun toggleLike(nodeId: String, command: String) {
        withController(
            onError = { sendResult(nodeId, command, false, "phone_unavailable") },
        ) { controller ->
            val song = controller.currentMediaItem.toSong()
            if (song.youtubeId.isBlank()) {
                sendResult(nodeId, command, false, "no_media")
                return@withController
            }

            val currentStatus = LikeManager.isLiked(song)
            serviceScope.launch {
                val success = LikeManager.setLikedAndWait(
                    song = song,
                    liked = !currentStatus,
                    context = applicationContext,
                    currentStatus = currentStatus,
                )
                withController(
                    onError = { sendResult(nodeId, command, success, if (success) "" else "login_required") },
                ) { updatedController ->
                    WearPlaybackStatePublisher(applicationContext).publish(updatedController)
                    sendResult(nodeId, command, success, if (success) "" else "login_required")
                }
            }
        }
    }

    private fun withController(
        onError: () -> Unit,
        action: (MediaController) -> Unit,
    ) {
        val mainExecutor = ContextCompat.getMainExecutor(this)

        // MediaController binds itself to the Looper used by its Builder. WearableListenerService
        // invokes us from a Google Play services thread, so both construction and every access must
        // be moved to the main executor. Building on the callback thread and merely completing the
        // future on main crashes the phone with "MediaController method ... wrong thread".
        mainExecutor.execute controllerSetup@{
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val future = runCatching {
                MediaController.Builder(this, token).buildAsync()
            }.onFailure {
                Log.w(TAG, "Unable to start phone playback controller", it)
            }.getOrNull() ?: run {
                onError()
                return@controllerSetup
            }

            future.addListener(
                {
                    val controller = runCatching { future.get() }
                        .onFailure {
                            Log.w(TAG, "Unable to connect to phone playback session", it)
                        }
                        .getOrNull()
                    if (controller == null) {
                        onError()
                    } else {
                        try {
                            action(controller)
                        } catch (error: RuntimeException) {
                            Log.e(TAG, "Wear OS playback command failed", error)
                            onError()
                        } finally {
                            controller.release()
                        }
                    }
                },
                mainExecutor,
            )
        }
    }

    private fun sendResult(
        nodeId: String,
        command: String,
        success: Boolean,
        reason: String,
    ) {
        val payload = WearPlaybackContract.resultPayload(success, command, reason)
        Wearable.getMessageClient(applicationContext)
            .sendMessage(
                nodeId,
                WearPlaybackContract.RESULT_PATH,
                payload.toByteArray(Charsets.UTF_8),
            )
            .addOnFailureListener { Log.w(TAG, "Unable to acknowledge Wear OS command", it) }
    }

    private companion object {
        const val TAG = "WearPlaybackBridge"
        const val RESTART_TRACK_THRESHOLD_MS = 5_000L
        const val SEEK_STEP_MS = 10_000L
    }
}
