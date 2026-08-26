package com.velthy.client.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val handle: String? = null,
    val thumbnailUrl: String? = null,
    val cookie: String,
    val savedAt: Long = System.currentTimeMillis(),
)

/**
 * Encrypted-at-rest storage for YouTube Music session cookies and multiple saved accounts.
 */
class AuthStore(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            "velthy_auth",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        Log.w("Velthy", "EncryptedSharedPreferences unavailable, falling back: ${it.message}")
        context.getSharedPreferences("velthy_auth_plain", Context.MODE_PRIVATE)
    }

    init {
        // Automatically migrate authenticated session from legacy "musique_auth" to "velthy_auth" only once
        if (!prefs.getBoolean(KEY_MIGRATED, false)) {
            runCatching {
                val legacyEncrypted = EncryptedSharedPreferences.create(
                    context,
                    "musique_auth",
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                val legacyCookie = legacyEncrypted.getString(KEY_COOKIE, null)
                val legacyAccounts = legacyEncrypted.getString(KEY_SAVED_ACCOUNTS, null)
                if (!legacyCookie.isNullOrBlank() && prefs.getString(KEY_COOKIE, null).isNullOrBlank()) {
                    prefs.edit()
                        .putString(KEY_COOKIE, legacyCookie)
                        .putString(KEY_SAVED_ACCOUNTS, legacyAccounts)
                        .apply()
                    Log.d("Velthy", "Migrated authenticated session from musique_auth to velthy_auth successfully.")
                }
                legacyEncrypted.edit().clear().apply()
            }
            runCatching {
                val legacyPlain = context.getSharedPreferences("musique_auth_plain", Context.MODE_PRIVATE)
                val legacyCookie = legacyPlain.getString(KEY_COOKIE, null)
                val legacyAccounts = legacyPlain.getString(KEY_SAVED_ACCOUNTS, null)
                if (!legacyCookie.isNullOrBlank() && prefs.getString(KEY_COOKIE, null).isNullOrBlank()) {
                    prefs.edit()
                        .putString(KEY_COOKIE, legacyCookie)
                        .putString(KEY_SAVED_ACCOUNTS, legacyAccounts)
                        .apply()
                    Log.d("Velthy", "Migrated session from musique_auth_plain to velthy_auth successfully.")
                }
                legacyPlain.edit().clear().apply()
            }
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
    }

    private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(loadSavedAccounts())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts.asStateFlow()

    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    val isSignedIn: Boolean
        get() = cookie?.contains("SAPISID") == true

    private fun loadSavedAccounts(): List<SavedAccount> {
        val raw = prefs.getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<SavedAccount>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun persistSavedAccounts(list: List<SavedAccount>) {
        _savedAccounts.value = list
        runCatching {
            val raw = json.encodeToString(list)
            prefs.edit().putString(KEY_SAVED_ACCOUNTS, raw).apply()
        }
    }

    fun saveAccount(account: SavedAccount) {
        val current = _savedAccounts.value.toMutableList()
        val existingIndex = current.indexOfFirst {
            it.id == account.id || (it.name == account.name && it.handle == account.handle) || it.cookie == account.cookie
        }
        if (existingIndex >= 0) {
            current[existingIndex] = account
        } else {
            current.add(account)
        }
        persistSavedAccounts(current)
    }

    fun removeAccount(id: String) {
        val updated = _savedAccounts.value.filterNot { it.id == id }
        persistSavedAccounts(updated)
    }

    /** The Discord account's bearer token. See DiscordRPC for why a user token. */
    var discordToken: String?
        get() = prefs.getString(KEY_DISCORD_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DISCORD_TOKEN, value).apply()

    var discordCodeVerifier: String?
        get() = prefs.getString(KEY_DISCORD_CODE_VERIFIER, null)
        set(value) = prefs.edit().putString(KEY_DISCORD_CODE_VERIFIER, value).apply()

    var discordAuthState: String?
        get() = prefs.getString(KEY_DISCORD_AUTH_STATE, null)
        set(value) = prefs.edit().putString(KEY_DISCORD_AUTH_STATE, value).apply()

    fun signOut() {
        prefs.edit()
            .remove(KEY_COOKIE)
            .putBoolean(KEY_MIGRATED, true)
            .apply()
        runCatching {
            appContext.getSharedPreferences("musique_auth_plain", Context.MODE_PRIVATE).edit().clear().apply()
        }
        runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                "musique_auth",
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).edit().clear().apply()
        }
    }

    private companion object {
        const val KEY_COOKIE = "cookie"
        const val KEY_SAVED_ACCOUNTS = "saved_accounts"
        const val KEY_MIGRATED = "legacy_migrated"
        const val KEY_DISCORD_TOKEN = "discord_token"
        const val KEY_DISCORD_CODE_VERIFIER = "discord_code_verifier"
        const val KEY_DISCORD_AUTH_STATE = "discord_auth_state"
    }
}
