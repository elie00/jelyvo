package ca.ilianokokoro.umihi.music.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.work.WorkInfo
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.ShelfHeader
import ca.ilianokokoro.umihi.music.ui.screens.downloads.DownloadManagerState
import ca.ilianokokoro.umihi.music.ui.screens.downloads.DownloadManagerTask
import ca.ilianokokoro.umihi.music.ui.screens.downloads.DownloadSummary
import ca.ilianokokoro.umihi.music.ui.screens.downloads.DownloadTaskCard
import ca.ilianokokoro.umihi.music.ui.screens.musicimport.LinkContent
import ca.ilianokokoro.umihi.music.ui.screens.stats.PeriodSelector
import ca.ilianokokoro.umihi.music.ui.screens.stats.StatsPeriod
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/**
 * Renders pieces of the secondary screens to `app/build/outputs/theme-showcase`, to be looked at.
 * The real screens need their ViewModels, so this stacks their own content.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SecondaryScreensScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun task(title: String, state: WorkInfo.State, failed: Int = 0) = DownloadManagerTask(
        id = UUID.randomUUID(),
        title = title,
        kind = "playlist",
        state = state,
        completed = 4,
        total = 12,
        failed = failed,
    )

    @Test
    fun secondaryScreens() {
        compose.setContent {
            StudioTestTheme(dark = true) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = PageGutter),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DownloadSummary(
                                DownloadManagerState(
                                    loading = false,
                                    downloadedCount = 128,
                                    storageBytes = 1_400_000_000,
                                )
                            )
                            DownloadTaskCard(task("Soirée", WorkInfo.State.RUNNING), onCancel = {})
                            DownloadTaskCard(task("Route", WorkInfo.State.FAILED, failed = 2), onRetry = {})
                        }
                        ShelfHeader(title = "Activité", modifier = Modifier.padding(top = 10.dp))
                        PeriodSelector(selected = StatsPeriod.MONTH, onSelect = {})
                        Box(Modifier.height(460.dp)) {
                            LinkContent(link = "", onLinkChange = {}, onAnalyze = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/outputs/theme-showcase").apply { mkdirs() }
        File(directory, "secondary-screens.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
