package ca.ilianokokoro.umihi.music.desktop.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secrets in the macOS Keychain rather than a preferences file, as the multiplatform roadmap
 * requires: a YouTube cookie header is a full account credential.
 *
 * There is no JVM binding for the Keychain, so this drives the `security` command. The secret never
 * appears in the argument vector: `-w` with no inline value makes the tool prompt, and it reads
 * that prompt from stdin, so the credential is piped instead. It asks twice, once to confirm.
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
            // -U replaces any existing entry rather than failing with "already exists". -w must come
            // last and carry no value, so the tool prompts and we can feed it over stdin.
            "-U", "-s", SERVICE, "-a", ACCOUNT, "-w",
        ).redirectErrorStream(true).start()

        process.outputStream.bufferedWriter().use { writer ->
            // Once for the prompt, once for its confirmation.
            writer.write(secret)
            writer.newLine()
            writer.write(secret)
            writer.newLine()
        }
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
