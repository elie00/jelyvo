package ca.ilianokokoro.umihi.music.desktop.account

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in YouTube account on this Mac.
 *
 * The account is the sync boundary: liked songs, saved albums, followed artists and remote
 * playlists live on it, so signing the desktop into the same account is what makes the two clients
 * show the same library. Anything the phone keeps locally — play history, local playlists,
 * downloads — does not cross this way and would need its own mechanism.
 */
class DesktopAccount(private val store: CredentialStore = KeychainStore) {
    private val _session = MutableStateFlow<DesktopYoutubeSession?>(null)
    val session: StateFlow<DesktopYoutubeSession?> = _session.asStateFlow()

    suspend fun restore() {
        _session.value = store.read()?.let(::DesktopYoutubeSession)
    }

    suspend fun signIn(rawCookieHeader: String): Boolean {
        val trimmed = rawCookieHeader.trim()
        if (trimmed.isBlank() || trimmed.length > 32_768 || trimmed.any { it.code < 32 || it.code == 127 }) return false
        if (!DesktopYoutubeSession(trimmed).looksAuthenticated) return false
        if (!store.write(trimmed)) return false
        _session.value = DesktopYoutubeSession(trimmed)
        return true
    }

    suspend fun signOut(): Boolean {
        if (!store.clear()) return false
        _session.value = null
        return true
    }
}

/**
 * A [YoutubeSession] built from the raw `Cookie:` header, matching how the phone stores it.
 *
 * dataSyncId stays null: it selects a brand account, and this path signs in as the default one.
 */
class DesktopYoutubeSession(override val rawCookieHeader: String) : YoutubeSession {

    override val cookieValues: Map<String, String> by lazy {
        rawCookieHeader.split(";").mapNotNull { entry ->
            val parts = entry.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    override val dataSyncId: String? = null

    /** The cookie YouTube derives its authorization hash from; without it a request is anonymous. */
    val looksAuthenticated: Boolean
        get() = !cookieValues["SAPISID"].isNullOrBlank() || !cookieValues["__Secure-3PAPISID"].isNullOrBlank()

    /** For display: never show the raw header, it is a credential. */
    val summary: String
        get() = "${cookieValues.size} cookies" + if (looksAuthenticated) " · SAPISID présent" else ""
}
