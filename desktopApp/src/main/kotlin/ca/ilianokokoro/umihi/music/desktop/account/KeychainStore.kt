package ca.ilianokokoro.umihi.music.desktop.account

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

interface CredentialStore {
    suspend fun read(): String?
    suspend fun write(secret: String): Boolean
    suspend fun clear(): Boolean
}

/** The secret is sent through stdin, never the process argument vector or a temporary file. */
object KeychainStore : CredentialStore {
    private const val SERVICE = "ca.ilianokokoro.umihi.music.desktop"
    private const val ACCOUNT = "youtube-session"
    private const val NOT_FOUND = 44

    override suspend fun read(): String? {
        val (code, output) = runSecurity(listOf("find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"))
        check(code == 0 || code == NOT_FOUND) { "Trousseau indisponible" }
        return output.trim().takeIf { code == 0 && it.isNotBlank() }
    }

    override suspend fun write(secret: String): Boolean = runSecurity(
        listOf("add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT, "-w"),
        "$secret\n$secret\n",
    ).first == 0

    override suspend fun clear(): Boolean = runSecurity(
        listOf("delete-generic-password", "-s", SERVICE, "-a", ACCOUNT),
    ).first.let { it == 0 || it == NOT_FOUND }

    private suspend fun runSecurity(arguments: List<String>, input: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf("/usr/bin/security") + arguments)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                coroutineScope {
                    val output = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
                    process.outputStream.bufferedWriter().use { writer -> input?.let(writer::write) }
                    if (!process.waitFor(15, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        error("Le Trousseau n'a pas répondu")
                    }
                    process.exitValue() to output.await()
                }
            } finally { process.destroyForcibly() }
        }
}
