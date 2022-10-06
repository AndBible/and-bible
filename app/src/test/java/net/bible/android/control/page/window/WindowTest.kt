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
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities
import net.bible.service.device.speak.AbstractSpeakTests
import net.bible.service.history.HistoryManager
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
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowTest {
    private lateinit var mockCurrentPageManagerProvider: Provider<CurrentPageManager>
    private var windowControl: WindowControl? = null
    var windowRepository: WindowRepository? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val bibleTraverser = mock(BibleTraverser::class.java)

        val bookmarkControl = BookmarkControl(AbstractSpeakTests.windowControl, mock(AndroidResourceProvider::class.java))
        mockCurrentPageManagerProvider = Provider {
            CurrentPageManager(SwordDocumentFacade(), bibleTraverser, bookmarkControl, windowRepository!!)
        }
        val mockHistoryManagerProvider = Provider { HistoryManager(windowControl!!) }
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
        windowControl = WindowControl(windowRepository!!)
        windowRepository!!.initialize()
        windowRepository = WindowRepository(mockCurrentPageManagerProvider, mockHistoryManagerProvider)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository!!.windowUpdateScope)
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
