package ca.ilianokokoro.umihi.music.core.helpers

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalAudioDataSource
import ca.ilianokokoro.umihi.music.models.Song

/** An audio file another app asked this one to open. */
object ExternalAudio {
    /** A file's name without its extension, which is the best title a bare file offers. */
    fun titleFromFileName(name: String?): String =
        name?.trim()?.substringBeforeLast('.')?.ifBlank { null } ?: ""

    fun isAudio(mimeType: String?): Boolean = mimeType?.startsWith("audio/") == true

    fun toSong(resolver: ContentResolver, uri: Uri): Song {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return Song(
            youtubeId = "${LocalAudioDataSource.LOCAL_ID_PREFIX}external-${uri.toString().hashCode()}",
            title = titleFromFileName(name),
            artist = "",
            duration = "",
            thumbnailHref = "",
        ).also { song ->
            song.isLocal = true
            song.localUri = uri.toString()
        }
    }
}
