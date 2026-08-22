package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
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
 * and launches the Android Package Installer via FileProvider.
 */
object AppUpdater {

    private const val TAG = "AppUpdater"

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

    fun resetState() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
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
                val apkFile = File(updateDir, "Musique-v${updateInfo.version}-client.apk")

                if (apkFile.exists() && apkFile.length() == updateInfo.fileSize && updateInfo.fileSize > 0) {
                    // APK already downloaded completely
                    _downloadState.value = DownloadState.ReadyToInstall(apkFile)
                    withContext(Dispatchers.Main) {
                        installApk(context, apkFile)
                    }
                    return@launch
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "MusiqueNativeAndroid")
                    .build()

                _downloadState.value = DownloadState.Downloading(0f, 0, updateInfo.fileSize, 0f)

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

                                lastTime = now
                                lastBytes = downloadedBytes
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState.ReadyToInstall(apkFile)
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Terjadi kesalahan saat mengunduh APK")
            }
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
