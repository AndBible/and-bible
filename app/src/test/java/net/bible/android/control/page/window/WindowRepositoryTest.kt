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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils


import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test


import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.contains
import org.junit.Assert.assertThat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowRepositoryTest {
    private var windowControl: WindowControl? = null
    private val windowRepository get() = windowControl!!.windowRepository

    @Before
    @Throws(Exception::class)
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowRepository.initialize()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
        windowRepository.clear()
    }

    @Test
    @Throws(Exception::class)
    fun testGetWindow() {
        assertThat(windowRepository.getWindow(1)?.id, equalTo(1L))
    }

    @Test
    @Throws(Exception::class)
    fun testGetActiveWindow() {
        assertThat(windowRepository.activeWindow.id, equalTo(1L))
    }

    @Test
    @Throws(Exception::class)
    fun testSetActiveWindow() {
        val newWindow = windowRepository.addNewWindow()
        assertThat(windowRepository.activeWindow.id, not(equalTo(newWindow.id)))
        windowRepository.activeWindow = newWindow
        assertThat(windowRepository.activeWindow.id, equalTo(newWindow.id))
    }

    @Test
    @Throws(Exception::class)
    fun testMoveWindowToPosition() {
        val originalWindow = windowRepository.activeWindow
        assertThat(windowRepository.windows, contains(originalWindow))

        val newWindow = windowRepository.addNewWindow()
        assertThat(windowRepository.activeWindow, equalTo(originalWindow))
        assertThat(windowRepository.windows, contains(originalWindow, newWindow))

        val newWindow2 = windowRepository.addNewWindow()
        assertThat(windowRepository.windows, contains(originalWindow, newWindow2, newWindow))

        windowRepository.moveWindowToPosition(newWindow, 0)
        assertThat(windowRepository.windows, contains(newWindow, originalWindow, newWindow2))

        windowRepository.moveWindowToPosition(newWindow, 1)
        assertThat(windowRepository.windows, contains(originalWindow, newWindow, newWindow2))

        windowRepository.moveWindowToPosition(originalWindow, 2)
        assertThat(windowRepository.windows, contains(newWindow, newWindow2, originalWindow))

        windowRepository.moveWindowToPosition(originalWindow, 0)
        assertThat(windowRepository.windows, contains(originalWindow, newWindow, newWindow2))
    }

    @Test
    @Throws(Exception::class)
    fun testMoveMissingWindowToPosition() {
        val originalWindow = windowRepository.activeWindow
        val newWindow = windowRepository.addNewWindow()

        windowRepository.close(newWindow)

        windowRepository.moveWindowToPosition(newWindow, 0)
        assertThat(windowRepository.windows, contains(originalWindow))
    }

    @Test
    fun testAddAfterDeletedWindowsDifferent() {
        val newWindow = windowRepository.addNewWindow()
        windowRepository.close(newWindow)
        val newWindow2 = windowRepository.addNewWindow()
        assertThat(newWindow == newWindow2, not(true))
        assertThat(newWindow.hashCode() == newWindow2.hashCode(), not(true))
    }
}
