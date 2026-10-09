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

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Fix round 1, minor (c): the bridge queues a window's bookmark writes under `bibleView.window.id`
 * (the key the rest of the app uses for that window, e.g. [BibleView]'s own writes), so a write from
 * one window waits for that window's queue and not for another window's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // note writes use UPSERT, which SDK 33's SQLite lacks
class BibleJavascriptInterfaceBookmarkOrderTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private val views = mutableListOf<BibleView>()
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
    }

    @After fun tearDown() {
        try {
            views.forEach { it.destroy() }
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    private fun bridgeFor(window: Window): BibleJavascriptInterface {
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
        // Only the teardown collaborator destroy() needs.
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        views += view
        return BibleJavascriptInterface(view)
    }

    private fun bookmark(control: BookmarkControl, ref: String): BibleBookmarkWithNotes = runBlocking {
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), ref),
            null, true, null,
        ))
    }

    private fun note(b: BibleBookmarkWithNotes) = runBlocking { dao.bibleBookmarkById(b.id) }?.notes

    @Test fun `a note save waits for its own window's queue and not for another window's`() {
        val control: BookmarkControl = GlobalContext.get().get()
        val jsActions: BookmarkJsActions = GlobalContext.get().get()
        val a = repo.activeWindow
        val b = repo.addNewWindow(a)
        val bridgeA = bridgeFor(a)
        val bridgeB = bridgeFor(b)
        val inA = bookmark(control, "Ps 119:1")
        val inB = bookmark(control, "Ps 119:2")

        val gate = CompletableDeferred<Unit>()
        val stuck = jsActions.launch(a.id) { gate.await() } // window a's queue is busy
        try {
            bridgeA.saveBookmarkNote(inA.id.toString(), "from a")
            bridgeB.saveBookmarkNote(inB.id.toString(), "from b")
            runBlocking {
                withTimeout(10_000) { while (note(inB) == null) delay(20) }
                delay(300) // window a's save would have landed by now if it did not wait for a's queue
            }
            assertEquals("from b", note(inB))
            assertNull("window a's save ran past its own window's queue", note(inA))
        } finally {
            // Also on a failed assertion: a job left waiting would stay in the app scope of the
            // suite's single JVM and hold window a's queue for every later test.
            gate.complete(Unit)
        }
        runBlocking {
            withTimeout(10_000) { stuck.join() }
            withTimeout(10_000) { while (note(inA) == null) delay(20) }
        }
        assertEquals("from a", note(inA))
    }
}
