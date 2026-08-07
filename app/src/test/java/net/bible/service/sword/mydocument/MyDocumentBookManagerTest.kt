/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.MyDocumentsUpdatedViaSyncEvent
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that a MyDocument's SWORD key map keeps resolving page keys across the
 * events that rebuild it — local edits and cloud sync.
 *
 * Regression coverage for a production crash where a page selected in
 * MyDocumentPagesActivity could not be resolved by MainBibleActivity:
 * `NoSuchKeyException: No entry for 'page_...' in MyDoc_...`. The document had
 * been re-registered by a sync update, and the replacement book's key map was
 * never built.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyDocumentBookManagerTest {
    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    private lateinit var document: MyDocument

    @Before
    fun setUp() {
        MyDocumentBookManager.clear()
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }

        document = MyDocument(name = "Test document", initials = "MyDoc_Test")
        dao.insert(document)
        addPage("page_one", "First page")
        MyDocumentBookManager.registerDocument(document)
    }

    @After
    fun tearDown() {
        MyDocumentBookManager.clear()
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }
    }

    private fun addPage(pageKey: String, title: String, documentId: IdType = document.id) {
        val page = MyDocumentPage(
            documentId = documentId,
            title = title,
            pageKey = pageKey,
            contentType = MyDocumentContentType.MARKDOWN,
            orderNumber = dao.pagesForDocument(documentId).size,
        )
        dao.insertPageWithContent(page, "content of $title")
    }

    private fun syncEventForPages(vararg pageKeys: String): MyDocumentsUpdatedViaSyncEvent {
        val entries = pageKeys.map { pageKey ->
            val page = dao.pageByKeyWithContent(document.id, pageKey)!!
            LogEntry(
                tableName = "MyDocumentPage",
                entityId1 = page.id,
                entityId2 = IdType.empty(),
                type = LogEntryTypes.UPSERT,
                lastUpdated = 0L,
                sourceDevice = "other-device",
            )
        }
        return MyDocumentsUpdatedViaSyncEvent(entries)
    }

    @Test
    fun registeredDocumentResolvesItsPages() {
        val book = Books.installed().getBook("MyDoc_Test")!!

        val key = book.getKey("page_one")

        assertEquals("page_one", key.osisRef)
        assertEquals("First page", key.name)
    }

    @Test
    fun refreshDocumentPicksUpPagesAddedAfterActivation() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        // Activate the book so its key map is built from the one-page state.
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")

        assertEquals("page_two", book.getKey("page_two").osisRef)
    }

    @Test
    fun refreshDocumentRecoversFromAKeyMapBuiltWhileTheDocumentHadNoPages() {
        val empty = MyDocument(name = "Empty document", initials = "MyDoc_Empty")
        dao.insert(empty)
        MyDocumentBookManager.registerDocument(empty)
        val book = Books.installed().getBook("MyDoc_Empty")!!
        // Activating with no pages freezes an empty key map. Without a rebuild
        // it stays empty for the rest of the session and every getKey() throws.
        assertEquals(0, book.globalKeyList.cardinality)

        addPage("page_late", "Late page", documentId = empty.id)
        MyDocumentBookManager.refreshDocument("MyDoc_Empty")

        assertEquals("page_late", book.getKey("page_late").osisRef)
    }

    @Test
    fun syncUpdateResolvesPagesAddedByAnotherDevice() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.onEventMainThread(syncEventForPages("page_two"))

        val refreshed = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", refreshed.getKey("page_two").osisRef)
        assertEquals("page_one", refreshed.getKey("page_one").osisRef)
    }

    @Test
    fun syncUpdateKeepsTheBookInstanceOpenWindowsAreHolding() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.onEventMainThread(syncEventForPages("page_two"))

        assertSame(
            "windows and history hold this reference; replacing it strands them on a removed book",
            book,
            Books.installed().getBook("MyDoc_Test")
        )
        assertEquals("page_two", book.getKey("page_two").osisRef)
    }

    @Test
    fun syncUpdateDropsPagesDeletedOnAnotherDevice() {
        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")
        val book = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", book.getKey("page_two").osisRef)

        val deleted = dao.pageByKeyWithContent(document.id, "page_two")!!
        val entry = LogEntry(
            tableName = "MyDocumentPage",
            entityId1 = deleted.id,
            entityId2 = IdType.empty(),
            type = LogEntryTypes.DELETE,
            lastUpdated = 0L,
            sourceDevice = "other-device",
        )
        dao.deletePageWithContent(dao.pageById(deleted.id)!!)
        MyDocumentBookManager.onEventMainThread(MyDocumentsUpdatedViaSyncEvent(listOf(entry)))

        assertTrue(book.globalKeyList.none { it.osisRef == "page_two" })
        assertEquals("page_one", book.getKey("page_one").osisRef)
    }

    @Test
    fun syncUpdateRegistersDocumentsCreatedOnAnotherDevice() {
        val other = MyDocument(name = "Other document", initials = "MyDoc_Other")
        dao.insert(other)
        addPage("page_other", "Other page", documentId = other.id)
        val page = dao.pageByKeyWithContent(other.id, "page_other")!!

        MyDocumentBookManager.onEventMainThread(
            MyDocumentsUpdatedViaSyncEvent(
                listOf(
                    LogEntry(
                        tableName = "MyDocumentPage",
                        entityId1 = page.id,
                        entityId2 = IdType.empty(),
                        type = LogEntryTypes.UPSERT,
                        lastUpdated = 0L,
                        sourceDevice = "other-device",
                    )
                )
            )
        )

        val book = Books.installed().getBook("MyDoc_Other")
        assertNotNull("document created on another device must be registered", book)
        assertEquals("page_other", book!!.getKey("page_other").osisRef)
    }

    @Test
    fun syncUpdateUnregistersDocumentsDeletedOnAnotherDevice() {
        dao.deleteDocumentWithPages(dao.documentById(document.id)!!)

        MyDocumentBookManager.onEventMainThread(
            MyDocumentsUpdatedViaSyncEvent(
                listOf(
                    LogEntry(
                        tableName = "MyDocument",
                        entityId1 = document.id,
                        entityId2 = IdType.empty(),
                        type = LogEntryTypes.DELETE,
                        lastUpdated = 0L,
                        sourceDevice = "other-device",
                    )
                )
            )
        )

        assertNull(Books.installed().getBook("MyDoc_Test"))
        assertTrue("MyDoc_Test" !in MyDocumentBookManager.registeredInitials)
    }

    @Test
    fun syncUpdateRebuildsMetadataWhenDocumentIsRenamed() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        val renamed = dao.documentById(document.id)!!.apply { name = "Renamed document" }
        dao.update(renamed)
        MyDocumentBookManager.onEventMainThread(
            MyDocumentsUpdatedViaSyncEvent(
                listOf(
                    LogEntry(
                        tableName = "MyDocument",
                        entityId1 = document.id,
                        entityId2 = IdType.empty(),
                        type = LogEntryTypes.UPSERT,
                        lastUpdated = 0L,
                        sourceDevice = "other-device",
                    )
                )
            )
        )

        val current = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("Renamed document", current.name)
        assertEquals("page_one", current.getKey("page_one").osisRef)
    }
}
