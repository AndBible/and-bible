/*
 * Copyright (c) 2026-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.bookmark

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities

/**
 * Data models for Study Pad content search functionality.
 * These models are used for search results and do not represent database entities.
 */

enum class EntryType {
    TEXT_ENTRY,      // Match from Study Pad text entry
    BOOKMARK_NOTE    // Match from bookmark note
}

data class ContentMatch(
    val entryId: IdType,           // ID of the matching entry
    val entryType: EntryType,      // Type of entry (TEXT_ENTRY or BOOKMARK_NOTE)
    val textSnippet: String,       // Text snippet with context (~100 chars)
    val matchStart: Int,           // Start position of match in snippet
    val matchEnd: Int              // End position of match in snippet
)

data class StudyPadSearchResult(
    val label: BookmarkEntities.Label,    // The Study Pad label
    val matchCount: Int,                  // Total number of matches in this Study Pad
    val matches: List<ContentMatch>       // List of all matches
)

data class StudyPadSearchResultTextSnippet(
    val text: String,
    val matchStart: Int,
    val matchEnd: Int
)
