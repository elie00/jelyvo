package ca.ilianokokoro.umihi.music.ui.screens.moods

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.localizedApiSectionTitle
import ca.ilianokokoro.umihi.music.ui.components.home.HomeFeedSectionRow

/** A mood/genre category page: the sections of playlists/stations behind a moods-hub chip. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoodCategoryScreen(
    browseId: String,
    params: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlaylist: (PlaylistInfo) -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
    application: Application,
    moodCategoryViewModel: MoodCategoryViewModel = viewModel(
        factory = MoodCategoryViewModel.Factory(application, browseId, params)
    ),
) {
    val state = moodCategoryViewModel.uiState.collectAsStateWithLifecycle().value

    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is MoodCategoryState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MusicSkeleton(MusicSkeletonLayout.SongList)
                    }
                    BackButton(onBack = onBack, modifier = Modifier.padding(8.dp))
                }

                is MoodCategoryState.Error -> {
                    ErrorMessage(ex = state.exception, onRetry = moodCategoryViewModel::load)
                    BackButton(onBack = onBack, modifier = Modifier.padding(8.dp))
                }

                is MoodCategoryState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                            start = 8.dp,
                            end = 8.dp
                        )
                    ) {
                        item(key = "header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BackButton(onBack = onBack)
                                Text(
                                    text = localizedApiSectionTitle(title),
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        state.sections.forEachIndexed { index, section ->
                            item(key = "section-$index-${section.title}") {
                                HomeFeedSectionRow(
                                    section = section,
                                    onPlay = PlayerManager::playSong,
                                    onOpenPlaylist = { onOpenPlaylist(it.playlist) },
                                    onOpenAlbum = {
                                        onOpenAlbum(it.browseId, it.title, it.thumbnail)
                                    },
                                    onOpenArtist = {
                                        onOpenArtist(it.browseId, it.name)
                                    },
                                )
                            }
                        }

                        if (state.sections.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.no_results),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
