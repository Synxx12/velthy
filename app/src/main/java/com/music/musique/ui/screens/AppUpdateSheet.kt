package com.music.musique.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.musique.BuildConfig
import com.music.musique.data.AppUpdateChecker
import com.music.musique.data.AppUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSheet(
    updateInfo: AppUpdateChecker.UpdateInfo?,
    isChecking: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by AppUpdater.downloadState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16161A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .animateContentSize(),
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Software Update",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (updateInfo != null) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "v${updateInfo.version}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                // 1. Loading / Checking State
                isChecking -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Checking for latest release...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }

                // 2. Update Available
                updateInfo != null -> {
                    UpdateAvailableContent(
                        updateInfo = updateInfo,
                        downloadState = downloadState,
                        onDownloadClick = {
                            AppUpdater.startDownload(context, updateInfo)
                        },
                        onInstallClick = { file ->
                            AppUpdater.installApk(context, file)
                        },
                        onCancelClick = {
                            AppUpdater.resetState()
                        },
                        onViewReleaseClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        },
                    )
                }

                // 3. Already Up to Date
                else -> {
                    UpToDateContent(
                        currentVersion = BuildConfig.VERSION_NAME,
                        onDismiss = onDismiss,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    updateInfo: AppUpdateChecker.UpdateInfo,
    downloadState: AppUpdater.DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onCancelClick: () -> Unit,
    onViewReleaseClick: () -> Unit,
) {
    // Release Overview Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Musique v${updateInfo.version}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = "Current version: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            if (updateInfo.fileSize > 0) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = AppUpdater.formatSize(updateInfo.fileSize),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Release Notes Section
        if (updateInfo.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "WHAT'S NEW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = Color.White.copy(alpha = 0.45f),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = updateInfo.releaseNotes,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Download & Install Action Area
    when (downloadState) {
        is AppUpdater.DownloadState.Idle -> {
            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Download & Install Update",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        is AppUpdater.DownloadState.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Downloading APK (${(downloadState.progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        text = "${AppUpdater.formatSize(downloadState.bytesDownloaded)} / ${AppUpdater.formatSize(downloadState.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${"%.1f".format(downloadState.speedMbPerSec)} MB/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF5252),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCancelClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        is AppUpdater.DownloadState.ReadyToInstall -> {
            Button(
                onClick = { onInstallClick(downloadState.file) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Install Now",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        is AppUpdater.DownloadState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2E1A1A))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDownloadClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry Download")
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // Secondary Link
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onViewReleaseClick) {
            Text(
                text = "View Release on GitHub",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun UpToDateContent(
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color(0xFF2E7D32).copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "You're on the latest version",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Musique Native v$currentVersion is up to date.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done")
        }
    }
}
