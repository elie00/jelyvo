package ca.ilianokokoro.umihi.music.core.helpers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

object ComposeHelper {

    fun <T : Any> getLazyKey(element: T, id: String, index: Int): String {
        return "${element::class}_${id}_${index}"
    }

    fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun String.getShortErrorFromLog(): String {
        return this
            .lineSequence()
            .take(7)
            .joinToString("\n")
    }

    @Composable
    fun rememberInteractionSource(): MutableInteractionSource {
        return remember { MutableInteractionSource() }
    }

    fun String.getBulletPointsFromMarkdown(): String =
        lineSequence()
            .map { it.trimStart() }
            .filter { it.startsWith("-") }
            .joinToString("\n") { "• " + it.removePrefix("-").trimStart() }

    @Composable
    fun rememberRepeatMode(player: Player?): Int {
        val repeatModeState =
            remember { mutableIntStateOf(player?.repeatMode ?: Player.REPEAT_MODE_OFF) }

        DisposableEffect(player) {
            if (player == null) {
                return@DisposableEffect onDispose {}
            }

            val listener = object : Player.Listener {
                override fun onRepeatModeChanged(repeatMode: Int) {
                    repeatModeState.intValue = repeatMode
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
            }
        }

        return repeatModeState.intValue
    }

    /**
     * Media id of the track the player is on, or null when nothing is loaded. Song.mediaItem sets
     * mediaId to the youtubeId, so a row can compare it against its own song without asking the
     * controller for the whole queue.
     */
    @Composable
    fun rememberCurrentMediaId(player: Player?): String? {
        val mediaIdState = remember { mutableStateOf(player?.currentMediaItem?.mediaId) }

        DisposableEffect(player) {
            if (player == null) {
                mediaIdState.value = null
                return@DisposableEffect onDispose {}
            }

            // The current item can already differ from what the initial remember captured (the
            // controller reconnects, or the row scrolled into view mid-track).
            mediaIdState.value = player.currentMediaItem?.mediaId

            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaIdState.value = mediaItem?.mediaId
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
            }
        }

        return mediaIdState.value
    }

    /** Whether playback is actually running, so a now-playing indicator can rest while paused. */
    @Composable
    fun rememberIsPlaying(player: Player?): Boolean {
        val isPlayingState = remember { mutableStateOf(player?.isPlaying == true) }

        DisposableEffect(player) {
            if (player == null) {
                isPlayingState.value = false
                return@DisposableEffect onDispose {}
            }

            isPlayingState.value = player.isPlaying

            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isPlayingState.value = isPlaying
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
            }
        }

        return isPlayingState.value
    }

    @Composable
    fun rememberShuffleMode(player: Player?): Boolean {
        val shuffleModeState =
            remember { mutableStateOf(player?.shuffleModeEnabled == true) }

        DisposableEffect(player) {
            if (player == null) {
                return@DisposableEffect onDispose {}
            }

            val listener = object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    shuffleModeState.value = shuffleModeEnabled
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
            }
        }

        return shuffleModeState.value
    }

}
