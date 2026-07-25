package ca.ilianokokoro.umihi.music.core.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupLocalPlaylistTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun legacyBackupWithoutDescriptionRemainsCompatible() {
        val playlist = json.decodeFromString<BackupLocalPlaylist>(
            """{"id":"road","name":"Road trip","songsJson":"[]","createdAt":42}"""
        )

        assertNull(playlist.description)
    }

    @Test
    fun descriptionSurvivesBackupRoundTrip() {
        val playlist = BackupLocalPlaylist(
            id = "focus",
            name = "Focus",
            songsJson = "[]",
            createdAt = 84,
            description = "Deep work",
        )

        val restored = json.decodeFromString<BackupLocalPlaylist>(
            json.encodeToString(playlist)
        )

        assertEquals(playlist, restored)
    }
}
