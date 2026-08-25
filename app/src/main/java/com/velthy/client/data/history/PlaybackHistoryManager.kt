package com.velthy.client.data.history

import android.content.Context
import android.util.Log
import com.velthy.client.data.model.Song
import com.velthy.client.data.settings.AppSettings
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
 * Manages playback history across both local device plays and synced YouTube Music sessions.
 * Provides separate tracking for [localHistory] (played on this device) and [remoteHistory]
 * (synced from YouTube Music cloud), matching InnerTune / ArchiveTune / YouTube Music standards.
 *
 * Real-time auto-synchronization: When a song is played, it is immediately registered to
 * local history and optimistically reflected in remote history, with automatic background
 * cloud reconciliation.
 */
object PlaybackHistoryManager {

    private const val TAG = "Musique"
    private const val LOCAL_HISTORY_FILE = "local_listening_history.json"
    private const val REMOTE_HISTORY_FILE = "remote_listening_history.json"
    private const val LEGACY_HISTORY_FILE = "listening_history.json"
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

    private val _localHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val localHistory: StateFlow<List<HistoryItem>> = _localHistory.asStateFlow()

    private val _remoteHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val remoteHistory: StateFlow<List<HistoryItem>> = _remoteHistory.asStateFlow()

    /** Backward compatibility unified flow (defaults to local history). */
    val history: StateFlow<List<HistoryItem>> = _localHistory.asStateFlow()

    private var localFile: File? = null
    private var remoteFile: File? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        localFile = File(context.filesDir, LOCAL_HISTORY_FILE)
        remoteFile = File(context.filesDir, REMOTE_HISTORY_FILE)

        scope.launch {
            loadFromDisk(context)
            syncWithYouTube()
        }
    }

    /**
     * Deduplicates a list of history items by [Song.videoId], retaining only the
     * latest occurrence (sorted newest to oldest).
     */
    fun deduplicateByLatest(items: List<HistoryItem>): List<HistoryItem> {
        val seen = HashSet<String>()
        val result = mutableListOf<HistoryItem>()
        val sorted = items.sortedByDescending { it.playedAt }
        for (item in sorted) {
            if (seen.add(item.song.videoId)) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * Synchronizes listening history with the user's YouTube Music account (FEmusic_history)
     * if signed in. Stores cloud history into [remoteHistory].
     * When [force] is false, respects [AppSettings.accountAutoSync].
     */
    suspend fun syncWithYouTube(force: Boolean = false): Boolean {
        if (!force && !AppSettings.accountAutoSync.value) return false
        if (com.velthy.client.data.innertube.Innertube.cookie == null) return false
        return runCatching {
            val response = com.velthy.client.data.innertube.Innertube.browse("FEmusic_history")
            val sections = com.velthy.client.data.innertube.InnertubeParser.parseHistorySections(response)
            if (sections.isNotEmpty()) {
                val now = System.currentTimeMillis()
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
                val olderStart = thisMonthStart - (30 * 24 * 60 * 60 * 1000L)

                val cloudItems = mutableListOf<HistoryItem>()
                for (sec in sections) {
                    val baseTime = when {
                        sec.title.contains("Today", ignoreCase = true) || sec.title.contains("Hari ini", ignoreCase = true) ->
                            now - 120_000L
                        sec.title.contains("Yesterday", ignoreCase = true) || sec.title.contains("Kemarin", ignoreCase = true) ->
                            yesterdayStart + (12 * 60 * 60 * 1000L)
                        sec.title.contains("week", ignoreCase = true) || sec.title.contains("minggu", ignoreCase = true) ->
                            thisWeekStart + (24 * 60 * 60 * 1000L)
                        sec.title.contains("month", ignoreCase = true) || sec.title.contains("bulan", ignoreCase = true) ->
                            thisMonthStart + (24 * 60 * 60 * 1000L)
                        else -> olderStart
                    }
                    var trackOffset = 0L
                    sec.songs.forEach { song ->
                        trackOffset += 60_000L
                        cloudItems.add(HistoryItem(song = song, playedAt = baseTime - trackOffset))
                    }
                }

                val deduplicated = deduplicateByLatest(cloudItems)
                lock.withLock {
                    val trimmed = deduplicated.take(MAX_HISTORY_ITEMS)
                    _remoteHistory.value = trimmed
                    saveFile(remoteFile, trimmed)
                    Log.d(TAG, "Auto-synced ${trimmed.size} tracks from YouTube Music cloud history.")
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Records a track play to local history (Played on this device).
     */
    fun recordPlay(song: Song?) {
        if (song == null || song.videoId.isBlank()) return
        scope.launch {
            lock.withLock {
                val now = System.currentTimeMillis()
                val newItem = HistoryItem(song = song, playedAt = now)

                // Update local history
                val filteredLocal = _localHistory.value.filterNot { it.song.videoId == song.videoId }.toMutableList()
                filteredLocal.add(0, newItem)
                val trimmedLocal = if (filteredLocal.size > MAX_HISTORY_ITEMS) filteredLocal.take(MAX_HISTORY_ITEMS) else filteredLocal
                _localHistory.value = trimmedLocal
                saveFile(localFile, trimmedLocal)

                // Optimistically update remote history if signed in & account auto-sync is enabled
                if (AppSettings.accountAutoSync.value && com.velthy.client.data.innertube.Innertube.cookie != null) {
                    val filteredRemote = _remoteHistory.value.filterNot { it.song.videoId == song.videoId }.toMutableList()
                    filteredRemote.add(0, newItem)
                    val trimmedRemote = if (filteredRemote.size > MAX_HISTORY_ITEMS) filteredRemote.take(MAX_HISTORY_ITEMS) else filteredRemote
                    _remoteHistory.value = trimmedRemote
                    saveFile(remoteFile, trimmedRemote)
                }

                Log.d(TAG, "Recorded play: ${song.title} (${song.videoId})")
            }
        }
    }

    /**
     * Removes an entry from local or remote history by videoId.
     */
    fun removeEntry(item: HistoryItem, isRemote: Boolean = false) {
        scope.launch {
            lock.withLock {
                if (isRemote) {
                    val updated = _remoteHistory.value.filterNot {
                        it.song.videoId == item.song.videoId
                    }
                    _remoteHistory.value = updated
                    saveFile(remoteFile, updated)
                } else {
                    val updated = _localHistory.value.filterNot {
                        it.song.videoId == item.song.videoId
                    }
                    _localHistory.value = updated
                    saveFile(localFile, updated)
                }
                Log.d(TAG, "Removed history item (${if (isRemote) "Remote" else "Local"}): ${item.song.title}")
            }
        }
    }

    /**
     * Clears all playback history.
     */
    fun clearHistory() {
        clearLocalHistory()
    }

    /**
     * Clears local playback history.
     */
    fun clearLocalHistory() {
        scope.launch {
            lock.withLock {
                _localHistory.value = emptyList()
                saveFile(localFile, emptyList())
                Log.d(TAG, "Cleared local playback history.")
            }
        }
    }

    /**
     * Clears remote cloud playback history cache.
     */
    fun clearRemoteHistory() {
        scope.launch {
            lock.withLock {
                _remoteHistory.value = emptyList()
                saveFile(remoteFile, emptyList())
                Log.d(TAG, "Cleared remote playback history cache.")
            }
        }
    }

    /**
     * Groups history items into chronological sections like YouTube Music & Musique.
     * Guarantees each song appears at most once in its most recent time group.
     */
    fun groupHistory(items: List<HistoryItem>): List<GroupedHistory> {
        val uniqueItems = deduplicateByLatest(items)
        if (uniqueItems.isEmpty()) return emptyList()

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

        for (item in uniqueItems) {
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
            result.add(GroupedHistory(TimeGroup.TODAY, "Today", todayItems))
        }
        if (yesterdayItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.YESTERDAY, "Yesterday", yesterdayItems))
        }
        if (thisWeekItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.THIS_WEEK, "This week", thisWeekItems))
        }
        if (thisMonthItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.EARLIER_THIS_MONTH, "Earlier this month", thisMonthItems))
        }
        if (olderItems.isNotEmpty()) {
            result.add(GroupedHistory(TimeGroup.OLDER, "Older", olderItems))
        }

        return result
    }

    fun formatPlayedTime(playedAt: Long): String {
        val now = System.currentTimeMillis()
        val diffSeconds = (now - playedAt) / 1000L
        if (diffSeconds < 60) return "Just now"
        if (diffSeconds < 3600) return "${diffSeconds / 60}m ago"

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

    private fun loadFromDisk(context: Context) {
        val lFile = localFile ?: File(context.filesDir, LOCAL_HISTORY_FILE)
        val rFile = remoteFile ?: File(context.filesDir, REMOTE_HISTORY_FILE)
        val legacy = File(context.filesDir, LEGACY_HISTORY_FILE)

        // Load local
        if (lFile.exists()) {
            val loaded = deduplicateByLatest(readFile(lFile))
            _localHistory.value = loaded
            saveFile(lFile, loaded)
        } else if (legacy.exists()) {
            // Migrate legacy
            val migrated = deduplicateByLatest(readFile(legacy))
            _localHistory.value = migrated
            saveFile(lFile, migrated)
        }

        // Load remote
        if (rFile.exists()) {
            val loaded = deduplicateByLatest(readFile(rFile))
            _remoteHistory.value = loaded
            saveFile(rFile, loaded)
        }
    }

    private fun readFile(file: File): List<HistoryItem> {
        return runCatching {
            val content = file.readText()
            if (content.isBlank()) return emptyList()
            val persisted = json.decodeFromString<List<PersistedEntry>>(content)
            persisted.map { p ->
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
        }.getOrDefault(emptyList())
    }

    private fun saveFile(file: File?, items: List<HistoryItem>) {
        if (file == null) return
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
            file.writeText(json.encodeToString(persisted))
        }.onFailure {
            Log.e(TAG, "Failed to save history file ${file.name}: ${it.message}", it)
        }
    }
}
