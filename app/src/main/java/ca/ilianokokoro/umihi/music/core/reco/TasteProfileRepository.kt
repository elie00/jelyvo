package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayEventRepository
import ca.ilianokokoro.umihi.music.models.Library
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the locally computed [TasteProfile]: feeds the pure scoring functions with the play
 * events, the cached liked songs and the not-interested veto list, and exposes the result as a
 * [StateFlow] the recommendation surfaces can observe.
 *
 * Refresh policy: once at [start] (IO, after publishing the persisted snapshot for an instant
 * first frame), then after each finished track via [onTrackFinished] — debounced by
 * [REFRESH_DEBOUNCE_MS] so a burst of quick skips triggers a single recomputation instead of one
 * per event. The last computed profile is mirrored to a JSON file in `filesDir` so the next app
 * start does not have to wait for the first recomputation.
 */
class TasteProfileRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playEventRepository = PlayEventRepository(appContext)
    private val historyRepository = HistoryRepository(appContext)
    private val datastoreRepository = DatastoreRepository(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val started = AtomicBoolean(false)
    private val refreshMutex = Mutex()
    private var debouncedRefreshJob: Job? = null

    private val _profile = MutableStateFlow<TasteProfile?>(null)
    val profile: StateFlow<TasteProfile?> = _profile.asStateFlow()

    /** Loads the persisted snapshot, then computes a fresh profile. Safe to call repeatedly. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            loadPersisted()?.let { persisted -> _profile.compareAndSet(null, persisted) }
            refreshNow()
        }
    }

    /** A track's play just ended: schedules a debounced recomputation. */
    fun onTrackFinished() {
        debouncedRefreshJob?.cancel()
        debouncedRefreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            refreshNow()
        }
    }

    /** Recomputes and publishes the profile right away. Never throws; failures keep the old one. */
    suspend fun refreshNow(nowMs: Long = System.currentTimeMillis()) = refreshMutex.withLock {
        try {
            val events = playEventRepository
                .eventsSince(nowMs - PlayEventRepository.RETENTION_MS)

            // Liked songs come from the library ResponseCache entry, whatever its age — the
            // profile degrades gracefully to history-only when the library was never loaded.
            val settings = datastoreRepository.getSettings()
            val likedSongs = ResponseCache.get<Library>(
                key = LibraryRepository.cacheKey(settings),
                ttlMs = Long.MAX_VALUE,
            )?.likedSongs.orEmpty()

            // History is the primary artist-name source; liked songs fill in never-played likes.
            val artistByYoutubeId = buildMap {
                likedSongs.forEach { song ->
                    if (song.youtubeId.isNotBlank() && song.artist.isNotBlank()) {
                        put(song.youtubeId, song.artist)
                    }
                }
                putAll(historyRepository.artistByYoutubeId())
            }

            val fresh = buildTasteProfile(
                events = events,
                likedIds = likedSongs.mapTo(HashSet()) { it.youtubeId },
                notInterestedIds = NotInterestedStore.hiddenIds,
                artistByYoutubeId = artistByYoutubeId,
                nowMs = nowMs,
            )
            _profile.value = fresh
            persist(fresh)
        } catch (exception: Exception) {
            printe(message = "Failed to refresh the taste profile", exception = exception)
        }
    }

    private fun loadPersisted(): TasteProfile? {
        val file = profileFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString(TasteProfile.serializer(), file.readText())
        } catch (exception: Exception) {
            // A corrupt snapshot behaves like a miss and gets rewritten on the next refresh.
            runCatching { file.delete() }
            null
        }
    }

    private fun persist(profile: TasteProfile) {
        try {
            profileFile().writeText(json.encodeToString(TasteProfile.serializer(), profile))
        } catch (exception: Exception) {
            printe(message = "Failed to persist the taste profile", exception = exception)
        }
    }

    private fun profileFile() = File(appContext.filesDir, PROFILE_FILE_NAME)

    companion object {
        const val PROFILE_FILE_NAME = "taste_profile.json"

        /** Skip bursts within this window collapse into one recomputation. */
        const val REFRESH_DEBOUNCE_MS = 15_000L

        @Volatile
        private var instance: TasteProfileRepository? = null

        fun getInstance(context: Context): TasteProfileRepository =
            instance ?: synchronized(this) {
                instance ?: TasteProfileRepository(context).also { instance = it }
            }
    }
}
