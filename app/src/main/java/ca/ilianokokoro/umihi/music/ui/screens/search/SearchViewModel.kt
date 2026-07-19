package ca.ilianokokoro.umihi.music.ui.screens.search


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    val songRepository = SongRepository()


    fun search() {
        viewModelScope.launch {
            val query = uiState.value.search
            if (query.isBlank()) {
                _uiState.update {
                    _uiState.value.copy(
                        screenState =
                            ScreenState.Success(results = listOf())
                    )
                }
                return@launch
            }

            songRepository.search(query).collect { apiResult ->
                _uiState.update {
                    when (apiResult) {
                        ApiResult.Loading -> it.copy(
                            screenState = ScreenState.Loading,
                            continuation = null
                        )

                        is ApiResult.Error -> it.copy(
                            screenState = ScreenState.Error(apiResult.exception),
                            continuation = null
                        )

                        is ApiResult.Success -> it.copy(
                            screenState = ScreenState.Success(results = apiResult.data.songs),
                            continuation = apiResult.data.continuation
                        )
                    }
                }
            }
        }

    }

    fun loadMore() {
        val state = uiState.value
        val token = state.continuation ?: return
        val current = (state.screenState as? ScreenState.Success)?.results ?: return
        if (state.isLoadingMore) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val more = songRepository.searchMore(token)
                val combined = (current + more.songs).distinctBy { it.youtubeId }
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.Success(results = combined),
                        continuation = more.continuation,
                        isLoadingMore = false,
                    )
                }
            } catch (e: Exception) {
                printe(message = "search loadMore failed", exception = e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onSearchFieldChange(newValue: String) {
        _uiState.update {
            it.copy(search = newValue)
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(application)
            }
        }
    }
}