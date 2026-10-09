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
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.isWorkspaceColorSet
import net.bible.sharedcore.theme.toGrayscaleArgb

private fun grayed(c: Color, mode: DisplayColorMode): Color =
    if (mode == DisplayColorMode.NORMAL) c else Color(toGrayscaleArgb(c.toArgb()))

/**
 * Grayscale EVERY role of a Material3 [ColorScheme] (not just the 6 accent roles): error,
 * surface/background, and all `onXxx`/container roles included. This enforces AndBible's
 * monochrome doctrine (CLAUDE.md: in BW "virtually everything should be grayscale") — e.g.
 * `MaterialTheme.colorScheme.error` must not render red on an e-ink screen. In COLOR_EINK the
 * base scheme is likewise grayed here; the intentionally-colored bits are the `LocalAbColors`
 * accents, which stay colored via [net.bible.sharedcore.theme.accentArgbFor]. No-op in NORMAL
 * (each role maps to itself).
 */
private fun ColorScheme.grayscale(mode: DisplayColorMode): ColorScheme = copy(
    primary = grayed(primary, mode),
    onPrimary = grayed(onPrimary, mode),
    primaryContainer = grayed(primaryContainer, mode),
    onPrimaryContainer = grayed(onPrimaryContainer, mode),
    inversePrimary = grayed(inversePrimary, mode),
    secondary = grayed(secondary, mode),
    onSecondary = grayed(onSecondary, mode),
    secondaryContainer = grayed(secondaryContainer, mode),
    onSecondaryContainer = grayed(onSecondaryContainer, mode),
    tertiary = grayed(tertiary, mode),
    onTertiary = grayed(onTertiary, mode),
    tertiaryContainer = grayed(tertiaryContainer, mode),
    onTertiaryContainer = grayed(onTertiaryContainer, mode),
    background = grayed(background, mode),
    onBackground = grayed(onBackground, mode),
    surface = grayed(surface, mode),
    onSurface = grayed(onSurface, mode),
    surfaceVariant = grayed(surfaceVariant, mode),
    onSurfaceVariant = grayed(onSurfaceVariant, mode),
    surfaceTint = grayed(surfaceTint, mode),
    inverseSurface = grayed(inverseSurface, mode),
    inverseOnSurface = grayed(inverseOnSurface, mode),
    error = grayed(error, mode),
    onError = grayed(onError, mode),
    errorContainer = grayed(errorContainer, mode),
    onErrorContainer = grayed(onErrorContainer, mode),
    outline = grayed(outline, mode),
    outlineVariant = grayed(outlineVariant, mode),
    scrim = grayed(scrim, mode),
    surfaceBright = grayed(surfaceBright, mode),
    surfaceDim = grayed(surfaceDim, mode),
    surfaceContainer = grayed(surfaceContainer, mode),
    surfaceContainerHigh = grayed(surfaceContainerHigh, mode),
    surfaceContainerHighest = grayed(surfaceContainerHighest, mode),
    surfaceContainerLow = grayed(surfaceContainerLow, mode),
    surfaceContainerLowest = grayed(surfaceContainerLowest, mode),
)

/**
 * The single seed → [ColorScheme] derivation for the whole app (A/B batch 4b).
 *
 * Deliberately NOT `@Composable`: the BibleView `set_config` payload derives the Vue layer's
 * chrome colours from this same function, outside any composition. Two derivation paths would
 * eventually disagree; one cannot.
 *
 * The returned scheme is **final** — seeding *and* the display-mode greyscale pass both happen
 * here. They used to be two steps, with `AbTheme` applying `grayscale(colorMode)` on top; that
 * left the second consumer (the BibleView payload) free to forget it, which it did: COLOR_EINK
 * shipped seeded-but-coloured roles to the Vue chrome while Compose greyscaled them. Folding the
 * pass in makes that drift impossible by construction rather than by a second condition that both
 * call sites must remember to write.
 *
 * - **No seed** (null, or `DEFAULT_WORKSPACE_COLOR_ARGB` — see [isWorkspaceColorSet]) → today's
 *   stock Material 3 scheme, unchanged. This is an identity requirement, not an approximation:
 *   it is what keeps every existing golden and every colourless workspace pixel-identical. (In
 *   NORMAL the greyscale pass is a no-op, so the identity holds exactly.)
 * - **BW** → the seed is ignored. A hue carries no information on a black-and-white screen, and
 *   seeding would give each workspace *different greys*, i.e. different contrast for the same UI
 *   on exactly the devices where contrast matters most.
 * - **COLOR_EINK** → seeded, *then* greyscaled like every other role (spec §5), matching the line
 *   `accentArgbFor` already draws: the base scheme greys, the deliberate accents stay coloured.
 */
fun abColorScheme(seedArgb: Int?, dark: Boolean, colorMode: DisplayColorMode): ColorScheme {
    val stock = if (dark) darkColorScheme() else lightColorScheme()
    val seeded = if (!isWorkspaceColorSet(seedArgb) || colorMode.isGreyBase) {
        stock
    } else {
        // Named arguments only: the positional list carries six optional role overrides between
        // `isAmoled` and `style` (signature read out of the artifact with javap, Step 2). Unlike the
        // role overrides, `isAmoled` has NO default in the real signature (confirmed by the Kotlin
        // compiler's own overload-resolution error when it was omitted) — it must be passed explicitly.
        dynamicColorScheme(
            seedColor = Color(seedArgb!!),
            isDark = dark,
            isAmoled = false,
            style = PaletteStyle.TonalSpot,
        )
    }
    return if (colorMode == DisplayColorMode.NORMAL) seeded else seeded.grayscale(colorMode)
}
