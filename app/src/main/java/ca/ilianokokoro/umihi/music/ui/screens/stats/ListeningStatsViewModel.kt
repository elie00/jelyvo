package ca.ilianokokoro.umihi.music.ui.screens.stats

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper
import ca.ilianokokoro.umihi.music.core.stats.StatsExporter
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        collectWrapped()
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

    private fun collectWrapped() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = calendar.get(Calendar.YEAR)
        val yearStart = calendar.timeInMillis

        viewModelScope.launch {
            combine(
                historyRepository.playCount(yearStart),
                historyRepository.topTracks(yearStart, limit = 1),
                historyRepository.topArtists(yearStart, limit = 1),
                historyRepository.trackPlayDurations(yearStart),
                historyRepository.dailyPlayCounts(yearStart),
            ) { totalPlays, topTracks, topArtists, durations, dailyCounts ->
                if (totalPlays == 0) return@combine null

                val listeningTimeMs = durations.sumOf { track ->
                    val perPlayMs = when {
                        track.durationMs > 0 -> track.durationMs
                        else -> durationToMillis(track.duration)
                    }
                    perPlayMs * track.playCount
                }
                val busiestDay = dailyCounts.maxByOrNull { it.playCount }
                WrappedStats(
                    year = year,
                    totalPlays = totalPlays,
                    listeningTimeMs = listeningTimeMs,
                    topTrack = topTracks.firstOrNull(),
                    topArtist = topArtists.firstOrNull(),
                    mostActiveDay = busiestDay?.let {
                        runCatching { LocalDate.parse(it.day) }.getOrNull()
                    },
                    mostActiveDayPlays = busiestDay?.playCount ?: 0,
                )
            }.collect { wrapped ->
                _uiState.update { it.copy(wrapped = wrapped) }
            }
        }
    }

    /**
     * Writes every period's totals and rankings to the JSON file picked through
     * ACTION_CREATE_DOCUMENT. Runs the queries once ([kotlinx.coroutines.flow.first]) — the export
     * is a snapshot, not a live subscription.
     */
    fun exportStats(uri: Uri) {
        if (_uiState.value.exportInProgress) return
        _uiState.update { it.copy(exportInProgress = true) }
        viewModelScope.launch {
            try {
                val periods = StatsPeriod.entries.map { period ->
                    val since = period.sinceMillis()
                    StatsExporter.buildPeriod(
                        period = period.name.lowercase(),
                        sinceMillis = since,
                        totalPlays = historyRepository.playCount(since).first(),
                        topTracks = historyRepository.topTracks(since).first(),
                        topArtists = historyRepository.topArtists(since).first(),
                    )
                }
                StatsExporter.exportToUri(
                    context = getApplication(),
                    uri = uri,
                    export = StatsExporter.buildExport(System.currentTimeMillis(), periods),
                )
                showToast(R.string.stats_export_success)
            } catch (e: Exception) {
                LogHelper.printe("Stats export failed", exception = e)
                showToast(R.string.stats_export_error)
            } finally {
                _uiState.update { it.copy(exportInProgress = false) }
            }
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(
            getApplication(),
            getApplication<Application>().getString(messageRes),
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Converts a display duration ("m:ss" or "h:mm:ss") into millis; 0 when unparseable. */
    private fun durationToMillis(duration: String): Long {
        if (duration.isBlank()) return 0L

        val parts = duration.split(":").map { it.trim().toLongOrNull() ?: return 0L }
        val seconds = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            1 -> parts[0]
            else -> 0L
        }
        return seconds * 1000
    }

    companion object {
        private const val HEATMAP_WEEKS = 26

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListeningStatsViewModel(application) }
        }
    }
}
