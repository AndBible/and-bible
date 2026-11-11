package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.defaultBible
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GitHub #4056: a cold start must reopen the Bible that was open, not the first installed one.
 *
 * On a cold start the workspace is loaded by an early reader (the TTS service manager reads
 * `activeWindow` from `platformStart()`) before app initialisation has finished. Then the
 * `initialized`-guarded `AppSettings.getBoolean` answers its default, so a `"first-time"` read
 * through it sees `true` on every start and resets the active window to the first installed
 * Bible at John 3:16.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositoryFirstTimeTest {
    private lateinit var f: Fixture
    private var reopened: WindowRepository? = null

    @Before fun setUp() { f = Fixture() }

    @After fun tearDown() {
        try {
            reopened?.clear()
        } finally {
            CommonUtils.windowControl.windowRepository = f.repo
            f.close()
        }
    }

    @Test fun earlyLoadBeforeAppInitKeepsTheOpenBible() {
        val first = defaultBible()
        val other = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }
            .first { it.initials != first.initials }

        runBlocking { f.repo.initialize() }
        f.repo.activeWindow.pageManager.currentBible.setCurrentDocument(other)
        f.repo.saveIntoDb(false)
        runBlocking { f.persistence.awaitPersisted() }

        // Cold start: a new repository loaded at the edge while app initialisation is still running.
        CommonUtils.initialized = false
        val repo = WindowRepository(CoroutineScope(Dispatchers.Main)).also { reopened = it }
        repo.persistence = f.persistence
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()

        val restored = repo.activeWindow.pageManager.currentBible.currentDocument
        assertNotEquals("precondition: the open Bible is not the first one", first.initials, other.initials)
        assertEquals(other.initials, restored?.initials)
    }
}
