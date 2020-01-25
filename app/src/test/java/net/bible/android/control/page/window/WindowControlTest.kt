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
        val repoFactory = mock(RepoFactory::class.java)
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        val mockCurrentPageManagerProvider = Provider { CurrentPageManager(swordContentFactory, SwordDocumentFacade(repoFactory), bibleTraverser, myNoteDao, windowRepository!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!, eventManager!!)
        windowRepository!!.initialize()
        reset<EventManager>(eventManager)
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

        val newWindow = windowControl!!.addNewWindow()
        assertThat(window1, equalTo(windowControl!!.activeWindow))

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
        assertThat(linksWindow.windowLayout.state, equalTo(WindowLayout.WindowState.SPLIT))
        assertThat(windowRepository!!.isMultiWindow, `is`(true))
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testShowLinkUsingDefaultBible() {
        val window1 = windowRepository!!.activeWindow
        window1.pageManager.setCurrentDocument(BOOK_KJV)

        windowControl!!.showLinkUsingDefaultBible(PS_139_3)

        val linksWindow = windowRepository!!.dedicatedLinksWindow
        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_KJV))
        assertThat(linksWindow.pageManager.currentBible.singleKey, equalTo(PS_139_3 as Key))
        assertThat(linksWindow.windowLayout.state, equalTo(WindowLayout.WindowState.SPLIT))
        assertThat(windowRepository!!.isMultiWindow, `is`(true))
        assertThat(windowControl!!.isActiveWindow(linksWindow), `is`(true))

        windowControl!!.activeWindow = window1
        window1.pageManager.setCurrentDocument(PassageTestData.ESV)

        windowControl!!.showLinkUsingDefaultBible(PS_139_3)

        // since links window has not been closed the Bible should not be changed
        assertThat(linksWindow.pageManager.currentBible.currentDocument, equalTo(BOOK_KJV))
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testAddNewWindow() {
        val activeWindow = windowControl!!.activeWindow
        activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2)

        val newWindow = windowControl!!.addNewWindow()
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
        val activeWindow = windowControl!!.activeWindow
        val newWindow1 = windowControl!!.addNewWindow()
        val newWindow2 = windowControl!!.addNewWindow()

        // simple state - just 1 window is minimised
        windowControl!!.minimiseWindow(newWindow2)
        assertThat(windowRepository!!.minimisedWindows, contains(newWindow2))

        // A window is maximized, the others should then all be minimized.
        windowControl!!.maximiseWindow(activeWindow)
        assertThat<List<Window>>(windowRepository!!.minimisedWindows, containsInAnyOrder(newWindow1, newWindow2))
    }

    @Test
    @Throws(Exception::class)
    fun testMinimiseWindow() {
        val newWindow = windowControl!!.addNewWindow()
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
        windowRepository!!.dedicatedLinksWindow.windowLayout.state = WindowState.SPLIT
        windowControl!!.minimiseWindow(onlyWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasItem(onlyWindow))
        verifyZeroInteractions(eventManager)
    }

    @Test
    @Throws(Exception::class)
    fun testMaximiseWindow() {
        val newWindow = windowControl!!.addNewWindow()
        windowControl!!.activeWindow = newWindow
        reset<EventManager>(eventManager)

        windowControl!!.maximiseWindow(newWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasItem(newWindow))
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasSize<Any>(1))

        verify<EventManager>(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent::class.java)))
    }

    @Test
    @Throws(Exception::class)
    fun testMaximiseAndLinksWindow() {
        val activeWindow = windowControl!!.activeWindow
        windowControl!!.addNewWindow() // add an extra window for good measure
        windowControl!!.showLinkUsingDefaultBible(PS_139_3)
        windowControl!!.activeWindow = activeWindow
        assertThat<List<Window>>(windowRepository!!.visibleWindows, hasSize<Any>(3))
        reset<EventManager>(eventManager)

        // making window active should remove links window
        windowControl!!.maximiseWindow(activeWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, contains(activeWindow))

        // showing link should re-display links window despite window being maximised
        windowControl!!.showLinkUsingDefaultBible(PS_139_3)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, contains(activeWindow, windowRepository!!.dedicatedLinksWindow))

        // maximise links window should be possible
        val linksWindow = windowRepository!!.dedicatedLinksWindow
        windowControl!!.maximiseWindow(linksWindow)
        assertThat<List<Window>>(windowRepository!!.visibleWindows, contains(linksWindow))
    }

    @Test
    @Throws(Exception::class)
    fun testCloseWindow() {
        val newWindow = windowControl!!.addNewWindow()
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
        windowRepository!!.dedicatedLinksWindow.windowLayout.state = WindowState.SPLIT
        val onlyNormalWindow = windowRepository!!.activeWindow
        windowControl!!.closeWindow(onlyNormalWindow)
        assertThat(windowRepository!!.windows, hasItem(onlyNormalWindow))

        verifyZeroInteractions(eventManager)
    }

    @Test
    @Throws(Exception::class)
    fun testCloseActiveWindow() {
        val activeWindow = windowControl!!.activeWindow
        val newWindow = windowControl!!.addNewWindow()
        reset<EventManager>(eventManager)

        windowControl!!.closeWindow(activeWindow)
        assertThat(windowRepository!!.activeWindow, equalTo(newWindow))
    }

    @Test
    @Throws(Exception::class)
    fun testRestoreWindow() {
        val activeWindow = windowControl!!.activeWindow
        val newWindow = windowControl!!.addNewWindow()
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
        val newWindow = windowControl!!.addNewWindow()
        assertThat(windowControl!!.isMultiWindow, equalTo(true))
        windowControl!!.closeWindow(newWindow)
        assertThat(windowControl!!.isMultiWindow, equalTo(false))
    }

    fun createWindowsMenu(): Menu {
        val menu = RoboMenu(RuntimeEnvironment.application)
        menu.add(0, R.id.windowSynchronise, 0, "Synchronise")
        menu.add(0, R.id.windowMinimise, 0, "Minimise")
        menu.add(0, R.id.windowMaximise, 0, "Maximise")
        menu.add(0, R.id.windowClose, 0, "Close")
        return menu
    }

    companion object {

        private val BOOK_KJV = Books.installed().getBook("KJV")
        private val PS_139_3 = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 3)
    }
}
