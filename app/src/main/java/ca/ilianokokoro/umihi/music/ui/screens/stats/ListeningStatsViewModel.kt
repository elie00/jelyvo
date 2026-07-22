package ca.ilianokokoro.umihi.music.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListeningStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRepository = HistoryRepository(application)
    private val _uiState = MutableStateFlow(ListeningStatsState())
    val uiState = _uiState.asStateFlow()

    private val selectedPeriod = MutableStateFlow(StatsPeriod.MONTH)

    init {
        collectRankings()
        collectHeatmap()
    }

    fun selectPeriod(period: StatsPeriod) {
        selectedPeriod.value = period
        _uiState.update { it.copy(period = period) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectRankings() {
        viewModelScope.launch {
            selectedPeriod
                .flatMapLatest { period ->
                    val since = period.sinceMillis()
                    combine(
                        historyRepository.topTracks(since),
                        historyRepository.topArtists(since),
                    ) { tracks, artists -> tracks to artists }
                }
                .collect { (tracks, artists) ->
                    _uiState.update { it.copy(topTracks = tracks, topArtists = artists) }
                }
        }
    }

    private fun collectHeatmap() {
        val since = LocalDate.now()
            .minusWeeks(HEATMAP_WEEKS.toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        viewModelScope.launch {
            historyRepository.dailyPlayCounts(since).collect { counts ->
                val parsed = counts.mapNotNull { entry ->
                    runCatching { LocalDate.parse(entry.day) to entry.playCount }.getOrNull()
                }.toMap()
                _uiState.update { it.copy(dailyCounts = parsed) }
            }
        }
    }

    companion object {
        private const val HEATMAP_WEEKS = 26

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListeningStatsViewModel(application) }
        }
    }
}
