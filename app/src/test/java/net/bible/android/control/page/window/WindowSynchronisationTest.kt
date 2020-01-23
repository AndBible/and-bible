package net.bible.android.control.page.window

import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.EventManager
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.download.RepoFactory
import net.bible.service.history.HistoryManager
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade

import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test

import javax.inject.Provider


import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28])
class WindowSynchronisationTest {

    private var eventManager: EventManager? = null

    private var windowRepository: WindowRepository? = null

    private var windowControl: WindowControl? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        eventManager = ABEventBus.getDefault()

        val swordContentFactory = mock(SwordContentFacade::class.java)
        val bibleTraverser = mock(BibleTraverser::class.java)
        val myNoteDao = mock(MyNoteDAO::class.java)
        val repoFactory = mock(RepoFactory::class.java)

        val mockCurrentPageManagerProvider = Provider { CurrentPageManager(swordContentFactory, SwordDocumentFacade(repoFactory), bibleTraverser, myNoteDao, windowRepository!!) }
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!, eventManager!!)
        windowRepository!!.initialize()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testSynchronizeScreens_verseChange() {
        val window2 = windowControl!!.addNewWindow()
        val (chapter, verse) = window2.pageManager.currentBible.currentChapterVerse
        assertThat(verse, not(equalTo(7)))

        val mainWindow = windowControl!!.activeWindow
        val newChapterVerse = ChapterVerse(chapter, 7)
        mainWindow.pageManager.currentBible.currentChapterVerse = newChapterVerse
        assertThat(mainWindow.pageManager.currentBible.currentChapterVerse.verse, equalTo(7))

        Thread.sleep(500)
        assertThat(window2.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
    }

    @Test
    @Throws(Exception::class)
    fun testSynchronizeScreens_chapterChange() {
        val window2 = windowControl!!.addNewWindow()
        val (chapter) = window2.pageManager.currentBible.currentChapterVerse
        assertThat(chapter, not(equalTo(3)))

        val newChapterVerse = ChapterVerse(3, 7)
        val mainWindow = windowControl!!.activeWindow
        mainWindow.pageManager.currentBible.currentChapterVerse = newChapterVerse
        assertThat(mainWindow.pageManager.currentBible.currentChapterVerse.chapter, equalTo(3))

        Thread.sleep(500)
        assertThat(window2.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
    }
}
