package com.velthy.client.ui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.velthy.client.data.stats.ArtistFacts
import com.velthy.client.data.stats.ListeningStats
import com.velthy.client.data.stats.ReplayPeriod
import com.velthy.client.data.stats.ReplaySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReplayState(
    val period: ReplayPeriod,
    val summary: ReplaySummary?,
    val loading: Boolean,
    val memberSince: String?,
) {
    val heroCard: ReplayHeroCard?
        get() = summary?.cards()?.firstOrNull()
}

@Composable
fun rememberReplayState(active: Boolean): Pair<ReplayState, (ReplayPeriod) -> Unit> {
    var period by rememberSaveable { mutableStateOf(ReplayPeriod.THIS_YEAR) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var memberSince by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        memberSince = withContext(Dispatchers.IO) {
            ListeningStats.months().firstOrNull()?.let {
                "%02d/%02d".format(Locale.ROOT, it.monthValue, it.year % 100)
            }
        }
    }
    LaunchedEffect(period, active) {
        if (!active) return@LaunchedEffect
        loading = summary == null
        summary = ListeningStats.summary(period)
        loading = false

        ArtistFacts.revision.drop(1).collectLatest {
            delay(SETTLE_MILLIS)
            summary = ListeningStats.summary(period)
        }
    }
    return ReplayState(period, summary, loading, memberSince) to
        { next: ReplayPeriod -> period = next }
}

private const val SETTLE_MILLIS = 1_200L

data class HeadlineRun(val text: String, val bold: Boolean)

private fun runs(vararg parts: Pair<String, Boolean>): List<HeadlineRun> =
    parts.map { HeadlineRun(it.first, it.second) }

fun ReplaySummary.storyHeadline(page: ReplayStoryPage): List<HeadlineRun> = when (page) {
    ReplayStoryPage.INTRO -> runs(
        "This is your " to false,
        "Replay" to true,
        " — the year in music you actually played." to false,
    )
    ReplayStoryPage.MINUTES -> runs(
        "You listened to " to false,
        "${formatMinutes(totalMs)} minutes" to true,
        " of music." to false,
    )
    ReplayStoryPage.SONGS -> runs(
        "You played " to false,
        countOf(totalPlays, "song") to true,
        ", one was your anthem." to false,
    )
    ReplayStoryPage.ARTISTS -> runs(
        "There was one " to false,
        "artist" to true,
        " you never got tired of." to false,
    )
    ReplayStoryPage.ALBUMS -> runs(
        "One " to false,
        "album" to true,
        " you kept coming back to." to false,
    )
    ReplayStoryPage.GENRES -> runs(
        "There was one " to false,
        "genre" to true,
        " you came back to again and again." to false,
    )
    ReplayStoryPage.HABITS -> runs(
        "You got through " to false,
        countOf(distinctSongs, "song") to true,
        " by " to false,
        countOf(distinctArtists, "artist") to true,
        "." to false,
    )
    ReplayStoryPage.SUMMARY -> runs("That was " to false, label to true, "." to false)
}

fun ReplaySummary.storyArtwork(page: ReplayStoryPage): String? {
    val pool = (
        songs.map { it.song.thumbnailUrl } +
            artists.map { it.artworkUrl } +
            albums.map { it.artworkUrl }
        ).filterNotNull().distinct()
    val pinned = when (page) {
        ReplayStoryPage.SONGS -> songs.firstOrNull()?.song?.thumbnailUrl
        ReplayStoryPage.ARTISTS -> artists.firstOrNull()?.artworkUrl
        ReplayStoryPage.ALBUMS -> albums.firstOrNull()?.artworkUrl
        else -> null
    }
    if (pinned != null) return pinned
    if (pool.isEmpty()) return null
    return pool[page.ordinal % pool.size]
}

fun storyHue(page: ReplayStoryPage): Float = when (page) {
    ReplayStoryPage.INTRO -> 0f
    ReplayStoryPage.MINUTES -> 40f
    ReplayStoryPage.SONGS -> 95f
    ReplayStoryPage.ARTISTS -> 145f
    ReplayStoryPage.ALBUMS -> 195f
    ReplayStoryPage.GENRES -> 240f
    ReplayStoryPage.HABITS -> 285f
    ReplayStoryPage.SUMMARY -> 325f
}

enum class ReplayStoryPage {
    INTRO, MINUTES, ARTISTS, SONGS, ALBUMS, GENRES, HABITS, SUMMARY;

    companion object {
        val ordered: List<ReplayStoryPage> = entries
    }
}

fun formatListening(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 1_440 -> "${minutes / 60} hr ${minutes % 60} min"
        else -> "${grouped(minutes)} min"
    }
}

fun formatMinutes(ms: Long): String = grouped(ms / 60_000)

fun grouped(value: Long): String = String.format(Locale.US, "%,d", value)

fun formatHour(hour: Int): String = when (hour) {
    0 -> "midnight"
    12 -> "midday"
    in 1..11 -> "$hour am"
    else -> "${hour - 12} pm"
}

fun formatDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
}.getOrDefault(iso)

fun countOf(count: Int, noun: String): String =
    "${grouped(count.toLong())} $noun" + if (count == 1) "" else "s"

data class ReplayRow(
    val key: String,
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val ms: Long,
    val plays: Int,
)

fun ReplaySummary.songRows(limit: Int): List<ReplayRow> =
    songs.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.song.videoId,
            rank = index + 1,
            title = entry.song.title,
            subtitle = entry.song.artist.takeIf { it.isNotBlank() },
            artworkUrl = entry.song.thumbnailUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.artistRows(limit: Int): List<ReplayRow> =
    artists.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.albumRows(limit: Int): List<ReplayRow> =
    albums.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title + "|" + entry.subtitle.orEmpty(),
            rank = index + 1,
            title = entry.title,
            subtitle = entry.subtitle,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.genreRows(limit: Int): List<ReplayRow> =
    genres.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = null,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

data class ReplayHeroCard(
    val label: String,
    val value: String,
    val detail: String?,
    val artworkUrl: String?,
    val page: ReplayStoryPage,
)

fun ReplaySummary.cards(): List<ReplayHeroCard> = buildList {
    add(
        ReplayHeroCard(
            label = "Minutes listened",
            value = formatMinutes(totalMs),
            detail = "${countOf(totalPlays, "play")} · $label",
            artworkUrl = songs.firstOrNull()?.song?.thumbnailUrl,
            page = ReplayStoryPage.MINUTES,
        ),
    )
    artists.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = "Top artist",
                value = it.title,
                detail = "${formatListening(it.ms)} · ${countOf(it.plays, "play")}",
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ARTISTS,
            ),
        )
    }
    songs.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = "Top song",
                value = it.song.title,
                detail = "${it.song.artist} · ${countOf(it.plays, "play")}",
                artworkUrl = it.song.thumbnailUrl,
                page = ReplayStoryPage.SONGS,
            ),
        )
    }
    albums.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = "Top album",
                value = it.title,
                detail = listOfNotNull(it.subtitle, formatListening(it.ms)).joinToString(" · "),
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ALBUMS,
            ),
        )
    }
}
