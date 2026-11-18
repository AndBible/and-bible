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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import net.bible.sharedcore.theme.toGrayscaleArgb
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.DefaultThemeProposal
import net.bible.sharedui.theme.WORKSPACE_COLOR_DOT_TAG
import net.bible.sharedui.theme.abColorScheme
import net.bible.sharedui.theme.keepsDefaultWorkspaceColor
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.workspaceDisplayArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #4149: a workspace with the default (unset, `#444444` sentinel) colour must be shown with the
 * colour of the theme an unset workspace gets, not as dark grey, in every list that shows workspace
 * colours. The active theme here is seeded from ANOTHER workspace's colour, as when the user's
 * current workspace has its own colour: the unset row must not borrow it.
 */
@OptIn(ExperimentalTestApi::class)
class WorkspaceDefaultColorDisplayTest {
    private val activeSeed = 0xFFFF8000.toInt()
    private val chosen = 0xFF2E7D32.toInt()
    private val rows = listOf(
        WorkspaceRowVd("1", "Chosen", null, chosen, isCurrent = true),
        WorkspaceRowVd("2", "Default", null, DEFAULT_WORKSPACE_COLOR_ARGB, isCurrent = false),
    )

    private fun ComposeUiTest.dotColors(
        dark: Boolean, mode: DisplayColorMode, content: @Composable () -> Unit,
    ): List<Color> {
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = dark, seedArgb = activeSeed, colorMode = mode, disableAnimations = true) {
                    Box(Modifier.width(360.dp)) { content() }
                }
            }
        }
        waitForIdle()
        return onAllNodesWithTag(WORKSPACE_COLOR_DOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().indices.map {
            val image = onAllNodesWithTag(WORKSPACE_COLOR_DOT_TAG, useUnmergedTree = true)[it].captureToImage()
            image.toPixelMap()[image.width / 2, image.height / 2]
        }
    }

    private fun expectedUnset(dark: Boolean, mode: DisplayColorMode): Color = when (mode) {
        DisplayColorMode.NORMAL -> DefaultThemeProposal.DEFAULT.scheme(dark)!!.primary
        DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoInk(dark)
        DisplayColorMode.BW -> Color(toGrayscaleArgb(abColorScheme(null, dark, mode).primary.toArgb()))
    }

    private fun expectedChosen(dark: Boolean, mode: DisplayColorMode): Color = when (mode) {
        DisplayColorMode.NORMAL -> Color(chosen)
        DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoInk(dark)
        DisplayColorMode.BW -> Color(toGrayscaleArgb(chosen))
    }

    private fun assertSelector(dark: Boolean, mode: DisplayColorMode) = runComposeUiTest {
        val dots = dotColors(dark, mode) {
            WorkspaceSelectorScreen(
                title = "Select workspace", workspaces = rows, dirty = false, canDelete = true,
                filtering = false, query = "", searchModeActive = false,
                copySettingsState = null, pendingSelectId = null,
                onQueryChange = {}, onOpenSearch = {}, onCloseSearch = {},
                onMove = { _, _ -> }, onSelect = {}, onRename = { _, _ -> },
                onClone = { _, _ -> }, onDelete = {}, onEditSettings = {}, onCopySettings = {},
                onCopySettingsToGlobal = {}, onChooseCopyTypes = {}, onChooseCopyTargets = {},
                onCancelCopySettings = {}, onCreate = {}, onSave = {}, onCancel = {},
                onConfirmPendingSelect = {}, onDismissPendingSelect = {}, onHelp = {}, onNavigateUp = {},
            )
        }
        assertEquals(listOf(expectedChosen(dark, mode), expectedUnset(dark, mode)), dots)
    }

    private fun assertQuick(dark: Boolean, mode: DisplayColorMode) = runComposeUiTest {
        val dots = dotColors(dark, mode) { WorkspaceQuickContent(rows = rows, onSelect = {}) }
        assertEquals(listOf(expectedChosen(dark, mode), expectedUnset(dark, mode)), dots)
    }

    @Test fun selectorLight() = assertSelector(dark = false, mode = DisplayColorMode.NORMAL)
    @Test fun selectorDark() = assertSelector(dark = true, mode = DisplayColorMode.NORMAL)
    @Test fun selectorBw() = assertSelector(dark = false, mode = DisplayColorMode.BW)
    @Test fun selectorMonochrome() = assertSelector(dark = true, mode = DisplayColorMode.MONOCHROME)
    @Test fun selectorEink() = assertSelector(dark = false, mode = DisplayColorMode.COLOR_EINK)
    @Test fun quickLight() = assertQuick(dark = false, mode = DisplayColorMode.NORMAL)
    @Test fun quickDark() = assertQuick(dark = true, mode = DisplayColorMode.NORMAL)
    @Test fun quickBw() = assertQuick(dark = true, mode = DisplayColorMode.BW)

    @Test
    fun resolverMapsOnlyTheUnsetSentinel() {
        for (mode in DisplayColorMode.entries) for (dark in listOf(false, true)) {
            val unsetScheme = abColorScheme(null, dark, mode).primary.toArgb()
            assertEquals(unsetScheme, workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, dark, mode), "$mode $dark")
            assertEquals(unsetScheme, workspaceDisplayArgb(null, dark, mode), "$mode $dark null")
            assertEquals(chosen, workspaceDisplayArgb(chosen, dark, mode), "$mode $dark chosen")
        }
    }

    @Test
    fun bwUnsetIsGreyOnly() {
        val argb = workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.BW)
        assertEquals(toGrayscaleArgb(argb), argb)
    }

    @Test
    fun confirmingTheUnchangedThemeColourKeepsTheDefault() {
        val shown = workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL)
        assertTrue(keepsDefaultWorkspaceColor(DEFAULT_WORKSPACE_COLOR_ARGB, shown, shown))
        assertTrue(keepsDefaultWorkspaceColor(null, shown, shown))
        // An alpha-less pick of the same RGB is the same colour.
        assertTrue(keepsDefaultWorkspaceColor(DEFAULT_WORKSPACE_COLOR_ARGB, shown, shown and 0x00FFFFFF))
    }

    @Test
    fun anyOtherPickIsAChosenColour() {
        val shown = workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL)
        assertFalse(keepsDefaultWorkspaceColor(DEFAULT_WORKSPACE_COLOR_ARGB, shown, chosen))
        // A workspace that already has its own colour stores whatever is confirmed.
        assertFalse(keepsDefaultWorkspaceColor(chosen, chosen, chosen))
    }
}
