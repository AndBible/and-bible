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

import kotlinx.coroutines.runBlocking
import android.os.Looper
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.mydocument.AiDocMarkerInfo
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
        runBlocking { dao.allDocuments() }.forEach { runBlocking { dao.deleteDocumentWithPages(it) } }

        document = MyDocument(name = "Test document", initials = "MyDoc_Test")
        runBlocking { dao.insert(document) }
        addPage("page_one", "First page")
        MyDocumentBookManager.registerDocument(document)
    }

    @After
    fun tearDown() {
        MyDocumentBookManager.clear()
        runBlocking { dao.allDocuments() }.forEach { runBlocking { dao.deleteDocumentWithPages(it) } }
    }

    private fun addPage(pageKey: String, title: String, documentId: IdType = document.id) {
        val page = MyDocumentPage(
            documentId = documentId,
            title = title,
            pageKey = pageKey,
            contentType = MyDocumentContentType.MARKDOWN,
            orderNumber = runBlocking { dao.pagesForDocument(documentId) }.size,
        )
        runBlocking { dao.insertPageWithContent(page, "content of $title") }
    }

    private fun syncEventForPages(vararg pageKeys: String): List<LogEntry> {
        val entries = pageKeys.map { pageKey ->
            val page = runBlocking { dao.pageByKeyWithContent(document.id, pageKey) }!!
            LogEntry(
                tableName = "MyDocumentPage",
                entityId1 = page.id,
                entityId2 = IdType.empty(),
                type = LogEntryTypes.UPSERT,
                lastUpdated = 0L,
                sourceDevice = "other-device",
            )
        }
        return entries
    }

    @Test
    fun registeredDocumentResolvesItsPages() {
        val book = Books.installed().getBook("MyDoc_Test")!!

        val key = book.getKey("page_one")

        assertEquals("page_one", key.getOsisRef())
        assertEquals("First page", key.getName())
    }

    @Test
    fun refreshDocumentPicksUpPagesAddedAfterActivation() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        // Activate the book so its key map is built from the one-page state.
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")

        assertEquals("page_two", book.getKey("page_two").getOsisRef())
    }

    @Test
    fun refreshDocumentRecoversFromAKeyMapBuiltWhileTheDocumentHadNoPages() {
        val empty = MyDocument(name = "Empty document", initials = "MyDoc_Empty")
        runBlocking { dao.insert(empty) }
        MyDocumentBookManager.registerDocument(empty)
        val book = Books.installed().getBook("MyDoc_Empty")!!
        // Activating with no pages freezes an empty key map. Without a rebuild
        // it stays empty for the rest of the session and every getKey() throws.
        assertEquals(0, book.getGlobalKeyList().getCardinality())

        addPage("page_late", "Late page", documentId = empty.id)
        MyDocumentBookManager.refreshDocument("MyDoc_Empty")

        assertEquals("page_late", book.getKey("page_late").getOsisRef())
    }

    @Test
    fun syncUpdateResolvesPagesAddedByAnotherDevice() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.handleSyncEvent(syncEventForPages("page_two"))

        val refreshed = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", refreshed.getKey("page_two").getOsisRef())
        assertEquals("page_one", refreshed.getKey("page_one").getOsisRef())
    }

    @Test
    fun syncUpdateKeepsTheBookInstanceOpenWindowsAreHolding() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.handleSyncEvent(syncEventForPages("page_two"))

        assertSame(
            "windows and history hold this reference; replacing it strands them on a removed book",
            book,
            Books.installed().getBook("MyDoc_Test")
        )
        assertEquals("page_two", book.getKey("page_two").getOsisRef())
    }

    @Test
    fun syncUpdateDropsPagesDeletedOnAnotherDevice() {
        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")
        val book = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", book.getKey("page_two").getOsisRef())

        val deleted = runBlocking { dao.pageByKeyWithContent(document.id, "page_two") }!!
        val entry = LogEntry(
            tableName = "MyDocumentPage",
            entityId1 = deleted.id,
            entityId2 = IdType.empty(),
            type = LogEntryTypes.DELETE,
            lastUpdated = 0L,
            sourceDevice = "other-device",
        )
        runBlocking { dao.deletePageWithContent(dao.pageById(deleted.id)!!) }
        MyDocumentBookManager.handleSyncEvent(listOf(entry))

        assertTrue(book.getGlobalKeyList().none { it.getOsisRef() == "page_two" })
        assertEquals("page_one", book.getKey("page_one").getOsisRef())
    }

    @Test
    fun syncUpdateRegistersDocumentsCreatedOnAnotherDevice() {
        val other = MyDocument(name = "Other document", initials = "MyDoc_Other")
        runBlocking { dao.insert(other) }
        addPage("page_other", "Other page", documentId = other.id)
        val page = runBlocking { dao.pageByKeyWithContent(other.id, "page_other") }!!

        MyDocumentBookManager.handleSyncEvent(
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

        val book = Books.installed().getBook("MyDoc_Other")
        assertNotNull("document created on another device must be registered", book)
        assertEquals("page_other", book!!.getKey("page_other").getOsisRef())
    }

    @Test
    fun syncUpdateUnregistersDocumentsDeletedOnAnotherDevice() {
        runBlocking { dao.deleteDocumentWithPages(dao.documentById(document.id)!!) }

        MyDocumentBookManager.handleSyncEvent(
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

        assertNull(Books.installed().getBook("MyDoc_Test"))
        assertTrue("MyDoc_Test" !in MyDocumentBookManager.registeredInitials)
    }

    @Test
    fun syncUpdateRebuildsMetadataWhenDocumentIsRenamed() {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        val renamed = runBlocking { dao.documentById(document.id) }!!.apply { name = "Renamed document" }
        runBlocking { dao.update(renamed) }
        MyDocumentBookManager.handleSyncEvent(
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

        val current = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("Renamed document", current.name)
        assertEquals("page_one", current.getKey("page_one").getOsisRef())
    }

    @Test
    fun aMyDocumentsSyncReachesTheManagerOnMain() {
        Books.installed().getBook("MyDoc_Test")!!.getKey("page_one")
        addPage("page_synced", "Synced page")
        val entries = syncEventForPages("page_synced")
        val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
            .map { it() }
            .single { it.category == SyncableDatabaseDefinition.MYDOCUMENTS }
        var deliveryLooper: Looper? = null
        val seen = mutableListOf<MyDocumentChange>()
        val subscription = MyDocumentBookManager.changes.subscribe {
            seen.add(it)
            deliveryLooper = Looper.myLooper()
        }
        try {
            val worker = Thread { accessor._reactToUpdates!!.invoke(entries) }
            worker.start()
            worker.join()
            assertTrue("sync must wait for main before refreshing registrations", seen.isEmpty())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("page_synced", Books.installed().getBook("MyDoc_Test")!!.getKey("page_synced").getOsisRef())
            assertEquals(listOf(MyDocumentChange.DocumentUpdated("MyDoc_Test")), seen)
            assertSame(Looper.getMainLooper(), deliveryLooper)
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun syncEmitsDocumentUpdatedAfterRefreshingRegistrations() {
        Books.installed().getBook("MyDoc_Test")!!.getKey("page_one")
        addPage("page_synced", "Synced page")
        var resolved = false
        val subscription = MyDocumentBookManager.changes.subscribe {
            if (it == MyDocumentChange.DocumentUpdated("MyDoc_Test")) {
                resolved = Books.installed().getBook("MyDoc_Test")!!.getKey("page_synced").getOsisRef() == "page_synced"
            }
        }
        try {
            MyDocumentBookManager.handleSyncEvent(syncEventForPages("page_synced"))
            assertTrue("subscribers must see refreshed keys at emission time", resolved)
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun deletingAnAiDocumentPageEmitsDocumentUpdatedThenAiDocPages() {
        val aiDocument = runBlocking { MyDocumentBookManager.getOrCreateAIDocument() }
        addPage("ai_page", "AI page", documentId = aiDocument.id)
        MyDocumentBookManager.refreshDocument(MyDocumentBookManager.AI_DOCUMENTS_INITIALS)
        val id = runBlocking { dao.pageByKeyWithContent(aiDocument.id, "ai_page") }!!.id
        val seen = mutableListOf<MyDocumentChange>()
        val subscription = MyDocumentBookManager.changes.subscribe { seen.add(it) }
        try {
            assertTrue(MyDocumentBookManager.deleteAIDocumentPage(id))
            assertEquals(
                listOf(
                    MyDocumentChange.DocumentUpdated(MyDocumentBookManager.AI_DOCUMENTS_INITIALS),
                    MyDocumentChange.AiDocPages(deletedPageIds = listOf(id)),
                ),
                seen,
            )
            assertNull(runBlocking { dao.pageById(id) })
        } finally {
            subscription.cancel()
        }
    }

    /**
     * D1 final review M8: the coroutine caller (AgentSessionManager's onStarted) deletes through the suspend variant
     * (direct DAO calls); it behaves like the blocking one. (Not run under DbTransactionMarker: the JSword
     * re-activation in refreshDocument still bridges, by design.)
     */
    @Test
    fun suspendingDeleteOfAnAiDocumentPageEmitsTheSameChanges() {
        val aiDocument = runBlocking { MyDocumentBookManager.getOrCreateAIDocument() }
        addPage("ai_page", "AI page", documentId = aiDocument.id)
        MyDocumentBookManager.refreshDocument(MyDocumentBookManager.AI_DOCUMENTS_INITIALS)
        val id = runBlocking { dao.pageByKeyWithContent(aiDocument.id, "ai_page") }!!.id
        val seen = mutableListOf<MyDocumentChange>()
        val subscription = MyDocumentBookManager.changes.subscribe { seen.add(it) }
        try {
            assertTrue(runBlocking { MyDocumentBookManager.deleteAIDocumentPageSuspending(id) })
            assertEquals(
                listOf(
                    MyDocumentChange.DocumentUpdated(MyDocumentBookManager.AI_DOCUMENTS_INITIALS),
                    MyDocumentChange.AiDocPages(deletedPageIds = listOf(id)),
                ),
                seen,
            )
            assertNull(runBlocking { dao.pageById(id) })
            assertFalse(runBlocking { MyDocumentBookManager.deleteAIDocumentPageSuspending(id) })
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun notifyAiDocPagesChangedReachesSubscribersUnchanged() {
        val marker = AiDocMarkerInfo(
            pageId = IdType(), documentId = document.id, documentInitials = "MyDoc_Test",
            pageTitle = "Marker", pageKey = "page_one", kjvOrdinalStart = null, kjvOrdinalEnd = null,
            sourcePromptId = IdType(), sourceBookInitials = "Commentary", sourceBookKey = "entry",
        )
        val change = MyDocumentChange.AiDocPages(
            markers = listOf(marker), deletedPageIds = listOf(IdType()),
            sourceBookInitials = "Commentary", sourceBookKey = "entry",
        )
        val seen = mutableListOf<MyDocumentChange>()
        val subscription = MyDocumentBookManager.changes.subscribe { seen.add(it) }
        try {
            MyDocumentBookManager.notifyAiDocPagesChanged(change)
            assertEquals(listOf(change), seen)
        } finally {
            subscription.cancel()
        }
    }

}
