package net.bible.android.view.activity.page.actionmode

import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import com.nhaarman.mockitokotlin2.*

import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowLayout
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Before
import org.junit.Test

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class VerseActionModeMediatorTest {

    private var mainBibleActivity: VerseActionModeMediator.ActionModeMenuDisplay = mock()

    private var bibleView: VerseActionModeMediator.VerseHighlightControl = mock()

    private var pageControl: PageControl = mock()

    private var verseMenuCommandHandler: VerseMenuCommandHandler = mock()

    private var currentPageManager: CurrentPageManager = mock()

    private var actionMode: ActionMode = mock()

    private lateinit var verseActionModeMediator: VerseActionModeMediator

    @Before
    fun setup() {
        whenever(pageControl.currentBibleVerse).thenReturn(TestData.DEFAULT_VERSE)

        whenever(actionMode.menuInflater).thenReturn(mock())

        whenever(mainBibleActivity.isVerseActionModeAllowed()).thenReturn(true)

        val bookmarkControl =  mock<BookmarkControl>()

        verseActionModeMediator = VerseActionModeMediator(mainBibleActivity, bibleView, pageControl, verseMenuCommandHandler, bookmarkControl)

    }

    @Test
    @Throws(Exception::class)
    fun testVerseLongPress() {

        verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE)
        verify(mainBibleActivity).showVerseActionModeMenu(any())
        verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE, true)
        verify(bibleView).enableVerseTouchSelection()
    }

    @Test
    @Throws(Exception::class)
    fun testUnselectVerseOnEndActionMode() {

        // setup action mode and get callback
        verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE)
        argumentCaptor<ActionMode.Callback>().apply {
            verify(mainBibleActivity).showVerseActionModeMenu(capture())
            firstValue.onCreateActionMode(actionMode, mock())
            firstValue.onDestroyActionMode(actionMode)

        }

        // call destroy actionmode and check verse is unhighlighted
        verify(bibleView).clearVerseHighlight()
        verify(bibleView).disableVerseTouchSelection()
    }

    @Test
    @Throws(Exception::class)
    fun testChangeWindowClearsActionMode() {

        // setup actionmode
        verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE)

        val windowEntity = WorkspaceEntities.Window(0,true, false, false,
            WorkspaceEntities.WindowLayout(WindowLayout.WindowState.MAXIMISED.toString()), 3)
        // publish window change event
        val windowRepository: WindowRepository = mock()
        ABEventBus.getDefault().post(CurrentWindowChangedEvent(
            Window(windowEntity, currentPageManager, windowRepository)))

        assertThat(verseActionModeMediator.isActionMode, `is`(false))
    }

    @Test
    @Throws(Exception::class)
    fun testActionIsCalled() {

        // setup action mode and get callback
        verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE)
        //val callback = ArgumentCaptor.forClass(ActionMode.Callback::class.java)
        argumentCaptor<ActionMode.Callback>().apply() {
            verify(mainBibleActivity).showVerseActionModeMenu(capture())
            val menuItem: MenuItem = mock()
            whenever(menuItem.itemId).thenReturn(R.id.compareTranslations)

            firstValue.onActionItemClicked(actionMode, menuItem)
        }

        // call destroy actionmode and check verse is unhighlighted
        verify(verseMenuCommandHandler).handleMenuRequest(R.id.compareTranslations, VerseRange(TestData.SELECTED_VERSE.versification, TestData.SELECTED_VERSE))
    }

    @Test
    @Throws(Exception::class)
    fun testExpandToNextVerse() {

        // setup action mode and get callback
        verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE)
        verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE, true)
        verify(bibleView).enableVerseTouchSelection()

        verseActionModeMediator.verseTouch(TestData.SELECTED_CHAPTER_VERSE_PLUS_1)
        verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE_PLUS_1)
    }

    private interface TestData {
        companion object {
            val DEFAULT_VERSE = Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, 3, 16)
            val SELECTED_CHAPTER_VERSE = ChapterVerse(3, 3)
            val SELECTED_CHAPTER_VERSE_PLUS_1 = ChapterVerse(3, 4)
            val SELECTED_VERSE = Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, SELECTED_CHAPTER_VERSE.chapter, SELECTED_CHAPTER_VERSE.verse)
        }
    }
}

