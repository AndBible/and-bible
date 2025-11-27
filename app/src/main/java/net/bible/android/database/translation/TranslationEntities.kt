/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.translation

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    primaryKeys = ["documentInitials", "keyName", "targetLanguage", "modelId"],
    indices = [
        Index("targetLanguage"),
        Index("createdAt"),
        Index("lastAccessedAt")
    ]
)
data class TranslationCacheEntry(
    val documentInitials: String,  // e.g., "KJV", "ESV"
    val keyName: String,           // e.g., "Gen.1", "Matt.5"
    val targetLanguage: String,    // e.g., "fi", "en"
    val modelId: String,           // e.g., "gpt-4o-mini"
    val translatedXml: String,
    val createdAt: Long,
    val lastAccessedAt: Long
)
