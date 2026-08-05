package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.TrackKind
import ca.ilianokokoro.umihi.music.core.domain.TrackSource
import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTrackAdapterTest {
    /**
     * Every field the UI reads back after a page has crossed the catalog seam. Album, artist and
     * playlist pages are converted to the neutral model and straight back, so anything missing
     * here is silently dropped for a whole screen — that is how video playback, the disc split
     * and the offline markers each broke in turn.
     */
    @Test
    fun `a full round trip keeps everything the screens depend on`() {
        val original = Song(
            youtubeId = "id",
            title = "Title",
            artist = "Artist",
            duration = "3:07",
            thumbnailHref = "https://art",
            thumbnailPath = "/data/art.jpg",
            audioFilePath = "/data/audio.webm",
            isExplicit = true,
            isLiked = true,
        ).also {
            it.artistId = "UCartist"
            it.albumId = "MPREalbum"
            it.albumName = "Album"
            it.trackNumber = 4
            it.discNumber = 2
            it.playCount = "45 M plays"
            it.viewCount = "1,2 M views"
            it.isVideo = true
            it.forceVideo = true
        }

        val restored = original.toTrack().toSong()

        assertEquals(original.youtubeId, restored.youtubeId)
        assertEquals(original.title, restored.title)
        assertEquals(original.artist, restored.artist)
        assertEquals(original.duration, restored.duration)
        assertEquals(original.thumbnailHref, restored.thumbnailHref)
        assertEquals(original.thumbnailPath, restored.thumbnailPath)
        assertEquals(original.audioFilePath, restored.audioFilePath)
        assertEquals(original.isExplicit, restored.isExplicit)
        assertEquals(original.isLiked, restored.isLiked)
        assertEquals(original.artistId, restored.artistId)
        assertEquals(original.albumId, restored.albumId)
        assertEquals(original.albumName, restored.albumName)
        assertEquals(original.trackNumber, restored.trackNumber)
        assertEquals(original.discNumber, restored.discNumber)
        assertEquals(original.playCount, restored.playCount)
        assertEquals(original.viewCount, restored.viewCount)
        assertEquals(original.isVideo, restored.isVideo)
        assertEquals(original.forceVideo, restored.forceVideo)
        // Derived from audioFilePath + thumbnailPath: the playlist download indicators read it.
        assertTrue(original.downloaded)
        assertTrue(restored.downloaded)
    }

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
    fun `round trips the play-the-clip choice`() {
        // Losing this flag silently resolved the audio stream for a video request, which played
        // as a black screen. The data source rebuilds its Song through Track, so it must survive.
        val clip = Song(youtubeId = "clip", title = "Clip").also { it.forceVideo = true }

        val track = clip.toTrack()
        assertTrue(track.preferVideoStream)
        assertTrue(track.toSong().forceVideo)

        val audio = Song(youtubeId = "audio", title = "Audio")
        assertFalse(audio.toTrack().preferVideoStream)
        assertFalse(audio.toTrack().toSong().forceVideo)
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
