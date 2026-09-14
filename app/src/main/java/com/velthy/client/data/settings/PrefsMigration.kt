package com.velthy.client.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time copy of a legacy SharedPreferences file onto its rebranded name.
 *
 * The app was renamed, and the private names it stores data under changed with
 * it. Renaming a preferences file without this would present every existing
 * install as a fresh one — its settings, queue, downloads and saved sources all
 * gone — so the old file is copied across once and the new name is used from
 * then on.
 *
 * The legacy file is deliberately left in place rather than deleted: it is
 * inert, and removing it would throw away the only copy if the new one is ever
 * half-written.
 */
internal fun migrateLegacyPrefs(
    context: Context,
    legacyName: String,
    currentName: String,
): SharedPreferences {
    val current = context.getSharedPreferences(currentName, Context.MODE_PRIVATE)
    if (current.all.isNotEmpty()) return current

    val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
    if (legacy.all.isEmpty()) return current

    current.edit().apply {
        legacy.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }.apply()

    return current
}
