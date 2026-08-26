package com.velthy.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velthy.client.data.model.Song
import com.velthy.client.ui.components.MessageState
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.components.ROW_DIVIDER_INSET
import com.velthy.client.ui.components.SongRow
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

const val LOCAL_TAB_SONGS = 0
const val LOCAL_TAB_ARTISTS = 1
const val LOCAL_TAB_ALBUMS = 2

/**
 * Pinned Top Bar Segmented Control hosted right inside FrostedTopBar.
 */
@Composable
fun LocalTopBarSegmentedControl(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalSegmentedTab(
            icon = Icons.Rounded.MusicNote,
            label = "Songs",
            selected = selectedTab == LOCAL_TAB_SONGS,
            onClick = {
                if (selectedTab != LOCAL_TAB_SONGS) {
                    haptics.play(Haptic.Select)
                    onSelectTab(LOCAL_TAB_SONGS)
                }
            },
            modifier = Modifier.weight(1f),
        )
        LocalSegmentedTab(
            icon = Icons.Rounded.Person,
            label = "Artists",
            selected = selectedTab == LOCAL_TAB_ARTISTS,
            onClick = {
                if (selectedTab != LOCAL_TAB_ARTISTS) {
                    haptics.play(Haptic.Select)
                    onSelectTab(LOCAL_TAB_ARTISTS)
                }
            },
            modifier = Modifier.weight(1f),
        )
        LocalSegmentedTab(
            icon = Icons.Rounded.Album,
            label = "Albums",
            selected = selectedTab == LOCAL_TAB_ALBUMS,
            onClick = {
                if (selectedTab != LOCAL_TAB_ALBUMS) {
                    haptics.play(Haptic.Select)
                    onSelectTab(LOCAL_TAB_ALBUMS)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Local Music folder view with three tabs: Songs (default), Artists, Albums.
 *
 * Tapping an artist or album name slides in a filtered song list inline, so
 * the tab bar stays visible and Back returns to the grid rather than leaving
 * the screen.
 */
@Composable
fun LocalMusicScreen(
    songs: List<Song>,
    selectedTab: Int = LOCAL_TAB_SONGS,
    drillDownLabel: String? = null,
    onDrillDownChange: (label: String?, songs: List<Song>) -> Unit = { _, _ -> },
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    emptyMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    // When non-null, we are showing a drill-down list for that artist or album.
    var internalDrillDownSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    val inDrillDown = drillDownLabel != null

    BackHandler(enabled = inDrillDown) {
        onDrillDownChange(null, emptyList())
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = if (inDrillDown) "drill:$drillDownLabel" else "tab:$selectedTab",
            transitionSpec = {
                val isDrillEntering = targetState.startsWith("drill:")
                val isDrillExiting = initialState.startsWith("drill:") && !targetState.startsWith("drill:")
                val initialTabIndex = initialState.substringAfter("tab:").toIntOrNull() ?: 0
                val targetTabIndex = targetState.substringAfter("tab:").toIntOrNull() ?: 0
                val goingForward = isDrillEntering || (!isDrillExiting && targetTabIndex > initialTabIndex)

                if (goingForward) {
                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(180)))
                }
            },
            label = "local_music_content",
            modifier = Modifier.fillMaxSize(),
        ) { key ->
            when {
                // Nothing to tab through.
                songs.isEmpty() && emptyMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        MessageState(message = emptyMessage)
                    }
                }

                key.startsWith("drill:") -> {
                    // Drill-down song list for artist / album
                    DrillDownSongList(
                        label = drillDownLabel ?: "",
                        songs = internalDrillDownSongs,
                        onSongClick = onSongClick,
                        onSongLongPress = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                        onShuffle = onShuffle,
                        onBack = {
                            onDrillDownChange(null, emptyList())
                        },
                        contentPadding = contentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_SONGS" -> {
                    SongsTab(
                        songs = songs,
                        onSongClick = onSongClick,
                        onSongLongPress = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                        contentPadding = contentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_ARTISTS" -> {
                    val artists = remember(songs) {
                        songs.groupBy { it.artist }
                            .entries
                            .sortedBy { it.key.lowercase() }
                    }
                    ArtistsTab(
                        artists = artists,
                        onArtistClick = { artist, artistSongs ->
                            internalDrillDownSongs = artistSongs
                            onDrillDownChange(artist, artistSongs)
                        },
                        contentPadding = contentPadding,
                    )
                }

                else -> {
                    // LOCAL_TAB_ALBUMS
                    val albums = remember(songs) {
                        songs.filter { it.albumName != null }
                            .groupBy { it.albumName!! }
                            .entries
                            .sortedBy { it.key.lowercase() }
                    }
                    AlbumsTab(
                        albums = albums,
                        onAlbumClick = { album, albumSongs ->
                            internalDrillDownSongs = albumSongs
                            onDrillDownChange(album, albumSongs)
                        },
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "songs_header", contentType = "header") {
            SectionHeader(
                icon = Icons.Rounded.LibraryMusic,
                title = "${songs.size} songs",
            )
        }
        itemsIndexed(
            items = songs,
            key = { index, song -> if (song.videoId.isNotEmpty()) song.videoId else "song_${index}_${song.title}" },
            contentType = { _, _ -> "song_row" },
        ) { index, song ->
            SongRow(
                song = song,
                onClick = { onSongClick(songs, index) },
                onLongPress = { onSongLongPress(song) },
                onSwipeToQueue = { onSongSwipe(song) },
                downloadedTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Artists tab ───────────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<Map.Entry<String, List<Song>>>,
    onArtistClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "artists_header", contentType = "header") {
            SectionHeader(
                icon = Icons.Rounded.Person,
                title = "${artists.size} artists",
            )
        }
        items(
            items = artists,
            key = { "artist_${it.key}" },
            contentType = { "artist_row" },
        ) { (artist, artistSongs) ->
            ArtistRow(
                name = artist,
                songCount = artistSongs.size,
                onClick = { onArtistClick(artist, artistSongs) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ArtistRow(name: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Albums tab ────────────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<Map.Entry<String, List<Song>>>,
    onAlbumClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "albums_header", contentType = "header") {
            SectionHeader(
                icon = Icons.Rounded.Album,
                title = "${albums.size} albums",
            )
        }
        // Songs but no albums: nothing here carries an album tag.
        if (albums.isEmpty()) {
            item(key = "albums_empty", contentType = "empty") {
                MessageState(message = "None of these tracks say what album they're from.")
            }
        }
        items(
            items = albums,
            key = { "album_${it.key}" },
            contentType = { "album_row" },
        ) { (album, albumSongs) ->
            AlbumRow(
                name = album,
                artist = albumSongs.firstOrNull()?.artist ?: "",
                songCount = albumSongs.size,
                onClick = { onAlbumClick(album, albumSongs) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AlbumRow(name: String, artist: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album icon square
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (artist.isNotBlank()) append("$artist · ")
                    append("$songCount ${if (songCount == 1) "song" else "songs"}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Drill-down song list ───────────────────────────────────────────────────────

@Composable
private fun DrillDownSongList(
    label: String,
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        // Back + title header
        item(key = "drill_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = PAGE_GUTTER, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Play / Shuffle action row
        item(key = "drill_actions", contentType = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { if (songs.isNotEmpty()) onSongClick(songs, 0) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                // Shuffle button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { if (songs.isNotEmpty()) onShuffle(songs) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Song rows
        itemsIndexed(
            items = songs,
            key = { index, song -> if (song.videoId.isNotEmpty()) "drill_${song.videoId}" else "drill_${index}_${song.title}" },
            contentType = { _, _ -> "song_row" },
        ) { index, song ->
            SongRow(
                song = song,
                onClick = { onSongClick(songs, index) },
                onLongPress = { onSongLongPress(song) },
                onSwipeToQueue = { onSongSwipe(song) },
            )
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun LocalSegmentedTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tab_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tab_color",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = {
                    if (!selected) haptics.play(Haptic.Select)
                    onClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
