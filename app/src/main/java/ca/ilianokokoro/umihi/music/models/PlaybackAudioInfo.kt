package ca.ilianokokoro.umihi.music.models

import android.content.res.Resources
import ca.ilianokokoro.umihi.music.R

data class PlaybackAudioInfo(
    val format: String? = null,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val channelCount: Int? = null,
    val videoHeight: Int? = null,
) {
    fun getDisplayLabel(resources: Resources): String {
        val sep = " ${resources.getString(R.string.dot)} "
        return buildString {
            videoHeight?.let {
                append(resources.getString(R.string.video_height_p, it))
                append(sep)
            }
            sampleRate?.let {
                append(resources.getString(R.string.khz, it / 1000f))
                append(sep)
            }
            bitrate?.let {
                append(resources.getString(R.string.kbps, it / 1000))
                append(sep)
            }
            format?.let { append(it.uppercase()) }
        }.trimEnd(' ', sep.trim().first())
    }
}
