package ca.ilianokokoro.umihi.music.core.spotify

import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Parsed result of the OAuth redirect (umihi://spotify-callback?...).
 * On success [code] is non-null; on denial/error [error] is non-null.
 */
data class SpotifyAuthCallback(
    val code: String?,
    val state: String?,
    val error: String?,
)

/**
 * Helper for the Authorization Code + PKCE flow.
 *
 * Usage:
 *   1. val pkce = SpotifyPkce.generate()
 *   2. Open buildAuthorizationUrl(pkce.codeChallenge, state) in a Custom Tab / browser.
 *   3. On redirect to REDIRECT_URI, call parseAuthorizationCode(uri), verify `state`.
 *   4. exchangeCodeForToken(callback.code, pkce.codeVerifier) -> access + refresh token.
 *   5. Later, refreshToken(refreshToken) to renew the access token.
 */
object SpotifyAuthHelper {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Builds the authorization URL to open in a browser / Custom Tab / WebView.
     * [state] should be random and re-checked on the callback (CSRF protection).
     */
    fun buildAuthorizationUrl(codeChallenge: String, state: String): String {
        return SpotifyConstants.Endpoints.AUTHORIZE.toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", SpotifyConstants.CLIENT_ID)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", SpotifyConstants.REDIRECT_URI)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("scope", SpotifyConstants.SCOPES)
            .addQueryParameter("state", state)
            .build()
            .toString()
    }

    /** Parses the redirect URI (custom scheme) into its OAuth parameters. */
    fun parseAuthorizationCode(redirectUri: String): SpotifyAuthCallback {
        val uri = redirectUri.toUri()
        return SpotifyAuthCallback(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
        )
    }

    /** Exchanges the authorization code for an access token (+ refresh token). */
    suspend fun exchangeCodeForToken(
        code: String,
        codeVerifier: String,
    ): SpotifyTokenResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", SpotifyConstants.REDIRECT_URI)
            .add("client_id", SpotifyConstants.CLIENT_ID)
            .add("code_verifier", codeVerifier)
            .build()

        postToken(form)
    }

    /** Renews an access token using a stored refresh token. */
    suspend fun refreshToken(
        refreshToken: String,
    ): SpotifyTokenResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", SpotifyConstants.CLIENT_ID)
            .build()

        postToken(form)
    }

    // FormBody already sets Content-Type: application/x-www-form-urlencoded.
    private fun postToken(form: FormBody): SpotifyTokenResponse {
        val request = Request.Builder()
            .url(SpotifyConstants.Endpoints.TOKEN)
            .post(form)
            .build()

        UmihiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IOException("Empty Spotify token response")
            if (!response.isSuccessful) {
                throw IOException("Spotify token HTTP ${response.code}: ${body.take(500)}")
            }
            return json.decodeFromString<SpotifyTokenResponse>(body)
        }
    }
}
