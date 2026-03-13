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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import net.bible.android.database.IdType

@Dao
interface MyDocumentDao {
    // ==================== Documents ====================

    @Insert
    fun insert(document: MyDocument)

    @Update
    fun update(document: MyDocument)

    @Delete
    fun delete(document: MyDocument)

    @Query("SELECT * FROM MyDocument ORDER BY orderNumber ASC")
    fun allDocuments(): List<MyDocument>

    @Update
    fun updateDocuments(documents: List<MyDocument>)

    @Query("SELECT * FROM MyDocument WHERE id = :id")
    fun documentById(id: IdType): MyDocument?

    @Query("SELECT * FROM MyDocument WHERE initials = :initials")
    fun documentByInitials(initials: String): MyDocument?

    @Query("SELECT * FROM MyDocument WHERE name = :name")
    fun documentByName(name: String): MyDocument?

    // ==================== Pages (metadata only) ====================

    @Insert
    fun insert(page: MyDocumentPage)

    @Update
    fun update(page: MyDocumentPage)

    @Update
    fun updatePages(pages: List<MyDocumentPage>)

    @Delete
    fun delete(page: MyDocumentPage)

    @Query("SELECT * FROM MyDocumentPage WHERE documentId = :documentId ORDER BY orderNumber")
    fun pagesForDocument(documentId: IdType): List<MyDocumentPage>

    @Query("SELECT * FROM MyDocumentPage WHERE id = :id")
    fun pageById(id: IdType): MyDocumentPage?

    @Query("SELECT * FROM MyDocumentPage WHERE documentId = :documentId AND pageKey = :pageKey")
    fun pageByKey(documentId: IdType, pageKey: String): MyDocumentPage?

    @Query("SELECT COUNT(*) FROM MyDocumentPage WHERE documentId = :documentId")
    fun pageCount(documentId: IdType): Int

    @Query("SELECT MAX(orderNumber) FROM MyDocumentPage WHERE documentId = :documentId")
    fun maxOrderNumber(documentId: IdType): Int?

    // ==================== Page content (separate table) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateContent(content: MyDocumentPageContent)

    @Query("DELETE FROM MyDocumentPageContent WHERE pageId = :pageId")
    fun deleteContent(pageId: IdType)

    @Query("SELECT content FROM MyDocumentPageContent WHERE pageId = :pageId")
    fun getContent(pageId: IdType): String?

    // ==================== Combined view (with content) ====================

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE documentId = :documentId ORDER BY orderNumber")
    fun pagesWithContentForDocument(documentId: IdType): List<MyDocumentPageWithContent>

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE id = :id")
    fun pageByIdWithContent(id: IdType): MyDocumentPageWithContent?

    @Query("SELECT * FROM MyDocumentPageWithContent WHERE documentId = :documentId AND pageKey = :pageKey")
    fun pageByKeyWithContent(documentId: IdType, pageKey: String): MyDocumentPageWithContent?

    // ==================== Cache lookup ====================

    /**
     * Find cached page by full context hash (strict matching).
     * Used when strictContextMatching=true.
     */
    @Query("""
        SELECT * FROM MyDocumentPageWithContent
        WHERE sourcePromptId = :promptId
        AND contextHash = :contextHash
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    fun findCachedPageByContextHash(
        promptId: IdType,
        contextHash: String
    ): MyDocumentPageWithContent?

    /**
     * Find cached page by verse range only (loose matching).
     * Used when strictContextMatching=false.
     */
    @Query("""
        SELECT * FROM MyDocumentPageWithContent
        WHERE sourcePromptId = :promptId
        AND kjvOrdinalStart = :kjvOrdinalStart
        AND kjvOrdinalEnd = :kjvOrdinalEnd
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    fun findCachedPageByVerseRange(
        promptId: IdType,
        kjvOrdinalStart: Int,
        kjvOrdinalEnd: Int
    ): MyDocumentPageWithContent?

    // ==================== Transaction helpers ====================

    @Transaction
    fun insertPageWithContent(page: MyDocumentPage, content: String) {
        insert(page)
        insertOrUpdateContent(MyDocumentPageContent(pageId = page.id, content = content))
    }

    @Transaction
    fun updatePageWithContent(page: MyDocumentPage, content: String) {
        update(page)
        insertOrUpdateContent(MyDocumentPageContent(pageId = page.id, content = content))
    }

    @Transaction
    fun deletePageWithContent(page: MyDocumentPage) {
        deleteContent(page.id)
        delete(page)
    }

    @Transaction
    fun deleteDocumentWithPages(document: MyDocument) {
        // Foreign key CASCADE will handle pages and their content
        delete(document)
    }
}
