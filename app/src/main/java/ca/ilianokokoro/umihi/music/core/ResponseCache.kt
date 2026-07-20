package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Small in-memory LRU cache for network responses, keyed by browseId/query. Entries expire after
 * a short TTL so repeat visits and back-navigation render instantly without going stale.
 */
object ResponseCache {
    private const val MAX_ENTRIES = 50
    const val DEFAULT_TTL_MS = 5L * 60 * 1000

    private data class Entry(val value: Any, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            return size > MAX_ENTRIES
        }
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

    /** Drops everything, e.g. when the signed-in account changes. */
    @Synchronized
    fun clear() {
        entries.clear()
    }
}

/**
 * Stale-while-revalidate wrapper around the usual `Loading -> Success/Error` repository flow:
 * a cached response is emitted instantly (no [ApiResult.Loading] flash), then the fetch still
 * runs and re-emits only if the fresh data differs. Without a cached entry it behaves exactly
 * like the plain repository flows. Fetch errors are swallowed when cached data was already
 * shown, so a background refresh failure never replaces content with an error screen.
 */
fun <T : Any> cachedApiFlow(
    key: String,
    ttlMs: Long = ResponseCache.DEFAULT_TTL_MS,
    useCache: Boolean = true,
    fetch: suspend () -> T,
): Flow<ApiResult<T>> = flow {
    val cached = if (useCache) ResponseCache.get<T>(key, ttlMs) else null
    if (cached != null) {
        emit(ApiResult.Success(cached))
    } else {
        emit(ApiResult.Loading)
    }

    val fresh = try {
        fetch()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        if (cached == null) {
            emit(ApiResult.Error(exception))
        }
        return@flow
    }

    ResponseCache.put(key, fresh)
    if (fresh != cached) {
        emit(ApiResult.Success(fresh))
    }
}.flowOn(Dispatchers.IO)
