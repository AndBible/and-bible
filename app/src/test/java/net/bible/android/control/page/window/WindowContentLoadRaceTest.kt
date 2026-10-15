package net.bible.android.control.page.window

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.Document
import net.bible.android.database.mydocument.AiDocMarkerInfo
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.view.activity.page.*
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Holds the real page's database read, before BibleView can allocate its load token. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class WindowContentLoadRaceTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() {
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
    }

    @Test fun anOlderHeldPageReadCannotReplaceTheNewWindowContent() = runTest(dispatcher, timeout = 30.seconds) {
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val database = DatabaseContainer.instance
        val originalDb = database.myDocumentDb
        val realDao = originalDb.myDocumentDao()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repoJob = SupervisorJob(coroutineContext[Job])
        val repo = WindowRepository(CoroutineScope(coroutineContext + repoJob))
        var view: BibleView? = null
        val heldDao = object : MyDocumentDao by realDao {
            override suspend fun aiDocMarkersForPage(bookInitials: String, bookKey: String): List<AiDocMarkerInfo> {
                val range = org.crosswire.jsword.passage.PassageKeyFactory.instance().getKey(
                    Versifications.instance().getVersification("KJV"), bookKey)
                if (range.contains(Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1))) {
                    entered.complete(Unit)
                    release.await()
                }
                return realDao.aiDocMarkersForPage(bookInitials, bookKey)
            }
        }
        val heldDb = mock<MyDocumentDatabase>()
        whenever(heldDb.myDocumentDao()).thenReturn(heldDao)
        try {
            control.windowRepository = repo
            repo.initializeAtEdge()
            val window = repo.activeWindow
            val host = mock<ReadingHostActivity>()
            whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
            val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false }, { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { error("Unused") })
            val koin = GlobalContext.get()
            view = BibleView(host, callbacks, WeakReference(window), control, koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
            window.bibleView = view
            view.lastUpdated = 1
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, PageTiltScroller(view, PageTiltScrollControl()))
            }
            val v11n = Versifications.instance().getVersification("KJV")
            val bible = Books.installed().getBook("KJV")!!
            database.myDocumentDb = heldDb
            window.pageManager.setCurrentDocumentAndKey(bible, Verse(v11n, BibleBook.GEN, 1, 1), false)
            window.loadText()
            entered.await()
            assertTrue("actual page DAO read must be held", entered.isCompleted && !release.isCompleted)
            window.pageManager.setCurrentDocumentAndKey(bible, Verse(v11n, BibleBook.GEN, 2, 1), false)
            window.loadText()
            awaitIo { view.firstDocument != null }
            val newerDocument = view.firstDocument
            val newerKey = view.initialKey
            assertTrue("fixture must publish Genesis 2", newerKey!!.getOsisRef().startsWith("Gen.2"))
            release.complete(Unit)
            awaitIo { repoJob.children.none { it.isActive } }
            assertSame("older currentPageContent must not replace the delivered document", newerDocument, view.firstDocument)
            assertEquals(newerKey, view.initialKey)
        } finally {
            release.complete(Unit)
            try {
                repoJob.cancelAndJoin()
                repo.windowList.forEach { it.destroy(destroyView = false) }
                view?.destroy()
            } finally {
                database.myDocumentDb = originalDb
                control.windowRepository = originalRepo
            }
        }
    }

    @Test fun staleWindowAdmissionCannotInvalidateANewerHeldSerialization() = runTest(dispatcher, timeout = 30.seconds) {
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val repoJob = SupervisorJob(coroutineContext[Job])
        val repo = WindowRepository(CoroutineScope(coroutineContext + repoJob))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var view: BibleView? = null
        try {
            control.windowRepository = repo
            repo.initializeAtEdge()
            val window = repo.activeWindow
            val host = mock<ReadingHostActivity>()
            whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
            val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false }, { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { error("Unused") })
            val koin = GlobalContext.get()
            val reader = BibleView(host, callbacks, WeakReference(window), control, koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
            view = reader
            window.bibleView = reader
            reader.lastUpdated = 1
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(reader, PageTiltScroller(reader, PageTiltScrollControl()))
            }
            val key = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 2, 1)
            window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV")!!, key, false)
            window.loadText()
            awaitIo { reader.firstDocument != null && repoJob.children.none { it.isActive } }
            val currentToken = window.contentLoadGeneration
            assertTrue("actual Window load must advance the source token", currentToken > 0)
            val newer = object : Document {
                override suspend fun contentMap(): Map<String, String> {
                    entered.complete(Unit)
                    release.await()
                    return mapOf("id" to "'newer'")
                }
            }
            val stale = object : Document {
                override suspend fun contentMap() = mapOf("id" to "'stale'")
            }
            val pending = launch(Dispatchers.IO) {
                reader.loadDocument(newer, key = key, windowContentGeneration = currentToken)
            }
            try {
                entered.await()
                assertFalse("newer serialization is actually held", release.isCompleted)
                reader.loadDocument(stale, windowContentGeneration = currentToken - 1)
                release.complete(Unit)
                pending.join()
                assertSame("stale admission must not invalidate the valid pending publication", newer, reader.firstDocument)
                assertEquals(key, reader.initialKey)
            } finally {
                release.complete(Unit)
                pending.cancelAndJoin()
            }
        } finally {
            release.complete(Unit)
            try {
                repoJob.cancelAndJoin()
                repo.windowList.forEach { it.destroy(destroyView = false) }
                view?.destroy()
            } finally {
                control.windowRepository = originalRepo
            }
        }
    }

    @Test fun aCancelledWindowDoesNotReadSettingsAtTheReadyViewBoundary() = runTest(dispatcher, timeout = 30.seconds) {
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val koin = GlobalContext.get()
        val originalSource = koin.get<net.bible.android.control.page.GlobalTextDisplaySettingsSource>()
        val tdsSnapshot = net.bible.test.KoinFactorySnapshot.of<net.bible.android.control.page.GlobalTextDisplaySettingsSource>()
        val settings = originalSource.current
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val guardReads = java.util.concurrent.atomic.AtomicInteger()
        val cancelledReads = java.util.concurrent.atomic.AtomicInteger()
        val armed = java.util.concurrent.atomic.AtomicBoolean(false)
        val repoJob = SupervisorJob(coroutineContext[Job])
        val repo = WindowRepository(CoroutineScope(coroutineContext + repoJob))
        var view: BibleView? = null
        val source = object : net.bible.android.control.page.GlobalTextDisplaySettingsSource {
            override val current: net.bible.android.database.WorkspaceEntities.TextDisplaySettings
                get() {
                    if (!armed.get() || !Thread.currentThread().name.startsWith("DefaultDispatcher")) return originalSource.current
                    if (repoJob.isCancelled) {
                        cancelledReads.incrementAndGet()
                        // Count the real attempted source access without touching a torn-down DB.
                        return settings
                    }
                    val current = originalSource.current
                    if (Thread.currentThread().stackTrace.any { it.className == Window::class.java.name && it.methodName.contains("isCurrentRequest") } && guardReads.incrementAndGet() == 2) {
                        // Hold the actual post-fetch key/settings read, whose blocking bridge has
                        // an independent Job. A ready view makes the next boundary non-suspending.
                        net.bible.service.db.blockingAtEdge {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                    return current
                }
        }
        try {
            koin.loadModules(listOf(org.koin.dsl.module {
                single<net.bible.android.control.page.GlobalTextDisplaySettingsSource> { source }
            }))
            control.windowRepository = repo
            repo.initializeAtEdge()
            val window = repo.activeWindow
            val host = mock<ReadingHostActivity>()
            whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
            val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false }, { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { error("Unused") })
            val reader = BibleView(host, callbacks, WeakReference(window), control, koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
            view = reader
            window.bibleView = reader
            reader.lastUpdated = 1
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(reader, PageTiltScroller(reader, PageTiltScrollControl()))
            }
            val key = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 2, 1)
            window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV")!!, key, false)
            armed.set(true)
            window.loadText()
            entered.await()
            assertEquals("actual pre-read and post-fetch guards must run", 2, guardReads.get())
            assertFalse("actual post-fetch settings source must remain held", release.isCompleted)
            repoJob.cancel()
            assertFalse("held synchronous reader must keep ownership during cancellation", repoJob.isCompleted)
            release.complete(Unit)
            repoJob.join()
            assertEquals("cancelled Window must not begin another key/settings read at the ready-view boundary", 0, cancelledReads.get())
            assertNull("cancelled Window must not publish a document", reader.firstDocument)
        } finally {
            release.complete(Unit)
            try {
                repoJob.cancelAndJoin()
                repo.windowList.forEach { it.destroy(destroyView = false) }
                view?.destroy()
            } finally {
                control.windowRepository = originalRepo
                tdsSnapshot.restore()
            }
        }
    }

    private suspend fun TestScope.awaitIo(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                while (!condition()) {
                    runCurrent()
                    yield()
                }
            }
        }
    }
}
