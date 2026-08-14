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
import org.junit.Test

/**
 * Exercises [WorkspaceEntities.TextDisplaySettings.copyIntoGlobalDefaults] — the seam both
 * workspace-selector screens (Compose [WorkspaceServiceImpl] and classic
 * [WorkspaceSelectorActivity]) call so their staged, in-memory workspace overrides don't get
 * written straight back over a copy-to-global on the next Save.
 *
 * NOT covered here: that the two call sites actually invoke this function with the right
 * arguments. That is a wiring fact, verified by the on-device checklist, not by this test.
 */
class WorkspaceGlobalCopyTest {
    private fun tds(fontSize: Int?) = WorkspaceEntities.TextDisplaySettings().apply {
        if (fontSize != null) setValue(Types.FONTSIZE, fontSize)
    }

    @Test
    fun `staged override matching the new global is nulled, a differing one is kept`() {
        val global = tds(14)
        val resolvedSource = tds(22) // the source's fully-resolved FONTSIZE being copied in
        val matching = tds(22)
        val differing = tds(18)

        WorkspaceEntities.TextDisplaySettings.copyIntoGlobalDefaults(
            global = global,
            resolvedSource = resolvedSource,
            dirtyTypes = setOf(Types.FONTSIZE),
            stagedWorkspaceSettings = listOf(matching, differing),
        )

        assertNull(matching.getValue(Types.FONTSIZE))       // now inherits — Save cannot resurrect it
        assertEquals(18, differing.getValue(Types.FONTSIZE)) // untouched, still a real override
    }

    @Test
    fun `the returned global carries the resolved source's value`() {
        val global = tds(14)
        val resolvedSource = tds(22)

        val newGlobal = WorkspaceEntities.TextDisplaySettings.copyIntoGlobalDefaults(
            global = global,
            resolvedSource = resolvedSource,
            dirtyTypes = setOf(Types.FONTSIZE),
            stagedWorkspaceSettings = emptyList(),
        )

        assertEquals(22, newGlobal.getValue(Types.FONTSIZE))
    }

    @Test
    fun `a staged workspace that already inherits is left alone, not crashed on`() {
        val global = tds(14)
        val resolvedSource = tds(22)
        val alreadyInheriting = tds(null)

        WorkspaceEntities.TextDisplaySettings.copyIntoGlobalDefaults(
            global = global,
            resolvedSource = resolvedSource,
            dirtyTypes = setOf(Types.FONTSIZE),
            stagedWorkspaceSettings = listOf(alreadyInheriting),
        )

        assertNull(alreadyInheriting.getValue(Types.FONTSIZE))
    }
}
