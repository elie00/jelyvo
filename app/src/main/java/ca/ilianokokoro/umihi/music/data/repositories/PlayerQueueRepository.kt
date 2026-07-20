package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.PlayerQueueState
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlayerQueueRepository(context: Context) {
    private val dataSource = AppDatabase.getInstance(context).playerQueueRepository()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun save(
        songs: List<Song>,
        currentIndex: Int,
        positionMs: Long,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ) {
        if (songs.isEmpty()) {
            dataSource.clear()
            return
        }
        dataSource.upsert(
            PlayerQueueState(
                itemsJson = json.encodeToString(songs.map(PersistedQueueSong::fromSong)),
                currentIndex = currentIndex.coerceIn(songs.indices),
                positionMs = positionMs.coerceAtLeast(0L),
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
            )
        )
    }

    suspend fun restore(): RestoredQueue? {
        val state = dataSource.get() ?: return null
        val songs = runCatching {
            json.decodeFromString<List<PersistedQueueSong>>(state.itemsJson)
                .map(PersistedQueueSong::toSong)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        return RestoredQueue(
            songs = songs,
            currentIndex = state.currentIndex.coerceIn(songs.indices),
            positionMs = state.positionMs.coerceAtLeast(0L),
            repeatMode = state.repeatMode,
            shuffleEnabled = state.shuffleEnabled,
        )
    }

    data class RestoredQueue(
        val songs: List<Song>,
        val currentIndex: Int,
        val positionMs: Long,
        val repeatMode: Int,
        val shuffleEnabled: Boolean,
    )
}

@Serializable
private data class PersistedQueueSong(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailHref: String,
    val uid: String,
    val isExplicit: Boolean,
    val isLiked: Boolean?,
    val artistId: String?,
    val albumId: String?,
    val albumName: String?,
    val isLocal: Boolean = false,
    val localUri: String? = null,
    val isSuggestion: Boolean = false,
) {
    fun toSong(): Song = Song(
        youtubeId = youtubeId,
        title = title,
        artist = artist,
        duration = duration,
        thumbnailHref = thumbnailHref,
        uid = uid,
        isExplicit = isExplicit,
        isLiked = isLiked,
    ).also { song ->
        song.artistId = artistId
        song.albumId = albumId
        song.albumName = albumName
        song.isLocal = isLocal
        song.localUri = localUri
        song.isSuggestion = isSuggestion
    }

    companion object {
        fun fromSong(song: Song) = PersistedQueueSong(
            youtubeId = song.youtubeId,
            title = song.title,
            artist = song.artist,
            duration = song.duration,
            thumbnailHref = song.thumbnailPath ?: song.thumbnailHref,
            uid = song.uid,
            isExplicit = song.isExplicit,
            isLiked = song.isLiked,
            artistId = song.artistId,
            albumId = song.albumId,
            albumName = song.albumName,
            isLocal = song.isLocal,
            localUri = song.localUri,
            isSuggestion = song.isSuggestion,
        )
    }
}
