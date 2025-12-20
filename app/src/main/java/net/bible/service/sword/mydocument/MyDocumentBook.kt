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

package net.bible.service.sword.mydocument

import android.util.Log
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key

private const val TAG = "MyDocumentBook"

/**
 * OpenFileState for MyDocument backend.
 */
class MyDocumentOpenFileState(
    private val documentId: IdType,
    private val ownMetadata: SwordBookMetaData
) : OpenFileState {
    private var _lastAccess: Long = 0L

    override fun getBookMetaData(): BookMetaData = ownMetadata
    override fun releaseResources() = Unit
    override fun close() = Unit
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

/**
 * Backend for MyDocument that reads content from the database.
 *
 * Content is returned wrapped in OSIS-like tags:
 * - MARKDOWN content: <OsisMarkdown>escaped content</OsisMarkdown>
 * - HTML content: <OsisHtml>escaped content</OsisHtml>
 * - OSIS content: returned as-is
 *
 * Rendering of Markdown/HTML happens in Vue.js (OsisMarkdown.vue, OsisHtml.vue).
 */
class MyDocumentBackend(
    private val documentId: IdType,
    metadata: SwordBookMetaData
) : AbstractKeyBackend<MyDocumentOpenFileState>(metadata) {

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    override fun initState(): MyDocumentOpenFileState {
        return MyDocumentOpenFileState(documentId, bookMetaData as SwordBookMetaData)
    }

    override fun getCardinality(): Int {
        return dao.pageCount(documentId)
    }

    override fun get(index: Int): Key {
        val pages = dao.pagesForDocument(documentId)
        if (index < 0 || index >= pages.size) {
            return DefaultLeafKeyList("", "")
        }
        val page = pages[index]
        // Use pageKey for both name and osisRef so getKey() can find it
        // Title is used as the display name via DefaultLeafKeyList's toString()
        return DefaultLeafKeyList(page.pageKey, page.pageKey)
    }

    override fun indexOf(that: Key): Int {
        val pages = dao.pagesForDocument(documentId)
        return pages.indexOfFirst { it.pageKey == that.name }
    }

    override fun readIndex(): Key {
        val key = DefaultKeyList(null, bookMetaData.name)
        for (k in iterator()) {
            key.addAll(k)
        }
        return key
    }

    override fun iterator(): MutableIterator<Key> {
        val pages = dao.pagesForDocument(documentId)
        return pages.map { page ->
            // Use pageKey for both name and osisRef so getKey() can find it
            DefaultLeafKeyList(page.pageKey, page.pageKey) as Key
        }.toMutableList().iterator()
    }

    override fun readRawContent(state: MyDocumentOpenFileState?, key: Key?): String {
        if (key == null) return ""

        val pageKey = key.name
        val page = dao.pageByKeyWithContent(documentId, pageKey)

        if (page == null) {
            Log.w(TAG, "Page not found: $pageKey in document $documentId")
            return "<div>Page not found</div>"
        }

        val content = page.content ?: ""

        // Wrap content in appropriate OSIS tag - rendering happens in Vue.js
        // Note: Tag names are lowercase, osisToTemplateString will convert to OsisMarkdown etc.
        // BVA (BibleViewAnchor) tag wraps content so verse-notifier.ts can find it via closest(".ordinal")
        // HTML content goes through SwordContentFacade.addAnchors() which adds BVAs automatically
        return when (page.contentType) {
            MyDocumentContentType.MARKDOWN ->
                "<div class=\"mydoc-markdown\"><BVA ordinal=\"0\"><markdown>${escapeXml(content)}</markdown></BVA></div>"
            MyDocumentContentType.HTML ->
                "<div class=\"mydoc-html\"><html>${escapeXml(content)}</html></div>"
            MyDocumentContentType.OSIS ->
                content
        }
    }

    /**
     * Get global key list (TOC) for this document.
     */
    override fun getGlobalKeyList(): Key {
        val pages = dao.pagesForDocument(documentId)
        val keyList = DefaultKeyList()
        pages.forEach { page ->
            // Use pageKey for both name and osisRef for consistency
            keyList.addAll(DefaultLeafKeyList(page.pageKey, page.pageKey))
        }
        return keyList
    }

    /**
     * Escape XML special characters while preserving Markdown/HTML formatting.
     * The content will be unescaped in Vue.js before rendering.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

/**
 * Creates SWORD metadata configuration for a MyDocument.
 */
private fun createMyDocumentMetadata(document: MyDocument): SwordBookMetaData {
    val conf = "[${document.initials}]\n" +
        "Description=${document.name}\n" +
        "Abbreviation=${document.initials}\n" +
        "Category=Generic Books\n" +
        "Lang=en\n" +
        "Version=1.0\n" +
        "Encoding=UTF-8\n" +
        "LCSH=Documents\n" +
        "SourceType=OSIS\n" +
        "ModDrv=RawGenBook\n" +
        "AndBibleMyDocument=1\n" +
        "AndBibleMyDocumentId=${document.id}"

    return SwordBookMetaData(conf.toByteArray(), document.initials).also {
        it.driver = SwordBookDriver.instance()
    }
}

/**
 * Creates a SwordGenBook for a MyDocument.
 */
fun createMyDocumentBook(document: MyDocument): SwordGenBook {
    val metadata = createMyDocumentMetadata(document)
    val backend = MyDocumentBackend(document.id, metadata)
    return SwordGenBook(metadata, backend)
}

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
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val document = dao.documentByInitials(initials) ?: return

        // Unregister and re-register
        unregisterDocument(initials)
        registerDocument(document)
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
        val allDocs = dao.allDocuments()
        allDocs.filter { it.id != aiDocument.id }.forEachIndexed { index, doc ->
            doc.orderNumber = index + 1
            dao.update(doc)
        }

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
}
