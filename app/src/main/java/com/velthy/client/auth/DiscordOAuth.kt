package com.velthy.client.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.velthy.client.data.Http
import com.velthy.client.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Standard Discord OAuth2 Authentication with PKCE.
 *
 * Opens Discord's official web authorization in Chrome Custom Tabs / external browser
 * and receives the callback via deep link redirect (`discord-1165706613961789445:/authorize/callback`).
 */
object DiscordOAuth {
    private const val TAG = "DiscordOAuth"
    const val CLIENT_ID = "1541308554173227080"
    const val REDIRECT_URI = "discord-1541308554173227080:/authorize/callback"
    const val SCOPE = "openid identify sdk.social_layer_presence"

    private fun generateRandomString(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Generates a new PKCE code verifier and launches Discord OAuth authorization
     * directly in the external browser (Chrome / default browser app).
     */
    fun launchOAuth(context: Context) {
        val authStore = AuthStore(context)
        val codeVerifier = generateRandomString(48)
        val state = generateRandomString(24)
        val codeChallenge = generateCodeChallenge(codeVerifier)

        authStore.discordCodeVerifier = codeVerifier
        authStore.discordAuthState = state

        val uri = Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.chrome")
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(chromeIntent, 0) != null) {
                context.startActivity(chromeIntent)
                return
            }
        } catch (_: Exception) {}

        context.startActivity(browserIntent)
    }

    /**
     * Handles the redirect intent from Discord, verifies PKCE, exchanges the code for a token,
     * and fetches the user profile.
     */
    suspend fun handleRedirect(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")

            if (error != null) {
                return@withContext Result.failure(Exception(errorDescription ?: error))
            }
            if (code.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No authorization code in redirect"))
            }

            val authStore = AuthStore(context)
            val savedState = authStore.discordAuthState
            val codeVerifier = authStore.discordCodeVerifier

            if (savedState != null && state != null && savedState != state) {
                return@withContext Result.failure(Exception("Invalid OAuth state parameter"))
            }
            if (codeVerifier.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No PKCE code verifier found"))
            }

            // Exchange authorization code for access token
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val tokenRequest = Request.Builder()
                .url("https://discord.com/api/v10/oauth2/token")
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val tokenResponse = Http.client.newCall(tokenRequest).execute()
            val tokenResponseBody = tokenResponse.body?.string().orEmpty()

            if (!tokenResponse.isSuccessful) {
                Log.e(TAG, "Token exchange failed: $tokenResponseBody")
                return@withContext Result.failure(Exception("Token exchange failed (${tokenResponse.code})"))
            }

            val tokenJson = JSONObject(tokenResponseBody)
            val accessToken = tokenJson.optString("access_token")
            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("No access_token in response"))
            }

            // Fetch user profile (@me)
            val userRequest = Request.Builder()
                .url("https://discord.com/api/v10/users/@me")
                .header("Authorization", "Bearer $accessToken")
                .build()

            val userResponse = Http.client.newCall(userRequest).execute()
            if (userResponse.isSuccessful) {
                val userJson = JSONObject(userResponse.body?.string().orEmpty())
                val username = userJson.optString("username")
                val globalName = userJson.optString("global_name").takeIf { it.isNotBlank() } ?: username
                val avatar = userJson.optString("avatar").takeIf { it.isNotBlank() }
                AppSettings.setDiscordAccount(username, globalName, avatar)
            }

            AppSettings.setDiscordToken(accessToken)
            AppSettings.setDiscordRpcEnabled(true)

            // Clear temporary OAuth state
            authStore.discordCodeVerifier = null
            authStore.discordAuthState = null

            Result.success(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Discord redirect: ${e.message}", e)
            Result.failure(e)
        }
    }
}
