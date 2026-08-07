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
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.db.DatabaseContainer
import org.apache.commons.text.StringEscapeUtils
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import java.util.Locale

private const val TAG = "MyDocumentBackend"

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
 * - MARKDOWN content: converted to XHTML via commonmark-java, then addAnchors() adds BVA elements
 * - HTML content: <OsisHtml>escaped content</OsisHtml>
 * - OSIS content: returned as-is
 *
 * Page list is cached to avoid repeated DB queries during TOC building.
 * Key construction uses title for display name and pageKey for osisRef (internal lookup).
 * Cache is invalidated in readIndex() (called during activation) and initState().
 */
class MyDocumentBackend(
    private val documentId: IdType,
    metadata: SwordBookMetaData
) : AbstractKeyBackend<MyDocumentOpenFileState>(metadata) {

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    private var cachedPages: List<MyDocumentPage>? = null

    private fun getPages(): List<MyDocumentPage> {
        return cachedPages ?: dao.pagesForDocument(documentId).also { cachedPages = it }
    }

    override fun initState(): MyDocumentOpenFileState {
        cachedPages = null
        return MyDocumentOpenFileState(documentId, bookMetaData as SwordBookMetaData)
    }

    override fun getCardinality(): Int = getPages().size

    override fun get(index: Int): Key {
        val pages = getPages()
        if (index < 0 || index >= pages.size) {
            return DefaultLeafKeyList("", "")
        }
        val page = pages[index]
        return DefaultLeafKeyList(page.title, page.pageKey)
    }

    override fun indexOf(that: Key): Int {
        val searchKey = that.osisRef?.takeIf { it.isNotEmpty() } ?: that.name
        return getPages().indexOfFirst { it.pageKey == searchKey }
    }

    override fun readIndex(): Key {
        // Clear cache so re-activation (deactivate+activate) reads fresh data from DB.
        // Without this, cachedPages retains stale entries from the previous activation
        // and newly added pages (e.g. AI responses) won't appear in the key map.
        cachedPages = null
        val key = DefaultKeyList(null, bookMetaData.name)
        for (k in iterator()) {
            key.addAll(k)
        }
        // An empty index is cached by SwordGenBook until the book is deactivated,
        // and every getKey() on it then throws NoSuchKeyException. Log it so a
        // document that silently loses its table of contents is traceable.
        if (key.cardinality == 0) {
            Log.w(TAG, "Empty key index built for document $documentId (${bookMetaData.initials})")
        }
        return key
    }

    override fun iterator(): MutableIterator<Key> {
        return getPages().map { page ->
            DefaultLeafKeyList(page.title, page.pageKey) as Key
        }.toMutableList().iterator()
    }

    override fun readRawContent(state: MyDocumentOpenFileState?, key: Key?): String {
        if (key == null) return ""

        val pageKey = key.osisRef?.takeIf { it.isNotEmpty() } ?: key.name
        val page = dao.pageByKeyWithContent(documentId, pageKey)

        if (page == null) {
            Log.w(TAG, "Page not found: $pageKey in document $documentId")
            return "<div>Page not found</div>"
        }

        val content = page.content ?: ""

        // MARKDOWN: converted to XHTML, addAnchors() adds BVA elements for scroll tracking
        // HTML: wrapped in <html> tag, rendered by Vue.js Html component
        // OSIS: returned as-is
        // AI footer is rendered by Vue.js OsisDocument component, not embedded in content.
        return when (page.contentType) {
            MyDocumentContentType.MARKDOWN -> {
                val xhtml = MarkdownToXhtml.convert(content)
                "<div class=\"mydoc-markdown\">$xhtml</div>"
            }
            MyDocumentContentType.HTML ->
                "<div class=\"mydoc-html\"><html>${StringEscapeUtils.escapeXml11(content)}</html></div>"
            MyDocumentContentType.OSIS -> content
        }
    }

    override fun getGlobalKeyList(): Key {
        val keyList = DefaultKeyList()
        getPages().forEach { page ->
            keyList.addAll(DefaultLeafKeyList(page.title, page.pageKey))
        }
        return keyList
    }
}

/**
 * Creates SWORD metadata configuration for a MyDocument.
 */
internal fun createMyDocumentMetadata(document: MyDocument): SwordBookMetaData {
    val conf = "[${document.initials}]\n" +
        "Description=${document.name}\n" +
        "Abbreviation=${document.initials}\n" +
        "Category=Generic Books\n" +
        "Lang=${Locale.getDefault().language}\n" +
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
