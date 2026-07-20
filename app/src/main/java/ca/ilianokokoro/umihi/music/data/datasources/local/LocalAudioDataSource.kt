package ca.ilianokokoro.umihi.music.data.datasources.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's on-device music via [MediaStore]. Each track is mapped to a [Song] flagged
 * [Song.isLocal] and carrying a playable content:// [Song.localUri], so the existing player pipeline
 * plays it directly without resolving a YouTube stream.
 */
class LocalAudioDataSource(private val context: Context) {

    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder =
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val trackUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri =
                    ContentUris.withAppendedId(ALBUM_ART_URI, cursor.getLong(albumIdColumn))

                songs.add(
                    Song(
                        youtubeId = "$LOCAL_ID_PREFIX$id",
                        title = cursor.getString(titleColumn) ?: "",
                        artist = cursor.getString(artistColumn)
                            ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                            ?: "",
                        duration = formatDuration(cursor.getLong(durationColumn)),
                        thumbnailHref = albumArtUri.toString(),
                    ).also { song ->
                        song.isLocal = true
                        song.localUri = trackUri.toString()
                    }
                )
            }
        }

        songs
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return ""
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val LOCAL_ID_PREFIX = "local:"
        private val ALBUM_ART_URI = "content://media/external/audio/albumart".toUri()
    }
}
