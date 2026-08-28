package com.velthy.client.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.velthy.client.data.model.HistorySection
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.UiState
import com.velthy.client.ui.components.MessageState
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.components.ROW_DIVIDER_INSET
import com.velthy.client.ui.components.SongRow
import com.velthy.client.ui.components.songListSkeleton

/**
 * What the account has been listening to, grouped by time periods (Today, Yesterday, This week, etc.).
 *
 * Tapping a row plays it with the rest of the history behind it, so the list
 * doubles as a queue that has already been approved once.
 */
@Composable
fun HistoryScreen(
    state: UiState<List<HistorySection>>,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        when (state) {
            is UiState.Loading -> songListSkeleton(count = 10, keyPrefix = "skeleton:history")

            is UiState.Error -> item(key = "history:message") {
                MessageState(
                    message = state.message,
                    actionLabel = "Try again",
                    onAction = onRetry,
                )
            }

            is UiState.Success -> {
                val sections = state.data
                val allSongs = sections.flatMap { it.songs }

                sections.forEachIndexed { sectionIdx, section ->
                    if (section.songs.isNotEmpty()) {
                        item(key = "header:${section.title}:$sectionIdx") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(
                                        start = PAGE_GUTTER,
                                        end = PAGE_GUTTER,
                                        top = if (sectionIdx == 0) 8.dp else 20.dp,
                                        bottom = 8.dp,
                                    ),
                            )
                        }

                        items(
                            count = section.songs.size,
                            key = { "${section.songs[it].videoId}:$sectionIdx:$it" },
                        ) { songIdx ->
                            val song = section.songs[songIdx]
                            val globalIndex = allSongs.indexOf(song).takeIf { it >= 0 } ?: 0

                            SongRow(
                                song = song,
                                onClick = { onSongClick(allSongs, globalIndex) },
                                onLongPress = { onSongLongPress(song) },
                                onSwipeToQueue = { onSongSwipe(song) },
                            )

                            if (songIdx < section.songs.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
