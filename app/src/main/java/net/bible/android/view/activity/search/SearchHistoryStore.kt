/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.search

import net.bible.service.common.CommonUtils.settings

object SearchHistoryStore {
    private const val BIBLE_SEARCH_HISTORY_KEY = "search_history_bible"
    private const val EPUB_SEARCH_HISTORY_KEY = "search_history_epub"
    private const val ENTRY_SEPARATOR = "\u001F"
    private const val MAX_HISTORY_ITEMS = 20

    fun bibleHistory(): List<String> = readHistory(BIBLE_SEARCH_HISTORY_KEY)
    fun epubHistory(): List<String> = readHistory(EPUB_SEARCH_HISTORY_KEY)

    fun addBibleQuery(query: String) = addQuery(BIBLE_SEARCH_HISTORY_KEY, query)
    fun addEpubQuery(query: String) = addQuery(EPUB_SEARCH_HISTORY_KEY, query)

    private fun addQuery(key: String, query: String) {
        val updated = updateHistory(readHistory(key), query)
        settings.setString(key, updated.takeIf { it.isNotEmpty() }?.joinToString(ENTRY_SEPARATOR))
    }

    private fun readHistory(key: String): List<String> = deserialize(settings.getString(key, null))

    internal fun deserialize(value: String?): List<String> = value
        ?.split(ENTRY_SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    internal fun updateHistory(
        existingHistory: List<String>,
        rawQuery: String,
        maxItems: Int = MAX_HISTORY_ITEMS
    ): List<String> {
        val query = rawQuery.replace(ENTRY_SEPARATOR, " ").trim()
        if (query.isEmpty()) return existingHistory

        val updated = existingHistory.toMutableList()
        updated.removeAll { it == query }
        updated.add(0, query)
        if (updated.size > maxItems) {
            updated.subList(maxItems, updated.size).clear()
        }
        return updated
    }
}
