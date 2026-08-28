package com.velthy.client.download

import com.velthy.client.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

sealed interface DownloadProgress {
    data object Queued : DownloadProgress
    data class Running(val fraction: Float) : DownloadProgress
    data object Done : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress

    val settled: Boolean get() = this is Done || this is Failed
}

object DownloadSession {

    data class Item(
        val videoId: String,
        val song: Song,
        val progress: DownloadProgress,
        val from: String? = null,
        val sequence: Long,
    )

    data class State(
        val items: List<Item> = emptyList(),
        val seenAt: Long = 0L,
        val settledAt: Long = 0L,
    ) {
        val waiting: Int get() = items.count { !it.progress.settled }
        val finished: Int get() = items.count { it.progress is DownloadProgress.Done }
        val failed: Int get() = items.count { it.progress is DownloadProgress.Failed }
        val busy: Boolean get() = waiting > 0

        val fraction: Float
            get() {
                if (items.isEmpty()) return 0f
                val total = items.sumOf { item ->
                    when (val progress = item.progress) {
                        is DownloadProgress.Running -> progress.fraction.toDouble()
                        DownloadProgress.Queued -> 0.0
                        else -> 1.0
                    }
                }
                return (total / items.size).toFloat().coerceIn(0f, 1f)
            }

        val visible: Boolean get() = items.isNotEmpty() && (busy || seenAt < settledAt)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val clock = AtomicLong(0L)

    private fun tick(): Long = clock.incrementAndGet()

    fun queued(song: Song, from: String? = null) {
        update { state ->
            val existing = state.items.indexOfFirst { it.videoId == song.videoId }
            val item = Item(
                videoId = song.videoId,
                song = song,
                progress = DownloadProgress.Queued,
                from = from ?: state.items.getOrNull(existing)?.from,
                sequence = if (existing >= 0) state.items[existing].sequence else tick(),
            )
            val items = if (existing >= 0) {
                state.items.toMutableList().also { it[existing] = item }
            } else {
                state.items + item
            }
            state.copy(items = items)
        }
    }

    fun running(videoId: String, fraction: Float) =
        set(videoId, DownloadProgress.Running(fraction))

    fun done(videoId: String) = set(videoId, DownloadProgress.Done)

    fun failed(videoId: String, reason: String) = set(videoId, DownloadProgress.Failed(reason))

    fun retitle(videoId: String, song: Song) {
        update { state ->
            val index = state.items.indexOfFirst { it.videoId == videoId }
            if (index < 0) return@update state
            state.copy(
                items = state.items.toMutableList().also {
                    it[index] = it[index].copy(song = song)
                },
            )
        }
    }

    fun forget(videoId: String) {
        update { state ->
            val items = state.items.filterNot { it.videoId == videoId }
            if (items.size == state.items.size) return@update state
            state.copy(
                items = items,
                settledAt = if (items.none { !it.progress.settled }) tick() else state.settledAt,
            )
        }
    }

    fun markSeen() {
        _state.update { it.copy(seenAt = tick()) }
    }

    fun clear() {
        _state.value = State()
    }

    private fun set(videoId: String, progress: DownloadProgress) {
        update { state ->
            val index = state.items.indexOfFirst { it.videoId == videoId }
            if (index < 0) return@update state
            val items = state.items.toMutableList().also {
                it[index] = it[index].copy(progress = progress)
            }
            val quiet = items.none { !it.progress.settled }
            state.copy(
                items = items,
                settledAt = if (progress.settled && quiet) tick() else state.settledAt,
            )
        }
    }

    private inline fun update(block: (State) -> State) {
        _state.update(block)
    }
}
