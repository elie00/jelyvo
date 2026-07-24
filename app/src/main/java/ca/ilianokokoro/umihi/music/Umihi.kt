package ca.ilianokokoro.umihi.music

import android.app.Application
import androidx.work.Configuration
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

class Umihi : Application(), SingletonImageLoader.Factory, Configuration.Provider {

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
        private const val IMAGE_CACHE_DIR = "image_cache"
        private const val DISK_CACHE_SIZE_BYTES = 250L * 1024 * 1024
        private const val MEMORY_CACHE_PERCENT = 0.25
    }
}
