package com.velthy.client.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.velthy.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * ⚡ In-App APK Updater for Musique Native
 *
 * Downloads APK releases directly from GitHub Releases with live progress tracking,
 * status bar notification progress, and launches the Android Package Installer via FileProvider.
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val NOTIFICATION_ID = 9902
    private const val CHANNEL_ID = "app_updates"

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(
            val progress: Float, // 0f .. 1f
            val bytesDownloaded: Long,
            val totalBytes: Long,
            val speedMbPerSec: Float,
        ) : DownloadState
        data class ReadyToInstall(val file: File) : DownloadState
        data class Error(val message: String) : DownloadState
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    fun resetState(context: Context? = null) {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
        context?.let { cancelNotification(it) }
    }

    /**
     * Formats bytes to human-readable size (e.g. "25.9 MB", "850 KB").
     */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            "%.1f MB".format(mb)
        } else {
            "%.0f KB".format(kb)
        }
    }

    fun startDownload(context: Context, updateInfo: AppUpdateChecker.UpdateInfo) {
        if (_downloadState.value is DownloadState.Downloading) return
        downloadJob?.cancel()

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadUrl = updateInfo.apkDownloadUrl.ifBlank {
                    throw IllegalStateException("URL unduhan APK tidak ditemukan di GitHub Release.")
                }

                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updateDir, "Velthy-v${updateInfo.version}.apk")

                if (apkFile.exists() && apkFile.length() == updateInfo.fileSize && updateInfo.fileSize > 0) {
                    // APK already downloaded completely
                    _downloadState.value = DownloadState.ReadyToInstall(apkFile)
                    showCompleteNotification(context, updateInfo.version, apkFile)
                    withContext(Dispatchers.Main) {
                        installApk(context, apkFile)
                    }
                    return@launch
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "VelthyAndroid")
                    .build()

                _downloadState.value = DownloadState.Downloading(0f, 0, updateInfo.fileSize, 0f)
                updateDownloadNotification(context, updateInfo.version, 0f, 0, updateInfo.fileSize, 0f)

                val response = Http.client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengunduh APK (HTTP ${response.code})")
                }

                val body = response.body ?: throw IllegalStateException("Response body kosong")
                val totalBytes = if (updateInfo.fileSize > 0) updateInfo.fileSize else body.contentLength()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L
                var currentSpeed = 0f

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDelta = now - lastTime
                            if (timeDelta >= 400L) {
                                val bytesDelta = downloadedBytes - lastBytes
                                currentSpeed = if (timeDelta > 0) {
                                    (bytesDelta.toFloat() / (1024f * 1024f)) / (timeDelta.toFloat() / 1000f)
                                } else 0f

                                val progress = if (totalBytes > 0) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                _downloadState.value = DownloadState.Downloading(
                                    progress = progress,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedMbPerSec = currentSpeed,
                                )

                                updateDownloadNotification(
                                    context = context,
                                    version = updateInfo.version,
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedMbPerSec = currentSpeed,
                                )

                                lastTime = now
                                lastBytes = downloadedBytes
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState.ReadyToInstall(apkFile)
                showCompleteNotification(context, updateInfo.version, apkFile)
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Terjadi kesalahan saat mengunduh APK")
                cancelNotification(context)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifikasi progres pengunduhan pembaruan aplikasi"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateDownloadNotification(
        context: Context,
        version: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedMbPerSec: Float,
    ) {
        runCatching {
            createNotificationChannel(context)
            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            val percent = (progress * 100).toInt()
            val text = "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)} · ${"%.1f".format(speedMbPerSec)} MB/s"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle("Mengunduh Musique v$version ($percent%)")
                .setContentText(text)
                .setProgress(100, percent, totalBytes <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun showCompleteNotification(context: Context, version: String, apkFile: File) {
        runCatching {
            createNotificationChannel(context)
            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle("Musique v$version Siap Diinstal")
                .setContentText("Unduhan selesai. Ketuk untuk menginstal pembaruan.")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelNotification(context: Context) {
        runCatching {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Prompts the Android Package Installer with FileProvider URI.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _downloadState.value = DownloadState.Error("File APK tidak ditemukan di penyimpanan")
                return
            }

            // Check Unknown Sources Permission for Android 8.0+ (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install error", e)
            _downloadState.value = DownloadState.Error("Gagal membuka installer: ${e.message}")
        }
    }
}
