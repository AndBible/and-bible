package net.bible.android.view.activity.page

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [net.bible.sharedui.reading.bridge.ReadingBridge] lifecycle and navigation family, driven through
 * a real [BibleView] and the adapter ([BibleJavascriptInterface]) as the JS bridge uses it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingBridgeNavigationFamilyTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private val views = mutableListOf<BibleView>()
    private val activities = mutableListOf<org.robolectric.android.controller.ActivityController<android.app.Activity>>()
    private val kjv get() = Books.installed().getBook("KJV")!!

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        repo.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(KJVA, BibleBook.JOHN, 3, 1))
    }

    @After fun tearDown() {
        try {
            views.forEach { runCatching { it.doDestroy() } }
            activities.forEach { runCatching { it.pause().stop().destroy() } }
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    private fun newView(attach: Boolean = false): BibleView {
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        whenever(host.lifecycle).thenReturn(LifecycleRegistry(host).apply { currentState = Lifecycle.State.RESUMED })
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock<ActivityBase>(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 },
            topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Not used") })
        val view = BibleView(host, callbacks, WeakReference(repo.activeWindow), windowControl,
            GlobalContext.get().get(), PageTiltScrollControl(),
            GlobalContext.get().get(), GlobalContext.get().get(),
            GlobalContext.get().get(), GlobalContext.get().get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        views += view
        view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
        if (attach) {
            val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).also { activities += it }
            val activity = controller.setup().get()
            activity.setContentView(view)
        }
        return view
    }

    private fun idleUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private val currentChapter get() = repo.activeWindow.pageManager.currentBible.singleKey.chapter

    @Test fun goToNextChapterMovesTheWindowsVerse() {
        val bridge = BibleJavascriptInterface(newView())
        assertEquals(3, currentChapter)
        bridge.goToNextChapter()
        idleUntil { currentChapter != 3 }
        assertEquals(4, currentChapter)
    }

    @Test fun scrolledToOrdinalUpdatesCurrentVerse() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val key = Verse(KJVA, BibleBook.GEN, 1, 1)
        val book = kjv as org.crosswire.jsword.book.sword.SwordBook
        view.firstDocument = net.bible.android.control.page.BibleDocument(
            emptyList(), org.crosswire.jsword.passage.VerseRange(KJVA, key),
            net.bible.android.misc.OsisFragment(
                net.bible.service.sword.SwordContentFacade.readOsisFragment(book, key, strings = net.bible.test.testCoreStrings()), key, book),
            book, key)
        view.initialKey = Verse(KJVA, BibleBook.JOHN, 3, 1)
        BibleJavascriptInterface(view).scrolledToOrdinal("John.3.1", 5)
        assertEquals(Verse(KJVA, 5).ordinal, bible.singleKey.ordinal)
    }

    @Test fun parseRefAnswersOsisOrNull() {
        val view = newView(attach = true)
        val bridge = BibleJavascriptInterface(view)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        bridge.parseRef(3, "Joh 3:16")
        shadowOf(Looper.getMainLooper()).idle()
        val ok = shadowOf(view).lastEvaluatedJavascript!!
        assertTrue(ok, ok.startsWith("bibleView.response(3, ") && ok.contains("John.3.16"))
        bridge.parseRef(4, "zzz")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("bibleView.response(4, null);", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun callsAfterSessionDisposeAreIgnored() {
        val view = newView(attach = true)
        val bridge = BibleJavascriptInterface(view)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        view.session.dispose()
        bridge.goToNextChapter()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, currentChapter)
        bridge.requestMoreToEnd(7)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("bibleView.response(7, null);", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun onKeyDownAltArrowsFocusWindows() {
        val first = repo.activeWindow
        val second = repo.addNewWindow(first)
        repo.activeWindow = first
        val view = newView()
        val bridge = BibleJavascriptInterface(view)
        bridge.onKeyDown("AltArrowRight")
        idleUntil { repo.activeWindow !== first }
        assertNotSame(first, repo.activeWindow)
        val moved = repo.activeWindow
        bridge.onKeyDown("AltArrowLeft")
        idleUntil { repo.activeWindow !== moved }
        assertNotSame(moved, repo.activeWindow)
        assertTrue(second.id == moved.id)
    }
}
