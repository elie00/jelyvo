package ca.ilianokokoro.umihi.music.core.helpers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.screens.stats.components.ReplayPoster
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.models.Song
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds and launches the ACTION_SEND chooser for a track. Local (MediaStore) files have no
 * YouTube URL to share, so callers hide the action for them.
 */
object ShareHelper {
    private const val LYRICS_CARD_DIRECTORY = "lyrics-share"
    private const val LYRICS_CARD_FILE = "lyrics.png"
    private const val LYRICS_CARD_ARTWORK_PX = 480


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
                    shareUrl("${YoutubeApiConstants.ORIGIN}/watch?v=${song.youtubeId}", positionSeconds),
                )
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    /**
     * Shares the lines as an image with the same text beside it, for the apps that drop one or the
     * other. If the image cannot be made, the text still goes.
     */
    suspend fun shareLyricsCard(context: Context, song: Song, passage: String) {
        val text = passage.trim()
        if (text.isEmpty()) return
        val uri = runCatching { writeLyricsCard(context, song, text) }.getOrNull()
        if (uri == null) {
            shareLyrics(context, song, passage)
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, lyricsShareText(context, song, text))
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    /** The year as an image. Nothing else goes with it: the poster already says everything. */
    suspend fun shareReplay(context: Context, title: String, rows: List<Pair<String, String>>) {
        val uri = withContext(Dispatchers.IO) {
            val poster = ReplayPoster.render(title, rows)
            val directory = File(context.cacheDir, LYRICS_CARD_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "replay.png")
            file.outputStream().use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    private suspend fun writeLyricsCard(context: Context, song: Song, passage: String): Uri {
        val artwork = song.thumbnailHref.takeIf(String::isNotBlank)?.let { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(LYRICS_CARD_ARTWORK_PX)
                .build()
            SingletonImageLoader.get(context).execute(request).image?.toBitmap()
        }
        return withContext(Dispatchers.IO) {
            val card = LyricsCard.render(passage, song.title, song.artist, artwork)
            // One file, overwritten each time: nothing here is worth keeping in the cache.
            val directory = File(context.cacheDir, LYRICS_CARD_DIRECTORY).apply { mkdirs() }
            val file = File(directory, LYRICS_CARD_FILE)
            file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private fun lyricsShareText(context: Context, song: Song, text: String): String =
        context.getString(
            R.string.share_lyrics_text,
            text,
            song.title,
            song.artist,
            "${YoutubeApiConstants.ORIGIN}/watch?v=${song.youtubeId}",
        )

    fun shareLyrics(context: Context, song: Song, passage: String) {
        val text = passage.trim()
        if (text.isEmpty()) return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, lyricsShareText(context, song, text))
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
