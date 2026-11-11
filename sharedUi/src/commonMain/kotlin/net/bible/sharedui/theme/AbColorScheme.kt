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
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.Variant
import com.materialkolor.toColorScheme
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

private fun seeded(seedArgb: Int, dark: Boolean, style: PaletteStyle): ColorScheme =
    // Named arguments only: the positional list carries six optional role overrides between
    // `isAmoled` and `style`. `isAmoled` has NO default in the real signature — pass it explicitly.
    dynamicColorScheme(seedColor = Color(seedArgb), isDark = dark, isAmoled = false, style = style)

/**
 * materialkolor TonalSpot with every palette's chroma capped at the seed's own HCT chroma.
 *
 * TonalSpot keeps only the seed's hue and forces fixed chromas (36/16/24/6/8). An achromatic seed
 * has an arbitrary hue (0 for black, ~209.5 for the greys), so plain TonalSpot turns black pink and
 * greys teal, and it over-saturates low-chroma colours (brown -> orange). Capping fixes both while
 * keeping TonalSpot's normalised tones; for a seed with chroma >= 36 it is identical to TonalSpot.
 * Mirrors materialkolor's `SchemeTonalSpot` constructor (2.1.1) apart from the caps.
 */
private fun greySafeTonalSpot(seedArgb: Int, dark: Boolean): ColorScheme {
    val seed = Hct.fromInt(seedArgb)
    fun palette(hue: Double, tonalSpotChroma: Double) =
        TonalPalette.fromHueAndChroma(hue, minOf(tonalSpotChroma, seed.chroma))
    return DynamicScheme(
        sourceColorHct = seed,
        variant = Variant.TONAL_SPOT,
        isDark = dark,
        contrastLevel = Contrast.Default.value,
        primaryPalette = palette(seed.hue, 36.0),
        secondaryPalette = palette(seed.hue, 16.0),
        tertiaryPalette = palette((seed.hue + 60.0) % 360.0, 24.0),
        neutralPalette = palette(seed.hue, 6.0),
        neutralVariantPalette = palette(seed.hue, 8.0),
    ).toColorScheme(isAmoled = false)
}

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
 * - **NORMAL** -> an unset workspace colour (null or the stored `#444444` sentinel; see
 *   [isWorkspaceColorSet]) uses Compose's stock Material 3 light/dark scheme.
 *   A chosen colour always uses [greySafeTonalSpot], keeping low-chroma seeds muted.
 *   Unseeded goldens and previews render the same stock baseline-violet theme as the app.
 * - **MONOCHROME** -> two-tone ink/paper scheme, with transparent scrim; seed ignored.
 * - **BW** -> the seed is ignored: the stock scheme is greyscaled so contrast is
 *   consistent across workspaces on black-and-white screens.
 * - **COLOR_EINK** -> a set colour (see [isWorkspaceColorSet]) seeds TonalSpot, then the scheme
 *   is greyscaled. An unset colour uses the stock scheme, greyscaled.
 *   This keeps equal contrast for every workspace colour on e-ink; deliberate accents stay coloured.
 */
fun abColorScheme(
    seedArgb: Int?,
    dark: Boolean,
    colorMode: DisplayColorMode,
): ColorScheme {
    val stock = if (dark) darkColorScheme() else lightColorScheme()
    return when (colorMode) {
        DisplayColorMode.NORMAL -> {
            if (!isWorkspaceColorSet(seedArgb)) stock
            else greySafeTonalSpot(seedArgb!!, dark)
        }
        DisplayColorMode.MONOCHROME -> monochromeScheme(dark)
        DisplayColorMode.BW -> stock.grayscale(colorMode)
        DisplayColorMode.COLOR_EINK ->
            (if (isWorkspaceColorSet(seedArgb)) seeded(seedArgb!!, dark, PaletteStyle.TonalSpot) else stock)
                .grayscale(colorMode)
    }
}

/** Every role is ink or paper; selection inverts and sheet/dialog edges replace the scrim. */
private fun monochromeScheme(dark: Boolean): ColorScheme {
    val ink = monoInk(dark)
    val paper = monoPaper(dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink, onPrimary = paper, primaryContainer = ink, onPrimaryContainer = paper,
        inversePrimary = paper,
        secondary = ink, onSecondary = paper, secondaryContainer = paper, onSecondaryContainer = ink,
        tertiary = ink, onTertiary = paper, tertiaryContainer = paper, onTertiaryContainer = ink,
        background = paper, onBackground = ink,
        surface = paper, onSurface = ink, surfaceVariant = paper, onSurfaceVariant = ink,
        surfaceTint = paper, inverseSurface = ink, inverseOnSurface = paper,
        error = ink, onError = paper, errorContainer = paper, onErrorContainer = ink,
        outline = ink, outlineVariant = ink, scrim = Color.Transparent,
        surfaceBright = paper, surfaceDim = paper,
        surfaceContainer = paper, surfaceContainerHigh = paper, surfaceContainerHighest = paper,
        surfaceContainerLow = paper, surfaceContainerLowest = paper,
    )
}
