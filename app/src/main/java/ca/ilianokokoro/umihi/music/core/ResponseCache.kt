package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.Locale

/**
 * Small in-memory LRU cache for network responses, keyed by browseId/query. Entries expire after
 * a short TTL so repeat visits and back-navigation render instantly without going stale.
 *
 * A response can additionally be mirrored to disk (opt-in, see [putToDisk]/[getFromDisk]) so a
 * cold start — where the in-memory cache is empty — can still paint the last-known screen instantly
 * before the network revalidation lands. Only screens whose models are fully `@Serializable`
 * currently opt in (the home feed); everything else keeps the memory-only behaviour untouched.
 */
object ResponseCache {
    private const val MAX_ENTRIES = 50
    const val DEFAULT_TTL_MS = 5L * 60 * 1000

    /**
     * Cold-start bootstrap window for the on-disk mirror. Independent of the (short) in-memory TTL:
     * the disk copy is only ever shown as *stale* content that is immediately revalidated, so a
     * generous bound keeps the last feed paintable across app restarts while still discarding
     * genuinely ancient data.
     */
    private const val DISK_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private data class Entry(val value: Any, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchSingleFlight = SingleFlight()

    @Volatile
    private var diskDir: File? = null

    /** Timestamped envelope so the disk mirror can honour [DISK_MAX_AGE_MS]. */
    @Serializable
    private class DiskEnvelope(val storedAtMs: Long, val payload: JsonElement)

    /**
     * Wires the on-disk mirror to persistent app storage. The optional legacy directory is
     * migrated with a cheap same-filesystem rename so users upgrading from the old cache-backed
     * implementation keep their already-fetched library on the very first launch.
     */
    fun init(storageDir: File, legacyStorageDir: File? = null) {
        val target = File(storageDir, CACHE_DIRECTORY)
        val legacy = legacyStorageDir?.let { File(it, CACHE_DIRECTORY) }
        if (!target.exists() && legacy?.exists() == true) {
            legacy.renameTo(target)
        }
        diskDir = target.apply { mkdirs() }
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Any> get(key: String, ttlMs: Long = DEFAULT_TTL_MS): T? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value as? T
    }

    @Synchronized
    fun put(key: String, value: Any) {
        entries[key] = Entry(value, System.currentTimeMillis())
    }

    /** Invalidates one entry after a mutation without flushing unrelated cached screens. */
    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
        runCatching { diskFileFor(key)?.delete() }
    }

    /**
     * Reads the on-disk mirror for [key], deserialized with [serializer]. Returns null when the
     * mirror is unset, missing, unreadable or older than [DISK_MAX_AGE_MS] (in which case the stale
     * file is dropped). Never throws — a corrupt entry simply behaves like a miss.
     */
    fun <T : Any> getFromDisk(key: String, serializer: KSerializer<T>): T? {
        val file = diskFileFor(key) ?: return null
        if (!file.exists()) return null
        return try {
            val envelope = json.decodeFromString(DiskEnvelope.serializer(), file.readText())
            if (System.currentTimeMillis() - envelope.storedAtMs > DISK_MAX_AGE_MS) {
                file.delete()
                null
            } else {
                json.decodeFromJsonElement(serializer, envelope.payload)
            }
        } catch (exception: Exception) {
            runCatching { file.delete() }
            null
        }
    }

    /** Write-behind mirror of [value] to disk (never blocks the caller). No-op until [init]. */
    fun <T : Any> putToDisk(key: String, value: T, serializer: KSerializer<T>) {
        val file = diskFileFor(key) ?: return
        ioScope.launch {
            runCatching {
                val envelope = DiskEnvelope(
                    storedAtMs = System.currentTimeMillis(),
                    payload = json.encodeToJsonElement(serializer, value),
                )
                file.writeText(json.encodeToString(DiskEnvelope.serializer(), envelope))
            }
        }
    }

    /** Total size in bytes of the on-disk mirror; 0 before [init]. */
    fun diskUsageBytes(): Long =
        diskDir?.listFiles()?.sumOf { it.length() } ?: 0L

    /** Drops everything (memory + disk mirror), e.g. when the signed-in account changes. */
    @Synchronized
    fun clear() {
        entries.clear()
        diskDir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * Shares one fetch between every concurrent caller of [key]. A waiting collector can be
     * cancelled without cancelling the owner computation or the other waiters; owner failures are
     * replayed to all waiters and the key is always released for a later retry.
     */
    suspend fun <T : Any> getOrFetch(key: String, fetch: suspend () -> T): T =
        fetchSingleFlight.execute(key, fetch)

    /** Maps a cache key to a filesystem-safe file under the mirror directory. */
    private fun diskFileFor(key: String): File? {
        val dir = diskDir ?: return null
        val safeName = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(128)
        return File(dir, "$safeName.json")
    }

    private const val CACHE_DIRECTORY = "response_cache"
}

/** Server-rendered labels are language-dependent, so they must never share a cache key. */
fun localizedCacheKey(base: String): String =
    "$base:${Locale.getDefault().toLanguageTag().ifBlank { "en" }}"

/**
 * Stale-while-revalidate wrapper around the usual `Loading -> Success/Error` repository flow:
 * a cached response is emitted instantly (no [ApiResult.Loading] flash), then the fetch still
 * runs and re-emits only if the fresh data differs. Without a cached entry it behaves exactly
 * like the plain repository flows. Fetch errors are swallowed when cached data was already
 * shown, so a background refresh failure never replaces content with an error screen.
 *
 * When [diskSerializer] is supplied, the response is additionally mirrored to disk and, on an
 * in-memory miss (cold start), served from there as the initial stale value. A disk-served value
 * is always replaced by the fresh fetch (even when equal) so any non-serialized transient fields
 * are restored to full fidelity.
 */
fun <T : Any> cachedApiFlow(
    key: String,
    ttlMs: Long = ResponseCache.DEFAULT_TTL_MS,
    useCache: Boolean = true,
    diskSerializer: KSerializer<T>? = null,
    fetch: suspend () -> T,
): Flow<ApiResult<T>> = flow {
    val memoryCached = if (useCache) ResponseCache.get<T>(key, ttlMs) else null
    val diskCached = if (memoryCached == null && useCache && diskSerializer != null) {
        ResponseCache.getFromDisk(key, diskSerializer)
    } else {
        null
    }
    val cached = memoryCached ?: diskCached

    if (cached != null) {
        emit(ApiResult.Success(cached))
    } else {
        emit(ApiResult.Loading)
    }

    val fresh = try {
        ResponseCache.getOrFetch(key, fetch)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        if (cached == null) {
            emit(ApiResult.Error(exception))
        }
        return@flow
    }

    ResponseCache.put(key, fresh)
    if (diskSerializer != null) {
        ResponseCache.putToDisk(key, fresh, diskSerializer)
    }
    // Re-emit on change, and always after a disk-served bootstrap so transient (non-serialized)
    // fields are restored from the fresh response.
    if (fresh != cached || diskCached != null) {
        emit(ApiResult.Success(fresh))
    }
}.flowOn(Dispatchers.IO)
