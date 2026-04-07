/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
    ]
)
data class MyDocumentPage(
    @PrimaryKey val id: IdType = IdType(),
    override val documentId: IdType,
    override var title: String,
    override var pageKey: String,               // Unique key within the document
    override var contentType: MyDocumentContentType = MyDocumentContentType.MARKDOWN,
    override var orderNumber: Int,
    override var createdAt: Long = System.currentTimeMillis(),
    override var updatedAt: Long = System.currentTimeMillis(),
    override var sourcePromptId: IdType? = null,  // Which prompt created this page (null = user-created)
    override var languageCode: String? = null     // Language of the page content (for TTS locale selection)
): BaseMyDocumentPage

/**
 * Page content stored separately (like BookmarkNotes)
 * This optimizes device sync when only metadata has changed
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

interface BaseMyDocumentPage {
    val title: String
    val pageKey: String
    val contentType: MyDocumentContentType
    val documentId: IdType
    val orderNumber: Int
    val createdAt: Long
    val updatedAt: Long
    val sourcePromptId: IdType?
    val languageCode: String?
}

/**
 * Cache metadata for AI-generated pages. Separated from MyDocumentPage to keep
 * page entity clean — cache fields are only relevant for LLM agent response caching.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MyDocumentPage::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = CASCADE
        )
    ],
    indices = [
        Index(value = ["sourcePromptId", "contextHash"]),
        Index(value = ["sourcePromptId", "kjvOrdinalStart", "kjvOrdinalEnd"]),
        Index(value = ["kjvOrdinalStart", "kjvOrdinalEnd"]),
        Index(value = ["sourceBookInitials", "sourceBookKey"])
    ]
)
data class AiPageCacheEntry(
    @PrimaryKey val pageId: IdType,
    val sourcePromptId: IdType,
    var sourceContext: String?,
    var kjvOrdinalStart: Int?,
    var kjvOrdinalEnd: Int?,
    var contextHash: String?,
    var usedWriteTools: Boolean = false,
    var sourceModelName: String? = null,
    /** Book initials of the document where the AI action was triggered (for non-Bible page matching) */
    var sourceBookInitials: String? = null,
    /** Book key (e.g. commentary key) where the AI action was triggered */
    var sourceBookKey: String? = null
)

/**
 * Lightweight projection for AI document markers shown in Bible view.
 * Returned by [MyDocumentDao.aiDocMarkersForRange].
 */
data class AiDocMarkerInfo(
    val pageId: IdType,
    val documentId: IdType,
    val documentInitials: String,
    val pageTitle: String,
    val pageKey: String,
    val kjvOrdinalStart: Int?,
    val kjvOrdinalEnd: Int?,
    val sourcePromptId: IdType?,
    val sourceBookInitials: String?,
    val sourceBookKey: String?,
)

@DatabaseView("""
    SELECT p.*, c.content
    FROM MyDocumentPage p
    LEFT OUTER JOIN MyDocumentPageContent c ON p.id = c.pageId
""")
data class MyDocumentPageWithContent(
    val id: IdType,
    override val documentId: IdType,
    override val title: String,
    override val pageKey: String,
    override val contentType: MyDocumentContentType,
    override val orderNumber: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val sourcePromptId: IdType?,
    override val languageCode: String?,
    val content: String?
): BaseMyDocumentPage

/**
 * DatabaseView for cache lookups: combines AiPageCacheEntry with page metadata and content.
 */
@DatabaseView("""
    SELECT c.pageId, c.sourcePromptId, c.sourceContext, c.kjvOrdinalStart,
           c.kjvOrdinalEnd, c.contextHash, c.usedWriteTools, c.sourceModelName,
           c.sourceBookInitials, c.sourceBookKey,
           p.title, p.pageKey, p.contentType, p.documentId,
           p.orderNumber, p.createdAt, p.updatedAt, p.languageCode, cnt.content
    FROM AiPageCacheEntry c
    INNER JOIN MyDocumentPage p ON c.pageId = p.id
    LEFT OUTER JOIN MyDocumentPageContent cnt ON p.id = cnt.pageId
""")
data class AiCachedPageWithContent(
    val pageId: IdType,
    override val sourcePromptId: IdType,
    val sourceContext: String?,
    val kjvOrdinalStart: Int?,
    val kjvOrdinalEnd: Int?,
    val contextHash: String?,
    val usedWriteTools: Boolean,
    val sourceModelName: String?,
    val sourceBookInitials: String?,
    val sourceBookKey: String?,
    override val title: String,
    override val pageKey: String,
    override val contentType: MyDocumentContentType,
    override val documentId: IdType,
    override val orderNumber: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val languageCode: String?,
    val content: String?
): BaseMyDocumentPage
