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

package net.bible.android.view.activity.page

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.progress.ProgressChange
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.mydocument.AiPageCacheEntry
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import net.bible.sharedui.reading.session.SessionEventRelay
import net.bible.sharedui.reading.session.SessionEventRelayPlatform
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #4061: an AI document page that arrives (or changes, or goes away) through cloud sync must reach an
 * already-open reading window as AI document marker events, without a restart.
 *
 * Drives the real MyDocuments sync hook (`_reactToUpdates` of the MYDOCUMENTS accessor, the call `CloudSync`
 * makes after applying a patch) and a [SessionEventRelay] subscribed to the real [MyDocumentBookManager.changes],
 * recording the JS the window receives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncedAiDocMarkersReachOpenViewTest {
    private class RecordingHost : JsHost {
        val js = CopyOnWriteArrayList<String>()
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            this.js += js
            callback?.invoke("null")
        }
    }

    private val platform = object : SessionEventRelayPlatform {
        override fun onActiveWindowChanged(isThisWindowActive: Boolean) {}
        override fun updateOffsets(immediate: Boolean) {}
        override fun markWindowsCheckNeeded() {}
        override fun updateConfig() {}
    }

    private val loaderPlatform = object : DocumentLoaderPlatform {
        override val lastUpdated = 0L
        override val topOffset = 0f
        override val bottomOffset = 0f
        override val hasActiveIndicator = false
        override fun configInputs(favouriteLabelIds: List<IdType>) = error("unused")
        override fun isCurrentView() = true
        override fun enableZoomForMap(isMap: Boolean) {}
    }

    private val surface = object : ReadingWindowSurface {
        override fun copyText(label: String?, text: String) {}
        override fun shareText(text: String, subject: String?) {}
        override fun shareHtml(html: String, title: String) {}
        override fun stopSelection(removeRanges: Boolean) {}
        override fun toast(text: String) {}
        override fun openWebSearch(query: String) {}
    }

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
    private val host = RecordingHost()
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var repo: net.bible.android.control.page.window.WindowRepository
    private lateinit var originalRepo: net.bible.android.control.page.window.WindowRepository
    private lateinit var previousDispatcher: CoroutineDispatcher
    private lateinit var previousLauncher: OrderedLauncher
    private lateinit var session: BibleWindowSession
    private lateinit var relay: SessionEventRelay

    @Before fun setUp() {
        previousDispatcher = MyDocumentBookManager.mainDispatcher
        previousLauncher = MyDocumentBookManager.launcher
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        MyDocumentBookManager.launcher = OrderedLauncher(syncScope)
        MyDocumentBookManager.clear()
        repo = net.bible.test.testWindowRepository()
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        session = BibleWindowSession(
            window = repo.activeWindow,
            jsChannel = channel,
            hostScope = { hostScope },
            surface = surface,
            navigator = java.lang.reflect.Proxy.newProxyInstance(
                ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
            ) { _, m, _ -> error("unexpected navigator call ${m.name}") } as ReadingNavigator,
        )
        relay = SessionEventRelay(
            session, DocumentLoader(session, loaderPlatform), platform,
            windowChanges = { EventSource<WindowChange>() },
            progressChanges = EventSource<ProgressChange>(),
            readingProgressSettingsChanged = EventSource<Unit>(),
            aiDocChanges = MyDocumentBookManager.changes,
            bookmarkChangeEvents = { EventSource<BookmarkChange>() },
        )
        relay.start()
    }

    @After fun tearDown() {
        try {
            relay.stop()
            session.dispose()
            MyDocumentBookManager.clear()
            DatabaseResetter.resetDatabase(repo.scope, hostScope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            MyDocumentBookManager.mainDispatcher = previousDispatcher
            MyDocumentBookManager.launcher = previousLauncher
            syncScope.cancel()
        }
    }

    private fun entry(table: String, id: IdType, type: LogEntryTypes) = LogEntry(
        tableName = table, entityId1 = id, entityId2 = IdType.empty(), type = type,
        lastUpdated = 0L, sourceDevice = "other-device",
    )

    /** Hands [entries] to the MyDocuments sync hook, as `CloudSync` does after applying a patch, and waits for it. */
    private fun syncApplied(entries: List<LogEntry>) {
        val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
            .map { it() }
            .single { it.category == SyncableDatabaseDefinition.MYDOCUMENTS }
        runBlocking { accessor._reactToUpdates!!.invoke(entries) }
        DatabaseResetter.runResetBlocking { MyDocumentBookManager.launcher.join("mydocument-sync") }
    }

    /** Writes an AI document page as a remote device's patch would leave it in the local database. */
    private fun insertSyncedAiPage(title: String): IdType = runBlocking {
        val aiDocument = MyDocumentBookManager.getOrCreateAIDocument()
        val pageId = IdType()
        val ordinal = Verse(KJVA, BibleBook.JOHN, 3, 16).ordinal
        dao.insertPageWithCacheEntry(
            MyDocumentPage(
                id = pageId, documentId = aiDocument.id, title = title, pageKey = "ai_$pageId",
                contentType = MyDocumentContentType.MARKDOWN, orderNumber = 0, sourcePromptId = IdType(),
            ),
            "content",
            AiPageCacheEntry(
                pageId = pageId, sourcePromptId = IdType(), sourceContext = null,
                kjvOrdinalStart = ordinal, kjvOrdinalEnd = ordinal, contextHash = "h",
            ),
        )
        pageId
    }

    private fun upsertEntries(pageId: IdType) = listOf(
        entry("MyDocumentPage", pageId, LogEntryTypes.UPSERT),
        entry("MyDocumentPageContent", pageId, LogEntryTypes.UPSERT),
        entry("AiPageCacheEntry", pageId, LogEntryTypes.UPSERT),
    )

    private fun markerJs(): List<String> = host.js.filter { it.contains("_ai_doc_markers") }

    @Test fun aiDocumentCreatedOnAnotherDeviceReachesTheOpenView() {
        val pageId = insertSyncedAiPage("Synced study")

        syncApplied(upsertEntries(pageId))

        assertTrue("JS: ${markerJs()}", markerJs().any {
            it.contains("add_or_update_ai_doc_markers") && it.contains(pageId.toString()) && it.contains("Synced study")
        })
    }

    @Test fun aiDocumentRenamedOnAnotherDeviceUpdatesTheOpenViewsMarker() {
        val pageId = insertSyncedAiPage("Old title")
        syncApplied(upsertEntries(pageId))
        host.js.clear()

        runBlocking { dao.update(dao.pageById(pageId)!!.apply { title = "New title" }) }
        syncApplied(listOf(entry("MyDocumentPage", pageId, LogEntryTypes.UPSERT)))

        assertTrue("JS: ${markerJs()}", markerJs().any {
            it.contains("add_or_update_ai_doc_markers") && it.contains(pageId.toString()) && it.contains("New title")
        })
    }

    @Test fun aiDocumentDeletedOnAnotherDeviceIsRemovedFromTheOpenView() {
        val pageId = insertSyncedAiPage("Doomed study")
        syncApplied(upsertEntries(pageId))
        host.js.clear()

        runBlocking { dao.deletePageWithContent(dao.pageById(pageId)!!) }
        syncApplied(listOf(
            entry("MyDocumentPage", pageId, LogEntryTypes.DELETE),
            entry("MyDocumentPageContent", pageId, LogEntryTypes.DELETE),
            entry("AiPageCacheEntry", pageId, LogEntryTypes.DELETE),
        ))

        assertTrue("JS: ${markerJs()}", markerJs().any {
            it.contains("delete_ai_doc_markers") && it.contains(pageId.toString())
        })
        assertFalse("a deleted page must not be re-added: ${markerJs()}", markerJs().any {
            it.contains("add_or_update_ai_doc_markers")
        })
    }

    @Test fun syncOfAPageWithoutAiMarkerSendsNoMarkerEvents() {
        val pageId = runBlocking {
            val doc = net.bible.android.database.mydocument.MyDocument(name = "Plain", initials = "MyDoc_Plain")
            dao.insert(doc)
            val page = MyDocumentPage(
                documentId = doc.id, title = "Plain page", pageKey = "plain",
                contentType = MyDocumentContentType.MARKDOWN, orderNumber = 0,
            )
            dao.insertPageWithContent(page, "text")
            page.id
        }

        syncApplied(listOf(entry("MyDocumentPage", pageId, LogEntryTypes.UPSERT)))

        assertTrue("JS: ${markerJs()}", markerJs().isEmpty())
    }
}
