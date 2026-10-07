package net.bible.android.control.page.window

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.PassageChangeMediator
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowControlPageChangeTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository

    @Before fun setUp() {
        // A fresh subscriber avoids the cached singleton whose handlers test teardown clears.
        windowControl = WindowControl()
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
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
        thread { PassageChangeMediator.onCurrentVerseChanged(a) }.join()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BibleBook.JOHN, b.pageManager.currentBible.singleKey.book)
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
