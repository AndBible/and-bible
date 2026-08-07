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

package net.bible.service.sword.mydocument

import android.util.Log
import kotlinx.serialization.Serializable
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.mydocument.AiDocMarkerInfo
import net.bible.android.database.mydocument.AiPageCacheEntry
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.MyDocumentsUpdatedViaSyncEvent
import net.bible.service.llm.agent.CacheableContext
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import java.util.Locale

private const val TAG = "MyDocumentBookManager"

/**
 * Event posted when a MyDocument is updated (pages added/removed).
 * Used to invalidate caches that depend on the document's key list.
 */
class MyDocumentUpdatedEvent(val initials: String)

/**
 * Event posted when AI document pages are created, updated, or deleted.
 * BibleView listens for this to refresh AI doc marker icons in the Bible text.
 *
 * For adds/updates: [markers] contains the current markers for the affected range.
 * For deletes: [deletedPageIds] contains the IDs of removed pages.
 */
class AiDocPagesChangedEvent(
    val markers: List<AiDocMarkerInfo> = emptyList(),
    val deletedPageIds: List<IdType> = emptyList(),
    /** Source book initials for non-Bible page markers (commentary, etc.) */
    val sourceBookInitials: String? = null,
    /** Source book key for non-Bible page markers */
    val sourceBookKey: String? = null,
)

/**
 * Extension property to check if a book is a MyDocument.
 */
val Book.isMyDocument: Boolean
    get() = bookMetaData.getProperty("AndBibleMyDocument") != null

/**
 * Extension property to get the MyDocument ID for a book.
 */
val Book.myDocumentId: IdType?
    get() = bookMetaData.getProperty("AndBibleMyDocumentId")?.let { IdType(it) }

/**
 * Extension property to check if a MyDocument is the AI Documents.
 */
val MyDocument.isAIDocument: Boolean
    get() = initials == MyDocumentBookManager.AI_DOCUMENTS_INITIALS

/**
 * Singleton manager for MyDocument books.
 * Handles registration/unregistration with JSword's Books.
 */
object MyDocumentBookManager {
    private val registeredBooks = mutableMapOf<String, SwordGenBook>()

    /** Special initials for the AI Documents default document */
    const val AI_DOCUMENTS_INITIALS = "AIDocuments"

    val registeredInitials: Set<String>
        get() = registeredBooks.keys.toSet()

    init {
        ABEventBus.register(this)
    }

    /**
     * Handle sync event: bring registrations in line with the database and
     * refresh only the BibleView windows that display documents affected by
     * the sync.
     *
     * Must run on the main thread (onEventMainThread) because SwordGenBook
     * and the JSword Activator are not thread-safe. Running the registration
     * refresh on a background thread causes a race condition where the main
     * thread sees a newly registered book whose internal key map hasn't been
     * activated yet, leading to NPE in getKey().
     */
    fun onEventMainThread(e: MyDocumentsUpdatedViaSyncEvent) {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val affectedInitials = mutableSetOf<String>()
        var refreshAll = false

        val documentIds = mutableListOf<IdType>()
        val pageIds = mutableListOf<IdType>()

        for (entry in e.updated) {
            when (entry.tableName) {
                "MyDocument" -> documentIds.add(entry.entityId1)
                "MyDocumentPage" -> {
                    pageIds.add(entry.entityId1)
                    // Deleted pages are already gone from DB — can't resolve parent document
                    if (entry.type == LogEntryTypes.DELETE) refreshAll = true
                }
                "MyDocumentPageContent", "AiPageCacheEntry" -> {
                    pageIds.add(entry.entityId1)
                }
            }
        }

        if (documentIds.isNotEmpty()) {
            affectedInitials.addAll(dao.initialsByIds(documentIds))
        }
        if (pageIds.isNotEmpty()) {
            affectedInitials.addAll(dao.initialsByPageIds(pageIds))
        }

        refreshRegistrations()

        val initialsToRefresh = if (refreshAll) registeredInitials else affectedInitials
        for (initials in initialsToRefresh) {
            SwordContentFacade.evictBook(initials)
            ABEventBus.post(MyDocumentUpdatedEvent(initials))
        }
        Log.i(TAG, "Sync update: refreshed ${initialsToRefresh.size} MyDocuments (refreshAll=$refreshAll)")
    }

    /**
     * Register all MyDocuments from the database.
     * Called at app startup.
     */
    fun registerAllDocuments() {
        Log.i(TAG, "Registering all MyDocuments")
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val documents = dao.allDocuments()

        for (document in documents) {
            registerDocument(document)
        }
        Log.i(TAG, "Registered ${documents.size} MyDocuments")
    }

    /**
     * Bring the registered books in line with the database after an external
     * change (currently: cloud sync).
     *
     * Books that are still present are re-activated in place rather than
     * replaced. Windows, the history stack and CurrentPage all hold direct
     * references to the SwordGenBook instance they were opened with, so
     * swapping in a fresh instance leaves those references pointing at a book
     * that has been removed from Books.installed() and deactivated. Keeping
     * the instance and refreshing its key map keeps every existing reference
     * valid. This mirrors what [refreshDocument] does for local edits.
     *
     * A document whose name changed cannot be refreshed in place because the
     * name is baked into the SWORD conf, so those are re-created.
     */
    private fun refreshRegistrations() {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val documents = dao.allDocuments()
        val currentInitials = documents.map { it.initials }.toSet()

        for (initials in registeredBooks.keys.toList()) {
            if (initials !in currentInitials) {
                unregisterDocument(initials)
            }
        }

        for (document in documents) {
            val book = registeredBooks[document.initials]
            when {
                book == null -> registerDocument(document)
                book.bookMetaData.name != document.name -> {
                    unregisterDocument(document.initials)
                    registerDocument(document)
                }
                else -> reactivate(book)
            }
        }
        Log.i(TAG, "Refreshed registrations: ${documents.size} MyDocuments")
    }

    /**
     * Rebuild a book's internal key map from the current database contents.
     *
     * The map is built once in SwordGenBook.activate() and cached until the
     * book is deactivated, so it must be explicitly rebuilt whenever pages are
     * added, removed or renamed. Otherwise getKey() throws NoSuchKeyException
     * for pages that do exist.
     */
    private fun reactivate(book: SwordGenBook) {
        Activator.deactivate(book)
        Activator.activate(book)
    }

    /**
     * Register a single MyDocument with JSword.
     */
    fun registerDocument(document: MyDocument): SwordGenBook? {
        // Check if already registered
        if (registeredBooks.containsKey(document.initials)) {
            Log.d(TAG, "Document already registered: ${document.initials}")
            return registeredBooks[document.initials]
        }

        // Check if already in Books.installed()
        val existing = Books.installed().getBook(document.initials)
        if (existing != null) {
            Log.d(TAG, "Document already in Books.installed(): ${document.initials}")
            return existing as? SwordGenBook
        }

        try {
            val book = createMyDocumentBook(document)
            Books.installed().addBook(book)
            registeredBooks[document.initials] = book
            Log.i(TAG, "Registered MyDocument: ${document.initials}")
            return book
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MyDocument: ${document.initials}", e)
            return null
        }
    }

    /**
     * Unregister a MyDocument from JSword.
     */
    fun unregisterDocument(initials: String) {
        val book = registeredBooks.remove(initials)
        if (book != null) {
            try {
                // Deactivate before removing so that Activator's internal set
                // doesn't retain a stale entry. Without this, a newly registered
                // book with the same initials would match via equals/hashCode,
                // causing Activator.activate() to skip activation and leaving
                // SwordGenBook's internal map null.
                Activator.deactivate(book)
                Books.installed().removeBook(book)
                Log.i(TAG, "Unregistered MyDocument: $initials")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister MyDocument: $initials", e)
            }
        }
    }

    /**
     * Refresh a MyDocument's registration (e.g., after adding/removing pages).
     * This re-creates the book with updated TOC.
     */
    fun refreshDocument(initials: String) {
        val book = registeredBooks[initials]
        if (book != null) {
            reactivate(book)
        } else {
            // Book not registered yet — register it
            val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
            val document = dao.documentByInitials(initials) ?: return
            registerDocument(document)
        }

        // Notify listeners to invalidate their caches
        ABEventBus.post(MyDocumentUpdatedEvent(initials))
    }

    /**
     * Get a registered MyDocument book by initials.
     */
    fun getBook(initials: String): SwordGenBook? {
        return registeredBooks[initials]
    }

    /**
     * Generate unique initials for a new MyDocument.
     * Checks Books.installed(), registeredBooks, and the database.
     */
    fun generateInitials(baseName: String): String {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val sanitized = baseName
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(10)
            .ifEmpty { "MyDoc" }

        var initials = "MyDoc_$sanitized"
        var counter = 1

        fun exists(initials: String): Boolean =
            Books.installed().getBook(initials) != null ||
            registeredBooks.containsKey(initials) ||
            dao.documentByInitials(initials) != null

        while (exists(initials)) {
            initials = "MyDoc_${sanitized}_$counter"
            counter++
        }

        return initials
    }

    /**
     * Clear all registered books (for testing).
     */
    fun clear() {
        for (initials in registeredBooks.keys.toList()) {
            unregisterDocument(initials)
        }
    }

    /**
     * Get or create the AI Documents default document.
     * This document is automatically created when AI-generated content needs to be saved.
     */
    fun getOrCreateAIDocument(): MyDocument {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

        // Check if AI Documents already exists
        val existing = dao.documentByInitials(AI_DOCUMENTS_INITIALS)
        if (existing != null) {
            return existing
        }

        // Create new AI Documents
        val aiDocument = MyDocument(
            name = "AI Documents",
            description = "Automatically generated documents from AI",
            initials = AI_DOCUMENTS_INITIALS,
            orderNumber = 0  // Always first in the list
        )
        dao.insert(aiDocument)
        registerDocument(aiDocument)

        // Shift other documents' order numbers
        val otherDocs = dao.allDocuments().filter { it.id != aiDocument.id }
        otherDocs.forEachIndexed { index, doc -> doc.orderNumber = index + 1 }
        dao.updateDocuments(otherDocs)

        Log.i(TAG, "Created AI Documents: $AI_DOCUMENTS_INITIALS")
        return aiDocument
    }

    /**
     * Check if a document can be deleted.
     * AI Documents cannot be deleted if it contains pages.
     */
    fun canDeleteDocument(document: MyDocument): Boolean {
        if (!document.isAIDocument) {
            return true
        }
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        return dao.pageCount(document.id) == 0
    }

    /**
     * Get an AI document page by ID.
     */
    fun getAIDocumentPage(pageId: IdType): MyDocumentPage? {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        return dao.pageById(pageId)
    }

    /**
     * Delete an AI document page.
     *
     * @param pageId ID of the page to delete
     * @return true if the page was deleted, false if not found
     */
    fun deleteAIDocumentPage(pageId: IdType): Boolean {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = dao.pageById(pageId) ?: return false
        dao.deletePageWithContent(page)
        refreshDocument(AI_DOCUMENTS_INITIALS)
        ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = listOf(pageId)))
        Log.i(TAG, "Deleted AI document page: $pageId")
        return true
    }

    @Serializable
    data class PageRawContent(
        val pageId: String,
        val contentType: String,
        val content: String,
        val title: String,
        val sourcePromptId: String?
    )

    /**
     * Get raw content for a page (for editing in WebView).
     */
    fun getPageRawContent(initials: String, pageKey: String): PageRawContent? {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val document = dao.documentByInitials(initials) ?: return null
        val page = dao.pageByKeyWithContent(document.id, pageKey) ?: return null
        return PageRawContent(
            pageId = page.id.toString(),
            contentType = page.contentType.name,
            content = page.content ?: "",
            title = page.title,
            sourcePromptId = page.sourcePromptId?.toString()
        )
    }

    /**
     * Save page content without refreshing the document registration.
     * Call refreshDocument separately after editing is complete.
     */
    fun savePageContent(pageId: IdType, content: String, title: String?) {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = dao.pageById(pageId) ?: return
        if (title != null) {
            page.title = title
            page.updatedAt = System.currentTimeMillis()
            dao.update(page)
        }
        dao.insertOrUpdateContent(MyDocumentPageContent(pageId = pageId, content = content))
    }

    /**
     * Create a new page in a document.
     */
    fun createPage(documentId: IdType, title: String, contentType: MyDocumentContentType): MyDocumentPage {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val pageId = IdType()
        val page = MyDocumentPage(
            id = pageId,
            documentId = documentId,
            title = title,
            pageKey = "page_${pageId}",
            contentType = contentType,
            orderNumber = (dao.maxOrderNumber(documentId) ?: -1) + 1,
            languageCode = Locale.getDefault().language
        )
        dao.insertPageWithContent(page, "")

        val document = dao.documentById(documentId)
        if (document != null) {
            refreshDocument(document.initials)
        }
        return page
    }

    /**
     * Data class containing information about a saved AI response page.
     */
    data class SavedPageInfo(
        val documentInitials: String,
        val pageKey: String
    )

    /**
     * Save an AI response as a new page in the AI Documents.
     *
     * @param response The LLM response content (markdown)
     * @param title Title for the page
     * @param sourcePromptId ID of the prompt that generated this response
     * @param cacheableContext Context data for cache key computation
     * @param usedWriteTools Whether the agent used write tools (bookmarks, notes, etc.)
     * @return Information about the saved page
     */
    fun saveAIResponse(
        response: String,
        title: String,
        sourcePromptId: IdType,
        cacheableContext: CacheableContext,
        usedWriteTools: Boolean = false,
        sourceModelName: String? = null
    ): SavedPageInfo {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val aiDocument = getOrCreateAIDocument()

        val pageId = IdType()
        val page = MyDocumentPage(
            id = pageId,
            documentId = aiDocument.id,
            title = title,
            pageKey = "ai_${pageId}",
            contentType = MyDocumentContentType.MARKDOWN,
            orderNumber = (dao.maxOrderNumber(aiDocument.id) ?: -1) + 1,
            sourcePromptId = sourcePromptId,
            languageCode = Locale.getDefault().language
        )

        val cacheEntry = AiPageCacheEntry(
            pageId = pageId,
            sourcePromptId = sourcePromptId,
            sourceContext = cacheableContext.toJson(),
            kjvOrdinalStart = cacheableContext.kjvOrdinalStart,
            kjvOrdinalEnd = cacheableContext.kjvOrdinalEnd,
            contextHash = cacheableContext.computeHash(),
            usedWriteTools = usedWriteTools,
            sourceModelName = sourceModelName,
            sourceBookInitials = cacheableContext.activeDocumentInitials,
            sourceBookKey = cacheableContext.sourceBookKey
        )

        // Save clean content - footer is rendered by Vue.js based on sourcePromptId
        dao.insertPageWithCacheEntry(page, response, cacheEntry)
        refreshDocument(aiDocument.initials)
        val start = cacheableContext.kjvOrdinalStart
        val end = cacheableContext.kjvOrdinalEnd
        val bookInitials = cacheableContext.activeDocumentInitials
        val bookKey = cacheableContext.sourceBookKey
        if (start != null && end != null) {
            val markers = dao.aiDocMarkersForRange(start, end)
            ABEventBus.post(AiDocPagesChangedEvent(markers))
        } else if (bookInitials != null && bookKey != null) {
            val markers = dao.aiDocMarkersForPage(bookInitials, bookKey)
            ABEventBus.post(AiDocPagesChangedEvent(markers, sourceBookInitials = bookInitials, sourceBookKey = bookKey))
        }

        Log.i(TAG, "Saved AI response as page: ${aiDocument.initials}/${page.pageKey}")
        return SavedPageInfo(aiDocument.initials, page.pageKey)
    }
}
