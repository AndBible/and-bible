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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.reading.isWorkspaceColorSet

private fun Color.toCssHex(): String = "#%06X".format(0xFFFFFF and toArgb())

/**
 * The Vue/BibleView layer's share of the workspace theme (A/B batch 4b, §7): the handful of scheme
 * roles its generic chrome needs, as CSS hex strings.
 *
 * Derived from the SAME [abColorScheme] the Compose UI uses, so the two palettes cannot drift; the
 * Vue side does no colour maths at all. That includes the display-mode greyscale pass, which lives
 * *inside* [abColorScheme] rather than at its call sites — so `COLOR_EINK` reaches the Vue chrome
 * greyscaled, exactly as it reaches Compose, without this function testing a second condition.
 *
 * NORMAL always sends roles, including for no seed or the default workspace colour, to keep Vue
 * and Compose on the same palette. Returns the literal `"null"` in BW, or in COLOR_EINK without a
 * set workspace colour; Vue's `var(…, fallback)` declarations preserve those modes' appearance.
 */
fun themeColorsJson(
    seedArgb: Int?,
    dark: Boolean,
    colorMode: DisplayColorMode,
    style: ThemePaletteStyle = ThemePaletteStyle.DEFAULT,
): String {
    // BW ignores the seed, so Vue's own monochrome rules apply; COLOR_EINK ships only a set colour
    // (unchanged). NORMAL always ships: the default colour is a real seed now, which is what keeps
    // the Vue chrome and Compose on the same colours.
    if (colorMode == DisplayColorMode.BW) return "null"
    if (colorMode == DisplayColorMode.COLOR_EINK && !isWorkspaceColorSet(seedArgb)) return "null"
    val s = abColorScheme(seedArgb, dark, colorMode, style)
    return """{"primary":"${s.primary.toCssHex()}",""" +
        """"onPrimary":"${s.onPrimary.toCssHex()}",""" +
        """"primaryContainer":"${s.primaryContainer.toCssHex()}",""" +
        """"onPrimaryContainer":"${s.onPrimaryContainer.toCssHex()}",""" +
        """"secondaryContainer":"${s.secondaryContainer.toCssHex()}",""" +
        """"onSecondaryContainer":"${s.onSecondaryContainer.toCssHex()}"}"""
}
