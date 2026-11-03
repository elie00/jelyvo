package ca.ilianokokoro.umihi.music.data.datasources.local

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.screens.library.folderPathOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's on-device music via [MediaStore]. Each track is mapped to a [Song] flagged
 * [Song.isLocal] and carrying a playable content:// [Song.localUri], so the existing player pipeline
 * plays it directly without resolving a YouTube stream.
 */
class LocalAudioDataSource(private val context: Context) {

    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        // VOLUME_EXTERNAL spans every mounted volume; EXTERNAL_CONTENT_URI only covers the
        // primary one, so anything on an SD card was invisible.
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // RELATIVE_PATH replaced the file path in Q, which is also when reading the latter stopped being
        // dependable, so each version asks for the one it can trust.
        val folderColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            folderColumnName,
        )

        // IS_MUSIC is a scanner heuristic driven mostly by where the file sits: a track dropped in
        // Download/ is commonly flagged podcast, audiobook, or nothing at all, and filtering on it
        // hid perfectly playable files. Excluding the system sounds instead keeps every real track.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0" +
            " AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0" +
            " AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
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
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val folderColumn = cursor.getColumnIndexOrThrow(folderColumnName)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val trackUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri =
                    ContentUris.withAppendedId(ALBUM_ART_URI, cursor.getLong(albumIdColumn))

                songs.add(
                    Song(
                        youtubeId = "$LOCAL_ID_PREFIX$id",
                        // Untagged files (a common case for imported tracks) have no TITLE;
                        // the file name beats showing an empty row.
                        title = cursor.getString(titleColumn)?.takeIf(String::isNotBlank)
                            ?: cursor.getString(displayNameColumn)?.substringBeforeLast('.')
                            ?: "",
                        artist = cursor.getString(artistColumn)
                            ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                            ?: "",
                        duration = formatDuration(cursor.getLong(durationColumn)),
                        thumbnailHref = albumArtUri.toString(),
                    ).also { song ->
                        song.isLocal = true
                        song.localUri = trackUri.toString()
                        song.localFolder = cursor.getString(folderColumn).let { value ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                folderPathOf(value, null)
                            } else {
                                folderPathOf(null, value)
                            }
                        }
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
