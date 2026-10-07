package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class WatchLibraryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = WatchLibraryStore(context)
    private val song = WatchSong("aB0-_123456", "Test", "Artist", "")

    @Before fun before() { store.clearDownloads(); context.getSharedPreferences("standalone_library", 0).edit().clear().commit() }
    @After fun after() { store.clearDownloads(); context.getSharedPreferences("standalone_library", 0).edit().clear().commit() }

    private fun partial(): File = File.createTempFile("test-", ".partial", store.audioFile(song.videoId).parentFile).apply { writeText("complete audio") }

    @Test fun clearInvalidatesInFlightDownload() {
        val token = store.downloadToken(song.videoId)
        store.clearDownloads()
        val partial = partial()
        assertFalse(store.publishDownload(song, partial, token))
        assertFalse(store.audioFile(song.videoId).exists())
        partial.delete()
    }

    @Test fun removeInvalidatesOnlyThatDownload() {
        val token = store.downloadToken(song.videoId)
        val otherToken = store.downloadToken("bB0-_123456")
        store.removeDownload(song.videoId)
        val partial = partial()
        assertFalse(store.publishDownload(song, partial, token))
        assertEquals(otherToken, store.downloadToken("bB0-_123456"))
        partial.delete()
    }

    @Test fun completedDownloadIsAtomicAndSurvivesNewStore() {
        val partial = partial()
        assertTrue(store.publishDownload(song, partial, store.downloadToken(song.videoId)))
        assertFalse(partial.exists())
        assertEquals(listOf(song), WatchLibraryStore(context).downloads())
        store.removeDownload(song.videoId)
        assertTrue(store.downloads().isEmpty())
    }

    @Test fun independentInstancesDoNotLoseConcurrentFavorites() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 20).map { index -> executor.submit {
                WatchLibraryStore(context).toggleFavorite(song.copy(videoId = index.toString().padStart(11, '0')))
            } }
            futures.forEach { it.get() }
            assertEquals(20, store.favorites().size)
        } finally { executor.shutdownNow() }
    }

    @Test fun recoversMetadataAfterProcessStoppedBetweenRenameAndIndexWrite() {
        store.audioFile(song.videoId).writeText("complete audio")
        assertTrue(store.downloads().isEmpty())
        assertTrue(store.registerExistingDownload(song, store.downloadToken(song.videoId)))
        assertEquals(listOf(song), store.downloads())
    }

    @Test fun invalidIdentifiersCannotEscapeAudioDirectory() {
        assertThrows(IllegalArgumentException::class.java) { store.audioFile("../../prefs") }
    }
}
