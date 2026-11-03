package ca.ilianokokoro.umihi.music.ui.screens.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.core.jam.DEFAULT_JAM_SERVER_URL
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.core.jam.PartyMember
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.JamPartyContent
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders a party, as its host sees it, to `app/build/outputs/theme-showcase`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class JamDialogScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hostView() {
        compose.setContent {
            StudioTestTheme(dark = true) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        JamPartyContent(
                            party = JamStatus.InParty(
                                code = "ABC123",
                                serverUrl = DEFAULT_JAM_SERVER_URL,
                                myMemberId = "me",
                                members = listOf(
                                    PartyMember("me", "Elie", isHost = true, connected = true),
                                    PartyMember("two", "Camille", connected = true),
                                    PartyMember("three", "Sam", connected = false),
                                ),
                            ),
                            onKick = {},
                            onSetHostOnlyControl = {},
                        )
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
        File(directory, "jam-host.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
