package ca.ilianokokoro.umihi.music.wear

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Stable Data Layer contract shared by the phone and Wear OS applications. */
object WearPlaybackContract {
    const val STATE_PATH = "/jelyvo/playback/state"
    const val COMMAND_PATH = "/jelyvo/playback/command"
    const val RESULT_PATH = "/jelyvo/playback/result"
    const val HANDOFF_TO_PHONE_PATH = "/jelyvo/playback/handoff-to-phone"

    const val COMMAND_PLAY = "play"
    const val COMMAND_PAUSE = "pause"
    const val COMMAND_TOGGLE = "toggle"
    const val COMMAND_PREVIOUS = "previous"
    const val COMMAND_NEXT = "next"
    const val COMMAND_SEEK_BACK = "seek_back_10"
    const val COMMAND_SEEK_FORWARD = "seek_forward_10"
    const val COMMAND_TOGGLE_LIKE = "toggle_like"
    /** Runs account mutations in the phone playback process when the Wear bridge is remote. */
    const val SESSION_COMMAND_TOGGLE_LIKE =
        "ca.ilianokokoro.umihi.music.command.TOGGLE_LIKE"
    const val SESSION_COMMAND_PUBLISH_STATE =
        "ca.ilianokokoro.umihi.music.command.PUBLISH_WEAR_STATE"
    const val COMMAND_TOGGLE_SHUFFLE = "toggle_shuffle"
    const val COMMAND_CYCLE_REPEAT = "cycle_repeat"
    const val COMMAND_VOLUME_UP = "volume_up"
    const val COMMAND_VOLUME_DOWN = "volume_down"
    const val COMMAND_REQUEST_STATE = "request_state"
    const val COMMAND_PLAY_QUEUE_PREFIX = "play_queue:"
    const val COMMAND_HANDOFF_TO_PHONE = "handoff_to_phone"

    const val RESULT_OK = "ok"
    const val RESULT_ERROR = "error"
    const val RESULT_SEPARATOR = "|"

    const val KEY_MEDIA_ID = "media_id"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ARTWORK_URI = "artwork_uri"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_HAS_PREVIOUS = "has_previous"
    const val KEY_HAS_NEXT = "has_next"
    const val KEY_IS_LIKED = "is_liked"
    const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    const val KEY_REPEAT_MODE = "repeat_mode"
    const val KEY_DEVICE_VOLUME = "device_volume"
    const val KEY_MAX_DEVICE_VOLUME = "max_device_volume"
    const val KEY_IS_DEVICE_MUTED = "is_device_muted"
    const val KEY_QUEUE_MEDIA_IDS = "queue_media_ids"
    const val KEY_QUEUE_TITLES = "queue_titles"
    const val KEY_QUEUE_ARTISTS = "queue_artists"
    const val KEY_QUEUE_ARTWORK_URIS = "queue_artwork_uris"
    const val KEY_QUEUE_INDEX = "queue_index"
    const val KEY_QUEUE_OFFSET = "queue_offset"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"

    fun playQueueCommand(index: Int): String = "$COMMAND_PLAY_QUEUE_PREFIX$index"

    fun queueIndexFromCommand(command: String): Int? = command
        .takeIf { it.startsWith(COMMAND_PLAY_QUEUE_PREFIX) }
        ?.removePrefix(COMMAND_PLAY_QUEUE_PREFIX)
        ?.toIntOrNull()

    fun resultPayload(success: Boolean, command: String, reason: String = ""): String =
        listOf(if (success) RESULT_OK else RESULT_ERROR, command, reason)
            .joinToString(RESULT_SEPARATOR)

    fun parseResult(payload: String): WearCommandResult? {
        val parts = payload.split(RESULT_SEPARATOR, limit = 3)
        if (parts.size < 2 || parts.first() !in setOf(RESULT_OK, RESULT_ERROR)) return null
        return WearCommandResult(
            success = parts.first() == RESULT_OK,
            command = parts[1],
            reason = parts.getOrElse(2) { "" },
        )
    }
}

data class WearPlaybackHandoff(
    val queue: List<WearQueueItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean = true,
)

fun WearPlaybackHandoff.encode(): ByteArray {
    require(queue.isNotEmpty()) { "A playback handoff requires at least one queue item" }
    require(currentIndex in queue.indices) { "The current index must belong to the queue" }
    val safeQueueStart = (currentIndex - HANDOFF_ITEMS_BEFORE_CURRENT).coerceAtLeast(0)
    val safeQueue = queue.drop(safeQueueStart).take(MAX_HANDOFF_QUEUE_SIZE)
    val safeCurrentIndex = currentIndex - safeQueueStart
    return ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(HANDOFF_FORMAT_VERSION)
            output.writeInt(safeQueue.size)
            safeQueue.forEach { item ->
                output.writeUTF(item.mediaId.take(MAX_HANDOFF_FIELD_LENGTH))
                output.writeUTF(item.title.take(MAX_HANDOFF_FIELD_LENGTH))
                output.writeUTF(item.artist.take(MAX_HANDOFF_FIELD_LENGTH))
                output.writeUTF(item.artworkUri.take(MAX_HANDOFF_FIELD_LENGTH))
            }
            output.writeInt(safeCurrentIndex)
            output.writeLong(positionMs.coerceAtLeast(0L))
            output.writeBoolean(playWhenReady)
        }
        bytes.toByteArray()
    }
}

fun decodeWearPlaybackHandoff(payload: ByteArray): WearPlaybackHandoff? = runCatching {
    DataInputStream(ByteArrayInputStream(payload)).use { input ->
        if (input.readInt() != HANDOFF_FORMAT_VERSION) return@runCatching null
        val queueSize = input.readInt()
        if (queueSize !in 1..MAX_HANDOFF_QUEUE_SIZE) return@runCatching null
        val queue = List(queueSize) {
            WearQueueItem(
                mediaId = input.readUTF(),
                title = input.readUTF(),
                artist = input.readUTF(),
                artworkUri = input.readUTF(),
            )
        }
        val index = input.readInt()
        if (index !in queue.indices || queue.any { it.mediaId.isBlank() }) return@runCatching null
        WearPlaybackHandoff(
            queue = queue,
            currentIndex = index,
            positionMs = input.readLong().coerceAtLeast(0L),
            playWhenReady = input.readBoolean(),
        )
    }
}.getOrNull()

data class WearQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
)

private const val HANDOFF_FORMAT_VERSION = 1
private const val MAX_HANDOFF_QUEUE_SIZE = 50
private const val MAX_HANDOFF_FIELD_LENGTH = 4_000
private const val HANDOFF_ITEMS_BEFORE_CURRENT = 3

data class WearCommandResult(
    val success: Boolean,
    val command: String,
    val reason: String = "",
)

data class WearPlaybackSnapshot(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUri: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isLiked: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val deviceVolume: Int = 0,
    val maxDeviceVolume: Int = 0,
    val isDeviceMuted: Boolean = false,
    val queue: List<WearQueueItem> = emptyList(),
    val queueIndex: Int = 0,
    val queueOffset: Int = 0,
    val updatedAtMs: Long = 0L,
) {
    val hasMedia: Boolean get() = mediaId.isNotBlank() || title.isNotBlank()

    fun estimatedPositionMs(nowMs: Long): Long {
        val elapsed = if (isPlaying) (nowMs - updatedAtMs).coerceAtLeast(0L) else 0L
        val upperBound = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        return (positionMs + elapsed).coerceIn(0L, upperBound)
    }
}
