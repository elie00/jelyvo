package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatastoreRepositoryInstrumentedTest {

    @Test
    fun repositoryConstructionDoesNotReenterSharedSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DatastoreRepository(context)

        assertEquals(
            DatastoreRepository.AudioQuality.High,
            repository.audioQuality.first(),
        )
        assertEquals(false, repository.dataSaverOnMetered.first())
    }
}
