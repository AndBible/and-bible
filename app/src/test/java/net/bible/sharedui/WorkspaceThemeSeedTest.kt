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

/**
 * The workspace-colour theme is always on (2026-09-18 decision, `docs/superpowers/status/compose-port-status.md`
 * "Queued -- retire the workspace_color_theme experimental flag"): there is no more master switch,
 * so these cases are no longer on/off pairs. [TOOLBAR_LITERAL_COLOR_FEATURE] is the one flag left --
 * a separate opt-out back to the literal toolbar colour, not decided by this removal.
 */
class WorkspaceThemeSeedTest {
    @Test
    fun `the workspace colour is always the seed, no flag required`() {
        assertEquals(ORANGE, workspaceThemeSeedArgb(ORANGE))
    }

    @Test
    fun `a workspace with no colour has no seed`() {
        assertNull(workspaceThemeSeedArgb(null))
    }

    @Test
    fun `the toolbar derives from the theme by default, no flag required`() {
        assertEquals(true, deriveToolbarFromTheme(emptySet()))
    }

    @Test
    fun `the literal opt-out keeps the literal toolbar colour`() {
        assertEquals(false, deriveToolbarFromTheme(setOf(TOOLBAR_LITERAL_COLOR_FEATURE)))
    }
}
