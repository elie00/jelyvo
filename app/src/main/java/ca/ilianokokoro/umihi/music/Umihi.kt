package ca.ilianokokoro.umihi.music

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import androidx.work.Configuration
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.diagnostics.PlaybackFailures
import ca.ilianokokoro.umihi.music.core.diagnostics.StabilityDiagnostics
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class Umihi : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // The Data Layer listener lives in a small disposable process. Avoid starting account
        // prefetch, caches and other phone UI work when Wear OS merely delivers a command.
        if (!isMainAppProcess(currentProcessName(), packageName)) return

        // Account screens are product data, not disposable artwork. Keep their last snapshot in
        // filesDir so Android cache cleanup cannot bring skeletons back on every cold launch.
        ResponseCache.init(storageDir = filesDir, legacyStorageDir = cacheDir)
        PlaybackFailures.init(this)
        startupScope.launch {
            StabilityDiagnostics.captureLatestUnexpectedExit(this@Umihi)
        }
        prewarmAccountLibrary()
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = Process.myPid()
        return (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
    }

    private fun prewarmAccountLibrary() {
        startupScope.launch {
            val settings = runCatching {
                DatastoreRepository(this@Umihi).getSettings()
            }.getOrNull() ?: return@launch
            if (settings.cookies.isEmpty()) return@launch

            // Each flow paints its disk snapshot first and refreshes silently. Running them at app
            // startup means Library and J'aime are ready before the user reaches either screen.
            launch {
                runCatching {
                    LibraryRepository().getLibrary(settings).first { it !is ApiResult.Loading }
                }
            }
            launch {
                runCatching {
                    PlaylistRepository(this@Umihi).retrieveAll(settings)
                        .first { it !is ApiResult.Loading }
                }
            }
            launch {
                runCatching {
                    PlaylistRepository(this@Umihi).retrieveOne(
                        playlist = Playlist(
                            PlaylistInfo(
                                id = YoutubeApiConstants.Browse.LIKED_SONGS_BROWSE_ID,
                                title = getString(R.string.liked_songs),
                            )
                        ),
                        settings = settings,
                    ).first { it !is ApiResult.Loading }
                }
            }
        }
    }

    /**
     * Disables WorkManager's eager ContentProvider startup. Feature toggles initialize it only
     * when they actually schedule or cancel background work.
     */
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder().build()
    }

    /**
     * App-wide image loader: shares [UmihiHttpClient]'s OkHttp connection pool (no second set of
     * TLS handshakes to the artwork hosts) and sizes the caches explicitly for an artwork-heavy
     * app instead of relying on Coil's small defaults.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { UmihiHttpClient.client }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(DISK_CACHE_SIZE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        // Public: StorageInspector measures this directory for the storage screen.
        const val IMAGE_CACHE_DIR = "image_cache"
        private const val DISK_CACHE_SIZE_BYTES = 250L * 1024 * 1024
        private const val MEMORY_CACHE_PERCENT = 0.25
    }
}

/** Defaults to main-process initialization if an old Android version cannot resolve its process. */
internal fun isMainAppProcess(processName: String?, packageName: String): Boolean =
    processName == null || processName == packageName
