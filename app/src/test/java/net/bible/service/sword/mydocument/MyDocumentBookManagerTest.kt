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

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.platform.OrderedLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var previousDispatcher: kotlinx.coroutines.CoroutineDispatcher
    private lateinit var previousLauncher: OrderedLauncher
    private lateinit var document: MyDocument

    @Before
    fun setUp() = runTest {
        previousDispatcher = MyDocumentBookManager.mainDispatcher
        previousLauncher = MyDocumentBookManager.launcher
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        MyDocumentBookManager.launcher = OrderedLauncher(syncScope)
        MyDocumentBookManager.clear()
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }

        document = MyDocument(name = "Test document", initials = "MyDoc_Test")
        dao.insert(document)
        addPage("page_one", "First page")
        MyDocumentBookManager.registerDocument(document)
    }

    @After
    fun tearDown() = runTest {
        try {
            MyDocumentBookManager.clear()
            dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }
        } finally {
            MyDocumentBookManager.mainDispatcher = previousDispatcher
            MyDocumentBookManager.launcher = previousLauncher
            syncScope.cancel()
        }
    }

    private suspend fun addPage(pageKey: String, title: String, documentId: IdType = document.id) {
        val page = MyDocumentPage(
            documentId = documentId,
            title = title,
            pageKey = pageKey,
            contentType = MyDocumentContentType.MARKDOWN,
            orderNumber = dao.pagesForDocument(documentId).size,
        )
        dao.insertPageWithContent(page, "content of $title")
    }

    private suspend fun syncEventForPages(vararg pageKeys: String): List<LogEntry> {
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
        return entries
    }

    @Test
    fun cloneSharesPopulatedPageCacheButInvalidatesItsOwnCacheIndependently() = runTest {
        val metadata = createMyDocumentMetadata(document)
        val original = MyDocumentBackend(document.id, metadata)
        assertEquals(1, original.getCardinality())
        val copy = original.clone() as MyDocumentBackend
        org.junit.Assert.assertNotSame(original, copy)
        assertSame(original.javaClass, copy.javaClass)
        assertSame(metadata, copy.bookMetaData)
        assertEquals("page_one", copy.get(0).getOsisRef())
        val cache = MyDocumentBackend::class.java.getDeclaredField("cachedPages").apply { isAccessible = true }
        assertNotNull(cache.get(original))
        assertSame(cache.get(original), cache.get(copy))

        addPage("page_two", "Second page")
        copy.initState()
        assertEquals(2, copy.getCardinality())
        assertEquals(1, original.getCardinality())
        org.junit.Assert.assertNotSame(cache.get(original), cache.get(copy))
        addPage("page_three", "Third page")
        original.initState()
        assertEquals(3, original.getCardinality())
        assertEquals(2, copy.getCardinality())
        assertEquals("page_three", original.get(2).getOsisRef())
        assertEquals("page_two", copy.get(1).getOsisRef())
    }

    @Test
    fun syncEventForUnknownDocumentRegistersIt() = runTest {
        val other = MyDocument(name = "Unknown", initials = "MyDoc_Unknown")
        dao.insert(other)
        MyDocumentBookManager.handleSyncEvent(listOf(LogEntry(
            tableName = "MyDocument", entityId1 = other.id, entityId2 = IdType.empty(),
            type = LogEntryTypes.UPSERT, lastUpdated = 0L, sourceDevice = "other-device",
        )))
        assertNotNull(Books.installed().getBook(other.initials))
    }

    @Test
    fun registerAllDocumentsAndGenerateInitialsHonorDatabaseCollisions() = runTest {
        MyDocumentBookManager.clear()
        dao.insert(MyDocument(name = "Collision", initials = "MyDoc_Test_1"))
        MyDocumentBookManager.registerAllDocuments()
        assertNotNull(Books.installed().getBook(document.initials))
        assertEquals("MyDoc_Test_2", MyDocumentBookManager.generateInitials("Test"))
        assertEquals("MyDoc_MyDoc", MyDocumentBookManager.generateInitials("!"))
    }

    @Test
    fun backendUsesInjectedDaoForIndexAndContent() = runTest {
        var reads = 0
        val backend = MyDocumentBackend(document.id, createMyDocumentMetadata(document)) {
            reads++
            dao
        }
        assertEquals(1, backend.getCardinality())
        val key = backend.get(0)
        assertEquals("page_one", key.getOsisRef())
        assertTrue(backend.readRawContent(backend.initState(), key).contains("content of First page"))
        assertEquals(2, reads)
    }

    @Test
    fun queuedSaveAndRefreshFinishAfterScreenScopeIsCancelled() = runTest {
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val page = dao.pageByKeyWithContent(document.id, "page_one")!!
        Books.installed().getBook(document.initials)!!.getKey("page_one")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val key = "mydocument-window-test"
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        val seen = mutableListOf<String>()
        val subscription = MyDocumentBookManager.changes.subscribe {
            if (it is MyDocumentChange.DocumentUpdated) seen += it.initials
        }
        try {
            val save = MyDocumentBookManager.launcher.launch(key) {
                gate.await()
                MyDocumentBookManager.savePageContent(page.id, "saved", "Updated")
            }
            val refresh = MyDocumentBookManager.launcher.launch(key) {
                MyDocumentBookManager.refreshDocument(document.initials)
            }
            testScheduler.runCurrent()
            screen.cancel()
            assertTrue(seen.isEmpty())
            gate.complete(Unit)
            testScheduler.advanceUntilIdle()
            save.join()
            refresh.join()
            assertEquals("saved", dao.pageByKeyWithContent(document.id, "page_one")!!.content)
            assertEquals("Updated", Books.installed().getBook(document.initials)!!.getKey("page_one").getName())
            assertEquals(listOf(document.initials), seen)
        } finally {
            gate.complete(Unit)
            subscription.cancel()
            screen.cancel()
            queuedScope.cancel()
        }
    }

    @Test
    fun coldReadingEntryWaitsForRegistrationBeforeRestoringWindows() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        MyDocumentBookManager.clear()
        net.bible.service.common.CommonUtils.initialized = false
        val intent = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            net.bible.sharedcore.nav.NavRoutes.READING,
        )
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java, intent,
        )
        var restorationSawBook: Boolean? = null
        controller.get().usableBibleGate = { true }
        val restored = net.bible.android.control.page.window.WorkspaceChanges.changes.subscribe {
            restorationSawBook = Books.installed().getBook(document.initials) != null
        }
        try {
            controller.create()
            assertNull("window restoration must wait for held startup registration", restorationSawBook)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) {
                if (restorationSawBook == null) org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
            }
            assertEquals(true, restorationSawBook)
        } finally {
            restored.cancel()
            controller.destroy()
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(controller.get(), false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(controller.get())
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun heldReadingIntentCannotReplaceNewerNonReadingIntent() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostClass = net.bible.android.view.activity.nav.NavHostComposeActivity::class.java
        val controller = org.robolectric.Robolectric.buildActivity(hostClass,
            net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.myDocuments()))
        val activity = controller.get()
        activity.usableBibleGate = { true }
        try {
            controller.create().start().resume()
            val reading = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.READING)
            val newer = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.myDocuments()).putExtra("arrival", "newer")
            val method = hostClass.getDeclaredMethod("applyNewIntent", android.content.Intent::class.java).apply { isAccessible = true }
            method.invoke(activity, reading)
            method.invoke(activity, newer)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertSame(newer, activity.intent)
            val pending = hostClass.getDeclaredField("pendingNewIntentRoute").apply { isAccessible = true }.get(activity)
            assertEquals(net.bible.sharedcore.nav.NavRoutes.myDocuments(), pending)
        } finally {
            controller.pause().stop().destroy()
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun heldReadingIntentCannotReplaceNewerReadingIntent() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostClass = net.bible.android.view.activity.nav.NavHostComposeActivity::class.java
        val controller = org.robolectric.Robolectric.buildActivity(hostClass,
            net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.myDocuments()))
        val activity = controller.get()
        activity.usableBibleGate = { true }
        try {
            controller.create().start().resume()
            val reading = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.READING)
            val newer = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(ctx, net.bible.sharedcore.nav.NavRoutes.READING).putExtra("arrival", "newer")
            val method = hostClass.getDeclaredMethod("applyNewIntent", android.content.Intent::class.java).apply { isAccessible = true }
            method.invoke(activity, reading)
            method.invoke(activity, newer)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertSame(newer, activity.intent)
            val pending = hostClass.getDeclaredField("pendingNewIntentRoute").apply { isAccessible = true }.get(activity)
            assertEquals(net.bible.sharedcore.nav.NavRoutes.READING, pending)
        } finally {
            controller.pause().stop().destroy()
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun delayedColdBootstrapDoesNotReclaimForegroundAfterPause() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java,
            net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING))
        val activity = controller.get()
        val other = Any()
        activity.usableBibleGate = { true }
        try {
            controller.create().start().resume().pause()
            net.bible.sharedcore.reading.ReadingHostPresence.setForeground(other)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertTrue(net.bible.sharedcore.reading.ReadingHostPresence.isForeground(other))
        } finally {
            controller.stop().destroy()
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(other)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun destroyedColdHostNeverBootstrapsAfterHeldRegistration() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java,
            net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING))
        val activity = controller.get()
        val other = Any()
        activity.usableBibleGate = { true }
        try {
            controller.create().start().resume().pause().stop().destroy()
            net.bible.sharedcore.reading.ReadingHostPresence.setForeground(other)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertTrue(net.bible.sharedcore.reading.ReadingHostPresence.isForeground(other))
            val field = activity.javaClass.getDeclaredField("readingAppBootstrapped").apply { isAccessible = true }
            assertFalse(field.getBoolean(activity))
        } finally {
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(other)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun destroyingPrecompositionColdHostRetractsVisibility() = runTest {
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java,
            net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING))
        val activity = controller.get()
        activity.usableBibleGate = { true }
        try {
            controller.create()
            controller.destroy()
            assertFalse(net.bible.sharedcore.reading.ReadingHostPresence.isForeground(activity))
            assertFalse(net.bible.sharedcore.reading.ReadingViewVisibility.isVisible)
        } finally {
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
        }
    }

    @Test
    fun pausedColdDeepLinkWaitsForResumeAndRecordsOldVerseOnce() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val history = org.koin.core.context.GlobalContext.get().get<net.bible.service.history.HistoryManager>()
        val historyField = net.bible.service.history.HistoryManager::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previousHistory = historyField.get(null)
        historyField.set(null, history)
        val intent = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING)
            .putExtra("openLink", "https://stepbible.org/?q=reference=John.3.16")
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java, intent)
        val activity = controller.get()
        val other = Any()
        activity.usableBibleGate = { true }
        var restored = false
        val sub = net.bible.android.control.page.window.WorkspaceChanges.changes.subscribe {
            if (!restored) {
                val target = activity.hostWindowRepository.activeWindow.targetLinksWindow
                val bible = net.bible.service.common.CommonUtils.defaultBible
                target.pageManager.setCurrentDocumentAndKey(bible, bible.getKey("Gen.1.1"), addHistoryItem = false)
                restored = true
            }
        }
        try {
            controller.create().start().resume().pause()
            net.bible.sharedcore.reading.ReadingHostPresence.setForeground(other)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { if (!restored) org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertTrue(net.bible.sharedcore.reading.ReadingHostPresence.isForeground(other))
            val window = activity.hostWindowRepository.activeWindow.targetLinksWindow
            val oldKey = window.pageManager.currentPage.singleKey!!.getOsisRef()
            assertEquals("Gen.1.1", oldKey)
            val depth = history.getHistory(window.id).size
            controller.resume()
            val recorded = history.getHistory(window.id).last() as net.bible.service.history.KeyHistoryItem
            assertEquals(oldKey, recorded.key.getOsisRef())
            assertEquals("John.3.16", window.pageManager.currentPage.singleKey!!.getOsisRef())
            assertEquals(depth + 1, history.getHistory(window.id).size)
            controller.pause().resume()
            assertEquals(depth + 1, history.getHistory(window.id).size)
        } finally {
            sub.cancel()
            controller.pause().stop().destroy()
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(other)
            history.clear()
            historyField.set(null, previousHistory)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun reclaimedWorkspaceIsReloadedBeforeDeferredDeepLink() = runTest {
        val savedInitialized = net.bible.service.common.CommonUtils.initialized
        val queuedScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        MyDocumentBookManager.launcher = OrderedLauncher(queuedScope)
        net.bible.service.common.CommonUtils.initialized = false
        val history = org.koin.core.context.GlobalContext.get().get<net.bible.service.history.HistoryManager>()
        val historyField = net.bible.service.history.HistoryManager::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previousHistory = historyField.get(null)
        historyField.set(null, history)
        val intent = net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING)
            .putExtra("openLink", "https://stepbible.org/?q=reference=John.3.16")
        val controller = org.robolectric.Robolectric.buildActivity(
            net.bible.android.view.activity.nav.NavHostComposeActivity::class.java, intent)
        val activity = controller.get()
        val other = Any()
        // Cold-start tests restart Koin; CommonUtils' lazy WindowControl may still be the old one.
        val currentWindowControl = org.koin.core.context.GlobalContext.get().get<net.bible.android.control.page.window.WindowControl>()
        val previousOwner = currentWindowControl.windowRepository
        activity.usableBibleGate = { true }
        var resumeReloads = 0
        var countResumeReloads = false
        var restored = false
        val sub = net.bible.android.control.page.window.WorkspaceChanges.changes.subscribe {
            if (countResumeReloads) resumeReloads++
            if (!restored) {
                val target = activity.hostWindowRepository.activeWindow.targetLinksWindow
                val bible = net.bible.service.common.CommonUtils.defaultBible
                target.pageManager.setCurrentDocumentAndKey(bible, bible.getKey("Gen.1.1"), addHistoryItem = false)
                restored = true
            }
        }
        try {
            controller.create().start().resume().pause()
            net.bible.sharedcore.reading.ReadingHostPresence.setForeground(other)
            testScheduler.advanceUntilIdle()
            MyDocumentBookManager.launcher.join("mydocument-sync")
            repeat(20) { if (!restored) org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).runOneTask() }
            assertTrue(net.bible.sharedcore.reading.ReadingHostPresence.isForeground(other))
            val window = activity.hostWindowRepository.activeWindow.targetLinksWindow
            val oldKey = window.pageManager.currentPage.singleKey!!.getOsisRef()
            assertEquals("Gen.1.1", oldKey)
            activity.hostWindowRepository.saveIntoDb(false)
            val owner = net.bible.android.control.page.window.WindowRepository(syncScope)
            currentWindowControl.windowRepository = owner
            assertTrue(currentWindowControl.windowRepository !== activity.hostWindowRepository)
            countResumeReloads = true
            controller.resume()
            assertTrue("actual reclaim must reload the workspace", resumeReloads > 0)
            val restoredWindow = activity.hostWindowRepository.activeWindow.targetLinksWindow
            val depth = 0
            val recorded = history.getHistory(restoredWindow.id).last() as net.bible.service.history.KeyHistoryItem
            assertEquals(oldKey, recorded.key.getOsisRef())
            assertEquals("John.3.16", restoredWindow.pageManager.currentPage.singleKey!!.getOsisRef())
            assertEquals(depth + 1, history.getHistory(restoredWindow.id).size)
            controller.pause().resume()
            assertEquals(depth + 1, history.getHistory(restoredWindow.id).size)
        } finally {
            sub.cancel()
            controller.pause().stop().destroy()
            currentWindowControl.windowRepository = previousOwner
            net.bible.sharedcore.reading.ReadingViewVisibility.setActivityVisible(activity, false)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(activity)
            net.bible.sharedcore.reading.ReadingHostPresence.clearForeground(other)
            history.clear()
            historyField.set(null, previousHistory)
            queuedScope.cancel()
            net.bible.service.common.CommonUtils.initialized = savedInitialized
        }
    }

    @Test
    fun registeredDocumentResolvesItsPages() = runTest {
        val book = Books.installed().getBook("MyDoc_Test")!!

        val key = book.getKey("page_one")

        assertEquals("page_one", key.getOsisRef())
        assertEquals("First page", key.getName())
    }

    @Test
    fun refreshDocumentPicksUpPagesAddedAfterActivation() = runTest {
        val book = Books.installed().getBook("MyDoc_Test")!!
        // Activate the book so its key map is built from the one-page state.
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")

        assertEquals("page_two", book.getKey("page_two").getOsisRef())
    }

    @Test
    fun refreshDocumentRecoversFromAKeyMapBuiltWhileTheDocumentHadNoPages() = runTest {
        val empty = MyDocument(name = "Empty document", initials = "MyDoc_Empty")
        dao.insert(empty)
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
    fun syncUpdateResolvesPagesAddedByAnotherDevice() = runTest {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        addPage("page_two", "Second page")
        MyDocumentBookManager.handleSyncEvent(syncEventForPages("page_two"))

        val refreshed = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", refreshed.getKey("page_two").getOsisRef())
        assertEquals("page_one", refreshed.getKey("page_one").getOsisRef())
    }

    @Test
    fun syncUpdateKeepsTheBookInstanceOpenWindowsAreHolding() = runTest {
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
    fun syncUpdateDropsPagesDeletedOnAnotherDevice() = runTest {
        addPage("page_two", "Second page")
        MyDocumentBookManager.refreshDocument("MyDoc_Test")
        val book = Books.installed().getBook("MyDoc_Test")!!
        assertEquals("page_two", book.getKey("page_two").getOsisRef())

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
        MyDocumentBookManager.handleSyncEvent(listOf(entry))

        assertTrue(book.getGlobalKeyList().none { it.getOsisRef() == "page_two" })
        assertEquals("page_one", book.getKey("page_one").getOsisRef())
    }

    @Test
    fun syncUpdateRegistersDocumentsCreatedOnAnotherDevice() = runTest {
        val other = MyDocument(name = "Other document", initials = "MyDoc_Other")
        dao.insert(other)
        addPage("page_other", "Other page", documentId = other.id)
        val page = dao.pageByKeyWithContent(other.id, "page_other")!!

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
    fun syncUpdateUnregistersDocumentsDeletedOnAnotherDevice() = runTest {
        dao.deleteDocumentWithPages(dao.documentById(document.id)!!)

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
    fun syncUpdateRebuildsMetadataWhenDocumentIsRenamed() = runTest {
        val book = Books.installed().getBook("MyDoc_Test")!!
        book.getKey("page_one")

        val renamed = dao.documentById(document.id)!!.apply { name = "Renamed document" }
        dao.update(renamed)
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
    fun aMyDocumentsSyncReachesTheManagerOnMain() = runTest {
        MyDocumentBookManager.mainDispatcher = Dispatchers.Main
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
            net.bible.test.DatabaseResetter.runResetBlocking {
                MyDocumentBookManager.launcher.join("mydocument-sync")
            }
            assertEquals("page_synced", Books.installed().getBook("MyDoc_Test")!!.getKey("page_synced").getOsisRef())
            assertEquals(listOf(MyDocumentChange.DocumentUpdated("MyDoc_Test")), seen)
            assertSame(Looper.getMainLooper(), deliveryLooper)
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun syncEmitsDocumentUpdatedAfterRefreshingRegistrations() = runTest {
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
    fun deletingAnAiDocumentPageEmitsDocumentUpdatedThenAiDocPages() = runTest {
        val aiDocument = MyDocumentBookManager.getOrCreateAIDocument()
        addPage("ai_page", "AI page", documentId = aiDocument.id)
        MyDocumentBookManager.refreshDocument(MyDocumentBookManager.AI_DOCUMENTS_INITIALS)
        val id = dao.pageByKeyWithContent(aiDocument.id, "ai_page")!!.id
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
            assertNull(dao.pageById(id))
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun suspendingDeleteOfAnAiDocumentPageEmitsTheSameChanges() = runTest {
        val aiDocument = MyDocumentBookManager.getOrCreateAIDocument()
        addPage("ai_page", "AI page", documentId = aiDocument.id)
        MyDocumentBookManager.refreshDocument(MyDocumentBookManager.AI_DOCUMENTS_INITIALS)
        val id = dao.pageByKeyWithContent(aiDocument.id, "ai_page")!!.id
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
            assertNull(dao.pageById(id))
            assertFalse(MyDocumentBookManager.deleteAIDocumentPage(id))
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun notifyAiDocPagesChangedReachesSubscribersUnchanged() = runTest {
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
