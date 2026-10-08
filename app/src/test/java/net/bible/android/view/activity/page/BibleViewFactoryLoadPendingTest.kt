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

package net.bible.android.view.activity.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F133: a window whose load timed out reloads exactly once when its BibleView is created. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewFactoryLoadPendingTest {
    private fun window(): Window {
        val repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        return Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(), isSynchronized = false, isPinMode = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.toString()),
            ),
            mock(CurrentPageManager::class.java),
            repo,
        )
    }

    /** Runs resolveView with a fake view type, recording the order of events. */
    private fun resolve(w: Window, map: MutableMap<IdType, String>, events: MutableList<String>) =
        BibleViewFactory.resolveView(
            w, map,
            rebind = { events += "rebind" },
            create = { events += "create"; "view" },
            assign = { events += "assign" },
            load = { events += "load" },
        )

    @Test
    fun aCreatedViewIsAssignedBeforeThePendingLoadRunsAndTheFlagIsCleared() {
        val w = window().apply { loadPending = true }
        val map = mutableMapOf<IdType, String>()
        val events = mutableListOf<String>()
        resolve(w, map, events)
        assertEquals(listOf("create", "assign", "load"), events)
        assertEquals("view", map[w.id])
        assertEquals(false, w.loadPending)
    }

    @Test
    fun aCachedViewIsOnlyReboundAndNeverLoads() {
        val w = window().apply { loadPending = true }
        val map = mutableMapOf(w.id to "cached")
        val events = mutableListOf<String>()
        assertEquals("cached", resolve(w, map, events))
        assertEquals(listOf("rebind"), events)
    }

    @Test
    fun aCreatedViewForAWindowWithoutAPendingLoadDoesNotLoad() {
        val events = mutableListOf<String>()
        resolve(window(), mutableMapOf(), events)
        assertEquals(listOf("create", "assign"), events)
    }
}
