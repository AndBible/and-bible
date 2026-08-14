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
package net.bible.android.view.activity.workspaces

import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGlobalCopyTest {
    private fun tds(fontSize: Int?) = WorkspaceEntities.TextDisplaySettings().apply {
        if (fontSize != null) setValue(Types.FONTSIZE, fontSize)
    }

    @Test
    fun stagedWorkspaceOverrideIsClearedWhenItMatchesTheNewGlobal() {
        // The source workspace's 22 has just been copied into the global defaults.
        val newGlobal = tds(22)
        val staged = listOf(tds(22), tds(14))     // one matches the new global, one does not

        val changed = WorkspaceEntities.TextDisplaySettings.propagateGlobalChange(
            dirtyTypes = setOf(Types.FONTSIZE),
            globalSettings = newGlobal,
            workspacesWithWindows = staged.map { it to emptyList() },
        )

        assertTrue(changed)
        assertNull(staged[0].getValue(Types.FONTSIZE))    // now inherits — Save cannot resurrect it
        assertEquals(14, staged[1].getValue(Types.FONTSIZE))
    }
}
