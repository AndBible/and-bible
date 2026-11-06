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
package net.bible.sharedui.workspaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/**
 * The workspace QUICK sheet must open scrolled to, and marking, the current workspace (#4015), the way the full
 * selector does. The rows arrive after the sheet is composed (the controller loads them asynchronously), so the
 * test feeds them late, as the host does.
 */
@OptIn(ExperimentalTestApi::class)
class WorkspaceQuickCurrentRowTest {
    private fun rows(count: Int, current: Int) = (1..count).map {
        WorkspaceRowVd("$it", "Workspace $it", null, 0xFF2E7D32.toInt(), isCurrent = it == current)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showSheet(
        items: List<WorkspaceRowVd>, animations: Boolean, lateRows: Boolean,
    ) {
        var shown by mutableStateOf(if (lateRows) emptyList() else items)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = !animations) {
                    AbQuickSheet(open = true, title = "Switch workspace", onDismiss = {}) {
                        WorkspaceQuickContent(rows = shown, onSelect = {})
                    }
                }
            }
        }
        waitForIdle()
        if (lateRows) { shown = items; waitForIdle() }
    }

    @Test
    fun opensScrolledToCurrentWorkspaceNearTheEnd() = runComposeUiTest {
        showSheet(rows(40, current = 37), animations = false, lateRows = false)
        onNodeWithText("Workspace 37", substring = true).assertIsDisplayed()
    }

    @Test
    fun opensScrolledToCurrentWorkspaceWhenRowsArriveLate() = runComposeUiTest {
        showSheet(rows(40, current = 37), animations = false, lateRows = true)
        onNodeWithText("Workspace 37", substring = true).assertIsDisplayed()
    }

    @Test
    fun opensScrolledToCurrentWorkspaceWithAnimations() = runComposeUiTest {
        showSheet(rows(40, current = 37), animations = true, lateRows = true)
        waitForIdle()
        onNodeWithText("Workspace 37", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentRowReportsSelectedSemantics() = runComposeUiTest {
        showSheet(rows(3, current = 2), animations = false, lateRows = false)
        onNodeWithText("Workspace 2", substring = true).assertIsSelected()
        onNodeWithText("Workspace 1").assertIsNotSelected()
    }
}
