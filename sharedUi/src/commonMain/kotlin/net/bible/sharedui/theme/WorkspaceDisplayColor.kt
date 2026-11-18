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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.isWorkspaceColorSet
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.toGrayscaleArgb

/** Test tag of the colour dot in workspace selector and quick-switch rows. */
const val WORKSPACE_COLOR_DOT_TAG = "workspaceColorDot"

/** Stroke width of the dashed ring that marks a default ("follows the theme") workspace colour. */
val WORKSPACE_DEFAULT_RING_WIDTH: Dp = 1.5.dp

private val RING_DASH: Dp = 3.dp
private val RING_GAP: Dp = 2.dp

/**
 * The fill that stands for a workspace's colour wherever it is shown (workspace selector and quick
 * rows, the colour settings swatch, the colour picker's starting colour) (#4149).
 *
 * A chosen colour is returned as is. An unset one (null or the stored `#444444` sentinel, see
 * [isWorkspaceColorSet]) means "follow the theme", so it resolves to the reading toolbar colour of a
 * workspace without its own colour: `primaryContainer` of the scheme an unset workspace gets in
 * [colorMode] ([abColorScheme] with no seed; Ink on paper: #F3EAD7 light, #2C2A26 dark; grey in BW).
 * In MONOCHROME and COLOR_EINK, where that role is ink, it is paper instead, so the ink
 * [workspaceDefaultRingArgb] stays visible. The unset look is always drawn with that dashed ring
 * ([WorkspaceColorCircle]).
 *
 * It is computed here rather than read from `MaterialTheme`, because a row or a detached settings
 * editor can describe a workspace other than the active one, whose own colour seeds the current theme.
 *
 * Known limit: the opt-in "Use system colors" (Material You) scheme is not followed here, as no
 * composition local carries that flag; with it on, an unset workspace shows the built-in default.
 */
fun workspaceDisplayArgb(workspaceArgb: Int?, dark: Boolean, colorMode: DisplayColorMode): Int {
    if (isWorkspaceColorSet(workspaceArgb)) return workspaceArgb!!
    val scheme = abColorScheme(null, dark, colorMode)
    return (if (colorMode.isInkBase) scheme.surface else scheme.primaryContainer).toArgb()
}

/** Colour of the dashed ring around a default workspace colour: the unset scheme's `outline`
 *  (Ink on paper's Faint #8A806D light, #7E786D dark; grey in BW; ink in MONOCHROME/COLOR_EINK). */
fun workspaceDefaultRingArgb(dark: Boolean, colorMode: DisplayColorMode): Int =
    abColorScheme(null, dark, colorMode).outline.toArgb()

/** Where a [WorkspaceColorCircle] is shown; decides how a CHOSEN colour is drawn. */
enum class WorkspaceColorCircleStyle {
    /** Selector and quick-switch rows: an identifier dot, ink in the ink modes, grey in BW. */
    DOT,

    /** Colour settings swatch: the colour itself with a solid outline; grey in MONOCHROME and BW,
     *  COLOR_EINK keeps the colour (its screen shows colour). */
    SWATCH,
}

/**
 * A workspace's colour circle. A chosen colour is a solid fill ([style] decides the per-mode
 * degrading). A default colour is [workspaceDisplayArgb]'s fill with a dashed
 * [workspaceDefaultRingArgb] ring, marking "default / follows the theme" in every display mode.
 */
@Composable
fun WorkspaceColorCircle(
    workspaceArgb: Int?,
    size: Dp,
    style: WorkspaceColorCircleStyle,
    modifier: Modifier = Modifier,
) {
    val mode = LocalDisplayColorMode.current
    val dark = LocalIsDarkTheme.current
    if (!isWorkspaceColorSet(workspaceArgb)) {
        val fill = Color(workspaceDisplayArgb(workspaceArgb, dark, mode))
        val ring = Color(workspaceDefaultRingArgb(dark, mode))
        Box(
            modifier.size(size).drawBehind {
                val stroke = WORKSPACE_DEFAULT_RING_WIDTH.toPx()
                drawCircle(fill)
                drawCircle(
                    ring,
                    radius = this.size.minDimension / 2 - stroke / 2,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(RING_DASH.toPx(), RING_GAP.toPx())),
                    ),
                )
            },
        )
        return
    }
    val argb = workspaceArgb!!
    when (style) {
        WorkspaceColorCircleStyle.DOT -> {
            val fill = if (mode.isInkBase) MaterialTheme.colorScheme.onSurface else Color(accentArgbFor(argb, mode))
            Box(modifier.size(size).drawBehind { drawCircle(fill) })
        }
        WorkspaceColorCircleStyle.SWATCH -> {
            val grey = mode == DisplayColorMode.MONOCHROME || mode == DisplayColorMode.BW
            val fill = Color(if (grey) toGrayscaleArgb(argb) else argb)
            val border = MaterialTheme.colorScheme.onSurfaceVariant
            Box(modifier.size(size).drawBehind { drawCircle(fill) }.border(1.dp, border, CircleShape))
        }
    }
}

/**
 * The workspace colour picker's starting colour: [workspaceDisplayArgb] in NORMAL mode, whatever
 * the current display mode. A stored colour only tints the theme in NORMAL, and the greyscale modes
 * would start an unset workspace on paper (white or black), which is also a preset: a deliberate
 * pick of it would then be read as "kept the default" by [keepsDefaultWorkspaceColor].
 */
fun workspacePickerStartArgb(workspaceArgb: Int?, dark: Boolean): Int =
    workspaceDisplayArgb(workspaceArgb, dark, DisplayColorMode.NORMAL)

/**
 * Whether confirming [pickedArgb] in the workspace colour picker keeps the workspace at its default.
 * True only when the workspace had no colour of its own ([storedArgb] unset) and the user confirmed
 * the unchanged starting colour [shownArgb] (from [workspacePickerStartArgb]); the caller then resets
 * instead of storing that theme colour as a chosen one, which would stop the workspace following the
 * theme. Alpha is ignored, as the picker works on opaque colours.
 */
fun keepsDefaultWorkspaceColor(storedArgb: Int?, shownArgb: Int, pickedArgb: Int): Boolean =
    !isWorkspaceColorSet(storedArgb) && (pickedArgb or OPAQUE) == (shownArgb or OPAQUE)

private const val OPAQUE: Int = 0xFF shl 24
