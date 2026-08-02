package ca.ilianokokoro.umihi.music.models

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.dto.GithubReleaseResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GithubReleaseResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses the release endpoint payload and strips the tag prefix`() {
        val payload = """
            {
                "tag_name": "v2.1.0",
                "target_commitish": "main",
                "body": "Notes:\n- Nouveau widget\n- Correctifs",
                "assets": [
                    {"browser_download_url": "https://github.com/elie00/jelyvo-releases/releases/download/v2.1.0/Jelyvo-Watch.apk", "name": "Jelyvo-Watch.apk"},
                    {"browser_download_url": "https://github.com/elie00/jelyvo-releases/releases/download/v2.1.0/Jelyvo.apk", "name": "Jelyvo.apk"}
                ]
            }
        """.trimIndent()

        val release = json.decodeFromString<GithubReleaseResponse>(payload)

        assertEquals("2.1.0", release.versionName)
        assertEquals(
            "https://github.com/elie00/jelyvo-releases/releases/download/v2.1.0/Jelyvo.apk",
            release.downloadUrl
        )
        assertEquals("• Nouveau widget\n• Correctifs", release.cleanBody)
    }

    @Test
    fun `watch apk is never selected as the phone update`() {
        val release = json.decodeFromString<GithubReleaseResponse>(
            """{
                "tag_name": "v2.4.0",
                "assets": [
                    {"browser_download_url": "https://example.test/Jelyvo-Watch.apk"}
                ]
            }""".trimIndent()
        )

        assertEquals(Constants.Url.Github.Release.DOWNLOAD, release.downloadUrl)
    }

    @Test
    fun `missing assets fall back to the stable latest-download url`() {
        val release = json.decodeFromString<GithubReleaseResponse>("""{"tag_name": "v2.1.0"}""")

        assertEquals(Constants.Url.Github.Release.DOWNLOAD, release.downloadUrl)
    }

    @Test
    fun `a body without markdown bullets is kept as-is`() {
        val release = json.decodeFromString<GithubReleaseResponse>(
            """{"tag_name": "v2.1.0", "body": "Simple note de version."}"""
        )

        assertEquals("Simple note de version.", release.cleanBody)
    }
}
