package ca.ilianokokoro.umihi.music

import android.app.Application
import androidx.work.Configuration
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.managers.NotificationManager
import ca.ilianokokoro.umihi.music.core.workers.NewReleasesScheduler
import ca.ilianokokoro.umihi.music.core.workers.OfflineSyncScheduler
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class Umihi : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    /**
     * Disables WorkManager's eager ContentProvider startup. The first scheduler call (delayed below)
     * initializes it with this configuration after the launch frames are already visible.
     */
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder().build()
    }

    override fun onCreate() {
        super.onCreate()

        // Notification-channel binder calls and WorkManager class loading are not needed for the
        // first frame. Give the activity a clean launch window before background maintenance starts.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            delay(BACKGROUND_INIT_DELAY_MS)
            NotificationManager.init(this@Umihi)

            // (Re)schedule the periodic new-releases check to match the persisted toggle. KEEP means
            // an already-running schedule is left untouched.
            val datastoreRepository = DatastoreRepository(this@Umihi)

            if (datastoreRepository.notifyNewReleases.first()) {
                NewReleasesScheduler.schedule(this@Umihi)
            } else {
                NewReleasesScheduler.cancel(this@Umihi)
            }

            // (Re)schedule the periodic offline auto-download to match the persisted toggle.
            if (datastoreRepository.autoDownload.first()) {
                OfflineSyncScheduler.schedule(
                    this@Umihi,
                    datastoreRepository.autoDownloadWifiOnly.first()
                )
            } else {
                OfflineSyncScheduler.cancel(this@Umihi)
            }
        }
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
        private const val IMAGE_CACHE_DIR = "image_cache"
        private const val DISK_CACHE_SIZE_BYTES = 250L * 1024 * 1024
        private const val MEMORY_CACHE_PERCENT = 0.25
        private const val BACKGROUND_INIT_DELAY_MS = 750L
    }
}
