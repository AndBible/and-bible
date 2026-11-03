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

package net.bible.android.view.compose

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.WorkspaceSelectorScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The workspace summary (window/document listing) is clamped to two lines with an ellipsis, as the
 * legacy `workspace_list_item.xml` did (`lines="2"`, `ellipsize="end"`); unbounded it grew very tall
 * for workspaces with many windows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceSelectorSummaryTruncationTest {
    @get:Rule val compose = createComposeRule()

    private val summaryState = androidx.compose.runtime.mutableStateOf("")

    private fun show(summary: String) {
        summaryState.value = summary
        if (!shown) { shown = true; setUp() }
        compose.waitForIdle()
    }

    private var shown = false

    private fun setUp() = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                WorkspaceSelectorScreen(
                    title = "Select workspace",
                    workspaces = listOf(WorkspaceRowVd("1", "Study", summaryState.value, 0xFF2E7D32.toInt(), isCurrent = false)),
                    dirty = false, canDelete = true, filtering = false, query = "", searchModeActive = false,
                    copySettingsState = null, pendingSelectId = null,
                    onQueryChange = {}, onOpenSearch = {}, onCloseSearch = {},
                    onMove = { _, _ -> }, onSelect = {}, onRename = { _, _ -> },
                    onClone = { _, _ -> }, onDelete = {}, onEditSettings = {}, onCopySettings = {},
                    onCopySettingsToGlobal = {}, onChooseCopyTypes = {}, onChooseCopyTargets = {},
                    onCancelCopySettings = {}, onCreate = {}, onSave = {}, onCancel = {},
                    onConfirmPendingSelect = {}, onDismissPendingSelect = {}, onHelp = {}, onNavigateUp = {},
                )
            }
        }
    }

    private fun heightOf(text: String): Dp =
        compose.onNodeWithText(text).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test fun longSummaryIsClampedToTwoLines() {
        val two = "Genesis 1\nExodus 2"
        show(two)
        val twoLines = heightOf(two)

        val long = (1..60).joinToString(", ") { "Document number $it" }
        show(long)
        assertEquals(twoLines.value, heightOf(long).value, 0.5f)
    }

    @Test fun oneLineSummaryStaysOneLine() {
        show("3 windows")
        val one = heightOf("3 windows")
        show("a\nb")
        assertTrue(heightOf("a\nb").value > one.value)
    }
}
