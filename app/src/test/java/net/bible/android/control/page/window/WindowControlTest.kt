package net.bible.android.control.page.window


import android.view.Menu
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.EventManager
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.download.RepoFactory
import net.bible.service.history.HistoryManager
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.isA
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.hamcrest.MockitoHamcrest.argThat
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28])
class WindowControlTest {
    private var eventManager: EventManager? = null

    private var windowRepository: WindowRepository? = null

    private var windowControl: WindowControl? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        eventManager = mock(EventManager::class.java)
        val swordContentFactory = mock(SwordContentFacade::class.java)
        val bibleTraverser = mock(BibleTraverser::class.java)
        val myNoteDao = mock(MyNoteDAO::class.java)
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        val mockCurrentPageManagerProvider = Provider { CurrentPageManager(swordContentFactory, SwordDocumentFacade(), bibleTraverser, myNoteDao, windowRepository!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!, eventManager!!)
        windowRepository!!.initialize()
        reset<EventManager>(eventManager)
        windowRepository!!.windowBehaviorSettings.autoPin = true
        windowControl!!.activeWindow.isPinMode = true
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testGetActiveWindow() {
        // should always be one default window that is active by default
        assertThat(windowControl!!.activeWindow.id, equalTo(2L))
    }

    @Test
    @Throws(Exception::class)
    fun testSetActiveWindow() {
        val window1 = windowControl!!.activeWindow

        val newWindow = windowControl!!.addNewWindow(window1)
        assertThat(newWindow, equalTo(windowControl!!.activeWindow))

        windowControl!!.activeWindow = newWindow
        assertThat(newWindow, equalTo(windowControl!!.activeWindow))
    }

    @Test
    @Throws(Exception::class)
    fun testIsCurrentActiveWindow() {
        val activeWindow = windowControl!!.activeWindow
        assertThat(windowControl!!.isActiveWindow(activeWindow), `is`(true))
    }

    @Test
    @Throws(Exception::class)
    fun testShowLink() {
        windowControl!!.showLink(BOOK_KJV, PS_139_3)

        val linksWindow = windowRepository!!.dedicatedLinksWindow
        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_KJV))
        assertThat(linksWindow.pageManager.currentBible.singleKey, equalTo(PS_139_3 as Key))
        assertThat(linksWindow.windowState, equalTo(WindowLayout.WindowState.SPLIT))
        assertThat(windowRepository!!.isMultiWindow, `is`(true))
    }

    //@Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testShowLinkUsingDefaultBible() {
        val window1 = windowRepository!!.activeWindow
        window1.pageManager.setCurrentDocument(BOOK_KJV)

        windowControl!!.showLinkUsingDefaultBible(PS_139_3)

        val linksWindow = windowRepository!!.dedicatedLinksWindow
        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_KJV))
        assertThat(linksWindow.pageManager.currentBible.singleKey, equalTo(PS_139_3 as Key))
        assertThat(linksWindow.windowState, equalTo(WindowLayout.WindowState.SPLIT))
        assertThat(windowRepository!!.isMultiWindow, `is`(true))
        assertThat(windowControl!!.isActiveWindow(linksWindow), `is`(true))

        windowControl!!.activeWindow = window1
        window1.pageManager.setCurrentDocument(PassageTestData.ESV)

        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_KJV))
        windowControl!!.showLinkUsingDefaultBible(PS_139_3)

        // since we have clicked link when active window is bible doc, open link in same
        // document in links window.
        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_ESV))

        // if we would have clicked from commentary, then document should not have changed
        // since links window has not been closed.. Test for that: TODO
    }

    //@Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testAddNewWindow() {
        val activeWindow = windowControl!!.activeWindow
        activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2)

        val newWindow = windowControl!!.addNewWindow(activeWindow)
        assertThat(windowRepository!!.windows, hasItem(newWindow))
        // documents should be defaulted from active window
        val biblePage = newWindow.pageManager.currentBible
        assertThat(biblePage.currentDocument, equalTo(PassageTestData.ESV))
        assertThat(biblePage.singleKey.name, equalTo(PassageTestData.PS_139_2.name))

        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testGetMinimisedWindows() {
        val active = windowControl!!.activeWindow
        val newWindow1 = windowControl!!.addNewWindow(active)
        val newWindow2 = windowControl!!.addNewWindow(active)

        // simple state - just 1 window is minimised
        windowControl!!.minimiseWindow(newWindow2)
        assertThat(windowRepository!!.minimisedWindows, contains(newWindow2))
    }

    @Test
    @Throws(Exception::class)
    fun testMinimiseWindow() {
        val newWindow = windowControl!!.addNewWindow(windowControl!!.activeWindow)
        reset<EventManager>(eventManager)

        windowControl!!.minimiseWindow(newWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, not(hasItem(newWindow)))

        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testMinimiseOnlyWindowPrevented() {
        val onlyWindow = windowControl!!.activeWindow
        windowControl!!.minimiseWindow(onlyWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasItem(onlyWindow))
        verifyZeroInteractions(eventManager)

        // test still prevented if links window is visible
        windowRepository!!.dedicatedLinksWindow.windowState = WindowState.SPLIT
        windowControl!!.minimiseWindow(onlyWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasItem(onlyWindow))
        verifyZeroInteractions(eventManager)
    }


    @Test
    @Throws(Exception::class)
    fun testMaximiseMinimiseWindows() {
        // issue #373

        val window1 = windowControl!!.activeWindow
        val window2 = windowControl!!.addNewWindow(window1)
        val window3 = windowControl!!.addNewWindow(window2)

        windowControl!!.minimiseWindow(window1)


        assertThat(window1.isVisible, equalTo(false))
        assertThat(window2.isVisible, equalTo(true))
        assertThat(window3.isVisible, equalTo(true))

    }

        @Test
    @Throws(Exception::class)
    fun testCloseWindow() {
        val newWindow = windowControl!!.addNewWindow(windowControl!!.activeWindow)
        reset<EventManager>(eventManager)

        windowControl!!.closeWindow(newWindow)
        assertThat(windowRepository!!.windows, not(hasItem(newWindow)))

        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testCloseOnlyWindowPrevented() {
        val onlyWindow = windowRepository!!.activeWindow
        windowControl!!.closeWindow(onlyWindow)
        assertThat(windowRepository!!.windows, hasItem(onlyWindow))

        verifyZeroInteractions(eventManager)
    }

    @Test
    @Throws(Exception::class)
    fun testCloseWindowPreventedIfOnlyOtherIsLinks() {
        windowRepository!!.dedicatedLinksWindow.windowState = WindowState.SPLIT
        val onlyNormalWindow = windowRepository!!.activeWindow
        windowControl!!.closeWindow(onlyNormalWindow)
        assertThat(windowRepository!!.windows, hasItem(onlyNormalWindow))

        verifyZeroInteractions(eventManager)
    }

    @Test
    @Throws(Exception::class)
    fun testCloseActiveWindow() {
        val activeWindow = windowControl!!.activeWindow
        val newWindow = windowControl!!.addNewWindow(activeWindow)
        reset<EventManager>(eventManager)

        windowControl!!.closeWindow(activeWindow)
        assertThat(windowRepository!!.activeWindow, equalTo(newWindow))
    }

    @Test
    @Throws(Exception::class)
    fun testRestoreWindow() {
        val activeWindow = windowControl!!.activeWindow
        val newWindow = windowControl!!.addNewWindow(activeWindow)
        windowControl!!.minimiseWindow(newWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, contains(activeWindow))
        reset<EventManager>(eventManager)

        windowControl!!.restoreWindow(newWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, containsInAnyOrder(activeWindow, newWindow))

        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testOrientationChange() {
        windowControl!!.orientationChange()
        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testIsMultiWindow() {
        assertThat(windowControl!!.isMultiWindow, equalTo(false))
        val newWindow = windowControl!!.addNewWindow(windowControl!!.activeWindow)
        assertThat(windowControl!!.isMultiWindow, equalTo(true))
        windowControl!!.closeWindow(newWindow)
        assertThat(windowControl!!.isMultiWindow, equalTo(false))
    }

    fun createWindowsMenu(): Menu {
        val menu = RoboMenu(RuntimeEnvironment.application)
        menu.add(0, R.id.windowSynchronise, 0, "Synchronise")
        menu.add(0, R.id.windowMinimise, 0, "Minimise")
        menu.add(0, R.id.windowClose, 0, "Close")
        return menu
    }

    companion object {

        private val BOOK_KJV = Books.installed().getBook("KJV")
        private val BOOK_ESV = Books.installed().getBook("ESV2011")
        private val PS_139_3 = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 3)
    }
}
