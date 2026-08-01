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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.isWorkspaceColorSet

/**
 * The single seed → [ColorScheme] derivation for the whole app (A/B batch 4b).
 *
 * Deliberately NOT `@Composable`: the BibleView `set_config` payload derives the Vue layer's
 * chrome colours from this same function, outside any composition. Two derivation paths would
 * eventually disagree; one cannot.
 *
 * - **No seed** (null, or `DEFAULT_WORKSPACE_COLOR_ARGB` — see [isWorkspaceColorSet]) → today's
 *   stock Material 3 scheme, unchanged. This is an identity requirement, not an approximation:
 *   it is what keeps every existing golden and every colourless workspace pixel-identical.
 * - **BW** → the seed is ignored. A hue carries no information on a black-and-white screen, and
 *   seeding would give each workspace *different greys*, i.e. different contrast for the same UI
 *   on exactly the devices where contrast matters most. `COLOR_EINK` does seed, matching the line
 *   `accentArgbFor` already draws (grey in BW, coloured in COLOR_EINK).
 *
 * `AbTheme` applies its existing `grayscale(colorMode)` pass AFTER this, so nothing here needs to
 * know about greyscaling beyond the BW opt-out.
 */
fun abColorScheme(seedArgb: Int?, dark: Boolean, colorMode: DisplayColorMode): ColorScheme {
    val stock = if (dark) darkColorScheme() else lightColorScheme()
    if (!isWorkspaceColorSet(seedArgb) || colorMode == DisplayColorMode.BW) return stock
    // Named arguments only: the positional list carries six optional role overrides between
    // `isAmoled` and `style` (signature read out of the artifact with javap, Step 2). Unlike the
    // role overrides, `isAmoled` has NO default in the real signature (confirmed by the Kotlin
    // compiler's own overload-resolution error when it was omitted) — it must be passed explicitly.
    return dynamicColorScheme(
        seedColor = Color(seedArgb!!),
        isDark = dark,
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
    )
}
