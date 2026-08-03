package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import ca.ilianokokoro.umihi.music.wear.WearPlaybackContract
import ca.ilianokokoro.umihi.music.wear.WearPlaybackHandoff
import ca.ilianokokoro.umihi.music.wear.WearQueueItem
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import ca.ilianokokoro.umihi.music.wear.encode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearUiState(
    val playback: WearPlaybackSnapshot = WearPlaybackSnapshot(),
    val phoneConnected: Boolean = false,
    val loading: Boolean = true,
    val commandPending: Boolean = false,
    val commandError: String? = null,
)

class WearPlaybackClient(context: Context) :
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val playbackCache = WearPlaybackCache(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var handoffAccepted: (() -> Unit)? = null
    private val _state = MutableStateFlow(WearUiState())
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    fun start() {
        dataClient.addListener(this)
        messageClient.addListener(this)
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
        messageClient.removeListener(this)
    }

    fun send(command: String) {
        _state.value = _state.value.copy(commandPending = true, commandError = null)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            _state.value = _state.value.copy(phoneConnected = nodes.isNotEmpty(), loading = false)
            val phone = nodes.firstOrNull()
            if (phone == null) {
                _state.value = _state.value.copy(
                    commandPending = false,
                    commandError = "phone_unavailable",
                )
                return@addOnSuccessListener
            }
            messageClient.sendMessage(
                phone.id,
                WearPlaybackContract.COMMAND_PATH,
                command.toByteArray(Charsets.UTF_8),
            ).addOnFailureListener {
                _state.value = _state.value.copy(
                    commandPending = false,
                    commandError = "send_failed",
                )
            }
        }.addOnFailureListener {
            _state.value = _state.value.copy(
                phoneConnected = false,
                loading = false,
                commandPending = false,
                commandError = "phone_unavailable",
            )
        }
    }

    fun sendHandoff(handoff: WearPlaybackHandoff, onAccepted: () -> Unit) {
        _state.value = _state.value.copy(commandPending = true, commandError = null)
        handoffAccepted = onAccepted
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            _state.value = _state.value.copy(phoneConnected = nodes.isNotEmpty(), loading = false)
            val phone = nodes.firstOrNull()
            if (phone == null) {
                failHandoff("phone_unavailable")
                return@addOnSuccessListener
            }
            messageClient.sendMessage(
                phone.id,
                WearPlaybackContract.HANDOFF_TO_PHONE_PATH,
                handoff.encode(),
            ).addOnFailureListener { failHandoff("send_failed") }
        }.addOnFailureListener {
            _state.value = _state.value.copy(phoneConnected = false, loading = false)
            failHandoff("phone_unavailable")
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(commandError = null)
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

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPlaybackContract.RESULT_PATH) return
        val result = WearPlaybackContract.parseResult(event.data.toString(Charsets.UTF_8)) ?: return
        _state.value = _state.value.copy(
            commandPending = false,
            commandError = result.reason.takeUnless { result.success },
        )
        if (result.command == WearPlaybackContract.COMMAND_HANDOFF_TO_PHONE) {
            val callback = handoffAccepted
            handoffAccepted = null
            if (result.success && callback != null) mainHandler.post(callback)
        }
    }

    private fun readSnapshot(item: com.google.android.gms.wearable.DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val snapshot = map.toSnapshot()
        playbackCache.save(snapshot)
        _state.value = _state.value.copy(
            playback = snapshot,
            loading = false,
            phoneConnected = true,
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

    private fun failHandoff(reason: String) {
        handoffAccepted = null
        _state.value = _state.value.copy(commandPending = false, commandError = reason)
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
    isLiked = getBoolean(WearPlaybackContract.KEY_IS_LIKED),
    shuffleEnabled = getBoolean(WearPlaybackContract.KEY_SHUFFLE_ENABLED),
    repeatMode = getInt(WearPlaybackContract.KEY_REPEAT_MODE),
    deviceVolume = getInt(WearPlaybackContract.KEY_DEVICE_VOLUME),
    maxDeviceVolume = getInt(WearPlaybackContract.KEY_MAX_DEVICE_VOLUME),
    isDeviceMuted = getBoolean(WearPlaybackContract.KEY_IS_DEVICE_MUTED),
    queue = queueItems(),
    queueIndex = getInt(WearPlaybackContract.KEY_QUEUE_INDEX),
    queueOffset = getInt(WearPlaybackContract.KEY_QUEUE_OFFSET),
    updatedAtMs = getLong(WearPlaybackContract.KEY_UPDATED_AT_MS),
)

private fun DataMap.queueItems(): List<WearQueueItem> {
    val ids = getStringArrayList(WearPlaybackContract.KEY_QUEUE_MEDIA_IDS).orEmpty()
    val titles = getStringArrayList(WearPlaybackContract.KEY_QUEUE_TITLES).orEmpty()
    val artists = getStringArrayList(WearPlaybackContract.KEY_QUEUE_ARTISTS).orEmpty()
    val artworks = getStringArrayList(WearPlaybackContract.KEY_QUEUE_ARTWORK_URIS).orEmpty()
    val size = minOf(ids.size, titles.size, artists.size, artworks.size)
    return List(size) { index ->
        WearQueueItem(ids[index], titles[index], artists[index], artworks[index])
    }
}
