package ru.ytkab0bp.beamklipper.utils

import org.json.JSONObject

// update.json maps a language code to the changelog text; English is the
// fallback. Null when the file is not valid JSON or has no English entry.
object ChangelogText {
    fun pick(json: String, language: String): String? = runCatching {
        val obj = JSONObject(json)
        if (obj.has(language)) obj.getString(language) else obj.getString("en")
    }.getOrNull()
}
