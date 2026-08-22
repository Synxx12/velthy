package com.music.bitchord.data

import com.music.bitchord.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

/**
 * Checks for updates against GitHub Releases for Musique Native (Client-Side Edition).
 * Polls for releases tagged with 'native-v*' (or fallback 'v*').
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkDownloadUrl: String,
        val fileSize: Long,
        val releaseNotes: String,
        val publishedAt: String,
    )

    private const val RELEASES_API_URL =
        "https://api.github.com/repos/Synxx12/musique-app-releases/releases"
    private const val FALLBACK_API_URL =
        "https://api.github.com/repos/Synxx12/musique-android/releases"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val update = fetchLatestNativeRelease(RELEASES_API_URL)
                ?: fetchLatestNativeRelease(FALLBACK_API_URL)
            if (update != null) {
                _available.value = update
            }
            update
        }.getOrNull()
    }

    private fun fetchLatestNativeRelease(url: String): UpdateInfo? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MusiqueNativeAndroid")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return null

            val releases = json.parseToJsonElement(body) as? JsonArray ?: return null
            for (element in releases) {
                val obj = element as? JsonObject ?: continue
                val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
                val releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: continue
                val releaseNotes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()

                // Check for native release tags
                val versionStr = when {
                    tag.startsWith("native-v") -> tag.removePrefix("native-v")
                    tag.startsWith("v") && !tag.contains("cloud") -> tag.removePrefix("v")
                    else -> null
                } ?: continue

                if (isNewer(versionStr, BuildConfig.VERSION_NAME)) {
                    // Extract APK download URL & file size from assets
                    var apkUrl = ""
                    var apkSize = 0L

                    val assets = obj["assets"]?.jsonArray
                    if (assets != null) {
                        for (assetEl in assets) {
                            val assetObj = assetEl.jsonObject
                            val name = assetObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = assetObj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                apkSize = assetObj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                                break
                            }
                        }
                    }

                    return UpdateInfo(
                        version = versionStr,
                        releaseUrl = releaseUrl,
                        apkDownloadUrl = apkUrl,
                        fileSize = apkSize,
                        releaseNotes = releaseNotes,
                        publishedAt = publishedAt,
                    )
                }
            }
            null
        }.getOrNull()
    }

    /** Numeric, dot-separated comparison — "1.10" outranks "1.9". */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
