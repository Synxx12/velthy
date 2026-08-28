package com.velthy.client.ui.replay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import com.velthy.client.data.stats.ArtistFacts
import com.velthy.client.data.stats.ReplayPeriod
import com.velthy.client.data.stats.ReplaySummary
import com.velthy.client.ui.components.PAGE_GUTTER
import com.velthy.client.ui.icons.VelthyIcons
import com.velthy.client.ui.player.MeshGradientBackground
import com.velthy.client.ui.player.rememberArtworkColors
import com.velthy.client.ui.theme.AccentRed

@Composable
fun ReplayScreen(
    state: ReplayState,
    holder: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    onOpenStory: (ReplayStoryPage) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    onShare: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val summary = state.summary
    val leadArtwork = summary?.songs?.firstOrNull()?.song?.thumbnailUrl
    val palette = rememberArtworkColors(leadArtwork)

    Box(modifier.fillMaxSize()) {
        MeshGradientBackground(palette = palette, trackKey = leadArtwork, animated = false)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item("heading") { Heading(state, onPeriodChange) }

            when {
                state.loading && summary == null -> item("loading") {
                    Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                    }
                }
                summary == null || summary.isEmpty -> item("empty") { EmptyReplay(state.period) }
                else -> {
                    item("cards") {
                        ReplayCardRow(
                            cards = summary.cards(),
                            holder = holder,
                            memberSince = state.memberSince,
                            onOpenStory = onOpenStory,
                        )
                    }
                    item("open") {
                        ReplayActionRow(VelthyIcons.Play, "Play your Replay") {
                            onOpenStory(ReplayStoryPage.INTRO)
                        }
                    }

                    chart(
                        key = "songs",
                        title = "Top songs",
                        rows = summary.songRows(CHART_LENGTH),
                        onClick = { index ->
                            summary.songs.getOrNull(index)?.let { onPlaySong(it.song) }
                        },
                    )
                    chart(
                        key = "artists",
                        title = "Top artists",
                        rows = summary.artistRows(CHART_LENGTH),
                        circular = true,
                        onClick = { index ->
                            val artist = summary.artists.getOrNull(index) ?: return@chart
                            onOpenArtist(artist.browseId, artist.title)
                        },
                    )
                    chart(
                        key = "albums",
                        title = "Top albums",
                        rows = summary.albumRows(CHART_LENGTH),
                        onClick = { index ->
                            val album = summary.albums.getOrNull(index) ?: return@chart
                            onOpenAlbum(album.browseId, album.title, album.subtitle, album.artworkUrl)
                        },
                    )
                    if (summary.genres.isNotEmpty()) {
                        chart(
                            key = "genres",
                            title = "Top genres",
                            rows = summary.genreRows(CHART_LENGTH),
                            onClick = {},
                        )
                    } else if (ArtistFacts.genresAvailable) {
                        item("genres-pending") {
                            Note(
                                text = "Genres are still being worked out. They fill in " +
                                    "as you listen, and the chart appears once there is " +
                                    "enough to rank.",
                                modifier = Modifier.padding(horizontal = PAGE_GUTTER + 10.dp),
                            )
                        }
                    }

                    item("habits") { Habits(summary) }
                    item("share") {
                        ReplayActionRow(Icons.Rounded.IosShare, "Share my Replay", onShare)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(state: ReplayState, onPeriodChange: (ReplayPeriod) -> Unit) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 10.dp)) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Replay",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = state.summary?.label ?: state.period.chip,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
        PeriodPicker(state.period, onPeriodChange)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PeriodPicker(selected: ReplayPeriod, onSelect: (ReplayPeriod) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ReplayPeriod.entries.forEach { period ->
            val active = period == selected
            val background by animateColorAsState(
                if (active) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                tween(160),
                label = "periodChip",
            )
            Text(
                text = period.chip,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = if (active) Color.Black else Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReplayCardRow(
    cards: List<ReplayHeroCard>,
    holder: String,
    memberSince: String?,
    onOpenStory: (ReplayStoryPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.label }) { card ->
            ReplayCreditCard(
                label = card.label,
                value = card.value,
                detail = card.detail,
                artworkUrl = card.artworkUrl,
                holder = holder,
                memberSince = memberSince,
                onClick = { onOpenStory(card.page) },
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

@Composable
private fun ReplayActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = PAGE_GUTTER + 10.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = VelthyIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun LazyListScope.chart(
    key: String,
    title: String,
    rows: List<ReplayRow>,
    onClick: (Int) -> Unit,
    circular: Boolean = false,
) {
    if (rows.isEmpty()) return
    item("$key-title") { SectionTitle(title) }
    items(rows, key = { "$key-${it.key}" }) { row ->
        ReplayChartRow(row, circular) { onClick(row.rank - 1) }
    }
    item("$key-gap") { Spacer(Modifier.height(20.dp)) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.W800,
        color = Color.White,
        modifier = Modifier.padding(
            start = PAGE_GUTTER + 10.dp,
            end = PAGE_GUTTER + 10.dp,
            top = 8.dp,
            bottom = 10.dp,
        ),
    )
}

@Composable
private fun ReplayChartRow(row: ReplayRow, circular: Boolean, onClick: () -> Unit) {
    val shape = if (circular) CircleShape else RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER + 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank, AccentRed)
        if (row.artworkUrl != null) {
            AsyncImage(
                model = row.artworkUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(shape),
            )
        } else {
            InitialTile(row.title, 48.dp, shape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.subtitle, formatListening(row.ms))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.plays > 0) {
            Text(
                text = row.plays.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun Habits(summary: ReplaySummary) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 10.dp)) {
        SectionTitleInline("The shape of it")
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Songs", summary.distinctSongs.toString(), Modifier.weight(1f))
            StatTile("Artists", summary.distinctArtists.toString(), Modifier.weight(1f))
            StatTile("Albums", summary.distinctAlbums.toString(), Modifier.weight(1f))
        }
        summary.peakHour?.let {
            Spacer(Modifier.height(10.dp))
            Note("You listen most around ${formatHour(it)}.")
        }
        summary.busiestDay?.let {
            Spacer(Modifier.height(6.dp))
            Note(
                "Your biggest day was ${formatDay(it)} — " +
                    "${formatListening(summary.busiestDayMs)} of it.",
            )
        }
    }
}

@Composable
private fun SectionTitleInline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.W800,
        color = Color.White,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier,
    )
}

@Composable
private fun EmptyReplay(period: ReplayPeriod) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = VelthyIcons.Clock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Not enough listening yet",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (period) {
                ReplayPeriod.THIS_MONTH -> "There isn't much from this month yet. " +
                    "Try All time, or come back after a few more sessions."
                else -> "Play some music and your Replay builds itself — every " +
                    "minute is counted here on the device, and nothing is sent anywhere."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

private const val CHART_LENGTH = 5
