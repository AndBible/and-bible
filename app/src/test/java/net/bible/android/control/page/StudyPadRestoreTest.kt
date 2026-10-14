package net.bible.android.control.page

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudyPadRestoreTest {
    private lateinit var repo: WindowRepository
    private lateinit var previousRepo: WindowRepository
    private lateinit var scope: CoroutineScope
    private val factory get() = get<FakeBookFactory>(FakeBookFactory::class.java)
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        DatabaseResetter.resetDatabase()
        previousRepo = CommonUtils.windowControl.windowRepository
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        repo = WindowRepository(scope)
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
    }

    @After fun tearDown() {
        try {
            try {
                repo.clear()
            } finally {
                try {
                    scope.cancel()
                } finally {
                    CommonUtils.windowControl.windowRepository = previousRepo
                    DatabaseResetter.resetDatabase()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun openStudyPad(): BookmarkEntities.Label {
        val label = BookmarkEntities.Label(name = "Restore this StudyPad")
        runBlocking { dao.insert(label) }
        repo.activeWindow.pageManager.setCurrentDocumentAndKey(factory.journalDocument, StudyPadKey(label))
        return label
    }

    @Test fun `save and reload restores the persisted StudyPad label and anchor`() {
        val label = openStudyPad()
        val id = repo.activeWindow.id
        repo.activeWindow.pageManager.currentGeneralBook.anchorOrdinal = OrdinalRange(7)
        repo.saveIntoDb(false)
        kotlinx.coroutines.runBlocking { repo.loadFromDb(repo.id) }
        val page = repo.getWindow(id)!!.pageManager.currentGeneralBook
        assertEquals(factory.journalDocument, page.currentDocument)
        assertEquals(label.id, (page.key as StudyPadKey).label.id)
        assertEquals(7, page.anchorOrdinal!!.start)
    }

    @Test fun `reload of a deleted label does not restore the journal`() {
        val label = openStudyPad()
        repo.saveIntoDb(false)
        runBlocking { dao.delete(label) }
        kotlinx.coroutines.runBlocking { repo.loadFromDb(repo.id) }
        assertNotEquals(factory.journalDocument, repo.activeWindow.pageManager.currentGeneralBook.currentDocument)
        assertFalse(repo.activeWindow.pageManager.currentGeneralBook.key is StudyPadKey)
    }

    @Test fun `split copies the source label without reading the bookmark DAO`() {
        val label = openStudyPad()
        val originalDb = DatabaseContainer.instance.bookmarkDb
        var reads = 0
        val countingDao = object : BookmarkDao by originalDb.bookmarkDao() {
            override suspend fun labelById(id: net.bible.android.database.IdType): BookmarkEntities.Label? {
                reads++
                return originalDb.bookmarkDao().labelById(id)
            }
        }
        val wrappedDb = mock<BookmarkDatabase>()
        whenever(wrappedDb.bookmarkDao()).thenReturn(countingDao)
        try {
            DatabaseContainer.instance.bookmarkDb = wrappedDb
            val split = repo.addNewWindow(repo.activeWindow)
            assertEquals(factory.journalDocument, split.pageManager.currentGeneralBook.currentDocument)
            assertEquals("clone must use the source label, not query it", 0, reads)
            assertSame(label, (split.pageManager.currentGeneralBook.key as StudyPadKey).label)
        } finally {
            DatabaseContainer.instance.bookmarkDb = originalDb
        }
    }
}
