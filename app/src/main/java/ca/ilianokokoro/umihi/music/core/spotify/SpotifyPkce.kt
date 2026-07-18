package ca.ilianokokoro.umihi.music.core.spotify

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.text.Charsets.US_ASCII

/**
 * A generated PKCE pair.
 *
 * The [codeVerifier] must be kept for the token exchange (send it as `code_verifier`).
 * The [codeChallenge] is sent in the authorization URL (`code_challenge` + `code_challenge_method=S256`).
 */
data class SpotifyPkcePair(
    val codeVerifier: String,
    val codeChallenge: String,
)

/**
 * PKCE (Proof Key for Code Exchange) helper for the Authorization Code + PKCE flow.
 *
 * code_challenge = BASE64URL-NO-PADDING( SHA-256( ASCII(code_verifier) ) )
 */
object SpotifyPkce {
    // 64 random bytes -> 86 base64url chars, within the required 43..128 range.
    private const val VERIFIER_BYTE_LENGTH = 64

    fun generate(): SpotifyPkcePair {
        val verifier = generateCodeVerifier()
        return SpotifyPkcePair(
            codeVerifier = verifier,
            codeChallenge = deriveCodeChallenge(verifier),
        )
    }

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    fun deriveCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(US_ASCII))
        return base64UrlNoPadding(digest)
    }

    private fun base64UrlNoPadding(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }
}
