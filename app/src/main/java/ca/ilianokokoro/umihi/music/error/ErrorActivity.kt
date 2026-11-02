package ca.ilianokokoro.umihi.music.error

import ca.ilianokokoro.umihi.music.ui.theme.dmSansFontFamily
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily

class ErrorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UmihiMusicTheme(fontFamily = nunitoFontFamily(), studioFontFamily = dmSansFontFamily()) {
                ErrorScreen()
            }
        }

    }
}