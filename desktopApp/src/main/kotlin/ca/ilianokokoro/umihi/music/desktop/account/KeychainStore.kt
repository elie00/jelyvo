package ca.ilianokokoro.umihi.music.desktop.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secrets in the macOS Keychain rather than a preferences file, as the multiplatform roadmap
 * requires: a YouTube cookie header is a full account credential.
 *
 * There is no JVM binding for the Keychain, so this drives the `security` command. Its one wart is
 * that writing passes the secret as an argument, which is briefly visible to `ps` for processes
 * owned by the same user. Reading and deleting do not expose it.
 */
object KeychainStore {
    private const val SERVICE = "ca.ilianokokoro.umihi.music.desktop"
    private const val ACCOUNT = "youtube-session"

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w",
        ).redirectErrorStream(false).start()

        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && output.isNotBlank()) output else null
    }

    suspend fun write(secret: String): Boolean = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "security", "add-generic-password",
            "-s", SERVICE, "-a", ACCOUNT, "-w", secret,
            // Replace any existing entry rather than failing with "already exists".
            "-U",
        ).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "security", "delete-generic-password", "-s", SERVICE, "-a", ACCOUNT,
        ).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    }
}
