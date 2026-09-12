package com.velthy.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import com.velthy.client.ui.components.thumbnailBorder
import com.velthy.client.ui.components.songListSkeleton
import com.velthy.client.ui.icons.VelthyIcons

@Composable
fun SearchScreen(
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    results: UiState<List<SearchResult>>?,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    listState: LazyListState,
    /**
     * Incremented by the view model once per first-page request, so a new search
     * can't inherit the scroll position of the last one (or of the recent
     * searches the user just came from).
     */
    scrollResetTrigger: Int = 0,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    /** The promoted card's Play — the same station a tapped result starts. */
    onTopResultPlay: (Song) -> Unit,
    /** The promoted card's Playlist — opens the add-to-playlist picker. */
    onTopResultPlaylist: (Song) -> Unit,
    onBrowseClick: (BrowseItem) -> Unit,
    /**
     * Holding an album or playlist hit rather than tapping it — the same menu
     * the shelves open, so a release found by searching can go on the queue
     * without a trip through its page.
     */
    onBrowseLongPress: ((BrowseItem) -> Unit)? = null,
    history: List<String>,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    onSuggestionFill: (String) -> Unit = {},
    /**
     * What the catalogue answers the typed text with, shown under the
     * completions — the Apple Music shape: keywords first, then a glimpse of
     * the songs themselves so the right one can be tapped without going through
     * a results page.
     */
    previewSongs: List<Song> = emptyList(),
    onPreviewSongPlay: (Song) -> Unit = {},
    onHistoryClick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    val focusManager = LocalFocusManager.current
    // Search keeps one list state while its contents change. Reset it for each
    // new request so choosing a recent search cannot inherit the history's
    // previous scroll position (or a previous result page's position).
    LaunchedEffect(scrollResetTrigger) {
        if (scrollResetTrigger > 0) listState.scrollToItem(0)
    }
    // A non-empty suggestion list means the field is mid-edit — see
    // MainViewModel.onQueryChange. Nothing below it is worth showing while it is
    // up: the results are for whatever was searched before this edit began, and
    // so are the filter tabs above them.
    val suggesting = suggestions.isNotEmpty()
    LaunchedEffect(listState, results, loadingMore) {
        if (results !is UiState.Success) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (!loadingMore && total > 0 && lastVisible >= total - 4) onLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The filter tabs stay fixed under the top bar, outside the scrolling
        // list, so they're always reachable rather than scrolling away with the
        // results beneath them. The bar's own height is reserved here so the
        // list below never climbs under the glass.
        Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
            if (results != null && !suggesting) {
                SearchFilterTabs(filter = filter, onFilterChange = onFilterChange)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            if (suggesting) {
                searchSuggestions(
                    suggestions = suggestions,
                    songs = previewSongs,
                    // Picking one is done typing, so the keyboard comes down
                    // with it and the results get the whole screen.
                    onClick = { term ->
                        onSuggestionClick(term)
                        focusManager.clearFocus()
                    },
                    onFill = onSuggestionFill,
                    onSongClick = { song ->
                        onPreviewSongPlay(song)
                        focusManager.clearFocus()
                    },
                    onSongLongPress = onSongLongPress,
                    onSongSwipe = onSongSwipe,
                )
            } else when (results) {
                null -> if (history.isEmpty()) {
                    item { MessageState("Search for songs, artists, albums and playlists") }
                } else {
                    recentSearches(history, onHistoryClick, onHistoryRemove, onHistoryClear)
                }
                is UiState.Loading -> songListSkeleton(circular = filter == SearchFilter.ARTISTS)
                is UiState.Error -> item { MessageState(results.message) }
                is UiState.Success -> {
                    // Tapping a track plays the tracks around it, not the browse
                    // rows, and not the promoted card.
                    val tracks = results.data.mapNotNull { row ->
                        when (row) {
                            is SearchResult.TopTrack -> row.song
                            is SearchResult.Track -> row.song
                            is SearchResult.Browse -> null
                        }
                    }
                    val topResult = results.data.filterIsInstance<SearchResult.TopTrack>().firstOrNull()
                    if (filter == SearchFilter.ALL && topResult != null) {
                        item(key = "search:top-result:${topResult.song.videoId}") {
                            TopResultCard(
                                song = topResult.song,
                                onPlay = { onTopResultPlay(topResult.song) },
                                onPlaylist = { onTopResultPlaylist(topResult.song) },
                                onLongPress = { onSongLongPress(topResult.song) },
                            )
                        }
                    }
                    searchSections(results.data, filter).forEach { section ->
                        section.title?.let { title ->
                            item(key = "search-section:$title") {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(
                                        start = PAGE_GUTTER,
                                        end = PAGE_GUTTER,
                                        top = 16.dp,
                                        bottom = 6.dp,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        itemsIndexed(
                            items = section.rows,
                            key = { _, row -> searchRowKey(row) },
                        ) { index, row ->
                            when (row) {
                                is SearchResult.TopTrack -> Unit
                                is SearchResult.Track -> SongRow(
                                    song = row.song,
                                    onClick = {
                                        val at = tracks.indexOf(row.song).coerceAtLeast(0)
                                        onSongClick(tracks, at)
                                    },
                                    onLongPress = { onSongLongPress(row.song) },
                                    onSwipeToQueue = { onSongSwipe(row.song) },
                                    downloadedTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                is SearchResult.Browse -> BrowseRow(
                                    item = row.item,
                                    onClick = { onBrowseClick(row.item) },
                                    onLongPress = onBrowseLongPress?.let { cb -> { cb(row.item) } },
                                )
                            }
                            if (index < section.rows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    if (loadingMore) songListSkeleton(
                        count = 3,
                        keyPrefix = "skeleton:search:more",
                        circular = filter == SearchFilter.ARTISTS,
                    )
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

private data class SearchSection(val title: String?, val rows: List<SearchResult>)

/**
 * The unfiltered page is useful only when its mixed result types are readable
 * at a glance, so it is split into one section per kind. A filtered page is one
 * kind already and stays a single untitled run.
 */
private fun searchSections(rows: List<SearchResult>, filter: SearchFilter): List<SearchSection> {
    if (filter != SearchFilter.ALL) return listOf(SearchSection(null, rows))
    return listOf(
        SearchSection("Songs", rows.filterIsInstance<SearchResult.Track>()),
        SearchSection(
            "Artists",
            rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.ARTIST },
        ),
        SearchSection(
            "Albums",
            rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.ALBUM },
        ),
        SearchSection(
            "Playlists",
            rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.PLAYLIST },
        ),
        SearchSection(
            "More",
            rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.OTHER },
        ),
    ).filter { it.rows.isNotEmpty() }
}

private fun searchRowKey(row: SearchResult): String = when (row) {
    is SearchResult.TopTrack -> "search_top:${row.song.videoId}"
    is SearchResult.Track -> "search_track:${row.song.videoId}"
    is SearchResult.Browse -> "search_browse:${row.item.type.name}:${row.item.browseId}"
}

/**
 * The promoted card the unfiltered page carries at its head.
 *
 * Deliberately louder than a row: it is the answer the search was most likely
 * for, so it gets the artwork at a size you can recognise and the two things
 * worth doing with it — play it, or put it on a playlist — without a trip
 * through the long-press menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(
    song: Song,
    onPlay: () -> Unit,
    onPlaylist: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 18.dp, bottom = 8.dp)
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress),
    ) {
        Text(
            text = "Top result",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = song.thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLongPress, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPlay,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            OutlinedButton(
                onClick = onPlaylist,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Playlist")
            }
        }
    }
}

/**
 * What YouTube would complete the half-typed query to, in place of the results
 * while it is being typed.
 *
 * The first row is the text as typed, put there by the view model rather than
 * taken from YouTube's answer, so running exactly what was asked for is always
 * the nearest row to the keyboard rather than something the thumb has to aim
 * past.
 */
private fun LazyListScope.searchSuggestions(
    suggestions: List<String>,
    songs: List<Song>,
    onClick: (String) -> Unit,
    onFill: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
) {
    // A list-level inset rather than padding hidden inside the first row, so the
    // gap under the bar stays stable even when that row changes its text.
    item(key = "suggestions:top-inset") { Spacer(Modifier.height(12.dp)) }
    itemsIndexed(suggestions, key = { _, term -> "suggest:$term" }) { index, term ->
        SuggestionRow(
            term = term,
            isQueryAction = index == 0,
            // The lead row *is* what's in the field, so there is nothing to fill
            // it with and the arrow would be a no-op button.
            onFill = if (index == 0) null else ({ onFill(term) }),
            onClick = { onClick(term) },
        )
    }
    // The songs themselves, under the completions. Tapping one plays it on the
    // spot — the same thing tapping it on the results page does — so the
    // keywords and the song can be picked from the one list.
    if (songs.isNotEmpty()) {
        item(key = "preview:header") {
            Text(
                text = "Songs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    top = 18.dp,
                    bottom = 6.dp,
                ),
            )
        }
        itemsIndexed(songs, key = { _, song -> "preview:${song.videoId}" }) { index, song ->
            SongRow(
                song = song,
                onClick = { onSongClick(song) },
                onLongPress = { onSongLongPress(song) },
                onSwipeToQueue = { onSongSwipe(song) },
                downloadedTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * One typeahead row: tap the text to search it, or the arrow to put it in the
 * field and carry on typing — the pair every mobile keyboard's own suggestion
 * strip uses, and the reason a longer completion isn't a dead end when it's
 * only nearly right.
 */
@Composable
private fun SuggestionRow(
    term: String,
    isQueryAction: Boolean,
    onFill: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = PAGE_GUTTER, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            // The first row is the deliberate action to search the exact text in
            // the field, not a server-provided completion. Naming it makes the
            // otherwise duplicated wording read as intentional.
            text = if (isQueryAction) "Search \u201C$term\u201D" else term,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onFill != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    VelthyIcons.NorthWest,
                    contentDescription = "Fill \"$term\"",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // Match the arrow button's full touch target, not only its width: a
            // width-only spacer left the first row shorter than the ones below.
            Spacer(Modifier.size(40.dp))
        }
    }
}

/**
 * What was searched for before, shown in place of the results while the field
 * is empty.
 */
private fun LazyListScope.recentSearches(
    history: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
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
            Text(
                text = "Clear",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    items(history, key = { "recent_kw:$it" }) { term ->
        RecentSearchRow(
            term = term,
            onClick = { onClick(term) },
            onFill = { onClick(term) },
            onRemove = { onRemove(term) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRow(
    item: BrowseItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
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

/**
 * Filter pills rather than a tab row: squarish rounded rectangles, the selected
 * one inverted. They scroll horizontally so a long label set never squeezes the
 * text, and the gutter padding sits inside the scroll so it scrolls with them.
 */
@Composable
private fun SearchFilterTabs(filter: SearchFilter, onFilterChange: (SearchFilter) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchFilter.entries.forEach { entry ->
            val selected = entry == filter
            Box(
                modifier = Modifier
                    .clip(FILTER_PILL_SHAPE)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    // Only the pill that isn't already selected has anything to
                    // report — re-tapping the current filter changes nothing, so
                    // buzzing for it would be feedback for a no-op.
                    .clickable {
                        if (!selected) haptics.play(Haptic.Select)
                        onFilterChange(entry)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** Rounded, but well short of a capsule — the corner reads as a cut, not a curve. */
private val FILTER_PILL_SHAPE = RoundedCornerShape(12.dp)
