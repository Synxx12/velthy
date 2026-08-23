package com.music.bitchord.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.history.PlaybackHistoryManager
import com.music.bitchord.data.history.PlaybackHistoryManager.HistoryItem
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.thumbnailBorder

enum class HistorySourceTab {
    LOCAL,
    REMOTE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    localHistoryItems: List<HistoryItem>,
    remoteHistoryItems: List<HistoryItem>,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    listState: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onRemoveItem: (HistoryItem, Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(HistorySourceTab.LOCAL) }

    val activeItems = when (selectedTab) {
        HistorySourceTab.LOCAL -> localHistoryItems
        HistorySourceTab.REMOTE -> remoteHistoryItems
    }

    val grouped = remember(activeItems) {
        PlaybackHistoryManager.groupHistory(activeItems)
    }

    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        if (selectedTab == HistorySourceTab.REMOTE) {
            onRefresh()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 84.dp,
                ),
            ) {
                // Top Summary Card (Local vs Remote)
                item(key = "history_summary_card", contentType = "summary_card") {
                    HistoryHeaderCard(
                        selectedTab = selectedTab,
                        localCount = localHistoryItems.size,
                        remoteCount = remoteHistoryItems.size,
                        onTabSelected = { selectedTab = it },
                    )
                }

                if (activeItems.isEmpty()) {
                    item(key = "history_empty_state", contentType = "empty") {
                        EmptyHistoryView(isRemote = selectedTab == HistorySourceTab.REMOTE)
                    }
                } else {
                    grouped.forEach { section ->
                        // Section Header
                        item(
                            key = "sec_header_${section.group.name}_${selectedTab.name}",
                            contentType = "sec_header",
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { }
                                    .padding(
                                        start = PAGE_GUTTER + 6.dp,
                                        end = PAGE_GUTTER + 6.dp,
                                        top = 22.dp,
                                        bottom = 6.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = section.label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (section.items.size == 1) "1 song" else "${section.items.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Section Songs (Fast, Lazy recycling with items)
                        items(
                            items = section.items,
                            key = { "hist_${it.song.videoId}_${it.playedAt}" },
                            contentType = { "history_song_row" },
                        ) { item ->
                            val isCurrent = currentSong?.videoId == item.song.videoId
                            val song = item.song

                            HistoryRowItem(
                                song = song,
                                isCurrent = isCurrent,
                                onClick = {
                                    val allSongs = activeItems.map { it.song }
                                    val songIdx = allSongs.indexOfFirst { it.videoId == song.videoId }
                                    onSongClick(allSongs, if (songIdx >= 0) songIdx else 0)
                                },
                                onLongPress = { onSongLongPress(song) },
                            )
                        }
                    }
                }
            }
        }

        // Floating Shuffle Button
        AnimatedVisibility(
            visible = activeItems.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PAGE_GUTTER + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { onShuffle(activeItems.map { it.song }) },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = {
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Top summary card matching Musique's clean surface card design.
 */
@Composable
private fun HistoryHeaderCard(
    selectedTab: HistorySourceTab,
    localCount: Int,
    remoteCount: Int,
    onTabSelected: (HistorySourceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = if (selectedTab == HistorySourceTab.LOCAL) "Local" else "Remote",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selectedTab == HistorySourceTab.LOCAL) "Played on this device" else "Synced from YouTube Music",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selectedTab == HistorySourceTab.LOCAL) "$localCount songs" else "$remoteCount songs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Segmented Control Pill [ Local | Remote ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                .padding(3.dp),
        ) {
            SegmentedPill(
                title = "Local",
                selected = selectedTab == HistorySourceTab.LOCAL,
                onClick = { onTabSelected(HistorySourceTab.LOCAL) },
                modifier = Modifier.weight(1f),
            )
            SegmentedPill(
                title = "Remote",
                selected = selectedTab == HistorySourceTab.REMOTE,
                onClick = { onTabSelected(HistorySourceTab.REMOTE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentedPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pill_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill_text",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = textColor,
        )
    }
}

/**
 * Clean, lightweight, zero-lag song row matching BitChord's SongRow standard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRowItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 52dp Artwork
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp)),
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(song.artist)
                    if (!song.durationText.isNullOrBlank()) {
                        append(" · ")
                        append(song.durationText)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = onLongPress,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyHistoryView(
    isRemote: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (isRemote) "No remote history" else "No local history",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isRemote) "Pull down to refresh or sync songs from your YouTube Music account" else "Music played on this device will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
