/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.page.window

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.AbstractSpeakTests
import net.bible.service.history.HistoryManager
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
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowSynchronisationTest {

    private var windowRepository: WindowRepository? = null

    private var windowControl: WindowControl? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val bibleTraverser = mock(BibleTraverser::class.java)

        val bookmarkControl = BookmarkControl(AbstractSpeakTests.windowControl, mock(AndroidResourceProvider::class.java))
        val mockCurrentPageManagerProvider = Provider { CurrentPageManager(SwordDocumentFacade(), bibleTraverser, bookmarkControl, windowRepository!!) }
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!)
        CommonUtils.settings.setBoolean("first-time", false)
        windowRepository!!.initialize()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testSynchronizeScreens_verseChange() {
        val window2 = windowControl!!.addNewWindow(windowControl!!.activeWindow)
        val (chapter, verse) = window2.pageManager.currentBible.currentChapterVerse
        assertThat(verse, not(equalTo(7)))

        val mainWindow = windowControl!!.activeWindow
        val newChapterVerse = ChapterVerse(chapter, 7)
        // TODO: these tests should change the verse with setCurrentVerseOrdinal
        mainWindow.pageManager.currentBible.currentChapterVerse = newChapterVerse
        assertThat(mainWindow.pageManager.currentBible.currentChapterVerse.verse, equalTo(7))
        windowControl!!.windowSync.synchronizeWindows(mainWindow)

        Thread.sleep(500)
        assertThat(window2.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
    }

    @Test
    @Throws(Exception::class)
    fun testSynchronizeScreens_chapterChange() {
        val window2 = windowControl!!.addNewWindow(windowControl!!.activeWindow)
        val (chapter) = window2.pageManager.currentBible.currentChapterVerse
        assertThat(chapter, not(equalTo(3)))

        val newChapterVerse = ChapterVerse(3, 7)
        val mainWindow = windowControl!!.activeWindow
        mainWindow.pageManager.currentBible.currentChapterVerse = newChapterVerse
        assertThat(mainWindow.pageManager.currentBible.currentChapterVerse.chapter, equalTo(3))
        windowControl!!.windowSync.synchronizeWindows(mainWindow)
        Thread.sleep(500)
        assertThat(window2.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
    }

    @Test
    @Throws(Exception::class)
    fun testWindowSyncInMaxMode() { // old max mode is in practive new normal mode with no pinned windows
        // Issue #371 and #536

        val window0 = windowControl!!.activeWindow.apply { isPinMode = false }
        val window1 = windowControl!!.addNewWindow(window0)
        val window2 = windowControl!!.addNewWindow(window0).apply { isSynchronised = false }
        val window3 = windowControl!!.addNewWindow(window0).apply { isSynchronised = true }
        val (chapter, verse) = window3.pageManager.currentBible.currentChapterVerse

        windowControl!!.restoreWindow(window0)
        Thread.sleep(500)
        val secondNewChapterVerse = ChapterVerse(chapter, 12)
        window0.pageManager.currentBible.currentChapterVerse = secondNewChapterVerse
        assertThat(window2.isVisible, equalTo(false))
        assertThat(window3.isVisible, equalTo(false))
        assertThat(window1.isVisible, equalTo(false))
        assertThat(window0.pageManager.currentBible.currentChapterVerse, equalTo(secondNewChapterVerse))

        windowControl!!.restoreWindow(window1)
        Thread.sleep(500)
        assertThat(window0.isVisible, equalTo(false))
        assertThat(window2.isVisible, equalTo(false))
        assertThat(window3.isVisible, equalTo(false))
        assertThat(window1.pageManager.currentBible.currentChapterVerse, equalTo(secondNewChapterVerse))

        windowControl!!.restoreWindow(window2)
        Thread.sleep(500)
        assertThat(window0.isVisible, equalTo(false))
        assertThat(window3.isVisible, equalTo(false))
        assertThat(window1.isVisible, equalTo(false))
        assertThat(window2.pageManager.currentBible.currentChapterVerse, not(equalTo(secondNewChapterVerse)))

        windowControl!!.restoreWindow(window3)
        Thread.sleep(500)
        assertThat(window0.isVisible, equalTo(false))
        assertThat(window2.isVisible, equalTo(false))
        assertThat(window1.isVisible, equalTo(false))
        assertThat(window3.isVisible, equalTo(true))
        assertThat(window3.pageManager.currentBible.currentChapterVerse, equalTo(secondNewChapterVerse))

    }
}
