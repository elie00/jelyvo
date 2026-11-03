package ca.ilianokokoro.umihi.music.core.jam

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A party someone was invited to. Nothing happens with it until they say yes. */
data class JamInvite(val serverUrl: String, val code: String) {
    val isOnDefaultServer: Boolean get() = serverUrl == DEFAULT_JAM_SERVER_URL

    /** The server's name alone, which is what a person can judge. */
    val serverHost: String get() = runCatching { URI(serverUrl).host }.getOrNull() ?: serverUrl
}

object JamInvites {
    private const val SCHEME = "jelyvo"
    private const val HOST = "party"
    private val CODE = Regex("[0-9A-Z]{6}")

    /** Opens the server's invite page, which opens the app or says how to get it. */
    fun link(serverUrl: String, code: String): String = "$serverUrl/invite/$code"

    /**
     * Reads `jelyvo://party/CODE?server=https%3A%2F%2F…`. These links come from anywhere, so anything
     * unexpected is refused rather than repaired, and only an https server with a plain address is
     * accepted: that server would receive the listener's name and what they play.
     */
    fun parse(link: String): JamInvite? {
        val uri = try {
            URI(link)
        } catch (_: URISyntaxException) {
            return null
        }
        if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host, ignoreCase = true)) {
            return null
        }
        val code = uri.path.orEmpty().trim('/').uppercase()
        if (!CODE.matches(code)) return null

        val server = uri.rawQuery.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("server=") }
            ?.removePrefix("server=")
            ?: return JamInvite(DEFAULT_JAM_SERVER_URL, code)
        val decoded = runCatching { URLDecoder.decode(server, StandardCharsets.UTF_8.name()) }
            .getOrNull() ?: return null
        val url = normalizeJamServerUrl(decoded) ?: return null
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (parsed.host.isNullOrBlank() || parsed.userInfo != null || !parsed.query.isNullOrEmpty()) {
            return null
        }
        return JamInvite(url, code)
    }
}
