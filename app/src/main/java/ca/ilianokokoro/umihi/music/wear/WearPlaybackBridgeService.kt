package ca.ilianokokoro.umihi.music.wear

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import ca.ilianokokoro.umihi.music.services.PlaybackService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Receives playback commands and acknowledges them to the paired Jelyvo Wear OS application. */
class WearPlaybackBridgeService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearPlaybackContract.COMMAND_PATH) return
        val command = messageEvent.data.toString(Charsets.UTF_8)
        if (command == WearPlaybackContract.COMMAND_TOGGLE_LIKE) {
            sendSessionCommand(
                nodeId = messageEvent.sourceNodeId,
                command = command,
                sessionAction = WearPlaybackContract.SESSION_COMMAND_TOGGLE_LIKE,
                failureReason = "login_required",
            )
            return
        }
        if (command == WearPlaybackContract.COMMAND_REQUEST_STATE) {
            sendSessionCommand(
                nodeId = messageEvent.sourceNodeId,
                command = command,
                sessionAction = WearPlaybackContract.SESSION_COMMAND_PUBLISH_STATE,
                failureReason = "phone_unavailable",
            )
            return
        }

        withController(
            onError = { sendResult(messageEvent.sourceNodeId, command, false, "phone_unavailable") },
        ) { controller ->
            val success = handlePlaybackCommand(controller, command)
            sendResult(
                nodeId = messageEvent.sourceNodeId,
                command = command,
                success = success,
                reason = if (success) "" else "unsupported_command",
            )
        }
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
            else -> return false
        }
        return true
    }

    private fun sendSessionCommand(
        nodeId: String,
        command: String,
        sessionAction: String,
        failureReason: String,
    ) {
        openController(
            onError = { sendResult(nodeId, command, false, "phone_unavailable") },
        ) { controller ->
            val resultFuture = runCatching {
                controller.sendCustomCommand(
                    SessionCommand(
                        sessionAction,
                        Bundle.EMPTY,
                    ),
                    Bundle.EMPTY,
                )
            }.getOrElse { error ->
                Log.e(TAG, "Unable to send Wear OS like command", error)
                controller.release()
                sendResult(nodeId, command, false, "phone_unavailable")
                return@openController
            }
            val mainExecutor = ContextCompat.getMainExecutor(this)
            resultFuture.addListener(
                {
                    val resultCode = runCatching { resultFuture.get().resultCode }
                        .getOrDefault(SessionResult.RESULT_ERROR_UNKNOWN)
                    controller.release()
                    val success = resultCode == SessionResult.RESULT_SUCCESS
                    sendResult(
                        nodeId,
                        command,
                        success,
                        if (success) "" else failureReason,
                    )
                },
                mainExecutor,
            )
        }
    }

    private fun withController(
        onError: () -> Unit,
        action: (MediaController) -> Unit,
    ) {
        openController(onError) { controller ->
            try {
                action(controller)
            } catch (error: RuntimeException) {
                Log.e(TAG, "Wear OS playback command failed", error)
                onError()
            } finally {
                controller.release()
            }
        }
    }

    private fun openController(
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
                        action(controller)
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
