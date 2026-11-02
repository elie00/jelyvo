package ca.ilianokokoro.umihi.music

import android.app.Application
import android.os.Looper
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Runs what a launch runs. A crash at startup is invisible to tests of isolated pieces, and it is
 * the one failure that makes every other feature unreachable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppStartupTest {
    @Test
    fun `the settings repository can be constructed`() {
        // It reads the process-wide settings flow, which is itself built from a repository: an
        // eager read in the constructor recursed until the stack overflowed.
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertNotNull(DatastoreRepository(context))
    }

    @Test
    fun `the main activity starts`() {
        // This environment answers a service binding with an empty component name, which media3 cannot
        // handle. Playback is not what this test is about: the screen must simply come up without it.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).run {
            declareActionUnbindable(MediaSessionService.SERVICE_INTERFACE)
            declareActionUnbindable(MediaLibraryService.SERVICE_INTERFACE)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // Without this the first composition never runs, and nothing on screen is really exercised.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        assertNotNull(activity)
        assertTrue(activity.window.decorView.isAttachedToWindow)
    }
}
