package com.velthy.client.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import com.velthy.client.data.model.Song
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.DownloadFolderStructure
import com.velthy.client.data.settings.DownloadFormat
import com.velthy.client.data.settings.DownloadLocation
import com.velthy.client.download.Downloads
import com.velthy.client.download.FlutterMigrationEngine
import com.velthy.client.download.StorageStats
import com.velthy.client.playback.AudioCache
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ── Storage & Cache Sheet (Settings Page Control Center) ──────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(StorageStats()) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showFolderStructurePicker by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var showCacheLimitDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    fun refreshStats() {
        scope.launch {
            stats = Downloads.calculateStorageStats(context)
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    val currentFormat by AppSettings.downloadFormat.collectAsStateWithLifecycle()
    val currentLocation by AppSettings.downloadLocation.collectAsStateWithLifecycle()
    val currentStructure by AppSettings.downloadFolderStructure.collectAsStateWithLifecycle()
    val cacheLimitBytes by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16161A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Storage & Cache",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            // 1. Storage Breakdown Card
            StorageBreakdownCard(stats = stats)

            Spacer(Modifier.height(20.dp))

            // 2. Automatic Cache Settings Section
            SectionTitle(title = "AUTOMATIC CACHE SETTINGS")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                val cacheLimitMb = (cacheLimitBytes / (1024 * 1024)).toInt()
                StorageSettingRow(
                    icon = Icons.Rounded.Storage,
                    iconBg = Color(0xFFE53935).copy(alpha = 0.18f),
                    iconTint = Color(0xFFFF5252),
                    title = "Automatic Song Cache Limit",
                    subtitle = "$cacheLimitMb MB (~${(cacheLimitMb / 4.2).toInt()} Songs)",
                    onClick = { showCacheLimitDialog = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.CleaningServices,
                    iconBg = Color(0xFFE53935).copy(alpha = 0.18f),
                    iconTint = Color(0xFFFF5252),
                    title = "Clear Temporary Cache",
                    subtitle = "Free up ${formatBytes(stats.imageTempCacheBytes)} of temporary memory",
                    onClick = {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        runCatching {
                            context.cacheDir.listFiles()?.forEach {
                                if (it.name != "musique_audio") it.deleteRecursively()
                            }
                        }
                        refreshStats()
                        Toast.makeText(context, "Temporary cache cleared", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // 3. Offline Downloads Section
            SectionTitle(title = "DOWNLOADS & OFFLINE STORAGE")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                StorageSettingRow(
                    icon = Icons.Rounded.MusicNote,
                    iconBg = Color(0xFF9C27B0).copy(alpha = 0.18f),
                    iconTint = Color(0xFFCE93D8),
                    title = "Download Audio Format",
                    subtitle = currentFormat.label,
                    onClick = { showFormatPicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.Folder,
                    iconBg = Color(0xFF1976D2).copy(alpha = 0.18f),
                    iconTint = Color(0xFF64B5F6),
                    title = "Download Storage Location",
                    subtitle = currentLocation.label,
                    onClick = { showLocationPicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.AccountTree,
                    iconBg = Color(0xFF00897B).copy(alpha = 0.18f),
                    iconTint = Color(0xFF4DB6AC),
                    title = "Download Folder Structure",
                    subtitle = currentStructure.label,
                    onClick = { showFolderStructurePicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    iconBg = Color(0xFF43A047).copy(alpha = 0.18f),
                    iconTint = Color(0xFF81C784),
                    title = "Export Songs to File Manager",
                    subtitle = "Copy all downloaded songs to Phone Music folder (/Music/Musique)",
                    onClick = {
                        scope.launch {
                            val count = Downloads.exportAllToMusicFolder(context)
                            Toast.makeText(
                                context,
                                if (count > 0) "$count songs exported to /Music/Musique" else "No downloaded songs to export",
                                Toast.LENGTH_SHORT,
                            ).show()
                            refreshStats()
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.Sync,
                    iconBg = Color(0xFF0288D1).copy(alpha = 0.18f),
                    iconTint = Color(0xFF4FC3F7),
                    title = "Migrate Flutter Musique Downloads",
                    subtitle = "Scan & import offline songs downloaded in the previous Flutter app",
                    onClick = {
                        scope.launch {
                            val res = FlutterMigrationEngine.migrate(context, force = true)
                            Toast.makeText(context, res.details, Toast.LENGTH_LONG).show()
                            refreshStats()
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.DeleteOutline,
                    iconBg = Color(0xFFE53935).copy(alpha = 0.18f),
                    iconTint = Color(0xFFFF5252),
                    title = "Delete All Offline Downloads",
                    subtitle = "Delete all offline song audio files from the device",
                    titleColor = Color(0xFFFF5252),
                    onClick = { showDeleteConfirmDialog = true },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showLocationPicker) {
        StorageLocationPickerDialog(
            current = currentLocation,
            onSelect = {
                AppSettings.setDownloadLocation(it)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
        )
    }

    if (showFolderStructurePicker) {
        FolderStructurePickerDialog(
            current = currentStructure,
            onSelect = {
                AppSettings.setDownloadFolderStructure(it)
                showFolderStructurePicker = false
            },
            onDismiss = { showFolderStructurePicker = false },
        )
    }

    if (showFormatPicker) {
        DownloadFormatPickerDialog(
            current = currentFormat,
            onSelect = {
                AppSettings.setDownloadFormat(it)
                showFormatPicker = false
            },
            onDismiss = { showFormatPicker = false },
        )
    }

    if (showCacheLimitDialog) {
        CacheLimitSliderDialog(
            currentBytes = cacheLimitBytes,
            onSave = {
                AppSettings.setAudioCacheLimitBytes(it)
                showCacheLimitDialog = false
            },
            onDismiss = { showCacheLimitDialog = false },
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF222228),
            title = {
                Text(
                    text = "Delete All Downloads?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "This will remove all offline song files from your device (${stats.downloadedSongsCount} songs, ${formatBytes(stats.downloadedSongsBytes)}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = Downloads.deleteAllDownloads(context)
                            Toast.makeText(context, "$deleted offline songs deleted", Toast.LENGTH_SHORT).show()
                            refreshStats()
                            showDeleteConfirmDialog = false
                        }
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }
}

// ── Download Settings Sheet (for Downloaded songs page / 3-dots) ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsSheet(
    onDismissRequest: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLocationPicker by remember { mutableStateOf(false) }
    var showFolderStructurePicker by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentFormat by AppSettings.downloadFormat.collectAsStateWithLifecycle()
    val currentLocation by AppSettings.downloadLocation.collectAsStateWithLifecycle()
    val currentStructure by AppSettings.downloadFolderStructure.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16161A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Download Settings",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                color = Color.White,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                StorageSettingRow(
                    icon = Icons.Rounded.Folder,
                    iconBg = Color(0xFF1976D2).copy(alpha = 0.18f),
                    iconTint = Color(0xFF64B5F6),
                    title = "Storage Location",
                    subtitle = currentLocation.label,
                    onClick = { showLocationPicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.AccountTree,
                    iconBg = Color(0xFF00897B).copy(alpha = 0.18f),
                    iconTint = Color(0xFF4DB6AC),
                    title = "Folder Structure",
                    subtitle = currentStructure.label,
                    onClick = { showFolderStructurePicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.MusicNote,
                    iconBg = Color(0xFF9C27B0).copy(alpha = 0.18f),
                    iconTint = Color(0xFFCE93D8),
                    title = "Download Audio Format",
                    subtitle = currentFormat.label,
                    onClick = { showFormatPicker = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.Shuffle,
                    iconBg = Color.White.copy(alpha = 0.12f),
                    iconTint = Color.White,
                    title = "Shuffle All Songs",
                    subtitle = "Shuffle and play all offline songs",
                    onClick = {
                        onShuffleAll()
                        onDismissRequest()
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    iconBg = Color(0xFF43A047).copy(alpha = 0.18f),
                    iconTint = Color(0xFF81C784),
                    title = "Export to File Manager",
                    subtitle = "Copy all songs to /Music/Musique folder",
                    onClick = {
                        scope.launch {
                            val count = Downloads.exportAllToMusicFolder(context)
                            Toast.makeText(
                                context,
                                if (count > 0) "$count songs exported to /Music/Musique" else "No downloaded songs to export",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.Sync,
                    iconBg = Color(0xFF0288D1).copy(alpha = 0.18f),
                    iconTint = Color(0xFF4FC3F7),
                    title = "Migrate Flutter Downloads",
                    subtitle = "Scan & import legacy offline songs from Flutter app",
                    onClick = {
                        scope.launch {
                            val res = FlutterMigrationEngine.migrate(context, force = true)
                            Toast.makeText(context, res.details, Toast.LENGTH_LONG).show()
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f),
                )

                StorageSettingRow(
                    icon = Icons.Rounded.DeleteOutline,
                    iconBg = Color(0xFFE53935).copy(alpha = 0.18f),
                    iconTint = Color(0xFFFF5252),
                    title = "Delete All Downloads",
                    subtitle = "Delete all offline songs from device",
                    titleColor = Color(0xFFFF5252),
                    onClick = { showDeleteConfirmDialog = true },
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showLocationPicker) {
        StorageLocationPickerDialog(
            current = currentLocation,
            onSelect = {
                AppSettings.setDownloadLocation(it)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
        )
    }

    if (showFolderStructurePicker) {
        FolderStructurePickerDialog(
            current = currentStructure,
            onSelect = {
                AppSettings.setDownloadFolderStructure(it)
                showFolderStructurePicker = false
            },
            onDismiss = { showFolderStructurePicker = false },
        )
    }

    if (showFormatPicker) {
        DownloadFormatPickerDialog(
            current = currentFormat,
            onSelect = {
                AppSettings.setDownloadFormat(it)
                showFormatPicker = false
            },
            onDismiss = { showFormatPicker = false },
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF222228),
            title = {
                Text(
                    text = "Delete All Downloads?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "This will remove all offline song files from your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = Downloads.deleteAllDownloads(context)
                            Toast.makeText(context, "$deleted offline songs deleted", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmDialog = false
                            onDismissRequest()
                        }
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }
}

// ── Components & Dialogs ──────────────────────────────────────────────────────

@Composable
private fun StorageBreakdownCard(stats: StorageStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "App Storage",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.PieChart,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Red: Streaming cache
        BreakdownRow(
            dotColor = Color(0xFFFF3B30),
            title = "Streaming Songs Cache",
            sizeText = "${formatBytes(stats.streamingCacheBytes)} (${stats.streamingCacheSongs} songs)",
        )

        Spacer(Modifier.height(10.dp))

        // Blue: Image & temp cache
        BreakdownRow(
            dotColor = Color(0xFF0A84FF),
            title = "Image & Temporary Cache",
            sizeText = formatBytes(stats.imageTempCacheBytes),
        )

        Spacer(Modifier.height(10.dp))

        // Green: Offline downloads
        BreakdownRow(
            dotColor = Color(0xFF30D158),
            title = "Offline Songs (Downloads)",
            sizeText = "${formatBytes(stats.downloadedSongsBytes)} (${stats.downloadedSongsCount} songs)",
        )
    }
}

@Composable
private fun BreakdownRow(dotColor: Color, title: String, sizeText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        ),
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun StorageSettingRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Dialog Pickers ────────────────────────────────────────────────────────────

@Composable
fun StorageLocationPickerDialog(
    current: DownloadLocation,
    onSelect: (DownloadLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202026),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1976D2).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Download Storage Location",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DownloadLocation.entries.forEach { location ->
                    val selected = location == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onSelect(location) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(location) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = Color.White.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (location) {
                                    DownloadLocation.APP_INTERNAL -> "App Internal (Secure)"
                                    DownloadLocation.PHONE_MUSIC -> "Phone Music Folder (/Music/Musique)"
                                    DownloadLocation.DOWNLOADS -> "Downloads Folder (/Download/Velthy)"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = location.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun FolderStructurePickerDialog(
    current: DownloadFolderStructure,
    onSelect: (DownloadFolderStructure) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202026),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00897B).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountTree,
                        contentDescription = null,
                        tint = Color(0xFF4DB6AC),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Download Folder Structure",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DownloadFolderStructure.entries.forEach { structure ->
                    val selected = structure == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onSelect(structure) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(structure) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = Color.White.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${structure.label} (${structure.pattern})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun DownloadFormatPickerDialog(
    current: DownloadFormat,
    onSelect: (DownloadFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202026),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF9C27B0).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFCE93D8),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Download Audio Format",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DownloadFormat.entries.forEach { format ->
                    val selected = format == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onSelect(format) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(format) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = Color.White.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = format.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = format.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun CacheLimitSliderDialog(
    currentBytes: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMb = (currentBytes / (1024 * 1024)).toFloat()
    var sliderValue by remember { mutableFloatStateOf(initialMb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202026),
        title = {
            Text(
                text = "Automatic Song Cache Limit",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${sliderValue.roundToInt()} MB (~${(sliderValue.roundToInt() / 4.2).toInt()} Songs)",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Maximum storage limit for automatic audio caching on device memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = (AppSettings.DEFAULT_CACHE_LIMIT_BYTES / (1024 * 1024)).toFloat()..
                        (AppSettings.MAX_CACHE_LIMIT_BYTES / (1024 * 1024)).toFloat(),
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sliderValue.roundToInt().toLong() * 1024 * 1024) }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.1f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
