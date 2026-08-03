package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.TrackKind
import ca.ilianokokoro.umihi.music.core.domain.TrackSource
import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTrackAdapterTest {
    @Test
    fun `maps canonical YouTube metadata into the shared model`() {
        val song = Song(
            youtubeId = "video-id",
            title = "Title",
            artist = "Artist",
            duration = "4:07",
            thumbnailHref = "https://art",
            isExplicit = true,
            isLiked = true,
        ).also {
            it.artistId = "artist-id"
            it.albumId = "album-id"
            it.albumName = "Album"
            it.isVideo = true
        }

        val track = song.toTrack()

        assertEquals("video-id", track.id)
        assertEquals("artist-id", track.artist.id)
        assertEquals("Artist", track.artist.name)
        assertEquals("album-id", track.album?.id)
        assertEquals(247_000L, track.durationMs)
        assertEquals(TrackKind.VIDEO, track.kind)
        assertTrue(track.explicit)
        assertEquals(true, track.liked)
    }

    @Test
    fun `round trips local playback identity through the Android adapter`() {
        val original = Song(
            youtubeId = "local-id",
            title = "Offline",
            artist = "Local artist",
            duration = "1:02:03",
            thumbnailHref = "content://art",
        ).also {
            it.isLocal = true
            it.localUri = "content://audio"
        }

        val restored = original.toTrack().toSong()

        assertEquals(original.youtubeId, restored.youtubeId)
        assertEquals(original.title, restored.title)
        assertEquals(original.artist, restored.artist)
        assertEquals(original.duration, restored.duration)
        assertEquals(TrackSource.LOCAL_DEVICE, original.toTrack().source)
        assertTrue(restored.isLocal)
        assertEquals("content://audio", restored.localUri)
        assertFalse(restored.isVideo)
    }

    @Test
    fun `round trips collection position and play count`() {
        val original = Song(
            youtubeId = "album-track",
            title = "Track 7",
            artist = "Artist",
        ).also {
            it.trackNumber = 7
            it.playCount = "45 M plays"
        }

        val track = original.toTrack()
        assertEquals(7, track.positionInCollection)
        assertEquals("45 M plays", track.playCountLabel)

        val restored = track.toSong()
        assertEquals(7, restored.trackNumber)
        assertEquals("45 M plays", restored.playCount)
    }
}
