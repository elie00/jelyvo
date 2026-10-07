package ca.ilianokokoro.umihi.music.desktop.playback

import ca.ilianokokoro.umihi.music.core.domain.PlaybackProgress
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration

/** All commands and callbacks run on the output's serial thread. Injectable without a toolkit. */
interface AudioOutput {
    fun dispatch(action: () -> Unit)
    fun open(url: String, events: Events)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun volume(value: Double)
    fun close()

    interface Events {
        fun ready()
        fun playing(value: Boolean)
        fun progress(value: PlaybackProgress)
        fun ended()
        fun failed()
    }
}

internal class JavaFxOutput : AudioOutput {
    private var player: MediaPlayer? = null

    override fun dispatch(action: () -> Unit) {
        synchronized(startupLock) {
            if (!started) {
                Platform.startup { Platform.setImplicitExit(false) }
                started = true
            }
        }
        Platform.runLater(action)
    }

    override fun open(url: String, events: AudioOutput.Events) {
        close()
        val media = Media(url)
        val created = MediaPlayer(media)
        player = created
        fun publishProgress() {
            val duration = created.totalDuration.toMillis()
            val position = created.currentTime.toMillis()
            if (duration.isFinite() && duration > 0 && position.isFinite()) {
                events.progress(PlaybackProgress(position.toLong().coerceAtLeast(0), duration.toLong()))
            }
        }
        created.setOnReady { publishProgress(); events.ready() }
        created.setOnPlaying { events.playing(true) }
        created.setOnPaused { events.playing(false) }
        created.setOnStalled { events.playing(false) }
        created.setOnEndOfMedia { events.ended() }
        created.setOnError { events.failed() }
        media.setOnError { events.failed() }
        created.currentTimeProperty().addListener { _, _, _ -> publishProgress() }
        created.totalDurationProperty().addListener { _, _, _ -> publishProgress() }
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun seek(positionMs: Long) { player?.seek(Duration.millis(positionMs.toDouble())) }
    override fun volume(value: Double) { player?.volume = value }

    override fun close() {
        val retired = player ?: return
        player = null
        retired.onReady = null
        retired.onPlaying = null
        retired.onPaused = null
        retired.onStalled = null
        retired.onEndOfMedia = null
        retired.onError = null
        retired.media.onError = null
        retired.stop()
        retired.dispose()
    }

    private companion object {
        val startupLock = Any()
        var started = false
    }
}
