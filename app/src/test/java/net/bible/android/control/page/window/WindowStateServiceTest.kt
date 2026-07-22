/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.window.WindowStateValue
import net.bible.test.DatabaseResetter
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowStateServiceTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun buildSnapshot_mapsWindowsInSortedOrderWithState() {
        val active = windowControl.activeWindow            // auto-creates window 1
        val w2 = windowControl.addNewWindow(active)         // window 2
        windowControl.minimiseWindow(w2)                    // w2 -> MINIMISED

        val svc = WindowStateServiceImpl()
        svc.refresh(windowRepository)
        val layout = svc.layout.value

        // ids in sortedWindows order
        assertThat(
            layout.windows.map { it.id },
            equalTo(windowRepository.sortedWindows.map { it.id.toString() })
        )
        assertThat(layout.activeWindowId, equalTo(windowRepository.activeWindow.id.toString()))
        val w2Snapshot = layout.windows.single { it.id == w2.id.toString() }
        assertThat(w2Snapshot.state, equalTo(WindowStateValue.MINIMISED))
        assertThat(w2Snapshot.isVisible, equalTo(false))
        assertThat(layout.maximizedWindowId, equalTo(null))
        assertThat(layout.restoreButtonsVisible, equalTo(true))
    }

    private fun liveService(): WindowStateService = GlobalContext.get().get()

    @Test
    fun addAndMinimiseWindow_updateLayoutWithoutManualRefresh() {
        val service = liveService()
        val active = windowControl.activeWindow
        val w2 = windowControl.addNewWindow(active)

        assertThat(service.layout.value.windows.map { it.id }, contains(*windowRepository.sortedWindows.map { it.id.toString() }.toTypedArray()))
        assertThat(service.layout.value.windows.any { it.id == w2.id.toString() }, equalTo(true))

        windowControl.minimiseWindow(w2)
        val w2Snapshot = service.layout.value.windows.single { it.id == w2.id.toString() }
        assertThat(w2Snapshot.state, equalTo(WindowStateValue.MINIMISED))
    }

    @Test
    fun maximiseWindow_setsMaximizedWindowIdInLayout() {
        val service = liveService()
        val active = windowControl.activeWindow
        windowControl.addNewWindow(active)
        windowControl.maximiseWindow(active)
        assertThat(service.layout.value.maximizedWindowId, equalTo(active.id.toString()))
        windowControl.unMaximise()
        assertThat(service.layout.value.maximizedWindowId, equalTo(null))
    }

    @Test
    fun setActiveWindow_updatesActiveWindowIdInLayout() {
        val service = liveService()
        val active = windowControl.activeWindow
        val w2 = windowControl.addNewWindow(active)
        windowControl.activeWindow = w2
        assertThat(service.layout.value.activeWindowId, equalTo(w2.id.toString()))
    }

    @Test
    fun windowChanged_syncFlagPropagatesToLayout() {
        val service = liveService()
        val active = windowControl.activeWindow
        val w2 = windowControl.addNewWindow(active)
        w2.isSynchronised = true    // triggers WindowChangedEvent via the isSynchronised setter
        val w2Snapshot = service.layout.value.windows.single { it.id == w2.id.toString() }
        assertThat(w2Snapshot.isSynchronised, equalTo(true))
    }
}
