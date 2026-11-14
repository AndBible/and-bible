package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.defaultBible
import net.bible.android.control.page.defaultVerse
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GitHub #4079: a workspace whose settings database has no `"first-time"` flag must still open at the
 * active window's saved position.
 *
 * The flag is missing whenever the settings database did not come from a run of this app: databases
 * restored from a backup that another exporter wrote (the Swift iOS app's Android-format export, the
 * legacy iOS migration, the store-shot demo data). Android loads the workspace at its cold-start edge
 * before `prepareData()` marks existing data as not first-time, so the missing flag looked like a
 * first run and the active window was reset to the first installed Bible at John 3:16.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositoryMissingFirstTimeFlagTest {
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

    private fun coldStart(): WindowRepository {
        CommonUtils.initialized = false
        val repo = WindowRepository(CoroutineScope(Dispatchers.Main)).also { reopened = it }
        repo.persistence = f.persistence
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        return repo
    }

    @Test fun savedPositionSurvivesASettingsDatabaseWithoutTheFlag() {
        val first = defaultBible()
        val other = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }
            .first { it.initials != first.initials }
        val saved = Verse(KJVA, BibleBook.JER, 9, 23)

        runBlocking { f.repo.initialize() }
        f.repo.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(other, saved)
        f.repo.saveIntoDb(false)
        runBlocking { f.persistence.awaitPersisted() }
        // A settings database written by something other than this app: no "first-time" row.
        DatabaseContainer.instance.settingsStore.setBoolean("first-time", null)

        val repo = coldStart()

        val page = repo.activeWindow.pageManager.currentBible
        assertNotEquals("precondition: the saved Bible is not the first one", first.initials, other.initials)
        assertEquals(other.initials, page.currentDocument?.initials)
        assertEquals(BibleBook.JER, page.singleKey.book)
        assertEquals(9, page.singleKey.chapter)
        assertEquals(23, page.singleKey.verse)
        assertEquals(false, DatabaseContainer.instance.settingsStore.getBoolean("first-time", true))
    }

    /** The first-run default still applies to a window that has no saved page (a true first start). */
    @Test fun aWindowWithoutASavedPageStillGetsTheFirstUseDefault() {
        DatabaseContainer.instance.settingsStore.setBoolean("first-time", null)

        val repo = coldStart()

        val page = repo.activeWindow.pageManager.currentBible
        assertEquals(defaultBible().initials, page.currentDocument?.initials)
        assertEquals(defaultVerse().start.book, page.singleKey.book)
        assertEquals(defaultVerse().start.chapter, page.singleKey.chapter)
        assertEquals(defaultVerse().start.verse, page.singleKey.verse)
        assertEquals(false, DatabaseContainer.instance.settingsStore.getBoolean("first-time", true))
    }
}
