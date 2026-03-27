/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.llm.tools

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.mydocument.MyDocument
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.llmJson
import org.crosswire.jsword.passage.PassageKeyFactory
import org.json.JSONArray
import org.json.JSONObject

/** Decode tool arguments from JSONObject to a typed @Serializable data class. */
inline fun <reified T> JSONObject.decodeArgs(): T =
    llmJson.decodeFromString<T>(this.toString())

/**
 * Strip markdown formatting from a title string.
 * Converts `[text](url)` links to just `text`, removes bold/italic markers, etc.
 * LLMs sometimes put markdown links in titles despite being told not to.
 */
private val markdownLinkRegex = Regex("""\[([^\]]*)\]\([^)]*\)""")
fun stripMarkdownFromTitle(title: String): String = title
    .replace(markdownLinkRegex, "$1")
    .replace("**", "")
    .replace("__", "")
    .replace("*", "")
    .replace("_", "")
    .replace("`", "")
    .replace("#", "")
    .trim()

/**
 * Normalize text content from LLM output.
 *
 * Some LLM providers return literal "\n" (backslash + n) in their JSON tool call
 * arguments instead of actual newline characters. This happens due to double-escaping
 * in the JSON arguments string. This function converts those literal escape sequences
 * back to actual characters.
 */
fun normalizeLlmText(text: String): String = text
    .replace("\\n", "\n")
    .replace("\\t", "\t")

/**
 * Generic fallback formatter for JSON strings in the agent log.
 * Parses as JSONObject and outputs key: value pairs.
 * Unwraps the ToolResult `{"status":"...","data":{...}}` wrapper automatically.
 * Truncates long values to keep the log readable.
 */
fun formatJsonForLog(json: String): String = try {
    val obj = JSONObject(json)
    // Unwrap ToolResult wrapper: format "data" contents directly
    val target = if (obj.has("status") && obj.has("data")) {
        when (val data = obj.opt("data")) {
            is JSONObject -> data
            is JSONArray -> return "${data.length()} items"
            else -> return data?.toString() ?: json
        }
    } else {
        obj
    }
    formatJsonObjectForLog(target)
} catch (_: Exception) {
    json
}

private fun formatJsonObjectForLog(obj: JSONObject): String =
    obj.keys().asSequence()
        .filter { it != "status" }
        .map { key ->
            val value = obj.opt(key)
            val str = when (value) {
                is JSONObject -> "{...}"
                is JSONArray -> "${value.length()} items"
                else -> value?.toString() ?: "null"
            }
            val truncated = if (str.length > 80) str.take(80) + "..." else str
            "$key: $truncated"
        }
        .joinToString(", ")

/**
 * Localize an OSIS verse reference to a human-readable name using JSword.
 * Falls back to the original reference on any error.
 */
fun localizeVerseRef(osisRef: String): String = try {
    PassageKeyFactory.instance().getKey(KJVA, osisRef).name
} catch (_: Exception) {
    osisRef
}

/**
 * Shorten a UUID string for display (first 8 chars).
 */
fun shortId(id: String): String = if (id.length > 8) id.take(8) + "..." else id

/**
 * Generate a unique label name by appending a numeric suffix if the name already exists.
 * E.g. "Study" → "Study (2)" → "Study (3)" etc.
 */
fun uniqueLabelName(baseName: String, existingNames: List<String>): String {
    val nameSet = existingNames.map { it.lowercase() }.toSet()
    if (baseName.lowercase() !in nameSet) return baseName
    var suffix = 2
    while ("$baseName ($suffix)".lowercase() in nameSet) suffix++
    return "$baseName ($suffix)"
}

/**
 * Resolve a MyDocument by ID or initials.
 * Tries documentId first, then falls back to initials lookup.
 *
 * @return The resolved MyDocument, or null if neither identifier matches
 */
fun resolveMyDocument(documentId: IdType?, initials: String?): MyDocument? {
    val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
    if (documentId != null && !documentId.isEmpty) {
        return dao.documentById(documentId)
    }
    if (!initials.isNullOrBlank()) {
        return dao.documentByInitials(initials)
    }
    return null
}
