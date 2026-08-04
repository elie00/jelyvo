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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import androidx.compose.ui.res.pluralStringResource
import ca.ilianokokoro.umihi.music.models.LocalLibrarySectionKind
import ca.ilianokokoro.umihi.music.models.MoodCategory
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import androidx.compose.foundation.layout.size
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.localizedApiSectionTitle
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.DockScrollToTopEffect
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar
import ca.ilianokokoro.umihi.music.ui.screens.search.components.TopResultCard


@Composable
fun SearchScreen(
    application: Application,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenMoodCategory: (MoodCategory) -> Unit = {},
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
            // The field and filters must survive a failed request: otherwise the only way out
            // of an error is retrying the exact same query.
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
                                        SearchFilter.Library -> R.string.search_library
                                    }
                                )
                            )
                        },
                    )
                }
            }

            if (uiState.screenState is ScreenState.Error) {
                ErrorMessage(
                    ex = uiState.screenState.exception,
                    errorMessage = stringResource(R.string.search_request_failed),
                    onRetry = searchViewModel::search
                )
            } else {
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
                    // An empty query never means "no results": on a fresh install both shelves are
                    // empty (and the moods fetch may simply have failed), which used to fall
                    // through to the no-results branch before the user had searched anything.
                } else if (uiState.search.isBlank()) {
                    val browseListState = rememberLazyListState()
                    DockScrollToTopEffect(browseListState)

                    LazyColumn(
                        state = browseListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING),
                    ) {
                        if (uiState.recentSearches.isEmpty() && uiState.moodSections.isEmpty()) {
                            item(key = "search_idle_prompt") {
                                Text(
                                    text = stringResource(R.string.search_idle_prompt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                )
                            }
                        }
                        if (uiState.recentSearches.isNotEmpty()) {
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
                        if (uiState.moodSections.isNotEmpty()) {
                            item(key = "moods_and_genres_header") {
                                Text(
                                    text = stringResource(R.string.moods_and_genres),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 16.dp,
                                        bottom = 4.dp,
                                    ),
                                )
                            }
                            items(
                                items = uiState.moodSections,
                                key = { "mood_section_${it.title}" },
                            ) { section ->
                                MoodSectionChips(
                                    section = section,
                                    onOpenMoodCategory = { category ->
                                        focusManager.clearFocus()
                                        onOpenMoodCategory(category)
                                    },
                                )
                            }
                        }
                    }
                } else when (uiState.screenState) {
                    ScreenState.Loading -> {
                        MusicSkeleton(MusicSkeletonLayout.Search)
                    }

                    is ScreenState.Success -> {
                        val results = uiState.screenState.results
                        val topResult = uiState.screenState.topResult
                        val librarySections = uiState.screenState.librarySections
                        if (results.isNotEmpty() || topResult != null || librarySections.isNotEmpty()) {
                            val listState = rememberLazyListState()
                            DockScrollToTopEffect(listState)

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
                                uiState.screenState.spellCorrection?.let { correction ->
                                    item(key = "spell_correction") {
                                        SpellCorrectionHeader(
                                            correction = correction,
                                            onSearch = searchViewModel::searchCorrection,
                                        )
                                    }
                                }

                                // Onglet « Ma bibliotheque » : sections locales groupees par
                                // origine, chacune sous un petit en-tete.
                                librarySections.forEach { section ->
                                    item(key = "library_header_${section.kind}") {
                                        SearchSectionHeader(
                                            text = stringResource(section.kind.titleRes()),
                                        )
                                    }
                                    items(
                                        items = section.playlists,
                                        key = { "library_playlist_${it.id}" },
                                    ) { match ->
                                        SearchBrowseResult(
                                            title = match.name,
                                            subtitle = pluralStringResource(
                                                R.plurals.song_count,
                                                match.songCount,
                                                match.songCount,
                                            ),
                                            thumbnail = match.coverHref,
                                            onClick = {
                                                searchViewModel.playLocalPlaylist(match.id)
                                            },
                                        )
                                    }
                                    items(
                                        items = section.songs,
                                        key = { "library_${section.kind}_${it.youtubeId}" },
                                        contentType = { "library_song" },
                                    ) { song ->
                                        SongListItem(
                                            song = song,
                                            onPress = { PlayerManager.playSong(song) },
                                            playNext = { PlayerManager.addNext(song, context) },
                                            addToQueue = {
                                                PlayerManager.addToQueue(song, context)
                                            },
                                            startRadio = {
                                                PlayerManager.startRadio(song, context)
                                            },
                                        )
                                    }
                                }

                                if (topResult != null) {
                                    item(key = "top_result") {
                                        TopResultCard(
                                            item = topResult,
                                            onPlay = { searchViewModel.playTopResult(topResult) },
                                            onOpen = {
                                                when (topResult) {
                                                    is SearchItem.Track ->
                                                        PlayerManager.playSong(topResult.song)

                                                    is SearchItem.Album -> onOpenAlbum(
                                                        topResult.id,
                                                        topResult.title,
                                                        topResult.thumbnail,
                                                    )

                                                    is SearchItem.Artist -> onOpenArtist(
                                                        Song(
                                                            youtubeId = "",
                                                            artist = topResult.name
                                                        ).also {
                                                            it.artistId = topResult.id
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                    }
                                }

                                // Section « Deja ecoute » : les correspondances de l'historique
                                // de lecture, quelques lignes au-dessus des resultats distants.
                                if (uiState.historyMatches.isNotEmpty()) {
                                    item(key = "history_matches_header") {
                                        SearchSectionHeader(
                                            text = stringResource(R.string.search_history_heading),
                                        )
                                    }
                                    items(
                                        items = uiState.historyMatches,
                                        // Prefixed keys: the same track may also be a remote result.
                                        key = { "history_${it.youtubeId}" },
                                        contentType = { "history_song" },
                                    ) { song ->
                                        SongListItem(
                                            song = song,
                                            onPress = { PlayerManager.playSong(song) },
                                            playNext = { PlayerManager.addNext(song, context) },
                                            addToQueue = {
                                                PlayerManager.addToQueue(song, context)
                                            },
                                            startRadio = {
                                                PlayerManager.startRadio(song, context)
                                            },
                                        )
                                    }
                                }

                                items(
                                    items = results,
                                    key = { it.id },
                                    contentType = { it::class }) { item ->
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
                                uiState.screenState.spellCorrection?.let { correction ->
                                    SpellCorrectionHeader(
                                        correction = correction,
                                        onSearch = searchViewModel::searchCorrection,
                                    )
                                }
                                Text(stringResource(R.string.no_results))
                            }
                        }
                    }
                }

            }
        }
    }

}

/**
 * The spell-correction header shown above the result rows, mirroring YT Music: a clickable
 * "Did you mean <corrected>" suggestion, or "Showing results for <corrected>" followed by a
 * clickable "Search instead for <original>" when the results were auto-corrected. [onSearch]
 * reruns the search with the tapped link's query and searchEndpoint params.
 */
@Composable
private fun SpellCorrectionHeader(
    correction: SpellCorrection,
    onSearch: (query: String, params: String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (correction.autoCorrected) {
            Text(
                text = correctionText(
                    R.string.search_showing_results_for,
                    correction.correctedQuery,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            correction.originalQuery?.let { original ->
                Text(
                    text = correctionText(R.string.search_instead_for, original),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onSearch(original, correction.originalParams)
                    },
                )
            }
        } else {
            Text(
                text = correctionText(
                    R.string.search_did_you_mean,
                    correction.correctedQuery,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    onSearch(correction.correctedQuery, correction.correctedParams)
                },
            )
        }
    }
}

/** Formats a "%1$s" string resource with [query] rendered in bold italics, YT Music style. */
@Composable
private fun correctionText(templateRes: Int, query: String): AnnotatedString {
    val template = stringResource(templateRes)
    val queryStyle = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    return buildAnnotatedString {
        val placeholder = "%1\$s"
        val index = template.indexOf(placeholder)
        if (index == -1) {
            append(template)
            append(" ")
            withStyle(queryStyle) { append(query) }
        } else {
            append(template.substring(0, index))
            withStyle(queryStyle) { append(query) }
            append(template.substring(index + placeholder.length))
        }
    }
}

/** One "Moods & genres" hub section rendered as a wrapping row of category chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodSectionChips(
    section: MoodCategorySection,
    onOpenMoodCategory: (MoodCategory) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (section.title.isNotBlank()) {
            Text(
                text = localizedApiSectionTitle(section.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            section.categories.forEach { category ->
                SuggestionChip(
                    onClick = { onOpenMoodCategory(category) },
                    label = { Text(localizedApiSectionTitle(category.title)) },
                )
            }
        }
    }
}

/** L'en-tete d'une section locale (« Ma bibliotheque », « Deja ecoute »). */
@Composable
private fun SearchSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Le libelle d'en-tete de chaque origine de l'onglet « Ma bibliotheque ». */
private fun LocalLibrarySectionKind.titleRes(): Int = when (this) {
    LocalLibrarySectionKind.LIKED -> R.string.liked_songs
    LocalLibrarySectionKind.PLAYLISTS -> R.string.on_device_playlists
    LocalLibrarySectionKind.LOCAL_AUDIO -> R.string.on_device
    LocalLibrarySectionKind.DOWNLOADS -> R.string.downloaded
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
