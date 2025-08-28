/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.ChapterVerse
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowRestoreSyncTest {

    private var windowControl: WindowControl? = null
    private val windowRepository get() = windowControl!!.windowRepository

    @Before
    @Throws(Exception::class)
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.settings.setBoolean("first-time", false)
        windowRepository.initialize()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testRestoreMinimizedSynchronizedWindow() {
        // Create two synchronized windows
        val window1 = windowControl!!.activeWindow
        val window2 = windowControl!!.addNewWindow(window1)
        
        // Ensure both windows are synchronized
        window1.isSynchronised = true
        window2.isSynchronised = true
        
        // Set initial position for both windows
        val initialChapterVerse = ChapterVerse(1, 1)
        window1.pageManager.currentBible.currentChapterVerse = initialChapterVerse
        window2.pageManager.currentBible.currentChapterVerse = initialChapterVerse
        
        println("Initial state:")
        println("Window1 position: ${window1.pageManager.currentBible.currentChapterVerse}")
        println("Window2 position: ${window2.pageManager.currentBible.currentChapterVerse}")
        println("Window1 visible: ${window1.isVisible}")
        println("Window2 visible: ${window2.isVisible}")
        
        // Minimize window2
        windowControl!!.minimiseWindow(window2)
        println("After minimizing window2:")
        println("Window1 visible: ${window1.isVisible}")
        println("Window2 visible: ${window2.isVisible}")
        
        assertThat(window2.isVisible, equalTo(false))
        assertThat(window1.isVisible, equalTo(true))
        
        // Move window1 to a different verse
        val newChapterVerse = ChapterVerse(2, 5)
        window1.pageManager.currentBible.currentChapterVerse = newChapterVerse
        println("After moving window1:")
        println("Window1 position: ${window1.pageManager.currentBible.currentChapterVerse}")
        println("Window2 position: ${window2.pageManager.currentBible.currentChapterVerse}")
        
        assertThat(window1.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
        
        // Restore window2 - this should synchronize it with window1's current position
        println("Before restoring window2:")
        println("Active window: ${windowControl!!.activeWindow.displayId}")
        println("Window1 sync: ${window1.isSynchronised}")
        println("Window2 sync: ${window2.isSynchronised}")
        println("Window1 pin mode: ${window1.isPinMode}")
        println("Window2 pin mode: ${window2.isPinMode}")
        println("Window1 links window: ${window1.isLinksWindow}")
        println("Window2 links window: ${window2.isLinksWindow}")
        
        windowControl!!.restoreWindow(window2)
        Thread.sleep(500) // Allow time for synchronization
        
        println("After restoring window2:")
        println("Window1 visible: ${window1.isVisible}")
        println("Window2 visible: ${window2.isVisible}")
        println("Active window: ${windowControl!!.activeWindow.displayId}")
        println("Window1 position: ${window1.pageManager.currentBible.currentChapterVerse}")
        println("Window2 position: ${window2.pageManager.currentBible.currentChapterVerse}")
        
        assertThat(window2.isVisible, equalTo(true))
        // This is the test that should pass with our fix:
        // Window2 should be synchronized to window1's current position
        assertThat(window2.pageManager.currentBible.currentChapterVerse, equalTo(newChapterVerse))
    }
}