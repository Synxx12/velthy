package com.velthy.client.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.velthy.client.data.DebugLog as Log
import androidx.annotation.RequiresApi
import com.velthy.client.data.model.Song
import java.io.File
import java.io.OutputStream

/**
 * Where a downloaded track goes, and how it gets there.
 *
 * The destination is the device's own Music folder, in a `Musique`
 * subfolder — somewhere the file manager lists, other players can open, and a
 * user can back up or delete without going through this app. That choice is
 * what makes this class necessary at all: an app-private directory would be
 * four lines of [File], but a shared one crosses the scoped-storage line and
 * the two sides of that line have nothing in common.
 *
 * It goes through the audio collection rather than Downloads specifically
 * because the files are `.webm` — a container extension Android's own mime
 * table ties to video regardless of what MIME type this class declares for
 * it — and a Gallery app crawling Downloads for video-looking files does not
 * care what column says otherwise. The audio collection is not on that path.
 *
 *  - **API 29+** goes through [MediaStore]. There is no filesystem path to
 *    write to; the store mints a row, hands back a content uri, and the file
 *    exists at a location it chooses. `IS_PENDING` keeps the row invisible to
 *    everything else until the bytes are all there, so a cancelled download is
 *    never a half-file somebody can find and play.
 *  - **API 26–28** is a real path and a runtime permission. The file is written
 *    beside its final name with a `.part` suffix and renamed on completion,
 *    which is the same guarantee `IS_PENDING` gives for free above, and the
 *    media scanner is told afterwards or the file stays invisible to everything
 *    that reads the index rather than the disk.
 *
 * Neither side writes tags — this class only ever copies the bytes googlevideo
 * sends. [MediaTagger] rewrites the finished file afterwards to add them; the
 * filename below is what every downloaded track carries regardless of
 * whether that rewrite finds a layout it recognises.
 */
object DownloadStore {

    private const val TAG = "Musique"

    /** The subfolder of Music that everything lands in. */
    const val FOLDER = "Velthy"

    private val relativePath = "${Environment.DIRECTORY_MUSIC}/$FOLDER"

    /**
     * Whether saving needs `WRITE_EXTERNAL_STORAGE` asked for at runtime.
     *
     * Only below API 29. From there on the app writes through the media store,
     * which grants access to rows it created and needs no permission for them —
     * and the permission it would ask for isn't grantable anyway.
     *
     * Every version check in this file is written out inline rather than
     * routed through this, deliberately: lint reads an inline `SDK_INT`
     * comparison as a guard around the API-29 calls beside it and does not
     * read a boolean property the same way, so hiding the check behind a name
     * costs a `NewApi` error on the release build.
     */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    // ---- Naming -------------------------------------------------------------

    /**
     * What the file is called: `Artist - Title.ext`.
     *
     * Artist first because a Music folder is sorted by name and nothing
     * else — no tags to group by — so leading with the artist is the only thing
     * that puts an album back together in the listing.
     */
    fun fileNameFor(song: Song, extension: String): String {
        val artist = sanitise(song.artist)
        val title = sanitise(song.title)
        val stem = when {
            artist.isEmpty() -> title
            title.isEmpty() -> artist
            else -> "$artist - $title"
        }.ifEmpty { song.videoId }
        return "${stem.take(MAX_STEM_CHARS).trimEnd()}.$extension"
    }

    /**
     * Everything a FAT32 volume, the media store or a shell would each object
     * to for its own reasons, plus the whitespace that survives them.
     */
    fun sanitise(raw: String): String = raw
        .replace(ILLEGAL, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .trim('.')

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    private val WHITESPACE = Regex("""\s+""")

    /** Long enough for anything real, short of the 255-byte filename ceiling. */
    private const val MAX_STEM_CHARS = 120

    data class Storable(val extension: String, val mimeType: String)

    /**
     * How to store a stream of codec [codec], or null if the media store will
     * reject it.
     */
    fun storable(codec: String?): Storable? = when (codec?.lowercase()?.trim()) {
        "flac", "x-flac" -> Storable("flac", "audio/flac")
        "wav", "x-wav", "wave" -> Storable("wav", "audio/x-wav")
        "alac", "m4a", "mp4", "aac" -> Storable("m4a", "audio/mp4")
        "mp3", "mpeg" -> Storable("mp3", "audio/mpeg")
        "opus", "webm" -> Storable("webm", "audio/ogg")
        else -> null
    }

    // ---- Lookup -------------------------------------------------------------

    /**
     * The uri of a file already saved under this name, or null.
     */
    fun existing(context: Context, name: String): Uri? {
        val internalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), name)
        if (internalFile.exists()) return Uri.fromFile(internalFile)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreEntry(context, name)
        } else {
            legacyFile(name).takeIf { it.exists() }?.let(Uri::fromFile)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreEntry(context: Context, name: String): Uri? = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "%$FOLDER%"),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendPath(cursor.getLong(0).toString())
                .build()
        }
    }.onFailure { Log.w(TAG, "media store lookup failed for $name: ${it.message}") }.getOrNull()

    /**
     * Whether [uri] still names a file that is there.
     */
    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") return uri.path?.let { File(it).exists() } == true
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
    }.getOrDefault(false)

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() } == true
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "could not delete $uri: ${it.message}") }.getOrDefault(false)

    // ---- Writing ------------------------------------------------------------

    /**
     * A destination that exists but is not yet a file anyone else can see.
     */
    class Pending internal constructor(
        private val context: Context,
        val uri: Uri,
        val name: String,
        /** Set on file-based paths: the `.part` file being written. */
        private val part: File?,
        /** Set on file-based paths: what [part] is renamed to. */
        private val target: File?,
    ) {
        fun openStream(): OutputStream =
            part?.outputStream()
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Could not open $name for writing")

        /** @return the uri the finished file can be reached at. */
        fun commit(): Uri {
            if (part != null && target != null) {
                if (!part.renameTo(target)) error("Could not finish writing $name")
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    null,
                    null,
                )
                return Uri.fromFile(target)
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        }

        fun abort() {
            part?.delete()
            if (part == null) runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    /**
     * Reserve [name] and return somewhere to write it.
     */
    fun begin(
        context: Context,
        name: String,
        mimeType: String,
        subfolder: String = "",
    ): Pending {
        val location = com.velthy.client.data.settings.AppSettings.downloadLocation.value
        val safeMimeType = when {
            name.endsWith(".m4a") || mimeType == "audio/mp4" -> "audio/mp4"
            name.endsWith(".flac") || mimeType == "audio/flac" -> "audio/flac"
            name.endsWith(".mp3") || mimeType == "audio/mpeg" -> "audio/mpeg"
            else -> "audio/ogg" // Universal audio MIME type for Opus/WebM accepted across all Android devices
        }

        // 1. App Internal (Private Directory)
        if (location == com.velthy.client.data.settings.DownloadLocation.APP_INTERNAL) {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val targetDir = if (subfolder.isNotBlank()) File(baseDir, subfolder) else baseDir
            if (!targetDir.exists()) targetDir.mkdirs()
            val target = File(targetDir, name)
            val part = File(targetDir, "$name.part")
            part.delete()
            return Pending(context, Uri.fromFile(target), name, part = part, target = target)
        }

        // 2. Phone Music Folder (/Music/Velthy)
        if (location == com.velthy.client.data.settings.DownloadLocation.PHONE_MUSIC) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relPath = if (subfolder.isNotBlank()) "${Environment.DIRECTORY_MUSIC}/$FOLDER/$subfolder" else "${Environment.DIRECTORY_MUSIC}/$FOLDER"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, safeMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = runCatching {
                    context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                }.getOrNull()

                if (uri != null) {
                    return Pending(context, uri, name, part = null, target = null)
                }
                // Fallback to internal storage if MediaStore is restricted on this OEM
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val targetDir = if (subfolder.isNotBlank()) File(baseDir, subfolder) else baseDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val target = File(targetDir, name)
                val part = File(targetDir, "$name.part")
                part.delete()
                return Pending(context, Uri.fromFile(target), name, part = part, target = target)
            } else {
                val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
                val targetDir = if (subfolder.isNotBlank()) File(baseDir, subfolder) else baseDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val target = File(targetDir, name)
                val part = File(targetDir, "$name.part")
                part.delete()
                return Pending(context, Uri.fromFile(target), name, part = part, target = target)
            }
        }

        // 3. Downloads Folder (/Download/Velthy)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relPath = if (subfolder.isNotBlank()) "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$subfolder" else "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, safeMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching {
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()

            if (uri != null) {
                return Pending(context, uri, name, part = null, target = null)
            }
        }

        val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        val targetDir = if (subfolder.isNotBlank()) File(baseDir, subfolder) else baseDir
        if (!targetDir.exists()) targetDir.mkdirs()
        val target = File(targetDir, name)
        val part = File(targetDir, "$name.part")
        part.delete()
        return Pending(context, Uri.fromFile(target), name, part = part, target = target)
    }

    @Suppress("DEPRECATION")
    private fun legacyFile(name: String) = File(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER),
        name,
    )
}
