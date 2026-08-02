package ca.ilianokokoro.umihi.music.wearapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.ilianokokoro.umihi.music.wear.WearPlaybackContract

class WearPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val client = WearPlaybackClient(application)
    val state = client.state

    init {
        client.start()
    }

    fun previous() = client.send(WearPlaybackContract.COMMAND_PREVIOUS)
    fun togglePlayback() = client.send(WearPlaybackContract.COMMAND_TOGGLE)
    fun next() = client.send(WearPlaybackContract.COMMAND_NEXT)
    fun seekBack() = client.send(WearPlaybackContract.COMMAND_SEEK_BACK)
    fun seekForward() = client.send(WearPlaybackContract.COMMAND_SEEK_FORWARD)
    fun toggleLike() = client.send(WearPlaybackContract.COMMAND_TOGGLE_LIKE)
    fun toggleShuffle() = client.send(WearPlaybackContract.COMMAND_TOGGLE_SHUFFLE)
    fun cycleRepeat() = client.send(WearPlaybackContract.COMMAND_CYCLE_REPEAT)
    fun volumeUp() = client.send(WearPlaybackContract.COMMAND_VOLUME_UP)
    fun volumeDown() = client.send(WearPlaybackContract.COMMAND_VOLUME_DOWN)
    fun playQueueIndex(index: Int) = client.send(WearPlaybackContract.playQueueCommand(index))
    fun clearError() = client.clearError()

    override fun onCleared() {
        client.stop()
    }
}
