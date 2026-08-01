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

    override fun onCleared() {
        client.stop()
    }
}
