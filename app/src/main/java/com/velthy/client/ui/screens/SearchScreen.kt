package com.velthy.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil3.compose.AsyncImage
import com.velthy.client.data.model.BrowseItem
import com.velthy.client.data.model.BrowseType
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.SearchFilter
import com.velthy.client.data.model.artworkAt
import com.velthy.client.data.model.SearchResult
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.UiState
import com.velthy.client.ui.components.MessageState
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.components.ROW_DIVIDER_INSET
import com.velthy.client.ui.components.SongRow
import com.velthy.client.ui.components.thumbnailBorder
import com.velthy.client.ui.components.songListSkeleton
import com.velthy.client.ui.icons.VelthyIcons

@Composable
fun SearchScreen(
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    results: UiState<List<SearchResult>>?,
    listState: LazyListState,
    recentSongs: List<Song> = emptyList(),
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onBrowseClick: (BrowseItem) -> Unit,
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
    onCategoryClick: (browseId: String, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        // The filters only mean something once there is a result set to narrow
        if (results != null) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                ) {
                    items(SearchFilter.entries) { entry ->
                        FilterPill(
                            label = entry.label,
                            selected = entry == filter,
                            onClick = { onFilterChange(entry) },
                        )
                    }
                }
            }
        }

        when (results) {
            null -> {
                // Landing Page: Recent searches + Explore Categories
                recentSearchesSection(
                    recentSongs = recentSongs,
                    history = history,
                    onSongClick = { song -> onSongClick(listOf(song), 0) },
                    onHistoryClick = onHistoryClick,
                    onHistoryRemove = onHistoryRemove,
                    onHistoryClear = onHistoryClear,
                )

                exploreCategoriesSection(onCategoryClick = onCategoryClick)
            }
            is UiState.Loading -> songListSkeleton(circular = filter == SearchFilter.ARTISTS)
            is UiState.Error -> item { MessageState(results.message) }
            is UiState.Success -> {
                // Tapping a track plays the tracks around it, not the browse rows.
                val tracks = results.data
                    .filterIsInstance<SearchResult.Track>()
                    .map { it.song }
                itemsIndexed(
                    items = results.data,
                    key = { _, row ->
                        when (row) {
                            is SearchResult.Track -> "search_track_${row.song.videoId}"
                            is SearchResult.Browse -> "search_browse_${row.item.type.name}_${row.item.browseId}"
                        }
                    },
                ) { index, row ->
                    when (row) {
                        is SearchResult.Track -> SongRow(
                            song = row.song,
                            onClick = {
                                val trackIndex = tracks.indexOfFirst { it.videoId == row.song.videoId }.coerceAtLeast(0)
                                onSongClick(tracks, trackIndex)
                            },
                            onLongPress = { onSongLongPress(row.song) },
                            onSwipeToQueue = { onSongSwipe(row.song) },
                            downloadedTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is SearchResult.Browse -> BrowseRow(
                            item = row.item,
                            onClick = { onBrowseClick(row.item) },
                        )
                    }
                    if (index < results.data.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Bar Search Input Pill Field pinned at the very top of the app.
 */
@Composable
fun SearchTopBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Type to search",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (onRecognitionClick != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRecognitionClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Music Recognition",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Recent searches: horizontal row of recently played songs + recent search queries.
 */
private fun LazyListScope.recentSearchesSection(
    recentSongs: List<Song>,
    history: List<String>,
    onSongClick: (Song) -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
) {
    val hasHistory = history.isNotEmpty() || recentSongs.isNotEmpty()
    if (!hasHistory) return

    item(key = "recent:header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onHistoryClear)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (recentSongs.isNotEmpty()) {
        item(key = "recent:songs_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(recentSongs.take(10), key = { "recent_song_${it.videoId}" }) { song ->
                    RecentSongCard(song = song, onClick = { onSongClick(song) })
                }
            }
        }
    }

    items(history.take(5), key = { "recent_kw:$it" }) { term ->
        RecentSearchRow(
            term = term,
            onClick = { onHistoryClick(term) },
            onFill = { onHistoryClick(term) },
            onRemove = { onHistoryRemove(term) },
        )
    }
}

@Composable
private fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = song.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(115.dp)
                .clip(RoundedCornerShape(12.dp))
                .thumbnailBorder(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentSearchRow(
    term: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = PAGE_GUTTER, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = term,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                VelthyIcons.NorthWest,
                contentDescription = "Fill \"$term\"",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove \"$term\" from recent searches",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Explore Categories 2x2 grid.
 */
private fun LazyListScope.exploreCategoriesSection(
    onCategoryClick: (browseId: String, title: String) -> Unit,
) {
    item(key = "categories:header") {
        Text(
            text = "Explore Categories",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 20.dp, bottom = 12.dp),
        )
    }

    item(key = "categories:grid") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExploreCategoryCard(
                    title = "New Releases",
                    gradient = Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFF9C27B0))),
                    icon = Icons.Rounded.Album,
                    onClick = { onCategoryClick("FEmusic_new_releases", "New Releases") },
                    modifier = Modifier.weight(1f),
                )
                ExploreCategoryCard(
                    title = "Top Charts",
                    gradient = Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800))),
                    icon = VelthyIcons.TrendingUp,
                    onClick = { onCategoryClick("FEmusic_charts", "Top Charts") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExploreCategoryCard(
                    title = "Moods & Genres",
                    gradient = Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFF673AB7))),
                    icon = Icons.Rounded.GraphicEq,
                    onClick = { onCategoryClick("FEmusic_moods_and_genres", "Moods & Genres") },
                    modifier = Modifier.weight(1f),
                )
                ExploreCategoryCard(
                    title = "Podcasts & Shows",
                    gradient = Brush.linearGradient(listOf(Color(0xFF009688), Color(0xFF00BCD4))),
                    icon = VelthyIcons.Podcasts,
                    onClick = { onCategoryClick("FEmusic_library_non_music_audio_list", "Podcasts & Shows") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ExploreCategoryCard(
    title: String,
    gradient: Brush,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BrowseRow(item: BrowseItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(
                    if (item.type == BrowseType.ARTIST) CircleShape
                    else RoundedCornerShape(8.dp),
                )
                .thumbnailBorder(
                    if (item.type == BrowseType.ARTIST) CircleShape
                    else RoundedCornerShape(8.dp),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle.ifBlank { item.type.name.lowercase().replaceFirstChar { it.uppercase() } },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Fully rounded pill; Material's FilterChip can't be padded this tightly. */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
