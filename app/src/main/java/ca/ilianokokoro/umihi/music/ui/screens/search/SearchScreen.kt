package ca.ilianokokoro.umihi.music.ui.screens.search

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import androidx.compose.foundation.layout.size
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar


@Composable
fun SearchScreen(
    application: Application,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    searchViewModel: SearchViewModel = viewModel(
        factory =
            SearchViewModel.Factory(application = application)
    )

) {
    val uiState = searchViewModel.uiState.collectAsStateWithLifecycle().value

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                focusManager.clearFocus()
                searchViewModel.onSearchFieldChange(spoken)
                searchViewModel.search()
            }
        }
    }

    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                context.getString(R.string.voice_search_prompt)
            )
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                R.string.voice_search_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (uiState.search.isBlank()) {
            focusRequester.requestFocus()
        }
    }
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (uiState.screenState is ScreenState.Error) {
                ErrorMessage(
                    ex = uiState.screenState.exception,
                    onRetry = searchViewModel::search
                )
            } else {
                SearchBar(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    value = uiState.search,
                    onValueChange = searchViewModel::onSearchFieldChange,
                    onSearch = searchViewModel::search,
                    onVoiceSearch = startVoiceSearch,
                    focusManager = focusManager,
                    focusRequester = focusRequester,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { searchViewModel.selectFilter(filter) },
                            label = {
                                Text(
                                    stringResource(
                                        when (filter) {
                                            SearchFilter.All -> R.string.search_all
                                            SearchFilter.Songs -> R.string.search_songs
                                            SearchFilter.Videos -> R.string.search_videos
                                            SearchFilter.Albums -> R.string.search_albums
                                            SearchFilter.Artists -> R.string.search_artists
                                        }
                                    )
                                )
                            },
                        )
                    }
                }

                if (uiState.showSuggestions && uiState.suggestions.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.suggestions, key = { it }) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion) },
                                modifier = Modifier.clickable {
                                    focusManager.clearFocus()
                                    searchViewModel.selectSuggestion(suggestion)
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                } else if (uiState.search.isBlank() && uiState.recentSearches.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "recent_searches_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.recent_searches),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TextButton(onClick = searchViewModel::clearRecentSearches) {
                                    Text(stringResource(R.string.recent_searches_clear_all))
                                }
                            }
                        }
                        items(uiState.recentSearches, key = { it }) { query ->
                            ListItem(
                                headlineContent = { Text(query) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { searchViewModel.removeRecentSearch(query) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.recent_search_remove),
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    focusManager.clearFocus()
                                    searchViewModel.selectSuggestion(query)
                                },
                            )
                        }
                    }
                } else when (uiState.screenState) {
                    ScreenState.Loading -> {
                        LoadingAnimation()
                    }

                    is ScreenState.Success -> {
                        val results = uiState.screenState.results
                        if (results.isNotEmpty()) {
                            val listState = rememberLazyListState()

                            // Infinite scroll: load the next page as we approach the end.
                            LaunchedEffect(listState, results.size, uiState.continuation) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                }.collect { lastVisible ->
                                    if (uiState.continuation != null &&
                                        lastVisible >= results.size - 3
                                    ) {
                                        searchViewModel.loadMore()
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                contentPadding = PaddingValues(bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING),
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                items(
                                    items = results,
                                    key = { it.id }) { item ->
                                    when (item) {
                                    is SearchItem.Track -> SongListItem(
                                        song = item.song,
                                        onPress = {
                                            PlayerManager.playSong(item.song)
                                        },
                                        playNext = {
                                            PlayerManager.addNext(item.song, context)
                                        },
                                        addToQueue = {
                                            PlayerManager.addToQueue(item.song, context)
                                        },
                                        startRadio = {
                                            PlayerManager.startRadio(item.song, context)
                                        },
                                        onViewArtist = { onOpenArtist(item.song) },
                                        onViewAlbum = {
                                            item.song.albumId?.let { id ->
                                                onOpenAlbum(id, item.song.albumName ?: "", item.song.thumbnailHref)
                                            }
                                        }
                                    )
                                    is SearchItem.Album -> SearchBrowseResult(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        thumbnail = item.thumbnail,
                                        onClick = { onOpenAlbum(item.id, item.title, item.thumbnail) },
                                    )
                                    is SearchItem.Artist -> SearchBrowseResult(
                                        title = item.name,
                                        subtitle = "",
                                        thumbnail = item.thumbnail,
                                        round = true,
                                        onClick = {
                                            onOpenArtist(Song(youtubeId = "", artist = item.name).also {
                                                it.artistId = item.id
                                            })
                                        },
                                    )
                                    }
                                }

                                if (uiState.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingAnimation()
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center

                            ) {
                                Text(stringResource(R.string.no_results))
                            }
                        }
                    }
                }

            }
        }
    }

}

@Composable
private fun SearchBrowseResult(
    title: String,
    subtitle: String,
    thumbnail: String,
    onClick: () -> Unit,
    round: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle.takeIf(String::isNotBlank)?.let { value ->
            { Text(value) }
        },
        leadingContent = {
            SquareImage(
                uri = thumbnail,
                modifier = Modifier.size(64.dp),
                cornerRadius = if (round) 32.dp else 8.dp,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

