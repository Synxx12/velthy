package com.velthy.client.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.velthy.client.R
import com.velthy.client.data.model.Song
import com.velthy.client.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while the download queue drains, and says so.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var drain: Job? = null
    private var notifier: Job? = null

    /** What the notification is currently about. */
    @Volatile
    private var current: Song? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()

        if (intent?.action == ACTION_CANCEL_ALL) {
            Downloads.active.value.keys.toList().forEach(Downloads::cancel)
            shutdown(stopWork = true)
            return START_NOT_STICKY
        }

        if (drain == null) {
            drain = scope.launch {
                drainQueue()
                shutdown(stopWork = false)
            }
            notifier = scope.launch { reflectProgress() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() = coroutineScope {
        val workers = AppSettings.parallelDownloads.value.coerceIn(1, 8)
        repeat(workers) { launch { work() } }
    }

    private suspend fun work() {
        var idleFor = 0L
        while (true) {
            val song = Downloads.takeNext()
            if (song == null) {
                if (idleFor >= IDLE_GRACE_MS && !Downloads.busy()) return
                delay(IDLE_POLL_MS)
                idleFor += IDLE_POLL_MS
                continue
            }
            idleFor = 0L
            current = song
            postNotification()

            val job = scope.launch { Downloads.run(this@DownloadService, song) }
            Downloads.onRunning(song.videoId, job)
            job.join()
            Downloads.onIdle(song.videoId)
        }
    }

    private suspend fun reflectProgress() {
        Downloads.active.collect {
            postNotification()
            delay(PROGRESS_REFRESH_MS)
        }
    }

    private fun shutdown(stopWork: Boolean) {
        if (stopWork) drain?.cancel()
        notifier?.cancel()
        drain = null
        notifier = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        Downloads.onStopped()
        super.onDestroy()
    }

    // ---- Notification -------------------------------------------------------

    private fun promote() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val active = Downloads.active.value
        val runningStates = active.values.filterIsInstance<DownloadState.Running>()
        val waiting = active.count { it.value is DownloadState.Queued }

        val percent = runningStates
            .takeIf { it.isNotEmpty() }
            ?.let { states -> states.sumOf { it.fraction.toDouble() } / states.size }
            ?.times(100)?.toInt()
            ?: 0

        val song = current
        val title = when {
            runningStates.size > 1 -> "Downloading ${runningStates.size} songs"
            else -> song?.title ?: "Downloading"
        }
        val text = when {
            runningStates.size > 1 && waiting > 0 -> "$waiting more queued"
            runningStates.size > 1 -> song?.title.orEmpty()
            song == null -> "Starting"
            waiting > 0 -> "${song.artist} · $waiting more queued"
            else -> song.artist
        }

        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, runningStates.isEmpty())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Songs being saved to your Music folder"
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 0x8175
        const val ACTION_CANCEL_ALL = "com.velthy.client.download.CANCEL_ALL"
        const val PROGRESS_REFRESH_MS = 250L
        const val IDLE_GRACE_MS = 2_000L
        const val IDLE_POLL_MS = 100L
    }
}
