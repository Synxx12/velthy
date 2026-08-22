package com.music.bitchord.data.history

import android.content.Context
import android.util.Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages playback history across both local plays and YouTube Music sessions.
 * Provides persistent local caching and time-based grouping matching Apple Music
 * & YouTube Music standards.
 */
object PlaybackHistoryManager {

    private const val TAG = "BitChord"
    private const val HISTORY_FILE_NAME = "listening_history.json"
    private const val MAX_HISTORY_ITEMS = 500

    @Serializable
    data class PersistedEntry(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String? = null,
        val durationText: String? = null,
        val artistId: String? = null,
        val albumId: String? = null,
        val albumName: String? = null,
        val isVideo: Boolean = false,
        val localUri: String? = null,
        val localPath: String? = null,
        val playedAt: Long = System.currentTimeMillis(),
    )

    data class HistoryItem(
        val song: Song,
        val playedAt: Long = System.currentTimeMillis(),
    )

    enum class TimeGroup {
        TODAY,
        YESTERDAY,
        THIS_WEEK,
        EARLIER_THIS_MONTH,
        OLDER,
    }

    data class GroupedHistory(
        val group: TimeGroup,
        val label: String,
        val items: List<HistoryItem>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Mutex()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private var storageFile: File? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        storageFile = File(context.filesDir, HISTORY_FILE_NAME)
        scope.launch {
            loadFromDisk()
            syncWithYouTube()
        }
    }

    /**
     * Synchronizes listening history with the user's YouTube Music account (FEmusic_history)
     * if signed in. Seamlessly merges cloud history with local playback storage.
     */
    suspend fun syncWithYouTube(): Boolean {
        if (com.music.bitchord.data.innertube.Innertube.cookie == null) return false
        return runCatching {
            val response = com.music.bitchord.data.innertube.Innertube.browse("FEmusic_history")
            val ytSongs = com.music.bitchord.data.innertube.InnertubeParser.collectSongsDeep(response)
            if (ytSongs.isNotEmpty()) {
                lock.withLock {
                    val local = _history.value.toMutableList()
                    val existingVideoIds = local.mapTo(HashSet()) { it.song.videoId }

                    var offset = 60_000L
                    val newCloudItems = ytSongs.filterNot { it.videoId in existingVideoIds }.map { song ->
                        offset += 180_000L // spaced back in time
                        HistoryItem(
                            song = song,
                            playedAt = System.currentTimeMillis() - offset,
                        )
                    }

                    val merged = (local + newCloudItems).sortedByDescending { it.playedAt }.take(MAX_HISTORY_ITEMS)
                    _history.value = merged
                    saveToDisk(merged)
                    Log.d(TAG, "Synced ${newCloudItems.size} new tracks from YouTube Music history.")
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Records a track play. If the identical track was recorded within 15 seconds,
     * updates its timestamp rather than creating an immediate duplicate.
     */
    fun recordPlay(song: Song?) {
        if (song == null || song.videoId.isBlank()) return
        scope.launch {
            lock.withLock {
                val now = System.currentTimeMillis()
                val current = _history.value.toMutableList()

                // If identical track was added less than 15s ago, just refresh timestamp
                val first = current.firstOrNull()
                if (first != null && first.song.videoId == song.videoId && (now - first.playedAt) < 15_000) {
                    current[0] = first.copy(playedAt = now)
                } else {
                    current.add(0, HistoryItem(song = song, playedAt = now))
                }

                // Trim to max capacity
                val trimmed = if (current.size > MAX_HISTORY_ITEMS) current.take(MAX_HISTORY_ITEMS) else current
                _history.value = trimmed
                saveToDisk(trimmed)
                Log.d(TAG, "Recorded play in history: ${song.title} (${song.videoId})")
            }
        }
    }

    /**
     * Removes an entry from history by videoId and timestamp.
     */
    fun removeEntry(item: HistoryItem) {
        scope.launch {
            lock.withLock {
                val updated = _history.value.filterNot {
                    it.song.videoId == item.song.videoId && it.playedAt == item.playedAt
                }
                _history.value = updated
                saveToDisk(updated)
                Log.d(TAG, "Removed history item: ${item.song.title}")
            }
        }
    }

    /**
     * Clears all playback history.
     */
    fun clearHistory() {
        scope.launch {
            lock.withLock {
                _history.value = emptyList()
                saveToDisk(emptyList())
                Log.d(TAG, "Cleared all playback history.")
            }
        }
    }

    /**
     * Groups history items into chronological sections like Apple Music & YouTube Music.
     */
    fun groupHistory(items: List<HistoryItem>): List<GroupedHistory> {
        if (items.isEmpty()) return emptyList()

        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
        val thisWeekStart = todayStart - (6 * 24 * 60 * 60 * 1000L)
        val thisMonthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayItems = mutableListOf<HistoryItem>()
        val yesterdayItems = mutableListOf<HistoryItem>()
        val thisWeekItems = mutableListOf<HistoryItem>()
        val thisMonthItems = mutableListOf<HistoryItem>()
        val olderItems = mutableListOf<HistoryItem>()

        for (item in items) {
            when {
                item.playedAt >= todayStart -> todayItems.add(item)
                item.playedAt >= yesterdayStart -> yesterdayItems.add(item)
                item.playedAt >= thisWeekStart -> thisWeekItems.add(item)
                item.playedAt >= thisMonthStart -> thisMonthItems.add(item)
                else -> olderItems.add(item)
            }
        }

        val result = mutableListOf<GroupedHistory>()
        if (todayItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.TODAY, "Hari Ini", todayItems))
        }
        if (yesterdayItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.YESTERDAY, "Kemarin", yesterdayItems))
        }
        if (thisWeekItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.THIS_WEEK, "Minggu Ini", thisWeekItems))
        }
        if (thisMonthItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.EARLIER_THIS_MONTH, "Bulan Ini", thisMonthItems))
        }
        if (olderItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.OLDER, "Sebelumnya", olderItems))
        }

        return result
    }

    fun formatPlayedTime(playedAt: Long): String {
        val now = System.currentTimeMillis()
        val diffSeconds = (now - playedAt) / 1000L
        if (diffSeconds < 60) return "Baru saja"
        if (diffSeconds < 3600) return "${diffSeconds / 60}m lalu"

        val itemCal = Calendar.getInstance().apply { timeInMillis = playedAt }
        val nowCal = Calendar.getInstance()

        return if (itemCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
            itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        ) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(playedAt))
        } else if (itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(playedAt))
        } else {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(playedAt))
        }
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val content = file.readText()
            if (content.isNotBlank()) {
                val persisted = json.decodeFromString<List<PersistedEntry>>(content)
                val mapped = persisted.map { p ->
                    HistoryItem(
                        song = Song(
                            videoId = p.videoId,
                            title = p.title,
                            artist = p.artist,
                            thumbnailUrl = p.thumbnailUrl,
                            durationText = p.durationText,
                            artistId = p.artistId,
                            albumId = p.albumId,
                            albumName = p.albumName,
                            isVideo = p.isVideo,
                            localUri = p.localUri,
                            localPath = p.localPath,
                        ),
                        playedAt = p.playedAt,
                    )
                }
                _history.value = mapped
                Log.d(TAG, "Loaded ${mapped.size} items from playback history storage.")
            }
        }.onFailure {
            Log.e(TAG, "Failed to load playback history: ${it.message}", it)
        }
    }

    private fun saveToDisk(items: List<HistoryItem>) {
        val file = storageFile ?: return
        runCatching {
            val persisted = items.map { item ->
                val s = item.song
                PersistedEntry(
                    videoId = s.videoId,
                    title = s.title,
                    artist = s.artist,
                    thumbnailUrl = s.thumbnailUrl,
                    durationText = s.durationText,
                    artistId = s.artistId,
                    albumId = s.albumId,
                    albumName = s.albumName,
                    isVideo = s.isVideo,
                    localUri = s.localUri,
                    localPath = s.localPath,
                    playedAt = item.playedAt,
                )
            }
            val content = json.encodeToString(persisted)
            file.writeText(content)
        }.onFailure {
            Log.e(TAG, "Failed to save playback history: ${it.message}", it)
        }
    }
}
