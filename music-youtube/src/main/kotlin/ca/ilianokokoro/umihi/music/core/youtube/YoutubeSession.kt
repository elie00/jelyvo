package ca.ilianokokoro.umihi.music.core.youtube

/**
 * What the InnerTube request layer needs to know about the signed-in account.
 *
 * Deliberately narrower than the app's settings object: the wire layer only ever reads the
 * cookies it must replay and the brand account a request acts on behalf of. Android's
 * `UmihiSettings` implements this, and the macOS client supplies its own Keychain-backed value.
 */
interface YoutubeSession {
    /** Value for the `Cookie:` header; empty when signed out. */
    val rawCookieHeader: String

    /** Individual cookies by name, used to derive the SAPISID authorization hash. */
    val cookieValues: Map<String, String>

    /** Brand account the request acts on behalf of, null on the default account. */
    val dataSyncId: String?
}
