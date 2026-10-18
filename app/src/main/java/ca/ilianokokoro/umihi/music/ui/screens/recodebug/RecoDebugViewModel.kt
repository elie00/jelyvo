package ca.ilianokokoro.umihi.music.ui.screens.recodebug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper
import ca.ilianokokoro.umihi.music.core.reco.RecoWeightsStore
import ca.ilianokokoro.umihi.music.core.reco.TasteProfile
import ca.ilianokokoro.umihi.music.core.reco.TasteProfileRepository
import ca.ilianokokoro.umihi.music.core.reco.skipRatesBySourceContext
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayEventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feeds the read-only recommendation debug screen: taste profile top lists, current vs default
 * weights and the per-source skip-rate health metric. The two buttons (recompute profile,
 * reset weights) are the only writes.
 */
class RecoDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepository = TasteProfileRepository.getInstance(application)
    private val weightsStore = RecoWeightsStore.getInstance(application)
    private val playEventRepository = PlayEventRepository(application)
    private val historyRepository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(RecoDebugState())
    val uiState = _uiState.asStateFlow()

    init {
        profileRepository.start()
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                profile?.let { publishProfile(it) }
            }
        }
        viewModelScope.launch { refreshWeightsAndHealth() }
    }

    /** Forces a full profile recomputation right now (the debug screen's refresh button). */
    fun recomputeProfile() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                profileRepository.refreshNow()
                refreshWeightsAndHealth()
            } finally {
                _uiState.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Drops the tuned weights back to the compiled-in defaults. */
    fun resetWeights() {
        viewModelScope.launch {
            weightsStore.reset()
            _uiState.update { it.copy(weights = weightsStore.current()) }
        }
    }

    private suspend fun publishProfile(profile: TasteProfile) {
        val topArtists = profile.affinity.artistAffinity.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(TOP_COUNT)
            .map { AffinityRow(label = it.key, score = it.value) }

        val topSongEntries = profile.affinity.songAffinity.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(TOP_COUNT)
        // Titles come from the local history; an id never played locally keeps its raw id.
        val songById = try {
            historyRepository.songsByIds(topSongEntries.map { it.key })
                .associateBy { it.youtubeId }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            LogHelper.printe("Failed to resolve debug track titles", exception = exception)
            emptyMap()
        }
        val topTracks = topSongEntries.map { entry ->
            val song = songById[entry.key]
            AffinityRow(
                label = song?.title ?: entry.key,
                sublabel = song?.artist.orEmpty(),
                score = entry.value,
            )
        }

        _uiState.update {
            it.copy(
                loading = false,
                eventCount = profile.eventCount,
                profileComputedAtMs = profile.computedAtMs,
                topArtists = topArtists,
                topTracks = topTracks,
                hourlyTopArtists = profile.hourlyTopArtists,
            )
        }
    }

    private suspend fun refreshWeightsAndHealth() {
        try {
            val nowMs = System.currentTimeMillis()
            val events = playEventRepository.eventsSince(nowMs - DAYS_30_MS)
            _uiState.update {
                it.copy(
                    weights = weightsStore.current(),
                    health7d = skipRatesBySourceContext(events, nowMs - DAYS_7_MS),
                    health30d = skipRatesBySourceContext(events, nowMs - DAYS_30_MS),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            LogHelper.printe("Failed to load reco debug data", exception = exception)
        }
    }

    companion object {
        private const val TOP_COUNT = 20
        private const val DAYS_7_MS = 7L * 24 * 60 * 60 * 1000
        private const val DAYS_30_MS = 30L * 24 * 60 * 60 * 1000

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecoDebugViewModel(application) }
        }
    }
}
