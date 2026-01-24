/*
 * Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.mydocument

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import net.bible.android.database.IdType

/**
 * Content type for My Document pages
 */
@Serializable
enum class MyDocumentContentType {
    MARKDOWN,   // Default - Markdown (can contain sword://, osis://, ab-w:// links)
    HTML,       // HTML fragment (when more control needed than MD provides)
    OSIS        // OSIS XML fragment (preserves original formatting)
}

/**
 * A user or AI-created document that is registered as a GenBook with JSword
 */
@Entity(
    indices = [
        Index(value = ["initials"], unique = true)
    ]
)
data class MyDocument(
    @PrimaryKey val id: IdType = IdType(),
    var name: String,
    var description: String? = null,
    var initials: String,              // JSword initials, e.g., "MyDoc_abc123" (generated)
    var orderNumber: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var sourcePromptId: IdType? = null // Which prompt created this document (null = user-created)
)

/**
 * A page within a My Document (represents a TOC entry)
 * Content is stored separately in MyDocumentPageContent for query optimization
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MyDocument::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "pageKey"], unique = true),
        // Cache lookup indices
        Index(value = ["sourcePromptId", "contextHash"]),  // strict=true lookup
        Index(value = ["sourcePromptId", "kjvOrdinalStart", "kjvOrdinalEnd"])  // strict=false lookup
    ]
)
data class MyDocumentPage(
    @PrimaryKey val id: IdType = IdType(),
    val documentId: IdType,
    var title: String,
    var pageKey: String,               // Unique key within the document
    var contentType: MyDocumentContentType = MyDocumentContentType.MARKDOWN,
    var orderNumber: Int,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var sourcePromptId: IdType? = null,  // Which prompt created this page (null = user-created)
    var sourceContext: String? = null,   // JSON-serialized context (for debug/display)
    // Cache fields
    var kjvOrdinalStart: Int? = null,    // KJVA verse ordinal start (for cross-version cache lookup)
    var kjvOrdinalEnd: Int? = null,      // KJVA verse ordinal end
    var contextHash: String? = null,     // SHA-256 hash of full context (for strict matching)
    var usedWriteTools: Boolean = false  // Whether the agent used write tools (bookmarks, notes, etc.)
)

/**
 * Page content stored separately (like BookmarkNotes)
 * This optimizes queries when only metadata is needed
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MyDocumentPage::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = CASCADE
        )
    ]
)
data class MyDocumentPageContent(
    @PrimaryKey val pageId: IdType,
    var content: String
)

/**
 * DatabaseView combining page metadata with content
 */
@DatabaseView("""
    SELECT p.*, c.content
    FROM MyDocumentPage p
    LEFT OUTER JOIN MyDocumentPageContent c ON p.id = c.pageId
""")
data class MyDocumentPageWithContent(
    val id: IdType,
    val documentId: IdType,
    val title: String,
    val pageKey: String,
    val contentType: MyDocumentContentType,
    val orderNumber: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val sourcePromptId: IdType?,
    val sourceContext: String?,
    val kjvOrdinalStart: Int?,
    val kjvOrdinalEnd: Int?,
    val contextHash: String?,
    val usedWriteTools: Boolean,
    val content: String?
)
