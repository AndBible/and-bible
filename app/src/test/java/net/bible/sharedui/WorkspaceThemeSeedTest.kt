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
package net.bible.sharedui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ORANGE = 0xFFFF8000.toInt()

class WorkspaceThemeSeedTest {
    @Test
    fun `master switch off means no seed, whatever the workspace colour is`() {
        assertNull(workspaceThemeSeedArgb(emptySet(), ORANGE))
        assertNull(workspaceThemeSeedArgb(setOf(TOOLBAR_LITERAL_COLOR_FEATURE), ORANGE))
    }

    @Test
    fun `master switch on passes the workspace colour through`() {
        assertEquals(ORANGE, workspaceThemeSeedArgb(setOf(WORKSPACE_COLOR_THEME_FEATURE), ORANGE))
    }

    @Test
    fun `a workspace with no colour has no seed even when the switch is on`() {
        assertNull(workspaceThemeSeedArgb(setOf(WORKSPACE_COLOR_THEME_FEATURE), null))
    }
}
