package ca.ilianokokoro.umihi.music.models.dto

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper.getBulletPointsFromMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reponse `releases/latest` du depot public jelyvo-releases. Version simplifiee de l'updater
 * upstream : un seul canal stable, pas de builds beta.
 */
@Immutable
@Serializable
data class GithubReleaseResponse(
    @SerialName("tag_name")
    val tagName: String,
    val body: String = "",
    val assets: List<AssetsResponse> = emptyList(),
) {
    val versionName: String
        get() = tagName.removePrefix("v")

    /** Notes de version : les puces markdown si presentes, sinon le corps brut. */
    val cleanBody: String
        get() = body.getBulletPointsFromMarkdown().ifBlank { body.trim() }

    /** L'asset APK reel de la release, ou l'URL stable `latest/download` en secours. */
    val downloadUrl: String
        get() = assets.firstOrNull { it.url.endsWith(".apk") }?.url
            ?: Constants.Url.Github.Release.DOWNLOAD
}

@Serializable
data class AssetsResponse(
    @SerialName("browser_download_url")
    val url: String,
)
