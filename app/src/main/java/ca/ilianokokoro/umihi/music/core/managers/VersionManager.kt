package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.exceptions.GithubRateLimitException
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.VersionCompare
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.datasources.local.VersionDataSource
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.GithubRepository
import ca.ilianokokoro.umihi.music.models.Version
import ca.ilianokokoro.umihi.music.models.dto.GithubReleaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Verification et installation des mises a jour depuis le depot public jelyvo-releases.
 * Un seul canal (stable) : la derniere release publiee fait foi.
 */
object VersionManager {

    private val _eventsFlow = MutableSharedFlow<ScreenEvent>()
    val eventsFlow = _eventsFlow.asSharedFlow()

    private var versionName: String? = null

    private val githubRepository: GithubRepository = GithubRepository()
    private lateinit var versionRepository: VersionDataSource

    fun initialize(context: Context) {
        if (versionName == null) {
            versionName = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            } catch (_: Exception) {
                String()
            }
        }
        if (!::versionRepository.isInitialized) {
            versionRepository = AppDatabase.getInstance(context).versionRepository()
        }
    }

    /**
     * Guarantees [initialize] has run before a consumer touches [versionRepository]/[versionName].
     * Idempotent and off the main thread: the caller may run [initialize] on a background dispatcher
     * so the packageInfo lookup and Room handle build never block the first frame.
     */
    private suspend fun ensureInitialized(context: Context) {
        if (versionName == null || !::versionRepository.isInitialized) {
            withContext(Dispatchers.IO) { initialize(context.applicationContext) }
        }
    }

    fun getVersionName(): String {
        return versionName.toString()
    }

    suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false
    ) {
        ensureInitialized(context)

        val settings = DatastoreRepository(context).settings.first()
        if (!settings.updateChecking && !manualCheck) {
            return
        }

        try {
            githubRepository.getReleaseInfoByUrl().collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val release = result.data
                        handleUpdateResult(
                            context = context,
                            manualCheck = manualCheck,
                            outdated = release.versionName.isNewUpdate(manualCheck),
                            release = release
                        )
                        return@collect
                    }

                    is ApiResult.Error -> throw result.exception
                    else -> Unit
                }
            }
        } catch (e: GithubRateLimitException) {
            if (manualCheck) {
                val formattedTime = if (e.retryAfterSeconds >= 60) {
                    context.getString(R.string.minutes, e.retryAfterSeconds / 60)
                } else {
                    context.getString(R.string.seconds, e.retryAfterSeconds)
                }

                val message = context.getString(R.string.github_rate_limit_retry, formattedTime)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            printe(message = e.message.orEmpty(), exception = e)
        } catch (ex: Exception) {
            if (manualCheck) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.update_check_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            printe(message = ex.message.orEmpty(), exception = ex)
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: GithubReleaseResponse,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()

        val apkFile = File(updateDir, Constants.Downloads.UPDATE_APK)
        val partialFile = File(updateDir, "${Constants.Downloads.UPDATE_APK}.part")
        partialFile.delete()

        val request = Request.Builder().url(release.downloadUrl).build()
        try {
            UmihiHttpClient.downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("APK download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("APK download failed: empty body")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    partialFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
            apkFile.delete()
            if (!partialFile.renameTo(apkFile)) {
                throw IOException("APK download failed: unable to finalize file")
            }
        } catch (exception: Exception) {
            partialFile.delete()
            throw exception
        }

        apkFile
    }

    fun installApk(
        context: Context,
        apkFile: File
    ) {
        if (hasInstallPermissions(context)) {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }
    }

    suspend fun requestInstallPermissions(
        activity: ComponentActivity
    ): Boolean = suspendCancellableCoroutine { continuation ->

        fun resumeWithResult(value: Boolean) {
            if (!continuation.isActive) {
                return
            }

            continuation.resume(value) { _, _, _ -> }
        }

        if (hasInstallPermissions(activity)) {
            resumeWithResult(true)
            return@suspendCancellableCoroutine
        }

        var launcher: ActivityResultLauncher<Intent>? = null

        launcher = activity.activityResultRegistry.register(
            "install_permissions_${System.nanoTime()}",
            ActivityResultContracts.StartActivityForResult()
        ) {
            launcher?.unregister()
            resumeWithResult(hasInstallPermissions(activity))
        }

        continuation.invokeOnCancellation {
            launcher.unregister()
        }

        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${activity.packageName}".toUri()
        }

        launcher.launch(intent)
    }

    private fun hasInstallPermissions(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.packageManager.canRequestPackageInstalls()
    }

    private suspend fun handleUpdateResult(
        context: Context,
        manualCheck: Boolean,
        outdated: Boolean,
        release: GithubReleaseResponse
    ) {
        if (outdated) {
            _eventsFlow.emit(
                ScreenEvent.UpdateAvailable(
                    githubReleaseResponse = release
                )
            )
        } else if (manualCheck) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.up_to_date_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    suspend fun ignoreUpdate(version: Version) {
        versionRepository.ignoreVersion(version)
    }

    /**
     * Vrai si cette version publiee est plus recente que la version installee, sauf si
     * l'utilisateur l'a explicitement ignoree (le check manuel repasse outre l'ignore).
     */
    private suspend fun String.isNewUpdate(manualCheck: Boolean): Boolean {
        val currentVersion = versionName ?: return true

        if (!VersionCompare.isNewer(incoming = this, current = currentVersion)) {
            return false
        }

        if (!manualCheck) {
            val ignored = versionRepository.getIgnoredVersions()
            if (ignored.contains(Version(this))) {
                return false
            }
        }

        return true
    }

    sealed class ScreenEvent {
        data class UpdateAvailable(val githubReleaseResponse: GithubReleaseResponse) :
            ScreenEvent()
    }
}
