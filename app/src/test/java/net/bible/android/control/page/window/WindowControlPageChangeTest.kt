package net.bible.android.control.page.window

import android.os.Looper
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.PageChange
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.page.BibleJavascriptInterface
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewHostCallbacks
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowControlPageChangeTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private val dispatchThreads = CopyOnWriteArrayList<Thread>()
    @OptIn(InternalCoroutinesApi::class)
    private val recordingDispatcher = object : CoroutineDispatcher(), Delay {
        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            (Dispatchers.Main as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
        }

        override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext) =
            (Dispatchers.Main as Delay).invokeOnTimeout(timeMillis, block, context)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchThreads.add(Thread.currentThread())
            Dispatchers.Main.dispatch(context, block)
        }
    }

    @Before fun setUp() {
        // A fresh subscriber avoids the cached singleton whose handlers test teardown clears.
        windowControl = WindowControl()
        repo = WindowRepository(CoroutineScope(recordingDispatcher))
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
    }

    @After fun tearDown() {
        try {
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    /** Two synchronised visible windows, the second following the first. */
    private fun twoSyncedWindows(): Pair<Window, Window> {
        val a = repo.activeWindow
        val b = repo.addNewWindow(a)
        a.isSynchronised = true
        b.isSynchronised = true
        val kjv = Books.installed().getBook("KJV")!!
        a.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(KJVA, BibleBook.GEN, 1, 1))
        b.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(KJVA, BibleBook.GEN, 1, 1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        return a to b
    }

    @Test fun syncsWhenEmittedOffTheMainThread() {
        val (a, b) = twoSyncedWindows()
        a.pageManager.currentBible.doSetKey(Verse(KJVA, BibleBook.JOHN, 3, 16))
        dispatchThreads.clear()
        val failure = AtomicReference<Throwable?>()
        thread {
            try {
                PassageChangeMediator.onCurrentVerseChanged(a)
                // The real WindowSync debounce must already be queued before emit returns;
                // draining main first would also let an incorrect subscribeOnMain pass.
                assertEquals(1, dispatchThreads.size)
                assertSame(Thread.currentThread(), dispatchThreads.single())
            } catch (t: Throwable) {
                failure.set(t)
            }
        }.join()
        failure.get()?.let { throw it }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BibleBook.JOHN, b.pageManager.currentBible.singleKey.book)
    }

    @Test fun contentLoaderEmitsForItsOwnWindowBeforeReturning() {
        val (_, window) = twoSyncedWindows()
        val page = window.pageManager.currentGeneralBook
        val book = Books.installed().getBook("KJV")!!
        val key = Verse(KJVA, BibleBook.GEN, 1, 1)
        page.onlySetCurrentDocument(book)
        val changes = mutableListOf<PageChange>()
        val subscription = PassageChangeMediator.changes.subscribe {
            if (it is PageChange.VerseChanged) changes.add(it)
        }
        try {
            assertIs<OsisDocument>(page.getPageContent(key))
            assertEquals(listOf<PageChange>(PageChange.VerseChanged(window)), changes)
        } finally {
            subscription.cancel()
        }
    }

    @Test fun javascriptScrollEmitsOnlyForANewCommentaryEntryAndTheOwningWindow() {
        val (_, window) = twoSyncedWindows()
        val page = window.pageManager.currentCommentary
        val book = Books.installed().getBook("KJV")!!
        val oldKey = Verse(KJVA, BibleBook.GEN, 1, 1)
        val nextKey = Verse(KJVA, BibleBook.GEN, 1, 2)
        page.onlySetCurrentDocument(book)
        page.doSetKey(oldKey)
        // Keep the real commentary and parsing logic while selecting it without a module-category switch.
        CurrentPageManager::class.java.getDeclaredField("currentPage").apply {
            isAccessible = true
            set(window.pageManager, page)
        }
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 },
            topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Not used") })
        val view = BibleView(host, callbacks, WeakReference(window), windowControl,
            GlobalContext.get().get(), PageTiltScrollControl(),
            GlobalContext.get().get(), GlobalContext.get().get(),
            GlobalContext.get().get(), GlobalContext.get().get())
        // The scroll bridge needs no client initialisation; install only the teardown collaborator.
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(
                view, PageTiltScrollControl()))
        }
        view.firstDocument = OsisDocument(OsisFragment(SwordContentFacade.readOsisFragment(book, oldKey), oldKey, book), book, oldKey)
        val bridge = BibleJavascriptInterface(view)
        val changes = mutableListOf<PageChange>()
        val subscription = PassageChangeMediator.changes.subscribe {
            if (it is PageChange.VerseChanged) changes.add(it)
        }
        try {
            bridge.scrolledToOrdinal(oldKey.getOsisRef(), 5)
            assertTrue(changes.isEmpty())
            bridge.scrolledToOrdinal(nextKey.getOsisRef(), 9)
            assertEquals(listOf<PageChange>(PageChange.VerseChanged(window)), changes)
            assertEquals(nextKey.getOsisRef(), page.key.getOsisRef())
            bridge.scrolledToOrdinal(nextKey.getOsisRef(), 10)
            assertEquals(listOf<PageChange>(PageChange.VerseChanged(window)), changes)
        } finally {
            subscription.cancel()
            view.destroy()
        }
    }

    @Test fun ignoresAWindowOfAnotherRepository() {
        val (a, b) = twoSyncedWindows()
        val otherRepo = WindowRepository(CoroutineScope(Dispatchers.Main))
        val foreign = Window(WorkspaceEntities.Window(workspaceId = IdType(), isSynchronized = true,
            isPinMode = false, isLinksWindow = false,
            windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.toString()), id = IdType()),
            a.pageManager, otherRepo)
        a.pageManager.currentBible.doSetKey(Verse(KJVA, BibleBook.JOHN, 3, 16))
        PassageChangeMediator.onCurrentVerseChanged(foreign)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BibleBook.GEN, b.pageManager.currentBible.singleKey.book)
    }
}
