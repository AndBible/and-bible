package net.bible.android.control.page.window

import net.bible.android.control.event.EventManagerStub
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.versification.BibleTraverser
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
import org.hamcrest.Matchers.contains
import org.junit.Assert.assertThat
import org.mockito.Mockito.mock

class WindowRepositoryTest {
    private var windowRepository: WindowRepository? = null
    private var windowControl: WindowControl? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val eventManager = EventManagerStub()
        val swordContentFactory = mock(SwordContentFacade::class.java)
        val bibleTraverser = mock(BibleTraverser::class.java)
        val myNoteDao = mock(MyNoteDAO::class.java)

        val mockCurrentPageManagerProvider = Provider { CurrentPageManager(swordContentFactory, SwordDocumentFacade(null), bibleTraverser, myNoteDao) }
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!, eventManager)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testGetWindow() {
        assertThat(windowRepository!!.getWindow(1)!!.screenNo, equalTo(1))
    }

    @Test
    @Throws(Exception::class)
    fun testGetActiveWindow() {
        assertThat(windowRepository!!.activeWindow.screenNo, equalTo(1))
    }

    @Test
    @Throws(Exception::class)
    fun testSetActiveWindow() {
        val newWindow = windowRepository!!.addNewWindow()
        assertThat(windowRepository!!.activeWindow.screenNo, not(equalTo(newWindow.screenNo)))
        windowRepository!!.activeWindow = newWindow
        assertThat(windowRepository!!.activeWindow.screenNo, equalTo(newWindow.screenNo))
    }

    @Test
    @Throws(Exception::class)
    fun testMoveWindowToPosition() {
        val originalWindow = windowRepository!!.activeWindow
        val newWindow = windowRepository!!.addNewWindow()
        val newWindow2 = windowRepository!!.addNewWindow()

        windowRepository!!.moveWindowToPosition(newWindow, 0)
        assertThat(windowRepository!!.windows, contains(newWindow, originalWindow, newWindow2))

        windowRepository!!.moveWindowToPosition(newWindow, 1)
        assertThat(windowRepository!!.windows, contains(originalWindow, newWindow, newWindow2))

        windowRepository!!.moveWindowToPosition(originalWindow, 2)
        assertThat(windowRepository!!.windows, contains(newWindow, newWindow2, originalWindow))

        windowRepository!!.moveWindowToPosition(originalWindow, 0)
        assertThat(windowRepository!!.windows, contains(originalWindow, newWindow, newWindow2))
    }

    @Test
    @Throws(Exception::class)
    fun testMoveMissingWindowToPosition() {
        val originalWindow = windowRepository!!.activeWindow
        val newWindow = windowRepository!!.addNewWindow()

        windowRepository!!.close(newWindow)

        windowRepository!!.moveWindowToPosition(newWindow, 0)
        assertThat(windowRepository!!.windows, contains(originalWindow))
    }

    @Test
    fun testAddAfterDeletedWindowsDifferent() {
        val newWindow = windowRepository!!.addNewWindow()
        windowRepository!!.close(newWindow)
        val newWindow2 = windowRepository!!.addNewWindow()
        assertThat(newWindow == newWindow2, not(true))
        assertThat(newWindow.hashCode() == newWindow2.hashCode(), not(true))
    }
}
