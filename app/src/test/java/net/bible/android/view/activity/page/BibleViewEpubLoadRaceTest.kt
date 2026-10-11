package net.bible.android.view.activity.page

import android.net.Uri
import com.nhaarman.mockitokotlin2.*
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.*
import net.bible.android.control.page.window.*
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.link.LinkControl
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import net.bible.service.sword.epub.EpubBackend
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.koin.core.context.GlobalContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewEpubLoadRaceTest {
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var view: BibleView
    private lateinit var window: Window
    private lateinit var host: ReadingHostActivity
    private val control = WindowControl()
    private val links get() = GlobalContext.get().get<LinkControl>()
    private val bookmarks get() = GlobalContext.get().get<BookmarkControl>()

    @Before fun setup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repo = WindowRepository(CoroutineScope(SupervisorJob() + Dispatchers.Main))
        control.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
        window = repo.activeWindow
        host = mock()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val lifecycle = LifecycleRegistry.createUnsafe(host)
        lifecycle.currentState = Lifecycle.State.STARTED
        whenever(host.lifecycle).thenReturn(lifecycle)
        val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false }, { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { error("Unused") })
        view = BibleView(host, callbacks, WeakReference(window), control, GlobalContext.get().get(), PageTiltScrollControl(), links, bookmarks, GlobalContext.get().get(), GlobalContext.get().get())
        window.bibleView = view
        view.lastUpdated = 1
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply { isAccessible = true; set(view, PageTiltScroller(view, PageTiltScrollControl())) }
    }

    @After fun cleanup() {
        try {
            if (::view.isInitialized) view.destroy()
            repo.scope.cancel()
            Dispatchers.resetMain()
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            Dispatchers.resetMain()
        }
    }

    private fun field(name: String): Any? = BibleView::class.java.getDeclaredField(name).run { isAccessible = true; get(view) }
    private fun doc(id: String, entered: CountDownLatch? = null, release: CountDownLatch? = null): Document = object : Document {
        override suspend fun contentMap(): Map<String, String> {
            entered?.countDown()
            check(release?.await(10, TimeUnit.SECONDS) != false)
            return mapOf("id" to "'$id'")
        }
    }

    @Test fun overlappingIoLoadsPublishOnlyTheNewerLocationAndDocument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val serialEntered = CountDownLatch(1)
        val serialRelease = CountDownLatch(1)
        val oldKey = DefaultLeafKeyList("old", "old")
        val newKey = DefaultLeafKeyList("new", "new")
        val old = launch(Dispatchers.IO) { view.loadDocument(doc("old", serialEntered, serialRelease), key = oldKey, anchorOrdinal = OrdinalRange(1), htmlId = "old") }
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { while (serialEntered.count != 0L) { runCurrent(); delay(10) } } }
            assertNull("Pending serialization must not publish location", view.initialKey)
            val newer = launch(Dispatchers.IO) { view.loadDocument(doc("new"), key = newKey, anchorOrdinal = OrdinalRange(9), htmlId = "new") }
            while (newer.isActive) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } }
            serialRelease.countDown()
            while (old.isActive) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } }
            assertEquals(newKey, view.initialKey)
            assertEquals(9, (field("initialAnchorOrdinal") as OrdinalRange).start)
            assertEquals("new", field("initialHtmlId"))
            assertEquals("{'id': 'new'}", field("latestDocumentStr"))
        } finally {
            serialRelease.countDown(); old.cancel()
        }
    }
    @Test fun concurrentIoGenerationsAreUniqueAndStaleMainPublicationIsRejected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val count = 8
        val entered = CountDownLatch(count)
        val release = CountDownLatch(1)
        val loads = (1..count).map { i ->
            launch(Dispatchers.IO) {
                view.loadDocument(doc("$i", entered, release), key = DefaultLeafKeyList("$i", "$i"), anchorOrdinal = OrdinalRange(i), htmlId = "$i")
            }
        }
        val page = window.pageManager.currentPage as CurrentPageBase
        val original = page.currentDocument
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { while (entered.count != 0L) { runCurrent(); delay(10) } } }
            assertEquals(count.toLong(), field("documentLoadGeneration"))
            assertNull(view.initialKey)
            assertNull(view.firstDocument)
            page.onlySetCurrentDocument(Books.installed().getBooks().first { it != original })
            release.countDown()
            loads.forEach { it.join() }
            assertNull(view.firstDocument)
            assertNull(view.initialKey)
            assertNull(field("initialAnchorOrdinal"))
            assertNull(field("initialHtmlId"))
        } finally {
            page.onlySetCurrentDocument(original)
            release.countDown()
            loads.forEach { it.cancel() }
        }
    }

    @Test fun firstReaderConfigurationIncludesPersistedFavouriteLabels() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val label = net.bible.android.database.bookmarks.BookmarkEntities.Label(name = "Persisted favourite", favourite = true)
        val dao = net.bible.service.db.DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        dao.insert(label)
        BibleView::class.java.getDeclaredField("htmlLoadingOngoing").apply { isAccessible = true; set(view, false) }
        val load = launch(Dispatchers.IO) { view.loadDocument(doc("first")) }
        try {
            load.join()
            // The detached real WebView queues its first reader JS; flush then inspect actual evaluation.
            BibleView::class.java.getDeclaredMethod("flushTasks").apply { isAccessible = true; invoke(view) }
            val script = org.robolectric.Shadows.shadowOf(view).lastEvaluatedJavascript
            assertTrue(script.contains("set_config"))
            assertTrue("First configuration must contain persisted favourites: $script", script.contains("favouriteLabels: [\"${label.id}\"]"))
        } finally {
            load.cancel()
        }
    }

    @Test fun epubUriFromAnotherDocumentOpensTarget() = checkEpubUri(false)

    @Test fun epubUriRejectsChangedSourceDocument() = checkEpubUri(true)

    private fun checkEpubUri(changeSource: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        fixture.setUp()
        val dir = fixture.buildEpub("uri-target")
        val state = net.bible.service.sword.epub.EpubBackendState(dir, net.bible.test.testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()))
        val target = SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData()))
        val oldMode = links.windowMode
        try {
            Books.installed().addBook(target)
            links.windowMode = net.bible.android.control.link.WindowMode.WINDOW_MODE_THIS
            val source = window.pageManager.currentPage.currentDocument
            assertNotEquals(target, source)
            assertTrue(view.openLink(Uri.parse("epub-ref://?book=${target.initials}&toKey=ch1&toId=")))
            if (changeSource) {
                val page = window.pageManager.currentPage
                (page as CurrentPageBase).onlySetCurrentDocument(target)
                // Changing source to another document before the deferred lookup must suppress navigation.
                (field("epubLinks\$delegate") as Lazy<*>).value.let {
                    val queue = it as net.bible.sharedcore.platform.OrderedLauncher
                    queue.join("epub-links")
                }
                assertEquals(target, page.currentDocument)
                assertNotEquals(state.keys.first().getOsisRef(), page.key?.getOsisRef())
            } else {
                withContext(Dispatchers.Default) {
                    withTimeout(10_000) {
                        while (window.pageManager.currentPage.currentDocument != target) { runCurrent(); delay(10) }
                    }
                }
                assertEquals(target, window.pageManager.currentPage.currentDocument)
            }
        } finally {
            links.windowMode = oldMode
            Books.installed().removeBook(target)
            fixture.tearDown()
        }
    }

}
