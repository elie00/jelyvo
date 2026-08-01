package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import ca.ilianokokoro.umihi.music.wear.WearPlaybackContract
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearUiState(
    val playback: WearPlaybackSnapshot = WearPlaybackSnapshot(),
    val phoneConnected: Boolean = false,
    val loading: Boolean = true,
)

class WearPlaybackClient(context: Context) : DataClient.OnDataChangedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val _state = MutableStateFlow(WearUiState())
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    fun start() {
        dataClient.addListener(this)
        dataClient.dataItems
            .addOnSuccessListener { items ->
                try {
                    items.firstOrNull { it.uri.path == WearPlaybackContract.STATE_PATH }
                        ?.let(::readSnapshot)
                } finally {
                    items.release()
                }
                refreshConnection(requestState = true)
            }
            .addOnFailureListener { refreshConnection(requestState = true) }
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    fun send(command: String) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            _state.value = _state.value.copy(phoneConnected = nodes.isNotEmpty(), loading = false)
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    WearPlaybackContract.COMMAND_PATH,
                    command.toByteArray(Charsets.UTF_8),
                )
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearPlaybackContract.STATE_PATH
            ) {
                readSnapshot(event.dataItem)
            }
        }
    }

    private fun readSnapshot(item: com.google.android.gms.wearable.DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        _state.value = _state.value.copy(
            playback = map.toSnapshot(),
            loading = false,
        )
    }

    private fun refreshConnection(requestState: Boolean) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            _state.value = _state.value.copy(
                phoneConnected = nodes.isNotEmpty(),
                loading = false,
            )
            if (requestState) {
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearPlaybackContract.COMMAND_PATH,
                        WearPlaybackContract.COMMAND_REQUEST_STATE.toByteArray(Charsets.UTF_8),
                    )
                }
            }
        }.addOnFailureListener {
            _state.value = _state.value.copy(phoneConnected = false, loading = false)
        }
    }
}

private fun DataMap.toSnapshot() = WearPlaybackSnapshot(
    mediaId = getString(WearPlaybackContract.KEY_MEDIA_ID).orEmpty(),
    title = getString(WearPlaybackContract.KEY_TITLE).orEmpty(),
    artist = getString(WearPlaybackContract.KEY_ARTIST).orEmpty(),
    artworkUri = getString(WearPlaybackContract.KEY_ARTWORK_URI).orEmpty(),
    positionMs = getLong(WearPlaybackContract.KEY_POSITION_MS),
    durationMs = getLong(WearPlaybackContract.KEY_DURATION_MS),
    isPlaying = getBoolean(WearPlaybackContract.KEY_IS_PLAYING),
    hasPrevious = getBoolean(WearPlaybackContract.KEY_HAS_PREVIOUS),
    hasNext = getBoolean(WearPlaybackContract.KEY_HAS_NEXT),
    updatedAtMs = getLong(WearPlaybackContract.KEY_UPDATED_AT_MS),
)
