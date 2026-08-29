package com.velthy.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velthy.client.ui.icons.VelthyIcons
import coil3.compose.AsyncImage
import com.velthy.client.data.model.CARD_ART_PX
import com.velthy.client.data.model.HEADER_ART_PX
import com.velthy.client.data.model.HomeShelf
import com.velthy.client.data.model.ShelfItem
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.UiState
import com.velthy.client.data.model.artworkAt
import com.velthy.client.ui.components.MessageState
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.components.PullToRefresh
import com.velthy.client.ui.components.SHELF_CARD_WIDTH
import com.velthy.client.ui.components.SignInBanner
import com.velthy.client.ui.components.feedMoreSkeleton
import com.velthy.client.ui.components.feedSkeleton
import com.velthy.client.ui.components.thumbnailBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    title: String = "Listen Now",
    signedIn: Boolean = true,
    onSignIn: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    onSongLongPress: ((Song) -> Unit)? = null,
    onCategoryClick: ((browseId: String, title: String) -> Unit)? = null,
) {
    val isNewTab = title == "New" || title.equals("explore", ignoreCase = true)

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (!signedIn && onSignIn != null) {
                item {
                    SignInBanner(onSignIn = onSignIn, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            when (state) {
                is UiState.Loading -> feedSkeleton()
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = "Retry", onAction = onRetry)
                }
                is UiState.Success -> {
                    itemsIndexedShelves(
                        shelves = state.data,
                        onItemClick = onItemClick,
                        onSongLongPress = onSongLongPress,
                        onCategoryClick = onCategoryClick,
                        isNewTab = isNewTab,
                    )
                    // Show skeleton at bottom when loading more (always visible when flag is true)
                    if (loadingMore) feedMoreSkeleton()
                    else if (onLoadMore != null) {
                        // Spacer so users see there's content below
                        item(key = "loadmore_bottom_spacer") { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    if (onLoadMore != null && state is UiState.Success) {
        // Trigger load-more when approaching the end of the list.
        val nearEnd by remember {
            derivedStateOf {
                val layout = listState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 5
            }
        }
        LaunchedEffect(nearEnd, loadingMore) {
            if (nearEnd && !loadingMore) onLoadMore()
        }
    }
}

/**
 * Shelves dispatcher matching Apple Music's layout hierarchy:
 * 1. Index 0 -> Full-bleed Hero Card Carousel (Wide landscape banners with overline & title).
 * 2. Song Shelves -> 4-Track Vertical Columns Carousel.
 * 3. Playlists / Moods / Genres -> 2-Row Grid Carousel.
 * 4. Albums / Releases / Charts -> Standard 1-Row Card Carousel.
 * 5. Bottom of New tab -> "More to Explore" categorized links.
 */
private fun LazyListScope.itemsIndexedShelves(
    shelves: List<HomeShelf>,
    onItemClick: (ShelfItem) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onCategoryClick: ((browseId: String, title: String) -> Unit)? = null,
    isNewTab: Boolean = false,
) {
    shelves.forEachIndexed { index, shelf ->
        val isSongShelf = shelf.items.any { it.videoId != null }
        val titleLower = shelf.title.lowercase()
        val isTwoRowShelf = !isSongShelf && shelf.items.size >= 6 && (
            titleLower.contains("playlist") ||
            titleLower.contains("mood") ||
            titleLower.contains("genre") ||
            titleLower.contains("updated")
        )

        item(key = "${shelf.title}_$index") {
            when {
                index == 0 -> HeroShelf(shelf = shelf, onItemClick = onItemClick)
                isSongShelf -> QuickPicksShelf(
                    shelf = shelf,
                    onItemClick = onItemClick,
                    onSongLongPress = onSongLongPress,
                )
                isTwoRowShelf -> TwoRowShelf(
                    shelf = shelf,
                    onItemClick = onItemClick,
                )
                else -> Shelf(
                    shelf = shelf,
                    onItemClick = onItemClick,
                )
            }
        }
    }

    if (isNewTab && onCategoryClick != null) {
        item(key = "more_to_explore_section") {
            MoreToExploreSection(onCategoryClick = onCategoryClick)
        }
    }
}

/** Shared by the home feed, Explore/New and Library so headings line up across tabs. */
@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onShowAll != null) {
            Text(
                text = "Show all",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * 4-Track Vertical Columns Carousel (Apple Music / YouTube Music style).
 * Shows columns of 4 songs scrolling horizontally with snap behavior.
 */
@Composable
private fun QuickPicksShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onClick = {
                shelf.items.firstOrNull()?.let(onItemClick)
            },
        )

        val columns = remember(shelf.items) { shelf.items.chunked(4) }
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = columns,
                key = { column -> column.firstOrNull()?.let { it.videoId ?: it.browseId } ?: column.hashCode() },
            ) { columnItems ->
                Column(
                    modifier = Modifier.fillParentMaxWidth(0.88f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    columnItems.forEach { item ->
                        QuickPickSongRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onMoreClick = onSongLongPress?.let { callback ->
                                {
                                    item.videoId?.let { id ->
                                        callback(
                                            Song(
                                                videoId = id,
                                                title = item.title,
                                                artist = item.subtitle,
                                                thumbnailUrl = item.thumbnailUrl,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickSongRow(
    item: ShelfItem,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(120),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .thumbnailBorder(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMoreClick != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Top Lead Shelf: Apple Music Wide Hero Banner Card Carousel.
 */
@Composable
private fun HeroShelf(shelf: HomeShelf, onItemClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(bottom = 26.dp)) {
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = shelf.items,
                key = { it.videoId ?: it.browseId ?: it.title },
            ) { item ->
                HeroBannerCard(
                    item = item,
                    category = shelf.subtitle.ifBlank { "FEATURED" },
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillParentMaxWidth(0.92f),
                )
            }
        }
    }
}

/** Apple Music style wide landscape banner card with overline, bold title & overlay */
@Composable
private fun HeroBannerCard(
    item: ShelfItem,
    category: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = category.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.65f)
                .clip(RoundedCornerShape(16.dp))
                .thumbnailBorder(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.thumbnailUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 2-Row Grid Carousel (Updated Playlists, Moods & Genres).
 * Stacks 2 square cards vertically per column, scrolling horizontally in pairs.
 */
@Composable
private fun TwoRowShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onClick = {
                shelf.items.firstOrNull()?.let(onItemClick)
            },
        )
        val columns = remember(shelf.items) { shelf.items.chunked(2) }
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(
                items = columns,
                key = { col -> col.firstOrNull()?.let { it.videoId ?: it.browseId ?: it.title } ?: col.hashCode() },
            ) { pair ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    pair.forEach { item ->
                        ShelfCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard 1-Row Card Carousel (Trending Now, Recent Releases, Indonesian Music, etc.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Shelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    leadingCard: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onClick = {
                shelf.items.firstOrNull()?.let(onItemClick)
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leadingCard?.let { card -> item(key = "leading") { card() } }
            items(
                items = shelf.items,
                key = { it.videoId ?: it.browseId ?: it.title },
            ) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { { it(item) } },
                )
            }
        }
    }
}

/**
 * Apple Music "More to Explore" bottom navigation section.
 */
@Composable
private fun MoreToExploreSection(
    onCategoryClick: (browseId: String, title: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 32.dp),
    ) {
        Text(
            text = "More to Explore",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        )

        val exploreCategories = listOf(
            "Browse by Genre" to "FEmusic_moods_and_genres",
            "Top Charts" to "FEmusic_charts",
            "New Releases" to "FEmusic_new_releases",
            "Decades" to "FEmusic_moods_and_genres",
            "Moods and Activities" to "FEmusic_moods_and_genres",
            "Worldwide Charts" to "FEmusic_charts",
        )

        exploreCategories.forEachIndexed { index, (name, browseId) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(browseId, name) }
                    .padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < exploreCategories.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            }
        }
    }
}

/**
 * The "New playlist" card, which is shaped like a shelf card and leads the
 * of the Library's playlist row.
 */
@Composable
internal fun NewShelfCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = if (modifier == Modifier) Modifier.width(SHELF_CARD_WIDTH) else modifier
    Column(
        modifier = cardModifier
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardModifier = if (modifier == Modifier) Modifier.width(SHELF_CARD_WIDTH) else modifier
    Column(
        modifier = cardModifier
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        when (item.browseId) {
            "local:downloads" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .thumbnailBorder(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelthyIcons.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            "local:all" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .thumbnailBorder(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF0D47A1)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            "app:history", "history" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .thumbnailBorder(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE65100), Color(0xFFBF360C)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            else -> {
                AsyncImage(
                    model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .thumbnailBorder(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
