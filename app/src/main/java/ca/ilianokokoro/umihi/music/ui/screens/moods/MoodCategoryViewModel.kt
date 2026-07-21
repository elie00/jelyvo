package ca.ilianokokoro.umihi.music.ui.screens.moods

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.MoodsRepository
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
sealed class MoodCategoryState {
    data object Loading : MoodCategoryState()
    data class Success(val sections: List<HomeFeedSection>) : MoodCategoryState()
    data class Error(val exception: Exception) : MoodCategoryState()
}

class MoodCategoryViewModel(
    application: Application,
    private val browseId: String,
    private val params: String,
) : AndroidViewModel(application) {
    private val moodsRepository = MoodsRepository()
    private val datastoreRepository = DatastoreRepository(application)

    private val _uiState = MutableStateFlow<MoodCategoryState>(MoodCategoryState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            moodsRepository.retrieveCategory(browseId, params, settings).collect { result ->
                _uiState.update {
                    when (result) {
                        is ApiResult.Loading -> MoodCategoryState.Loading
                        is ApiResult.Success -> MoodCategoryState.Success(result.data)
                        is ApiResult.Error -> {
                            printe(
                                message = "Failed to load mood category",
                                exception = result.exception
                            )
                            MoodCategoryState.Error(result.exception)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun Factory(
            application: Application,
            browseId: String,
            params: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MoodCategoryViewModel(application, browseId, params)
            }
        }
    }
}
