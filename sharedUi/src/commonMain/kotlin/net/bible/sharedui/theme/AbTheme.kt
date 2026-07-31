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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.toGrayscaleArgb

private fun grayed(c: Color, mode: DisplayColorMode): Color =
    if (mode == DisplayColorMode.NORMAL) c else Color(toGrayscaleArgb(c.toArgb()))

/**
 * Grayscale EVERY role of a Material3 [ColorScheme] (not just the 6 accent roles): error,
 * surface/background, and all `onXxx`/container roles included. This enforces AndBible's
 * monochrome doctrine (CLAUDE.md: in BW "virtually everything should be grayscale") — e.g.
 * `MaterialTheme.colorScheme.error` must not render red on an e-ink screen. In COLOR_EINK the
 * base scheme is likewise grayed here; the intentionally-colored bits are the [LocalAbColors]
 * accents, which stay colored via [accentArgbFor]. No-op in NORMAL (each role maps to itself).
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

// Base accents (colored). Adjust hues later; e-ink polish is not chased in Phase 0.
private val BookmarkBase = Color(0xFFFFC107)
private val ActiveWindowBase = Color(0xFF2196F3)
private val HelperLineBase = Color(0xFF4CAF50)

/** The active [DisplayColorMode], exposed so content (e.g. the passage-grid category palette) can
 *  grayscale its own non-scheme colors in BW/COLOR_EINK. Defaults to NORMAL outside an [AbTheme]. */
val LocalDisplayColorMode = staticCompositionLocalOf { DisplayColorMode.NORMAL }

/** Whether [AbTheme] resolved to the dark scheme. Exposed so content can branch on night mode
 *  without re-deriving it from a colour. Defaults to `false` outside an [AbTheme]. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * AndBible's Material3 theme for the Compose UI path.
 *
 * @param darkTheme whether to use the dark color scheme. **Hosts MUST supply this from
 *   [net.bible.service.device.ScreenSettings.nightMode]** (the resolved app night boolean —
 *   honours AndBible's auto/sunset/manual/system night setting, the same source the classic
 *   screens read). The [isSystemInDarkTheme] default is only a fallback for `@Preview`s and
 *   tests that have no `ScreenSettings`; a real host that lets it default would wrongly follow
 *   the OS theme instead of AndBible's own night setting. This is the contract every Compose host
 *   follows — thread it exactly like [colorMode]/[disableAnimations] come from `CommonUtils`.
 * @param colorMode NORMAL keeps full color; BW/COLOR_EINK grayscale the whole base scheme
 *   (accents in [LocalAbColors] stay colored in COLOR_EINK via [accentArgbFor]).
 * @param disableAnimations exposed to content via [LocalDisableAnimations].
 */
@Composable
fun AbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorMode: DisplayColorMode = DisplayColorMode.NORMAL,
    disableAnimations: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    // In BW/COLOR_EINK the entire base scheme is grayscale; accents follow accentArgbFor (colored in COLOR_EINK).
    val scheme = if (colorMode == DisplayColorMode.NORMAL) baseScheme else baseScheme.grayscale(colorMode)
    val accents = AbColors(
        bookmark = Color(accentArgbFor(BookmarkBase.toArgb(), colorMode)),
        activeWindow = Color(accentArgbFor(ActiveWindowBase.toArgb(), colorMode)),
        helperLine = Color(accentArgbFor(HelperLineBase.toArgb(), colorMode)),
    )
    CompositionLocalProvider(
        LocalAbColors provides accents,
        LocalDisableAnimations provides disableAnimations,
        LocalDisplayColorMode provides colorMode,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
