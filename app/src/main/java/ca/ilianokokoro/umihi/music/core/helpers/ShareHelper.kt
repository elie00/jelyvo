package ca.ilianokokoro.umihi.music.core.helpers

import android.content.Context
import android.content.Intent
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.Song

/**
 * Builds and launches the ACTION_SEND chooser for a track. Local (MediaStore) files have no
 * YouTube URL to share, so callers hide the action for them.
 */
object ShareHelper {

    /**
     * "Title • Artist" plus the YouTube Music watch URL, optionally anchored at a playback
     * position ("&t=42s") so the recipient lands mid-track.
     */
    fun shareSong(context: Context, song: Song, positionSeconds: Long? = null) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(
                    R.string.share_song_text,
                    song.title,
                    song.artist,
                    // music.youtube.com rather than Song.youtubeUrl (www.youtube.com): the shared
                    // link should open YouTube Music, while youtubeUrl also feeds playback URIs.
                    shareUrl("${Constants.YoutubeApi.ORIGIN}/watch?v=${song.youtubeId}", positionSeconds),
                )
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    /**
     * Appends the position marker to a watch URL. The prefix already carries "?v=", so the
     * timestamp joins with "&". Non-positive positions share the plain URL — "&t=0s" would only
     * add noise.
     */
    internal fun shareUrl(youtubeUrl: String, positionSeconds: Long?): String {
        if (positionSeconds == null || positionSeconds <= 0L) {
            return youtubeUrl
        }
        return "$youtubeUrl&t=${positionSeconds}s"
    }
}
