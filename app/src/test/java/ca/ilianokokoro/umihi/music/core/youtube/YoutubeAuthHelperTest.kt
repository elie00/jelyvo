package ca.ilianokokoro.umihi.music.core.youtube

import java.util.Locale
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YoutubeAuthHelperTest {
    @Test
    fun `context keeps language but omits device region and implicit search filter`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.Builder().setLanguage("fr").setRegion("CG").build())

            val body = YoutubeAuthHelper.buildContextBody(
                idName = "query",
                id = "stromae",
                settings = null,
            )
            val client = body["context"]!!.jsonObject["client"]!!.jsonObject

            assertEquals("fr", client["hl"]!!.jsonPrimitive.content)
            assertFalse("gl" in client)
            assertFalse("params" in body)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
