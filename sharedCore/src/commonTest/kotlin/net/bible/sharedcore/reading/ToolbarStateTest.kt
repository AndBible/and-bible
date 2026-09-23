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

package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertFalse

class ToolbarStateTest {

    /**
     * T20 (final review, minor): this comment used to read "...so today's literal colour is the
     * default", true only while `workspace_color_theme`'s master switch existed and literal colour
     * really was the app's default. That switch is retired (Task 20, 2026-09-23) -- derived-from-theme
     * is now unconditionally the default; see [ToolbarState.deriveToolbarFromTheme]'s own kdoc. `EMPTY`
     * is a placeholder sentinel no real `ToolbarStateServiceImpl` ever emits, not a claim about what
     * the app's default rendering is -- `false` here is simply an inert starting value before the
     * real service populates it.
     */
    @Test
    fun `EMPTY's placeholder deriveToolbarFromTheme is false, not a claim about the app's real default`() {
        assertFalse(ToolbarState.EMPTY.deriveToolbarFromTheme)
    }
}
