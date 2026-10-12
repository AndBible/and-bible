package net.bible.android.control.page

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.mydocument.AiDocMarkerInfo
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PageContentCancellationTest {
    @After fun resetDatabase() = DatabaseResetter.resetDatabase()

    @Test fun cancelledHeldContentDoesNotReturnAnErrorDocumentToItsCaller() = runTest(timeout = 30.seconds) {
        val database = DatabaseContainer.instance
        val originalDb = database.myDocumentDb
        val realDao = originalDb.myDocumentDao()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val heldDao = object : MyDocumentDao by realDao {
            override suspend fun aiDocMarkersForPage(bookInitials: String, bookKey: String): List<AiDocMarkerInfo> {
                entered.complete(Unit)
                release.await()
                return realDao.aiDocMarkersForPage(bookInitials, bookKey)
            }
        }
        val heldDb = mock<MyDocumentDatabase>()
        whenever(heldDb.myDocumentDao()).thenReturn(heldDao)
        val manager = CurrentPageManager(mock<BibleTraverser>(), mock<BookmarkControl>(), mock<WindowControl>())
        val window = Window(net.bible.android.database.WorkspaceEntities.Window(
            workspaceId = net.bible.android.database.IdType(), isSynchronized = false, isPinMode = false,
            windowLayout = net.bible.android.database.WorkspaceEntities.WindowLayout("MINIMISED")), manager,
            net.bible.android.control.page.window.WindowRepository(backgroundScope))
        manager.window = window
        val book = Books.installed().getBook("KJV")!!
        val page = manager.currentBible
        val v11n = org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV")
        val key = org.crosswire.jsword.passage.VerseRange(v11n,
            org.crosswire.jsword.passage.Verse(v11n, org.crosswire.jsword.versification.BibleBook.GEN, 1, 1))
        val responses = mutableListOf<Document>()
        var contentJob: kotlinx.coroutines.Job? = null
        try {
            page.setCurrentDocumentAndKey(book, key)
            database.myDocumentDb = heldDb
            // This is the BibleView chapter-loader shape: publish only after the content call returns.
            contentJob = launch { responses += page.getPageContent(key) }
            entered.await()
            contentJob.cancelAndJoin()
            assertTrue("Cancellation must not publish a stale document or ErrorDocument", responses.isEmpty())
        } finally {
            try {
                contentJob?.cancelAndJoin()
            } finally {
                database.myDocumentDb = originalDb
                window.destroy(destroyView = false)
            }
        }
    }
}
