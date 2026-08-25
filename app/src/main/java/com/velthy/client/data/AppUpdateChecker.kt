package com.velthy.client.data

import com.velthy.client.BuildConfig
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
 * Checks for updates against GitHub Releases for Velthy (Client-Side Edition).
 * Polls for releases tagged with 'v*' (or fallback 'native-v*').
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
        "https://api.github.com/repos/Synxx12/velthy/releases"
    private const val FALLBACK_API_URL =
        "https://api.github.com/repos/Synxx12/musique-app-releases/releases"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Direct Zero-Rate-Limit Redirect Check (100% realtime, never rate limits)
            val redirectUpdate = fetchLatestFromRedirect()

            // 2. Try GitHub REST API for detailed release notes & asset metadata
            val apiUpdate = fetchLatestNativeRelease(RELEASES_API_URL)
                ?: fetchLatestNativeRelease(FALLBACK_API_URL)

            val finalUpdate = when {
                apiUpdate != null -> apiUpdate
                redirectUpdate != null -> redirectUpdate
                else -> null
            }

            if (finalUpdate != null) {
                _available.value = finalUpdate
            }
            finalUpdate
        }.getOrNull()
    }

    /**
     * Resolves the latest release via GitHub's HTTP 302 /releases/latest redirect.
     * This bypasses GitHub API's 60 req/hour rate limit completely and is 100% realtime.
     */
    private fun fetchLatestFromRedirect(): UpdateInfo? {
        return runCatching {
            val noRedirectClient = Http.client.newBuilder()
                .followRedirects(false)
                .build()
            val request = Request.Builder()
                .url("https://github.com/Synxx12/velthy/releases/latest")
                .header("User-Agent", "Mozilla/5.0 (Android) VelthyNative")
                .build()
            val response = noRedirectClient.newCall(request).execute()
            val location = response.header("Location").orEmpty()
            if (response.code in 300..399 && location.isNotBlank()) {
                val tag = location.substringAfterLast("/")
                val versionStr = when {
                    tag.startsWith("native-v") -> tag.removePrefix("native-v")
                    tag.startsWith("v") && !tag.contains("cloud") -> tag.removePrefix("v")
                    else -> null
                } ?: return null

                if (isNewer(versionStr, BuildConfig.VERSION_NAME)) {
                    val apkUrl = "https://github.com/Synxx12/velthy/releases/download/$tag/Velthy-v$versionStr.apk"
                    val realSize = fetchContentLength(apkUrl)
                    val realNotes = fetchReleaseNotesFromHtml(location).ifBlank {
                        "Versi terbaru Velthy v$versionStr telah dirilis dengan peningkatan performa dan stabilitas audio."
                    }

                    return UpdateInfo(
                        version = versionStr,
                        releaseUrl = location,
                        apkDownloadUrl = apkUrl,
                        fileSize = realSize,
                        releaseNotes = realNotes,
                        publishedAt = "",
                    )
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Resolves the exact Content-Length in bytes from GitHub CDN via HTTP HEAD.
     */
    private fun fetchContentLength(url: String): Long {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Android) MusiqueNative")
                .build()
            Http.client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    /**
     * Extracts the real markdown release notes from the GitHub release page HTML.
     */
    private fun fetchReleaseNotesFromHtml(releaseUrl: String): String {
        return runCatching {
            val request = Request.Builder()
                .url(releaseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) VelthyWeb")
                .build()
            Http.client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val match = Regex("""markdown-body[^>]*>([\s\S]*?)</div>""").find(html)
                if (match != null) {
                    match.groupValues[1]
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                } else ""
            }
        }.getOrDefault("")
    }

    private fun fetchLatestNativeRelease(url: String): UpdateInfo? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VelthyNativeAndroid")
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

                // Check for release tags (v1.3.x / native-v1.3.x)
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
                        val isArm64 = android.os.Build.SUPPORTED_ABIS.any { it.contains("arm64", ignoreCase = true) }
                        val isArmv7 = android.os.Build.SUPPORTED_ABIS.any { it.contains("armeabi", ignoreCase = true) }
                        val isX86 = android.os.Build.SUPPORTED_ABIS.any { it.contains("x86", ignoreCase = true) }

                        // 1. Prefer architecture-specific lightweight build (e.g. arm64-v8a ~27MB)
                        val preferred = assets.firstOrNull { assetEl ->
                            val name = (assetEl as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (!name.endsWith(".apk", ignoreCase = true) || name.contains("cloud", ignoreCase = true)) return@firstOrNull false
                            if (isArm64 && name.contains("arm64", ignoreCase = true)) return@firstOrNull true
                            if (isArmv7 && !isArm64 && name.contains("armeabi-v7a", ignoreCase = true)) return@firstOrNull true
                            if (isX86 && !isArm64 && name.contains("x86", ignoreCase = true)) return@firstOrNull true
                            false
                        } ?: assets.firstOrNull { assetEl ->
                            // 2. Default lightweight release APK (Velthy-v1.X.apk or Musique-v1.X-client.apk)
                            val name = (assetEl as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                            name.endsWith(".apk", ignoreCase = true) &&
                                (name.contains("velthy", ignoreCase = true) || name.contains("musique", ignoreCase = true) || name.contains("client", ignoreCase = true)) &&
                                !name.contains("latest", ignoreCase = true) &&
                                !name.contains("universal", ignoreCase = true)
                        } ?: assets.firstOrNull { assetEl ->
                            // 3. Fallback: Any compatible non-cloud APK
                            val name = (assetEl as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                            name.endsWith(".apk", ignoreCase = true) && !name.contains("cloud", ignoreCase = true)
                        }

                        if (preferred != null) {
                            val assetObj = preferred.jsonObject
                            apkUrl = assetObj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            apkSize = assetObj["size"]?.jsonPrimitive?.longOrNull ?: 0L
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
