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
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.search.SearchControl
import net.bible.android.control.link.WindowMode
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.sharedcore.platform.UserNotifier
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.GlobalContext
import org.mockito.Mockito
import net.bible.android.control.page.CurrentPage
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ReadingLinkHandler
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ReadingLinkHandler] on a bare [BibleWindowSession] with a mocked [LinkControl] and a recording navigator. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingLinkHandlerTest {
    private class RecordingNavigator : ReadingNavigator {
        val downloadSearches = mutableListOf<String?>()
        val webLinks = mutableListOf<String>()
        override fun openDownloads(addons: Boolean) = error("unexpected")
        override fun openDownloadSearch(initials: String?) { downloadSearches += initials }
        override fun openReadingProgress(tab: Int) = error("unexpected")
        override fun openReadingProgressSettings() = error("unexpected")
        override fun openWorkspaceSelector() = error("unexpected")
        override fun openSearch(document: Book?) = error("unexpected")
        override fun openPromptEditor(promptId: String) = error("unexpected")
        override fun openExternalUrl(url: String) = error("unexpected")
        override fun openWebLink(url: String) { webLinks += url }
        override fun startKeyChooser(page: CurrentPage) = error("unexpected")
        override suspend fun assignLabels(
            bookmark: BookmarkEntities.BaseBookmarkWithNotes,
            labelIds: List<IdType>,
        ): List<IdType>? = error("unexpected")
    }

    private val navigator = RecordingNavigator()
    private val windowControl = WindowControl()
    private lateinit var repo: WindowRepository
    private lateinit var fakeBooks: FakeBookFactory
    private lateinit var linkControl: LinkControl
    private lateinit var linkPlatform: LinkPlatform
    private lateinit var hostScope: CoroutineScope
    private lateinit var handler: ReadingLinkHandler
    private var epubOpened = 0

    @Before fun setUp() {
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        repo.initializeAtEdge()
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        repo.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) {}
            override fun showError(message: String, cause: Throwable?) {}
        }
        fakeBooks = FakeBookFactory(testCoreStrings())
        linkPlatform = Mockito.mock(LinkPlatform::class.java)
        linkControl = LinkControl(fakeBooks, windowControl, GlobalContext.get().get<BookmarkControl>(),
            GlobalContext.get().get<SearchControl>(), testAppSettings(), notifier, testCoreStrings(), linkPlatform)
        // Show links in the active window so the effect is observable on it.
        linkControl.windowMode = WindowMode.WINDOW_MODE_THIS
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val session = BibleWindowSession(
            repo.activeWindow, BibleViewJsChannel(mock<JsHost>()) {}, { hostScope },
            mock<ReadingWindowSurface>(), navigator,
            windowControlProvider = { windowControl },
            linkControlProvider = { linkControl },
        )
        handler = ReadingLinkHandler(session) { _, _, _, _ -> epubOpened++ }
    }

    @After fun tearDown() {
        hostScope.cancel()
        DatabaseResetter.resetDatabase(repo.scope)
    }

    private val shownDocument get() = repo.activeWindow.pageManager.currentPage.currentDocument
    private val shownKey get() = repo.activeWindow.pageManager.currentPage.key

    @Test fun referenceWithoutOsisIsNotHandled() {
        val documentBefore = shownDocument
        val keyBefore = shownKey
        assertFalse(handler.handle("osis://?v11n=KJV"))
        assertFalse(handler.handle("osis://"))
        assertEquals(documentBefore, shownDocument)
        assertEquals(keyBefore, shownKey)
    }

    @Test fun malformedKnownLinksAreNotHandledInsteadOfThrowing() {
        assertFalse(handler.handle("multi://"))
        assertFalse(handler.handle("ab-w://"))
        assertFalse(handler.handle("my-notes://?v11n=KJV"))
        assertFalse(handler.handle("my-notes://?ordinal=x&v11n=KJV"))
        assertFalse(handler.handle("epub-ref://?book=X"))
        assertFalse(handler.handle("journal://?entryId=1"))
        assertFalse(handler.handle("ab-find-all://?type=g"))
        assertFalse(handler.handle("ab-find-all://?name=G1"))
    }

    @Test fun unknownSchemeIsNotHandled() {
        assertFalse(handler.handle("mailto:someone@example.org"))
        assertFalse(handler.handle("no scheme at all"))
        assertFalse(handler.isKnownScheme("mailto:someone@example.org"))
        assertTrue(handler.isKnownScheme("osis://?osis=Gen.1.1"))
    }

    @Test fun epubLinkForNonEpubBookIsNotHandled() {
        assertNotNull("test modules must provide KJV", Books.installed().getBook("KJV"))
        assertFalse(handler.handle("epub-ref://?book=KJV&toKey=ch1&toId="))
        assertFalse(handler.handle("epub-ref://?book=NoSuchBook&toKey=ch1&toId="))
        assertEquals(0, epubOpened)
    }

    @Test fun downloadSchemeOpensDownloadSearch() {
        assertTrue(handler.handle("download://?initials=KJV"))
        assertEquals(listOf<String?>("KJV"), navigator.downloadSearches)
    }

    @Test fun multiReferenceOpensTheReferenceOverlay() {
        assertTrue(handler.handle("multi://?osis=John.3.16&osis=Rom.5.8&v11n=KJV"))
        assertEquals(fakeBooks.multiDocument, shownDocument)
        val key = shownKey as BookAndKeyList
        assertEquals(listOf("John 3:16", "Romans 5:8"), key.map { (it as BookAndKey).key.getName() })
    }

    @Test fun encodedSpacesInOsisAreDecoded() {
        assertTrue(handler.handle("osis://?osis=%20Gen.1.2%20&v11n=KJV"))
        assertEquals("Genesis 1:2", repo.activeWindow.pageManager.currentPage.singleKey?.getName())
        assertTrue(handler.handle("osis://?osis=+Gen.1.3+&v11n=KJV"))
        assertEquals("Genesis 1:3", repo.activeWindow.pageManager.currentPage.singleKey?.getName())
    }

    @Test fun webLinksGoThroughTheNavigator() {
        assertTrue(handler.handle("https://example.org/a?b=c"))
        assertTrue(handler.handle("http://example.org"))
        assertEquals(listOf("https://example.org/a?b=c", "http://example.org"), navigator.webLinks)
    }

    @Test fun errorLinkReportsTheError() {
        assertTrue(handler.handle("ab-error://error"))
        verify(linkPlatform).reportJsError()
    }
}
