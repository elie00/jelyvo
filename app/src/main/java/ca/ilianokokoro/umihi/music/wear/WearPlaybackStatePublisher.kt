package ca.ilianokokoro.umihi.music.wear

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.DataClient

/** Publishes the latest phone playback snapshot as a persistent Wear OS DataItem. */
class WearPlaybackStatePublisher(context: Context) {
    // Keep playback fully functional on de-Googled phones. The Wearable client is optional and
    // Google Play services availability failures must never take the media service down.
    private val dataClient: DataClient? = runCatching {
        Wearable.getDataClient(context.applicationContext)
    }.onFailure { error ->
        Log.i(TAG, "Wear OS Data Layer is unavailable", error)
    }.getOrNull()

    fun publish(player: Player) {
        val client = dataClient ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L).coerceAtMost(
            duration.takeIf { it > 0L } ?: Long.MAX_VALUE,
        )

        val request = PutDataMapRequest.create(WearPlaybackContract.STATE_PATH).apply {
            dataMap.putString(WearPlaybackContract.KEY_MEDIA_ID, item?.mediaId.orEmpty())
            dataMap.putString(WearPlaybackContract.KEY_TITLE, metadata?.title?.toString().orEmpty())
            dataMap.putString(WearPlaybackContract.KEY_ARTIST, metadata?.artist?.toString().orEmpty())
            dataMap.putString(
                WearPlaybackContract.KEY_ARTWORK_URI,
                metadata?.artworkUri?.toString().orEmpty(),
            )
            dataMap.putLong(WearPlaybackContract.KEY_POSITION_MS, position)
            dataMap.putLong(WearPlaybackContract.KEY_DURATION_MS, duration)
            dataMap.putBoolean(WearPlaybackContract.KEY_IS_PLAYING, player.isPlaying)
            dataMap.putBoolean(WearPlaybackContract.KEY_HAS_PREVIOUS, player.hasPreviousMediaItem())
            dataMap.putBoolean(WearPlaybackContract.KEY_HAS_NEXT, player.hasNextMediaItem())
            dataMap.putLong(WearPlaybackContract.KEY_UPDATED_AT_MS, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        client.putDataItem(request).addOnFailureListener { error ->
            Log.w(TAG, "Unable to publish playback state to Wear OS", error)
        }
    }

    private companion object {
        const val TAG = "WearPlaybackPublisher"
    }
}
