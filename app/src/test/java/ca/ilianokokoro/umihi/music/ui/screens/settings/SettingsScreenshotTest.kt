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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.BooleanSettingItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsItem
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsRowDivider
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SettingsSection
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.SliderSettingItem
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders settings rows to `app/build/outputs/theme-showcase`, to be looked at. The real screen
 * needs its ViewModel, so this stacks the same sections and rows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Réglages",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        SettingsSection(title = "Compte") {
                            SettingsItem(
                                title = "Se connecter",
                                subtitle = "Pour retrouver votre bibliothèque",
                                leadingIcon = Icons.Outlined.Login,
                                onClick = {},
                            )
                        }
                        SettingsSection(title = "Apparence") {
                            SettingsItem(
                                title = "Thème",
                                subtitle = "Studio",
                                leadingIcon = Icons.Outlined.Brush,
                                onClick = {},
                            )
                            SettingsRowDivider()
                            SettingsItem(
                                title = "Couleur",
                                subtitle = "Rouge",
                                leadingIcon = Icons.Outlined.Palette,
                                onClick = {},
                            )
                        }
                        SettingsSection(title = "Lecture") {
                            BooleanSettingItem(
                                title = "Garder l'écran allumé",
                                subtitle = "Pendant la lecture",
                                leadingIcon = Icons.Outlined.StayCurrentPortrait,
                                value = true,
                                onToggle = {},
                            )
                            SettingsRowDivider()
                            BooleanSettingItem(
                                title = "Égaliseur",
                                subtitle = "Une description assez longue pour passer sur plusieurs lignes dans le réglage",
                                leadingIcon = Icons.Outlined.Equalizer,
                                value = false,
                                onToggle = {},
                            )
                            SettingsRowDivider()
                            SliderSettingItem(
                                title = "Fondu enchaîné",
                                subtitle = "4 secondes",
                                leadingIcon = Icons.Outlined.Speed,
                                value = 4,
                                valueRange = 0..12,
                                onValueChange = {},
                            )
                        }
                        SettingsSection(title = "À propos") {
                            SettingsItem(
                                title = "Version",
                                subtitle = "2.6.12",
                                leadingIcon = Icons.Outlined.Info,
                                enabled = false,
                                onClick = {},
                            )
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
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun settingsDark() = render("settings-dark", dark = true)

    @Test
    fun settingsLight() = render("settings-light", dark = false)
}
