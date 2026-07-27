package ca.ilianokokoro.umihi.music.ui.screens.auth

import ca.ilianokokoro.umihi.music.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthScreenTest {
    @Test
    fun `youtube music install intent falls back to the web login destination`() {
        val url =
            "intent://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music" +
                "#Intent;scheme=https;end"

        assertEquals(Constants.Auth.END_URL, authWebFallback(url))
    }

    @Test
    fun `regular web and unrelated intents are not rewritten`() {
        assertNull(authWebFallback("https://accounts.google.com/"))
        assertNull(authWebFallback("intent://example.com/#Intent;scheme=https;end"))
    }
}
