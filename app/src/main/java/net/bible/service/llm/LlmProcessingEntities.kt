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

package net.bible.service.llm

import androidx.room.Entity
import androidx.room.Index

/**
 * Cache entry for LLM-processed document content.
 *
 * Supports multiple processing types (translation, summarization, etc.)
 * through the processingType and processingParams fields.
 */
@Entity(
    tableName = "LlmProcessingCacheEntry",
    primaryKeys = ["documentInitials", "keyName", "processingType", "processingParams"],
    indices = [
        Index("processingType"),
        Index("modelId")
    ]
)
data class LlmProcessingCacheEntry(
    val documentInitials: String,   // e.g., "KJV", "ESV"
    val keyName: String,            // e.g., "Gen.1", "Matt.5"
    val processingType: String,     // e.g., "translations", "summaries"
    val processingParams: String,   // e.g., "fi" for translation, "short" for summary
    val modelId: String,            // e.g., "gpt-4o-mini" (informational only)
    val processedXml: String,       // The processed content
    val createdAt: Long,
    val languageCode: String? = null // Language of the processed output (e.g., "fi", "en")
)

data class CacheEntrySummary(
    val documentInitials: String,
    val keyName: String,
    val processingType: String,
    val processingParams: String,
    val modelId: String,
    val createdAt: Long,
    val languageCode: String?,
    val xmlSize: Int
)

data class CacheStats(
    val entryCount: Int,
    val totalSize: Long
)
