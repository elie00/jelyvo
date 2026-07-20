package ca.ilianokokoro.umihi.music.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListeningStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRepository = HistoryRepository(application)
    private val _uiState = MutableStateFlow(ListeningStatsState())
    val uiState = _uiState.asStateFlow()

    init {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        viewModelScope.launch {
            combine(
                historyRepository.topTracks(monthStart),
                historyRepository.topArtists(monthStart),
            ) { tracks, artists -> ListeningStatsState(tracks, artists) }
                .collect { state -> _uiState.update { state } }
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListeningStatsViewModel(application) }
        }
    }
}
