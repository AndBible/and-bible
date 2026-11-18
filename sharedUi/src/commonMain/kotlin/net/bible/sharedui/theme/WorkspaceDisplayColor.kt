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

package net.bible.sharedui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.isWorkspaceColorSet
import net.bible.sharedcore.theme.accentArgbFor

/** Test tag of the colour dot in workspace selector and quick-switch rows. */
const val WORKSPACE_COLOR_DOT_TAG = "workspaceColorDot"

/**
 * The ARGB that stands for a workspace's colour wherever it is shown (workspace selector and quick
 * rows, the colour settings swatch, the colour picker's starting colour) (#4149).
 *
 * A chosen colour is returned as is. An unset one (null or the stored `#444444` sentinel, see
 * [isWorkspaceColorSet]) means "follow the theme", so it resolves to the primary of the scheme an
 * unset workspace gets in [colorMode] ([abColorScheme] with no seed): the built-in default theme in
 * NORMAL, ink in MONOCHROME and COLOR_EINK, the greyscaled stock primary in BW. That is the scheme the
 * reading toolbar derives from for such a workspace. It is computed here rather than read from
 * `MaterialTheme`, because a row or a detached settings editor can describe a workspace other than
 * the active one, whose own colour seeds the current theme.
 */
fun workspaceDisplayArgb(workspaceArgb: Int?, dark: Boolean, colorMode: DisplayColorMode): Int =
    if (isWorkspaceColorSet(workspaceArgb)) workspaceArgb!!
    else abColorScheme(null, dark, colorMode).primary.toArgb()

/**
 * The colour dot of a workspace row. The per-workspace colour is a decorative-but-meaningful
 * identifier, not a scheme colour, so it is not greyscaled by `AbTheme` automatically
 * (CategoryPalette idiom): ink in MONOCHROME and COLOR_EINK, grey in BW, otherwise
 * [workspaceDisplayArgb].
 */
@Composable
fun workspaceDotColor(workspaceArgb: Int?): Color {
    val mode = LocalDisplayColorMode.current
    return if (isPureMonochrome()) MaterialTheme.colorScheme.onSurface
    else Color(accentArgbFor(workspaceDisplayArgb(workspaceArgb, LocalIsDarkTheme.current, mode), mode))
}

/**
 * Whether confirming [pickedArgb] in the workspace colour picker keeps the workspace at its default.
 * True only when the workspace had no colour of its own ([storedArgb] unset) and the user confirmed
 * the unchanged starting colour [shownArgb] (the theme colour from [workspaceDisplayArgb]); the caller
 * then resets instead of storing that theme colour as a chosen one, which would stop the workspace
 * following the theme. Alpha is ignored, as the picker works on opaque colours.
 */
fun keepsDefaultWorkspaceColor(storedArgb: Int?, shownArgb: Int, pickedArgb: Int): Boolean =
    !isWorkspaceColorSet(storedArgb) && (pickedArgb or OPAQUE) == (shownArgb or OPAQUE)

private const val OPAQUE: Int = 0xFF shl 24
