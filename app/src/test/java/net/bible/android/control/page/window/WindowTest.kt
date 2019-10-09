package net.bible.android.control.page.window

import net.bible.android.TestBibleApplication
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Book

import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import robolectric.MyRobolectricTestRunner

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Ignore
import org.mockito.Mockito.mock

@RunWith(MyRobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class WindowTest {

    @Before
    @Throws(Exception::class)
    fun setUp() {
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testGetRestoreStateJson() {

        val swordContentFactory = mock(SwordContentFacade::class.java)
        val bibleTraverser = mock(BibleTraverser::class.java)
        val myNoteDao = mock(MyNoteDAO::class.java)

        val mockCurrentPageManager = CurrentPageManager(swordContentFactory, SwordDocumentFacade(null), bibleTraverser, myNoteDao)

        // initialise Window
        var window = Window(2, WindowState.MINIMISED, mockCurrentPageManager)
        var layout = window.windowLayout
        window.isSynchronised = true
        layout.weight = 1.23456f

        var pageManager = window.pageManager
        var biblePage = pageManager.currentBible
        biblePage.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2)

        // serialize state
        val json = window.stateJson
        println(json)

        // recreate window from saved state
        window = Window(mockCurrentPageManager)
        window.restoreState(json)
        layout = window.windowLayout
        assertThat(window.screenNo, equalTo(2))
        assertThat(layout.state, equalTo(WindowState.MINIMISED))
        assertThat(window.isSynchronised, equalTo(true))
        assertThat(layout.weight, equalTo(1.23456f))

        pageManager = window.pageManager
        biblePage = pageManager.currentBible
        assertThat<Book>(biblePage.currentDocument, equalTo<Book>(PassageTestData.ESV))
        assertThat(biblePage.singleKey.name, equalTo(PassageTestData.PS_139_2.name))
    }
}
