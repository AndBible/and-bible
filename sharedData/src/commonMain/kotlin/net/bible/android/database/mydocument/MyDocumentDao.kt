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

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import net.bible.android.database.IdType

@Dao
interface MyDocumentDao {
    // ==================== Documents ====================

    @Insert
    suspend fun insert(document: MyDocument)

    @Update
    suspend fun update(document: MyDocument)

    @Delete
    suspend fun delete(document: MyDocument)

    @Query("SELECT * FROM MyDocument ORDER BY orderNumber ASC")
    suspend fun allDocuments(): List<MyDocument>

    @Update
    suspend fun updateDocuments(documents: List<MyDocument>)

    @Query("SELECT * FROM MyDocument WHERE id = :id")
    suspend fun documentById(id: IdType): MyDocument?

    @Query("SELECT * FROM MyDocument WHERE initials = :initials")
    suspend fun documentByInitials(initials: String): MyDocument?

    @Query("SELECT * FROM MyDocument WHERE name = :name")
    suspend fun documentByName(name: String): MyDocument?

    @Query("SELECT MAX(orderNumber) FROM MyDocument")
    suspend fun maxDocumentOrderNumber(): Int?

    @Query("SELECT DISTINCT initials FROM MyDocument WHERE id IN (:ids)")
    suspend fun initialsByIds(ids: List<IdType>): List<String>

    @Query("SELECT DISTINCT d.initials FROM MyDocumentPage p JOIN MyDocument d ON p.documentId = d.id WHERE p.id IN (:pageIds)")
    suspend fun initialsByPageIds(pageIds: List<IdType>): List<String>

    // ==================== Pages (metadata only) ====================

    @Insert
    suspend fun insert(page: MyDocumentPage)

    @Update
    suspend fun update(page: MyDocumentPage)

    @Update
    suspend fun updatePages(pages: List<MyDocumentPage>)

    @Delete
    suspend fun delete(page: MyDocumentPage)

    @Query("SELECT * FROM MyDocumentPage WHERE documentId = :documentId ORDER BY orderNumber")
    suspend fun pagesForDocument(documentId: IdType): List<MyDocumentPage>

    @Query("SELECT * FROM MyDocumentPage WHERE id = :id")
    suspend fun pageById(id: IdType): MyDocumentPage?

    @Query("SELECT * FROM MyDocumentPage WHERE documentId = :documentId AND pageKey = :pageKey")
    suspend fun pageByKey(documentId: IdType, pageKey: String): MyDocumentPage?

    @Query("SELECT COUNT(*) FROM MyDocumentPage WHERE documentId = :documentId")
    suspend fun pageCount(documentId: IdType): Int

    @Query("SELECT MAX(orderNumber) FROM MyDocumentPage WHERE documentId = :documentId")
    suspend fun maxOrderNumber(documentId: IdType): Int?

    // ==================== Page content (separate table) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContent(content: MyDocumentPageContent)

    @Query("DELETE FROM MyDocumentPageContent WHERE pageId = :pageId")
    suspend fun deleteContent(pageId: IdType)

    @Query("SELECT content FROM MyDocumentPageContent WHERE pageId = :pageId")
    suspend fun getContent(pageId: IdType): String?

    // ==================== Combined view (with content) ====================

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE documentId = :documentId ORDER BY orderNumber")
    suspend fun pagesWithContentForDocument(documentId: IdType): List<MyDocumentPageWithContent>

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE id = :id")
    suspend fun pageByIdWithContent(id: IdType): MyDocumentPageWithContent?

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE documentId = :documentId AND pageKey = :pageKey")
    suspend fun pageByKeyWithContent(documentId: IdType, pageKey: String): MyDocumentPageWithContent?

    // ==================== AI page cache ====================

    @Insert
    suspend fun insert(cacheEntry: AiPageCacheEntry)

    @Query("SELECT * FROM AiPageCacheEntry WHERE pageId = :pageId")
    suspend fun getCacheEntry(pageId: IdType): AiPageCacheEntry?

    /**
     * Find cached page by full context hash (strict matching).
     * Used when strictContextMatching=true.
     */
    @Query("""
        SELECT * FROM AiCachedPageWithContent
        WHERE sourcePromptId = :promptId
        AND contextHash = :contextHash
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun findCachedPageByContextHash(
        promptId: IdType,
        contextHash: String
    ): AiCachedPageWithContent?

    /**
     * Find cached page by verse range only (loose matching).
     * Used when strictContextMatching=false.
     */
    @Query("""
        SELECT * FROM AiCachedPageWithContent
        WHERE sourcePromptId = :promptId
        AND kjvOrdinalStart = :kjvOrdinalStart
        AND kjvOrdinalEnd = :kjvOrdinalEnd
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun findCachedPageByVerseRange(
        promptId: IdType,
        kjvOrdinalStart: Int,
        kjvOrdinalEnd: Int
    ): AiCachedPageWithContent?

    // ==================== AI doc markers (verse-range lookup) ====================

    /**
     * Find AI document pages whose KJV ordinal range overlaps the given range.
     * Used to show AI doc marker icons in Bible view.
     */
    @Query("""
        SELECT c.pageId, p.documentId, d.initials AS documentInitials,
               p.title AS pageTitle, p.pageKey, c.kjvOrdinalStart, c.kjvOrdinalEnd,
               c.sourcePromptId, c.sourceBookInitials, c.sourceBookKey
        FROM AiPageCacheEntry c
        INNER JOIN MyDocumentPage p ON c.pageId = p.id
        INNER JOIN MyDocument d ON p.documentId = d.id
        WHERE c.kjvOrdinalStart IS NOT NULL AND c.kjvOrdinalEnd IS NOT NULL
          AND c.kjvOrdinalStart <= :rangeEnd AND c.kjvOrdinalEnd >= :rangeStart
    """)
    suspend fun aiDocMarkersForRange(rangeStart: Int, rangeEnd: Int): List<AiDocMarkerInfo>

    /**
     * Find AI document pages created from a specific non-Bible document page.
     * Used to show AI doc marker icons on commentary and other non-Bible pages.
     */
    @Query("""
        SELECT c.pageId, p.documentId, d.initials AS documentInitials,
               p.title AS pageTitle, p.pageKey, c.kjvOrdinalStart, c.kjvOrdinalEnd,
               c.sourcePromptId, c.sourceBookInitials, c.sourceBookKey
        FROM AiPageCacheEntry c
        INNER JOIN MyDocumentPage p ON c.pageId = p.id
        INNER JOIN MyDocument d ON p.documentId = d.id
        WHERE c.sourceBookInitials = :bookInitials AND c.sourceBookKey = :bookKey
    """)
    suspend fun aiDocMarkersForPage(bookInitials: String, bookKey: String): List<AiDocMarkerInfo>

    // ==================== Transaction helpers ====================

    @Transaction
    suspend fun insertPageWithContent(page: MyDocumentPage, content: String) {
        insert(page)
        insertOrUpdateContent(MyDocumentPageContent(pageId = page.id, content = content))
    }

    @Transaction
    suspend fun insertPageWithCacheEntry(page: MyDocumentPage, content: String, cacheEntry: AiPageCacheEntry) {
        insert(page)
        insertOrUpdateContent(MyDocumentPageContent(pageId = page.id, content = content))
        insert(cacheEntry)
    }

    @Transaction
    suspend fun updatePageWithContent(page: MyDocumentPage, content: String) {
        update(page)
        insertOrUpdateContent(MyDocumentPageContent(pageId = page.id, content = content))
    }

    @Transaction
    suspend fun deletePageWithContent(page: MyDocumentPage) {
        // Content is deleted automatically via FK CASCADE
        delete(page)
    }

    @Transaction
    suspend fun deleteDocumentWithPages(document: MyDocument) {
        // Foreign key CASCADE will handle pages and their content
        delete(document)
    }
}
