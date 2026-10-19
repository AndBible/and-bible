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
package net.bible.sharedui.reading

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceResultTargetTest {
    @Test
    fun aDifferentWorkspaceIsLoaded() = assertEquals("b", workspaceToLoad("b", changed = false, currentWorkspaceId = "a"))

    @Test
    fun aDifferentWorkspaceWinsOverTheChangedFlag() = assertEquals("b", workspaceToLoad("b", changed = true, currentWorkspaceId = "a"))

    @Test
    fun theCurrentWorkspaceIsReloadedOnlyWhenItChanged() {
        assertEquals("a", workspaceToLoad("a", changed = true, currentWorkspaceId = "a"))
        assertEquals("a", workspaceToLoad(null, changed = true, currentWorkspaceId = "a"))
        assertEquals(null, workspaceToLoad("a", changed = false, currentWorkspaceId = "a"))
    }

    @Test
    fun aPlainSaveThatChangedNothingAppliesNothing() = assertEquals(null, workspaceToLoad(null, changed = false, currentWorkspaceId = "a"))
}
