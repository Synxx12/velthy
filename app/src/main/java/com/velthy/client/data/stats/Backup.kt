package com.velthy.client.data.stats

import android.content.Context
import android.net.Uri
import com.velthy.client.BuildConfig
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.SearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole of what this app knows about you, as one JSON file you own.
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun suggestedName(): String =
        "velthy-backup-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    suspend fun exportTo(context: Context, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val buckets = ListeningStats.exportAll()
            val file = BackupFile(
                versionName = BuildConfig.VERSION_NAME,
                exportedAt = Instant.now().toString(),
                settings = AppSettings.exportPrefs().mapNotNull { (key, value) ->
                    PrefValue.of(value)?.let { key to it }
                }.toMap(),
                listening = buckets,
            )
            val text = json.encodeToString(BackupFile.serializer(), file)
            context.contentResolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            buckets.size
        }
    }

    suspend fun importFrom(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open that file")
            val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { error("That doesn't look like a Velthy backup") }
            require(file.app == APP_TAG || file.app == "bitchord") { "That backup is from another app" }
            require(file.version <= SCHEMA_VERSION) {
                "That backup was written by a newer version of Velthy"
            }

            ListeningStats.importAll(file.listening)
            AppSettings.importPrefs(file.settings.mapValues { it.value.decoded() })
            SearchHistory.reload()
            Summary(
                months = file.listening.size,
                settings = file.settings.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    data class Summary(
        val months: Int,
        val settings: Int,
        val from: String,
        val at: String,
    )

    private const val APP_TAG = "velthy"
    private const val SCHEMA_VERSION = 1

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val version: Int = SCHEMA_VERSION,
        val versionName: String = "",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue> = emptyMap(),
        val listening: List<StoredBucket> = emptyList(),
    )

    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        fun decoded(): Any? = when (type) {
            BOOLEAN -> value?.toBooleanStrictOrNull()
            INT -> value?.toIntOrNull()
            LONG -> value?.toLongOrNull()
            FLOAT -> value?.toFloatOrNull()
            STRING -> value
            STRING_SET -> values.toSet()
            else -> null
        }

        companion object {
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue(BOOLEAN, value.toString())
                is Int -> PrefValue(INT, value.toString())
                is Long -> PrefValue(LONG, value.toString())
                is Float -> PrefValue(FLOAT, value.toString())
                is String -> PrefValue(STRING, value)
                is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
                else -> null
            }

            private const val BOOLEAN = "bool"
            private const val INT = "int"
            private const val LONG = "long"
            private const val FLOAT = "float"
            private const val STRING = "string"
            private const val STRING_SET = "stringSet"
        }
    }
}
