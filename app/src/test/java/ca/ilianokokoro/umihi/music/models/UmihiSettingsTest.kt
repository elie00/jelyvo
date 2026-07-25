package ca.ilianokokoro.umihi.music.models

import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UmihiSettingsTest {

    @Test
    fun `audio defaults preserve the previous highest-bitrate behaviour`() {
        val settings = UmihiSettings(cookies = Cookies(), dataSyncId = null)

        assertEquals(AudioQuality.High, settings.audioQuality)
        assertFalse(settings.dataSaverOnMetered)
    }
}
