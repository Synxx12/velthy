package com.velthy.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velthy.client.data.YtMusicRepository
import com.velthy.client.data.model.HomeShelf
import com.velthy.client.data.model.LibraryPage
import com.velthy.client.data.model.ShelfItem
import com.velthy.client.data.model.UiState
import com.velthy.client.ui.icons.VelthyIcons
import com.velthy.client.ui.components.MessageState
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.components.PullToRefresh
import com.velthy.client.ui.components.librarySkeleton
import com.velthy.client.ui.player.MeshGradientBackground
import com.velthy.client.ui.player.rememberArtworkColors
import com.velthy.client.ui.replay.ReplayHeroCard
import java.util.Locale

/**
 * The signed-in library: the saved collections, as shelves of cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    replayCard: ReplayHeroCard? = null,
    onOpenReplay: () -> Unit = {},
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
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
            // Replay leading banner
            item(key = "replay") { ReplayBanner(replayCard, onOpenReplay) }

            item(key = "shelf:$ON_DEVICE") {
                Shelf(
                    shelf = HomeShelf(
                        title = ON_DEVICE,
                        items = listOf(
                            ShelfItem(
                                title = "Downloads",
                                subtitle = "Downloaded songs",
                                thumbnailUrl = null,
                                videoId = null,
                                browseId = "local:downloads",
                            ),
                            ShelfItem(
                                title = "Local Music",
                                subtitle = "Audio files on device",
                                thumbnailUrl = null,
                                videoId = null,
                                browseId = "local:all",
                            ),
                            ShelfItem(
                                title = "History",
                                subtitle = "Listening history",
                                thumbnailUrl = null,
                                videoId = null,
                                browseId = "history",
                            ),
                        ),
                    ),
                    onItemClick = onShelfItemClick,
                    onItemLongPress = onShelfItemLongPress,
                )
            }
            if (!signedIn) {
                item {
                    MessageState(
                        message = "Sign in to your Google account to see your YouTube Music " +
                            "liked songs, playlists and history.",
                        actionLabel = "Sign in",
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = "Retry", onAction = onRetry)
                }
                is UiState.Success -> {
                    val shelves = state.data.shelves
                    if (shelves.none { it.title == PLAYLISTS }) {
                        item(key = "shelf:$PLAYLISTS") {
                            PlaylistShelf(
                                shelf = HomeShelf(PLAYLISTS, emptyList()),
                                onItemClick = onShelfItemClick,
                                onItemLongPress = onShelfItemLongPress,
                                onNewPlaylist = onNewPlaylist,
                            )
                        }
                    }
                    shelves.forEach { shelf ->
                        item(key = "shelf:${shelf.title}") {
                            if (shelf.title == PLAYLISTS) {
                                PlaylistShelf(
                                    shelf = shelf,
                                    onItemClick = onShelfItemClick,
                                    onItemLongPress = onShelfItemLongPress,
                                    onNewPlaylist = onNewPlaylist,
                                )
                            } else {
                                Shelf(
                                    shelf = shelf,
                                    onItemClick = onShelfItemClick,
                                    onItemLongPress = onShelfItemLongPress,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The way in to Replay, at the top of the library page.
 */
@Composable
private fun ReplayBanner(card: ReplayHeroCard?, onClick: () -> Unit) {
    val palette = rememberArtworkColors(card?.artworkUrl)
    Box(
        Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.matchParentSize()) {
            MeshGradientBackground(
                palette = palette,
                trackKey = card?.artworkUrl ?: "replay",
                continuous = true,
                blurRadius = 28.dp,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Your Replay",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = card?.let { "${it.value} ${it.label.lowercase(Locale.ROOT)} · ${it.detail}" }
                        ?: "Top songs, artists, albums and genres — counted on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = VelthyIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The one shelf on this page that can be written to: it leads with the tile
 * that creates a playlist, and holding a card gets rename and delete on top of
 * the queue actions every other shelf's menu offers.
 */
@Composable
private fun PlaylistShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    Shelf(
        shelf = shelf,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        leadingCard = {
            NewShelfCard(
                icon = VelthyIcons.Plus,
                label = "New playlist",
                subtitle = "Saved to YouTube Music",
                onClick = onNewPlaylist,
            )
        },
    )
}

private const val PLAYLISTS = "Playlists"
private const val ON_DEVICE = "On Device"
