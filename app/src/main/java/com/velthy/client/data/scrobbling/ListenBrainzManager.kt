package com.velthy.client.data.scrobbling

import android.util.Log
import com.velthy.client.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.velthy.client.data.Http

object ListenBrainzManager {
    private const val TAG = "ListenBrainzManager"
    private const val API_URL = "https://api.listenbrainz.org/1/submit-listens"

    suspend fun submitPlayingNow(
        token: String,
        song: Song?,
        positionMs: Long,
        durationMsOverride: Long? = null,
    ): Boolean {
        if (token.isBlank() || song == null) return false
        return withContext(Dispatchers.IO) {
            try {
                val durationMs = durationMsOverride ?: parseDurationMs(song.durationText)
                // The API rejects a zero/negative duration_ms, and it is
                // optional — so only send it when it is actually known.
                val durationPart = if (durationMs > 0) "\"duration_ms\":$durationMs," else ""
                val releaseName = song.albumName.orEmpty()
                val releasePart = if (releaseName.isBlank()) "" else "\"release_name\":\"${escapeJson(releaseName)}\","
                val trackMetadata = """{"track_metadata":{"artist_name":"${escapeJson(song.artist)}","track_name":"${escapeJson(song.title)}",$releasePart"additional_info":{${durationPart}"position_ms":$positionMs,"submission_client":"Velthy"}}}"""
                val bodyJson = "{\"listen_type\":\"playing_now\",\"payload\":[$trackMetadata]}"
                Log.d(TAG, "submitPlayingNow: $bodyJson")
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request =
                    Request.Builder()
                        .url(API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Token $token")
                        .build()

                Http.client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "playing_now submitted for ${song.title}")
                        true
                    } else {
                        val bodyText = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                        Log.w(TAG, "playing_now submit failed: ${resp.code} - $bodyText")
                        false
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "submitPlayingNow failed", ex)
                false
            }
        }
    }

    suspend fun submitFinished(
        token: String,
        song: Song?,
        startMs: Long,
        endMs: Long,
        durationMsOverride: Long? = null,
    ): Boolean {
        if (token.isBlank() || song == null) return false
        return withContext(Dispatchers.IO) {
            try {
                val durationMs = durationMsOverride ?: parseDurationMs(song.durationText)
                val durationPart = if (durationMs > 0) "\"duration_ms\":$durationMs," else ""
                val releaseName = song.albumName.orEmpty()
                val releasePart = if (releaseName.isBlank()) "" else "\"release_name\":\"${escapeJson(releaseName)}\","
                var listenedAtStart = startMs / 1000L
                val MIN_LISTEN_TS = 1033430400L
                if (listenedAtStart < MIN_LISTEN_TS) {
                    listenedAtStart = System.currentTimeMillis() / 1000L
                }
                val trackMetadata = """{"listened_at":$listenedAtStart,"track_metadata":{"artist_name":"${escapeJson(song.artist)}","track_name":"${escapeJson(song.title)}",$releasePart"additional_info":{${durationPart}"start_ms":$startMs,"end_ms":$endMs,"submission_client":"Velthy"}}}"""
                val bodyJson = "{\"listen_type\":\"single\",\"payload\":[$trackMetadata]}"
                Log.d(TAG, "submitFinished: $bodyJson")
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request =
                    Request.Builder()
                        .url(API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Token $token")
                        .build()

                Http.client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "finished listen submitted for ${song.title}")
                        true
                    } else {
                        val bodyText = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                        Log.w(TAG, "finished listen submit failed: ${resp.code} - $bodyText")
                        false
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "submitFinished failed", ex)
                false
            }
        }
    }

    private fun parseDurationMs(text: String?): Long {
        if (text == null) return 0L
        val parts = text.split(":")
        if (parts.size != 2) return 0L
        val minutes = parts[0].toLongOrNull() ?: return 0L
        val seconds = parts[1].toLongOrNull() ?: return 0L
        return (minutes * 60 + seconds) * 1000
    }

    suspend fun validateToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("User token cannot be blank."))
        try {
            val request = Request.Builder()
                .url("https://api.listenbrainz.org/1/validate-token")
                .header("Authorization", "Token ${token.trim()}")
                .get()
                .build()

            Http.client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val valid = body.contains("\"valid\": true") || body.contains("\"valid\":true")
                    if (valid) {
                        val username = Regex(""""user_name":\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1) ?: "ListenBrainz User"
                        Result.success(username)
                    } else {
                        val msg = Regex(""""message":\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1) ?: "Invalid ListenBrainz token. Please check your token."
                        Result.failure(Exception(msg))
                    }
                } else {
                    Result.failure(Exception("Validation failed (HTTP ${resp.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
