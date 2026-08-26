package com.velthy.client.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import com.velthy.client.data.DebugLog as Log
import com.velthy.client.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class FlutterMigrationResult(
    val downloadedSongsMigrated: Int = 0,
    val totalFound: Int = 0,
    val details: String = "",
)

object FlutterMigrationEngine {

    private const val TAG = "FlutterMigration"
    private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
    private const val FLUTTER_INDEX_KEY = "flutter.musique_offline_downloads_index_v1"
    private const val FLUTTER_LEGACY_INDEX_KEY = "musique_offline_downloads_index_v1"
    private const val MIGRATION_DONE_KEY = "flutter_downloads_migrated_v3"

    /**
     * Checks if migration has already been automatically executed.
     */
    fun isMigrationDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences("musique_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(MIGRATION_DONE_KEY, false)
    }

    /**
     * Performs thorough metadata-rich migration from Flutter Musique to Android Native.
     */
    suspend fun migrate(context: Context, force: Boolean = false): FlutterMigrationResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("musique_settings", Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean(MIGRATION_DONE_KEY, false)) {
            Log.d(TAG, "Flutter migration already completed. Skipping automatic run.")
            return@withContext FlutterMigrationResult(0, 0, "Already migrated")
        }

        var migratedCount = 0
        val currentSaved = Downloads.saved.value.toMutableMap()
        val currentMetadata = mutableMapOf<String, SavedSongMetadata>()

        // Load existing metadata first
        Downloads.getDownloadedSongs(context).forEach { song ->
            currentMetadata[song.videoId] = SavedSongMetadata(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationText = song.durationText,
                albumName = song.albumName,
                uri = song.localUri ?: currentSaved[song.videoId] ?: "",
            )
        }

        val coversDir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
        val retriever = MediaMetadataRetriever()

        // 1. Scan Flutter SharedPreferences index
        runCatching {
            val flutterPrefs = context.getSharedPreferences(FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
            val rawIndex = flutterPrefs.getString(FLUTTER_INDEX_KEY, null)
                ?: flutterPrefs.getString(FLUTTER_LEGACY_INDEX_KEY, null)

            if (!rawIndex.isNullOrBlank()) {
                Log.d(TAG, "Found Flutter downloads index with content")
                val jsonObject = JSONObject(rawIndex)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val recordObj = jsonObject.optJSONObject(key) ?: continue
                    val songObj = recordObj.optJSONObject("song")
                    val audioPath = recordObj.optString("audioPath")

                    val videoId = songObj?.optString("id")?.takeIf { it.isNotBlank() } ?: key
                    var title = songObj?.optString("title")?.takeIf { it.isNotBlank() }
                    var artist = songObj?.optString("artist")?.takeIf { it.isNotBlank() }
                    var albumName = songObj?.optString("album")?.takeIf { it.isNotBlank() }
                    var coverUrl = songObj?.optString("thumbnailUrl")?.takeIf { it.isNotBlank() }
                        ?: songObj?.optString("coverUrl")?.takeIf { it.isNotBlank() }
                    var durationMs = songObj?.optLong("durationMs", 0L) ?: 0L
                    if (durationMs == 0L) {
                        val dur = songObj?.optLong("duration", 0L) ?: 0L
                        durationMs = if (dur in 1..9999) dur * 1000 else dur
                    }

                    // Resolve audio file on disk
                    val targetFile = resolveExistingAudioFile(context, audioPath, title ?: "", artist ?: "")
                    if (targetFile != null && targetFile.exists() && targetFile.length() > 1024) {
                        val fileUri = Uri.fromFile(targetFile).toString()

                        // Extract embedded tags and album artwork
                        runCatching {
                            retriever.setDataSource(targetFile.absolutePath)
                            val tagTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val tagArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val tagAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            val tagDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

                            if (title.isNullOrBlank() || title == "Unknown Title") {
                                title = tagTitle?.takeIf { it.isNotBlank() }
                                    ?: parseTitleFromFilename(targetFile.nameWithoutExtension)
                            }
                            if (artist.isNullOrBlank() || artist == "Unknown Artist") {
                                artist = tagArtist?.takeIf { it.isNotBlank() }
                                    ?: parseArtistFromFilename(targetFile.nameWithoutExtension)
                            }
                            if (albumName.isNullOrBlank()) {
                                albumName = tagAlbum?.takeIf { it.isNotBlank() }
                            }
                            if (durationMs <= 0L && tagDur != null && tagDur > 0) {
                                durationMs = tagDur
                            }

                            // Extract embedded picture if coverUrl is missing
                            if (coverUrl.isNullOrBlank()) {
                                val artBytes = retriever.embeddedPicture
                                if (artBytes != null && artBytes.isNotEmpty()) {
                                    val artFile = File(coversDir, "art_${videoId.hashCode().toString(16)}.jpg")
                                    artFile.writeBytes(artBytes)
                                    coverUrl = Uri.fromFile(artFile).toString()
                                }
                            }
                        }

                        val durationText = if (durationMs > 0) {
                            val totalSecs = durationMs / 1000
                            String.format(Locale.US, "%d:%02d", totalSecs / 60, totalSecs % 60)
                        } else null

                        val finalTitle = title?.takeIf { it.isNotBlank() } ?: targetFile.nameWithoutExtension
                        val finalArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"

                        currentSaved[videoId] = fileUri
                        currentMetadata[videoId] = SavedSongMetadata(
                            videoId = videoId,
                            title = finalTitle,
                            artist = finalArtist,
                            thumbnailUrl = coverUrl,
                            durationText = durationText,
                            albumName = albumName,
                            uri = fileUri,
                        )
                        migratedCount++
                        Log.d(TAG, "Migrated Flutter index song: $finalArtist - $finalTitle (Cover: ${coverUrl != null}, Dur: $durationText)")
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Error reading Flutter SharedPreferences index: ${it.message}") }

        // 2. Scan physical Musique download directories for any files
        val candidateDirectories = listOfNotNull(
            File("/storage/emulated/0/Music/Musique"),
            File("/storage/emulated/0/Download/Musique"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Musique"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Musique"),
            File(context.filesDir, "app_flutter/musique_downloads"),
            File(context.filesDir, "musique_downloads"),
            context.getExternalFilesDir(null)?.let { File(it, "musique_downloads") },
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "Musique") },
        ).distinctBy { it.absolutePath }

        for (dir in candidateDirectories) {
            if (!dir.exists() || !dir.isDirectory) continue
            runCatching {
                dir.walkTopDown().filter { it.isFile && isAudioFile(it.name) }.forEach { file ->
                    val fileUri = Uri.fromFile(file).toString()
                    val alreadyIndexed = currentSaved.values.any { it == fileUri } ||
                        currentMetadata.values.any { it.uri == fileUri }

                    if (!alreadyIndexed) {
                        var title: String? = null
                        var artist: String? = null
                        var albumName: String? = null
                        var durationText: String? = null
                        var coverUrl: String? = null

                        runCatching {
                            retriever.setDataSource(file.absolutePath)
                            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

                            title = metaTitle?.takeIf { it.isNotBlank() } ?: parseTitleFromFilename(file.nameWithoutExtension)
                            artist = metaArtist?.takeIf { it.isNotBlank() } ?: parseArtistFromFilename(file.nameWithoutExtension)
                            albumName = metaAlbum?.takeIf { it.isNotBlank() }

                            if (metaDur != null && metaDur > 0) {
                                val totalSecs = metaDur / 1000
                                durationText = String.format(Locale.US, "%d:%02d", totalSecs / 60, totalSecs % 60)
                            }

                            val artBytes = retriever.embeddedPicture
                            if (artBytes != null && artBytes.isNotEmpty()) {
                                val artFile = File(coversDir, "art_${file.name.hashCode().toString(16)}.jpg")
                                artFile.writeBytes(artBytes)
                                coverUrl = Uri.fromFile(artFile).toString()
                            }
                        }

                        val finalTitle = title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                        val finalArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                        val syntheticId = "flutter_${finalTitle.hashCode().toString(16)}_${finalArtist.hashCode().toString(16)}"

                        currentSaved[syntheticId] = fileUri
                        currentMetadata[syntheticId] = SavedSongMetadata(
                            videoId = syntheticId,
                            title = finalTitle,
                            artist = finalArtist,
                            thumbnailUrl = coverUrl,
                            durationText = durationText,
                            albumName = albumName,
                            uri = fileUri,
                        )
                        migratedCount++
                        Log.d(TAG, "Adopted physical legacy audio file: $finalArtist - $finalTitle (Cover: ${coverUrl != null})")
                    }
                }
            }.onFailure { Log.w(TAG, "Error scanning dir ${dir.absolutePath}: ${it.message}") }
        }
        runCatching { retriever.release() }

        // 3. Persist migrated records to Downloads system
        if (migratedCount > 0 || force) {
            Downloads.importMigrated(currentSaved, currentMetadata)
            Log.d(TAG, "Successfully committed ${currentMetadata.size} total tracks to Downloads database.")
        }

        prefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()

        FlutterMigrationResult(
            downloadedSongsMigrated = migratedCount,
            totalFound = currentMetadata.size,
            details = if (migratedCount > 0) "$migratedCount legacy songs imported with full metadata" else "${currentMetadata.size} offline songs up to date",
        )
    }

    private fun parseArtistFromFilename(name: String): String {
        return if (name.contains(" - ")) {
            name.substringBefore(" - ").trim()
        } else {
            "Unknown Artist"
        }
    }

    private fun parseTitleFromFilename(name: String): String {
        return if (name.contains(" - ")) {
            name.substringAfter(" - ").trim()
        } else {
            name.trim()
        }
    }

    private fun resolveExistingAudioFile(
        context: Context,
        audioPath: String?,
        title: String,
        artist: String,
    ): File? {
        if (!audioPath.isNullOrBlank()) {
            val directFile = File(audioPath)
            if (directFile.exists()) return directFile
        }

        val cleanTitle = sanitize(title)
        val cleanArtist = sanitize(artist)

        val potentialNames = listOfNotNull(
            "$cleanArtist - $cleanTitle.m4a",
            "$cleanArtist - $cleanTitle.opus",
            "$cleanArtist - $cleanTitle.mp3",
            "$cleanArtist - $cleanTitle.flac",
            "$cleanTitle.m4a",
            "$cleanTitle.opus",
            "$cleanTitle.mp3",
            if (audioPath != null) File(audioPath).name else null,
        )

        val searchDirs = listOfNotNull(
            File("/storage/emulated/0/Music/Musique"),
            File("/storage/emulated/0/Download/Musique"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Musique"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Musique"),
            File(context.filesDir, "app_flutter/musique_downloads"),
            context.getExternalFilesDir(null)?.let { File(it, "musique_downloads") },
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "Musique") },
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            for (name in potentialNames) {
                val candidate = File(dir, name)
                if (candidate.exists()) return candidate
            }
        }

        return null
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".m4a") || lower.endsWith(".mp3") ||
            lower.endsWith(".flac") || lower.endsWith(".opus") ||
            lower.endsWith(".ogg") || lower.endsWith(".webm") ||
            lower.endsWith(".wav") || lower.endsWith(".aac")
    }

    private fun sanitize(input: String): String {
        return input.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
