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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
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
import net.bible.sharedui.theme.WORKSPACE_DEFAULT_RING_WIDTH
import net.bible.sharedui.theme.abColorScheme
import net.bible.sharedui.theme.keepsDefaultWorkspaceColor
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.monoPaper
import net.bible.sharedui.theme.ringFraction
import net.bible.sharedui.theme.workspaceDisplayArgb
import net.bible.sharedui.theme.workspacePickerStartArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #4149: a workspace with the default (unset, `#444444` sentinel) colour must be shown as the
 * toolbar colour of a workspace without its own colour (the unset scheme's primaryContainer) with a
 * dashed "follows the theme" ring, not as dark grey, in every list that shows workspace colours. The active theme here is seeded from ANOTHER workspace's colour, as when the user's
 * current workspace has its own colour: the unset row must not borrow it.
 */
@OptIn(ExperimentalTestApi::class)
class WorkspaceDefaultColorDisplayTest {
    private val activeSeed = 0xFFFF8000.toInt()
    // Amber: far from the default ring's (outline) colour in every mode, also as BW grey, so the
    // "a chosen dot has no ring" check is not a near miss.
    private val chosen = 0xFFFFC107.toInt()
    private val rows = listOf(
        WorkspaceRowVd("1", "Chosen", null, chosen, isCurrent = true),
        WorkspaceRowVd("2", "Default", null, DEFAULT_WORKSPACE_COLOR_ARGB, isCurrent = false),
    )

    /** Centre colour and dashed-ring fraction of every workspace dot, rendered at density 3. */
    private fun ComposeUiTest.dots(
        dark: Boolean, mode: DisplayColorMode, content: @Composable () -> Unit,
    ): List<Pair<Color, Float>> {
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en"), LocalDensity provides Density(3f)) {
                AbTheme(darkTheme = dark, seedArgb = activeSeed, colorMode = mode, disableAnimations = true) {
                    Box(Modifier.width(360.dp)) { content() }
                }
            }
        }
        waitForIdle()
        val ringPx = WORKSPACE_DEFAULT_RING_WIDTH.value * 3f
        val ring = expectedRing(dark, mode)
        return onAllNodesWithTag(WORKSPACE_COLOR_DOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().indices.map {
            val image = onAllNodesWithTag(WORKSPACE_COLOR_DOT_TAG, useUnmergedTree = true)[it].captureToImage()
            val pixels = image.toPixelMap()
            val c = image.width / 2f
            pixels[image.width / 2, image.height / 2] to pixels.ringFraction(c, c, c - ringPx / 2, ring)
        }
    }

    /** The toolbar colour of a workspace without its own colour: the unset scheme's primaryContainer
     *  (Ink on paper: #F3EAD7 light, #2C2A26 dark); paper in the ink modes, so the ink ring shows. */
    private fun expectedUnset(dark: Boolean, mode: DisplayColorMode): Color = when (mode) {
        DisplayColorMode.NORMAL -> Color(if (dark) 0xFF2C2A26 else 0xFFF3EAD7)
        DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoPaper(dark)
        DisplayColorMode.BW -> Color(toGrayscaleArgb(abColorScheme(null, dark, mode).primaryContainer.toArgb()))
    }

    /** Ink on paper's Faint (#8A806D light, #7E786D dark); ink in the ink modes; grey in BW. */
    private fun expectedRing(dark: Boolean, mode: DisplayColorMode): Color = when (mode) {
        DisplayColorMode.NORMAL -> Color(if (dark) 0xFF7E786D else 0xFF8A806D)
        DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoInk(dark)
        DisplayColorMode.BW -> Color(toGrayscaleArgb(abColorScheme(null, dark, mode).outline.toArgb()))
    }

    private fun expectedChosen(dark: Boolean, mode: DisplayColorMode): Color = when (mode) {
        DisplayColorMode.NORMAL -> Color(chosen)
        DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoInk(dark)
        DisplayColorMode.BW -> Color(toGrayscaleArgb(chosen))
    }

    private fun assertDots(dark: Boolean, mode: DisplayColorMode, dots: List<Pair<Color, Float>>) {
        assertEquals(listOf(expectedChosen(dark, mode), expectedUnset(dark, mode)), dots.map { it.first })
        val (chosenRing, defaultRing) = dots.map { it.second }
        // Default: a DASHED ring, so part of the circle is ring colour and part is not.
        assertTrue(defaultRing in 0.25f..0.85f, "default ring fraction $defaultRing ($mode, dark=$dark)")
        // Chosen: no ring (in the ink modes the ink fill equals the ring colour, so skip it there).
        if (!mode.isInkBase) assertEquals(0f, chosenRing, "chosen ring fraction ($mode, dark=$dark)")
    }

    private fun assertSelector(dark: Boolean, mode: DisplayColorMode) = runComposeUiTest {
        val dots = dots(dark, mode) {
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
        assertDots(dark, mode, dots)
    }

    private fun assertQuick(dark: Boolean, mode: DisplayColorMode) = runComposeUiTest {
        val dots = dots(dark, mode) { WorkspaceQuickContent(rows = rows, onSelect = {}) }
        assertDots(dark, mode, dots)
    }

    @Test fun selectorLight() = assertSelector(dark = false, mode = DisplayColorMode.NORMAL)
    @Test fun selectorDark() = assertSelector(dark = true, mode = DisplayColorMode.NORMAL)
    @Test fun selectorBw() = assertSelector(dark = false, mode = DisplayColorMode.BW)
    @Test fun selectorMonochrome() = assertSelector(dark = true, mode = DisplayColorMode.MONOCHROME)
    @Test fun selectorEink() = assertSelector(dark = false, mode = DisplayColorMode.COLOR_EINK)
    @Test fun quickLight() = assertQuick(dark = false, mode = DisplayColorMode.NORMAL)
    @Test fun quickDark() = assertQuick(dark = true, mode = DisplayColorMode.NORMAL)
    @Test fun quickBw() = assertQuick(dark = true, mode = DisplayColorMode.BW)
    @Test fun quickEink() = assertQuick(dark = false, mode = DisplayColorMode.COLOR_EINK)
    @Test fun quickMonochromeLight() = assertQuick(dark = false, mode = DisplayColorMode.MONOCHROME)

    @Test
    fun pickerStartsOnTheNormalThemeContainer() {
        for (dark in listOf(false, true)) {
            assertEquals(
                DefaultThemeProposal.DEFAULT.scheme(dark)!!.primaryContainer.toArgb(),
                workspacePickerStartArgb(DEFAULT_WORKSPACE_COLOR_ARGB, dark), "dark=$dark",
            )
        }
        assertEquals(chosen, workspacePickerStartArgb(chosen, dark = true))
    }

    @Test
    fun pickingInkInMonochromeIsAChosenColour() {
        // MONOCHROME shows an unset workspace on paper (white in light, black in dark), and both are
        // presets. The picker starts on the NORMAL theme colour, so a deliberate pick is stored.
        for (dark in listOf(false, true)) {
            val paper = monoPaper(dark).toArgb()
            assertEquals(paper, workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, dark, DisplayColorMode.MONOCHROME))
            val start = workspacePickerStartArgb(DEFAULT_WORKSPACE_COLOR_ARGB, dark)
            assertFalse(keepsDefaultWorkspaceColor(DEFAULT_WORKSPACE_COLOR_ARGB, start, paper))
            assertFalse(keepsDefaultWorkspaceColor(DEFAULT_WORKSPACE_COLOR_ARGB, start, monoInk(dark).toArgb()))
        }
    }

    @Test
    fun resolverMapsOnlyTheUnsetSentinel() {
        for (mode in DisplayColorMode.entries) for (dark in listOf(false, true)) {
            val scheme = abColorScheme(null, dark, mode)
            val unset = (if (mode.isInkBase) scheme.surface else scheme.primaryContainer).toArgb()
            assertEquals(unset, workspaceDisplayArgb(DEFAULT_WORKSPACE_COLOR_ARGB, dark, mode), "$mode $dark")
            assertEquals(unset, workspaceDisplayArgb(null, dark, mode), "$mode $dark null")
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
