package net.bible.android.control.page.window

import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.EventManager
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.RepoFactory
import net.bible.service.history.HistoryManager
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Book

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28])
class WindowTest {
    private lateinit var mockCurrentPageManagerProvider: Provider<CurrentPageManager>
    private var windowControl: WindowControl? = null
    var windowRepository: WindowRepository? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val eventManager = ABEventBus.getDefault()
        val swordContentFactory = mock(SwordContentFacade::class.java)
        val bibleTraverser = mock(BibleTraverser::class.java)
        val myNoteDao = mock(MyNoteDAO::class.java)

        mockCurrentPageManagerProvider = Provider {
            CurrentPageManager(swordContentFactory, SwordDocumentFacade(), bibleTraverser, myNoteDao, windowRepository!!)
        }
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!, eventManager)
        windowRepository!!.initialize()
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    //@Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testGetRestoreStateJson() {
        // initialise Window
        val pageManager = mockCurrentPageManagerProvider.get()
        var window = Window(
            WorkspaceEntities.Window(
                workspaceId = 0,
                isSynchronized = true,
                isPinMode = false,
                isLinksWindow = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.MINIMISED.toString()),
                id = 2
            ),
            pageManager,
            windowRepository!!
        )
        window.isSynchronised = true
        window.weight = 1.23456f

        //var pageManager = window.pageManager
        var biblePage = pageManager.currentBible
        biblePage.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2)

        // serialize state
        val entity = window.entity
        println(entity)

        val newPm = mockCurrentPageManagerProvider.get()
        // recreate window from saved state
        window = Window(entity, newPm, windowRepository!!)
        assertThat(window.id, equalTo(2L))
        assertThat(window.windowState, equalTo(WindowState.MINIMISED))
        assertThat(window.isSynchronised, equalTo(true))
        assertThat(window.weight, equalTo(1.23456f))

        //pageManager = window.pageManager
        biblePage = pageManager.currentBible
        assertThat<Book>(biblePage.currentDocument, equalTo<Book>(PassageTestData.ESV))
        assertThat(biblePage.singleKey.name, equalTo(PassageTestData.PS_139_2.name))
    }
}
