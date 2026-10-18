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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.CommonUtils
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ReadingSheets
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Content family (share, copy, help, LLM, MyDocument, prompt editor) of [ReadingBridge], driven on a
 * bare [BibleWindowSession] with a recording surface and sheet host (no WebView).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingBridgeContentFamilyTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var hostScope: CoroutineScope
    private lateinit var session: BibleWindowSession
    private lateinit var bridge: ReadingBridge
    private lateinit var launcher: OrderedLauncher
    private val appScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val evaluated = CopyOnWriteArrayList<String>()
    private val surface = RecordingSurface()
    private val sheets = RecordingSheets()

    private class RecordingSurface : ReadingWindowSurface {
        val copies = CopyOnWriteArrayList<Pair<String?, String>>()
        val htmlShares = CopyOnWriteArrayList<Pair<String, String>>()
        override fun copyText(label: String?, text: String) { copies += label to text }
        override fun shareText(text: String, subject: String?) {}
        override fun shareHtml(html: String, title: String) { htmlShares += html to title }
        override fun stopSelection(removeRanges: Boolean) {}
        override fun toast(text: String) {}
        override fun openWebSearch(query: String) {}
    }

    private class RecordingSheets : ReadingSheets {
        val shared = CopyOnWriteArrayList<ShareVersesInput>()
        val helps = CopyOnWriteArrayList<Pair<String?, String>>()
        val regenerates = CopyOnWriteArrayList<Pair<IdType, IdType>>()
        @Volatile var confirm: (() -> Unit)? = null
        var autoConfirm = false
        override fun showReadHistory(bookId: String, chapter: Int) {}
        override fun showCompare(range: VerseRange) {}
        override fun showShare(input: ShareVersesInput) { shared += input }
        override fun showPromptSelector(
            selection: Selection,
            context: net.bible.service.llm.PromptContext,
            category: net.bible.android.control.page.DocumentCategory?,
        ) {}
        override fun showRegenerate(pageId: IdType, windowId: IdType) { regenerates += pageId to windowId }
        override fun showHelp(title: String?, html: String) { helps += title to html }
        override fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) {
            if (autoConfirm) onConfirm() else confirm = onConfirm
        }
    }

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        launcher = OrderedLauncher(appScope)
        MyDocumentBookManager.launcher = launcher
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val host = object : JsHost {
            override val isMainThread = true
            override val isAttached = true
            override val mainDispatcher = Dispatchers.Unconfined
            override fun post(block: () -> Unit) = block()
            override fun evaluate(js: String, callback: ((String) -> Unit)?) {
                evaluated += js
                callback?.invoke("null")
            }
        }
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        val bookmarkControl = BookmarkControl(windowControl, testAppSettings(), testCoreStrings(), launcher,
            net.bible.android.platform.androidSaxTextPolicy())
        session = BibleWindowSession(
            repo.activeWindow, channel, { hostScope }, surface, mock<ReadingNavigator>(),
            windowControlProvider = { windowControl },
            bookmarkControlProvider = { bookmarkControl },
        )
        session.hostSheets = sheets
        bridge = ReadingBridge(session,
            progressJsActionsProvider = { ProgressJsActions(launcher) },
            bookmarkJsActionsProvider = { BookmarkJsActions(launcher, bookmarkControl) })
    }

    @After fun tearDown() {
        try {
            session.dispose()
            appScope.cancel()
            hostScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    /** Runs the main looper (the bridge posts to Dispatchers.Main) until [done] holds. */
    private fun awaitMain(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("main-thread work did not finish", done())
    }

    private fun john316Ordinal(): Int {
        val book = Books.installed().getBook("KJV") as SwordBook
        return book.versification.getOrdinal(Verse(book.versification, BibleBook.JOHN, 3, 16))
    }

    @Test fun copyVerseUsesTheVerseTextNotTheSelection() {
        bridge.copyVerse("KJV", john316Ordinal(), -1)
        awaitMain { surface.copies.isNotEmpty() }
        val (label, text) = surface.copies.single()
        assertTrue("label names the verse: $label", label?.contains("John") == true)
        assertTrue("clipboard text is the KJV verse: $text", text.contains("For God so loved the world"))
    }

    @Test fun regenerateCarriesTheAskingWindowsId() {
        val pageId = IdType()
        bridge.regenerateMyDocumentPage(pageId.toString())
        awaitMain { sheets.regenerates.isNotEmpty() }
        // The window whose page asked, which the host must target (not whichever window is active).
        assertEquals(pageId to session.window.id, sheets.regenerates.single())
    }

    @Test fun copyVerseAfterDisposeIsIgnored() {
        session.dispose()
        bridge.copyVerse("KJV", john316Ordinal(), -1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(surface.copies.isEmpty())
    }

    @Test fun shareVerseGoesToTheShareSheet() {
        bridge.shareVerse("KJV", john316Ordinal(), -1)
        awaitMain { sheets.shared.isNotEmpty() }
        assertTrue(sheets.shared.single().verses.isNotEmpty())
    }

    @Test fun shareVerseAfterDisposeIsIgnored() {
        session.dispose()
        bridge.shareVerse("KJV", john316Ordinal(), -1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(sheets.shared.isEmpty())
    }

    @Test fun helpDialogShowsTheHelpSheet() {
        bridge.helpDialog("<b>x</b>", "Title")
        assertEquals(listOf<Pair<String?, String>>("Title" to "<b>x</b>"), sheets.helps.toList())
    }

    @Test fun helpBookmarksShowsTheBookmarksHelpWithTheTutorialLink() {
        bridge.helpBookmarks()
        val (title, html) = sheets.helps.single()
        assertEquals(testCoreStrings().bookmarksAndMyNotesTitle, title)
        assertTrue(html.contains("youtube.com/playlist"))
        assertTrue(html.contains(testCoreStrings().verseTip))
    }

    @Test fun shareHtmlTitleUsesTheFirstDocumentName() {
        session.firstDocument = MultiFragmentDocument(emptyList())
        bridge.shareHtml("<p>a</p>")
        session.firstDocument = MyNotesDocument(emptyList(), VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1)))
        bridge.shareHtml("<p>b</p>")
        assertEquals(
            listOf("<p>a</p>" to testCoreStrings().multiDescription, "<p>b</p>" to testCoreStrings().myNotesAbbreviation),
            surface.htmlShares.toList(),
        )
    }

    @Test fun getMyDocumentPageRawContentOfMissingPageAnswersNull() {
        bridge.getMyDocumentPageRawContent(41, "AI", "no-such-page")
        awaitMain { evaluated.isNotEmpty() }
        assertEquals(listOf("bibleView.response(41, null);"), evaluated.toList())
    }

    @Test fun getMyDocumentPageRawContentAfterDisposeAnswersNull() {
        session.dispose()
        bridge.getMyDocumentPageRawContent(42, "AI", "no-such-page")
        assertEquals(listOf("bibleView.response(42, null);"), evaluated.toList())
    }

    @Test fun deleteMyDocumentPageClosesARemovableWindow() {
        val extra = repo.addNewWindow()
        session.window = extra
        sheets.autoConfirm = true
        val before = repo.sortedWindows.size
        assertTrue(before > 1)
        bridge.deleteMyDocumentPage(IdType().toString())
        awaitMain { repo.sortedWindows.size < before }
        assertFalse(repo.sortedWindows.contains(extra))
    }

    @Test fun deleteMyDocumentPageSkipsClosingWhenTheSessionWasDisposedMeanwhile() {
        val extra = repo.addNewWindow()
        session.window = extra
        bridge.deleteMyDocumentPage(IdType().toString())
        awaitMain { sheets.confirm != null }
        val before = repo.sortedWindows.size
        session.dispose()
        sheets.confirm!!.invoke()
        // The queued deletion hops to Main at its end; pump the looper until the queue is drained.
        val drained = launcher.launch("mydocument-window-${extra.id}") {}
        awaitMain { drained.isCompleted }
        assertEquals("a disposed session closes no window", before, repo.sortedWindows.size)
    }

    @Test fun llmActionAfterDisposeIsIgnored() {
        val localSheets = mock<ReadingSheets>()
        session.hostSheets = localSheets
        session.dispose()
        bridge.llmAction("KJV", john316Ordinal(), -1, "text")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        verify(localSheets, never()).showPromptSelector(any(), any(), any())
    }
}
