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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.AbstractSpeakTests
import net.bible.service.sword.SwordDocumentFacade
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.DefaultLeafKeyList

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.shadows.ShadowLog
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowTest {
    private lateinit var mockCurrentPageManagerProvider: () -> CurrentPageManager
    private var windowControl: WindowControl? = null
    private val windowRepository: WindowRepository get() = windowControl!!.windowRepository

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val bibleTraverser = mock(BibleTraverser::class.java)

        val bookmarkControl = BookmarkControl(AbstractSpeakTests.windowControl, mock(AndroidResourceProvider::class.java))
        mockCurrentPageManagerProvider = {
            CurrentPageManager(bibleTraverser, bookmarkControl, windowControl!!)
        }
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowRepository.initialize()
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
        val pageManager = mockCurrentPageManagerProvider()
        var window = Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(),
                isSynchronized = true,
                isPinMode = false,
                isLinksWindow = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.MINIMISED.toString()),
            ),
            pageManager,
            windowRepository
        )
        window.isSynchronised = true
        window.weight = 1.23456f

        //var pageManager = window.pageManager
        var biblePage = pageManager.currentBible
        biblePage.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2)

        // serialize state
        val entity = window.entity
        println(entity)

        val newPm = mockCurrentPageManagerProvider()
        // recreate window from saved state
        window = Window(entity, newPm, windowRepository)
        assertThat(window.windowState, equalTo(WindowState.MINIMISED))
        assertThat(window.isSynchronised, equalTo(true))
        assertThat(window.weight, equalTo(1.23456f))

        //pageManager = window.pageManager
        biblePage = pageManager.currentBible
        assertThat<Book>(biblePage.currentDocument, equalTo<Book>(PassageTestData.ESV))
        assertThat(biblePage.singleKey.name, equalTo(PassageTestData.PS_139_2.name))
    }
    @Test
    fun switchingWorkspaceReleasesOutgoingWindowsAndPagesButKeepsReplacementLive() {
        val previousRepository = windowControl!!.windowRepository
        val repository = WindowRepository(CoroutineScope(Dispatchers.Main + Job().apply { cancel() }))
        val outgoing = mutableListOf<Window>()
        fun pages(window: Window) = with(window.pageManager) {
            listOf(currentDictionary, currentGeneralBook, currentMap)
        }
        val meta = mock(BookMetaData::class.java)
        `when`(meta.getProperty("AndBibleSpecial")).thenReturn("1")
        `when`(meta.getProperty("AndBibleMyDocument")).thenReturn("1")
        val book = mock(Book::class.java).apply {
            `when`(bookMetaData).thenReturn(meta)
            `when`(initials).thenReturn("MyDoc_Switch")
            `when`(globalKeyList).thenReturn(DefaultKeyList().apply {
                addAll(DefaultLeafKeyList("p1", "p1"))
            })
        }
        fun prime(window: Window) {
            window.windowState = WindowState.VISIBLE
            window.pageManager.currentBible.onlySetCurrentDocument(book)
            window.loadText()
            pages(window).forEach {
                it.onlySetCurrentDocument(book)
                assertThat(it.cachedGlobalKeyList!!.size, equalTo(1))
            }
        }
        try {
            windowControl!!.windowRepository = repository
            repository.initialize()
            outgoing += repository.sortedWindows
            outgoing.forEach(::prime)
            val workspace = WorkspaceEntities.Workspace("replacement")
            net.bible.service.db.DatabaseContainer.instance.workspaceDb.workspaceDao().insertWorkspace(workspace)

            repository.loadFromDb(workspace.id)
            assertThat(repository.id, equalTo(workspace.id))
            val replacement = repository.activeWindow
            prime(replacement)
            ShadowLog.clear()

            MyDocumentBookManager.emitForTest(MyDocumentChange.DocumentUpdated("MyDoc_Switch"))

            val reloads = ShadowLog.getLogs().map { it.msg }
            assertThat(reloads.count { it == "updateText ${replacement.hashCode()}" }, equalTo(1))
            pages(replacement).forEach { assertThat(it.hasCachedKeyListForTest(), equalTo(false)) }
            outgoing.forEach { window ->
                assertThat("discarded window must not reload", reloads.count { it == "updateText ${window.hashCode()}" }, equalTo(0))
                pages(window).forEach {
                    assertThat("discarded page must not react", it.hasCachedKeyListForTest(), equalTo(true))
                }
            }
        } finally {
            repository.clear(destroy = true)
            outgoing.forEach { it.destroy() }
            windowControl!!.windowRepository = previousRepository
        }
    }

    @Test
    fun destroyingAWindowStopsReloadsAndAllItsPagesReactingToDocumentUpdates() {
        // loadText's synchronous setup stays real; cancel only the background content fetch.
        val repository = WindowRepository(CoroutineScope(Dispatchers.Main + Job().apply { cancel() }))
        fun createWindow(): Window = Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(), isSynchronized = false, isPinMode = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.toString()),
            ),
            mockCurrentPageManagerProvider(),
            repository,
        )
        val live = createWindow()
        val destroyed = createWindow()
        val meta = mock(BookMetaData::class.java)
        `when`(meta.getProperty("AndBibleSpecial")).thenReturn("1")
        `when`(meta.getProperty("AndBibleMyDocument")).thenReturn("1")
        val book = mock(Book::class.java).apply {
            `when`(bookMetaData).thenReturn(meta)
            `when`(initials).thenReturn("MyDoc_A")
            `when`(globalKeyList).thenReturn(DefaultKeyList().apply {
                addAll(DefaultLeafKeyList("p1", "p1"))
            })
        }
        fun pages(window: Window) = with(window.pageManager) {
            listOf(currentDictionary, currentGeneralBook, currentMap)
        }
        try {
            listOf(live, destroyed).forEach { window ->
                window.windowState = WindowState.VISIBLE
                // Mark a MyDocument as displayed using the real loadText setup.
                window.pageManager.currentBible.onlySetCurrentDocument(book)
                window.loadText()
                pages(window).forEach { page ->
                    page.onlySetCurrentDocument(book)
                    assertThat(page.cachedGlobalKeyList!!.size, equalTo(1))
                }
            }
            destroyed.destroy()
            ShadowLog.clear()

            MyDocumentBookManager.emitForTest(MyDocumentChange.DocumentUpdated("MyDoc_A"))

            val reloads = ShadowLog.getLogs().map { it.msg }
            assertThat(reloads.count { it == "updateText ${live.hashCode()}" }, equalTo(1))
            assertThat(reloads.count { it == "updateText ${destroyed.hashCode()}" }, equalTo(0))
            pages(live).forEach { assertThat(it.hasCachedKeyListForTest(), equalTo(false)) }
            pages(destroyed).forEach {
                assertThat("destroyed window's pages must not react", it.hasCachedKeyListForTest(), equalTo(true))
            }
        } finally {
            live.destroy()
            destroyed.destroy()
        }
    }

}
