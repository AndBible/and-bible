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
import androidx.compose.ui.graphics.Color

/**
 * Candidate built-in default themes, inspired by andbible.org (warm paper, ink, amber/gold).
 *
 * A proposal replaces ONLY the scheme used in [net.bible.service.common.DisplayColorMode.NORMAL]
 * when the workspace colour is not set (the user's Settings > Colors workspace colour always wins,
 * and BW / MONOCHROME / COLOR_EINK never see a proposal). [CURRENT] is today's stock Material 3
 * baseline and stays the default until the maintainer picks one.
 *
 * Site tokens (website/assets/css/site.css): light ground #F7F1E3, surface #FFFAF0, surface-2
 * #F3EAD7, ink #2A2620, accent #D99A2B, accent-ink #9A5B10; dark ground #161616, surface #1C1C1B,
 * ink #EFE9DD, accent #FEBA2A. Logo (ic_logo.xml): #7B4639 bronze, #D97E35, #DF983B, #FEBA2A.
 */
enum class DefaultThemeProposal {
    /** Today's stock Material 3 light/dark scheme (baseline violet). */
    CURRENT,

    /** A: the site's amber/gold. Gold toolbar on warm paper; amber-ink buttons. */
    AMBER,

    /** B: the logo's bronze brown as a strong toolbar; warm paper surfaces. */
    BRONZE,

    /** C: the site's ink-on-paper look. Paper toolbar, ink buttons, amber selection accent. */
    INK,

    /** D: the site accent #D99A2B through the same seeded TonalSpot path as a user workspace colour. */
    SEEDED;

    /** The proposal's scheme, or null for [CURRENT] (the caller then uses the stock scheme). */
    fun scheme(dark: Boolean): ColorScheme? = when (this) {
        CURRENT -> null
        AMBER -> if (dark) amberDark() else amberLight()
        BRONZE -> if (dark) bronzeDark() else bronzeLight()
        INK -> if (dark) inkDark() else inkLight()
        SEEDED -> greySafeTonalSpot(SITE_ACCENT, dark)
    }

    companion object {
        /** Parses a build/debug switch value; anything unknown falls back to [CURRENT]. */
        fun parse(value: String?): DefaultThemeProposal =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CURRENT
    }
}

/**
 * The proposal used for unset workspace colours. Set once by the host at startup (Android: from
 * `BuildConfig.DEFAULT_THEME_PROPOSAL`, debug builds may override it); defaults to current behaviour.
 */
var activeDefaultThemeProposal: DefaultThemeProposal = DefaultThemeProposal.CURRENT

private const val SITE_ACCENT = 0xFFD99A2B.toInt()
private const val LOGO_BRONZE = 0xFF7B4639.toInt()

private val Paper = Color(0xFFFFFAF0)
private val Ground = Color(0xFFF7F1E3)
private val Paper2 = Color(0xFFF3EAD7)
private val Border = Color(0xFFE3D8C0)
private val Ink = Color(0xFF2A2620)
private val Muted = Color(0xFF5D5546)
private val Faint = Color(0xFF8A806D)
private val AccentInk = Color(0xFF9A5B10)
private val Gold = Color(0xFFFEBA2A)
private val NightGround = Color(0xFF161616)
private val NightSurface = Color(0xFF1C1C1B)
private val NightSurface2 = Color(0xFF242320)
private val NightSurface3 = Color(0xFF2C2A26)
private val NightInk = Color(0xFFEFE9DD)
private val NightMuted = Color(0xFFA59F93)
private val NightFaint = Color(0xFF7E786D)

/** Warm paper neutrals shared by every light proposal (site ground/surface tokens). */
private fun ColorScheme.sitePaper(): ColorScheme = copy(
    background = Ground, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = Paper2, onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = Paper, surfaceDim = Color(0xFFE9E0CC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Paper,
    surfaceContainer = Ground, surfaceContainerHigh = Paper2, surfaceContainerHighest = Border,
    outline = Faint, outlineVariant = Border,
    inverseSurface = Ink, inverseOnSurface = Ground,
)

/** The site's night neutrals shared by every dark proposal. */
private fun ColorScheme.siteNight(): ColorScheme = copy(
    background = NightGround, onBackground = NightInk,
    surface = NightSurface, onSurface = NightInk, surfaceVariant = NightSurface3, onSurfaceVariant = NightMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = NightSurface3, surfaceDim = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF121212), surfaceContainerLow = NightGround,
    surfaceContainer = NightSurface, surfaceContainerHigh = NightSurface2, surfaceContainerHighest = NightSurface3,
    outline = NightFaint, outlineVariant = Color(0xFF3A372F),
    inverseSurface = NightInk, inverseOnSurface = NightGround,
)

private fun amberLight() = greySafeTonalSpot(SITE_ACCENT, false).sitePaper().copy(
    primary = AccentInk, onPrimary = Color.White,
    primaryContainer = Gold, onPrimaryContainer = Ink,
    inversePrimary = Gold,
    secondaryContainer = Color(0xFFFFE3A8), onSecondaryContainer = Ink,
)

private fun amberDark() = greySafeTonalSpot(SITE_ACCENT, true).siteNight().copy(
    primary = Gold, onPrimary = Ink,
    primaryContainer = Color(0xFF4A3812), onPrimaryContainer = Color(0xFFFFDFA0),
    inversePrimary = AccentInk,
    secondaryContainer = Color(0xFF3D3320), onSecondaryContainer = Color(0xFFFFDFA0),
)

private fun bronzeLight() = greySafeTonalSpot(LOGO_BRONZE, false).sitePaper().copy(
    primary = Color(0xFF7B4639), onPrimary = Color.White,
    primaryContainer = Color(0xFF7B4639), onPrimaryContainer = Color(0xFFFFF4E8),
    inversePrimary = Color(0xFFF2B8A5),
    secondaryContainer = Color(0xFFF6DDD2), onSecondaryContainer = Color(0xFF3A1F18),
)

private fun bronzeDark() = greySafeTonalSpot(LOGO_BRONZE, true).siteNight().copy(
    primary = Color(0xFFF2B8A5), onPrimary = Color(0xFF3A1F18),
    primaryContainer = Color(0xFF4E2D25), onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFF7B4639),
    secondaryContainer = Color(0xFF443028), onSecondaryContainer = Color(0xFFFFDBCF),
)

private fun inkLight() = greySafeTonalSpot(SITE_ACCENT, false).sitePaper().copy(
    primary = Ink, onPrimary = Ground,
    primaryContainer = Paper2, onPrimaryContainer = Ink,
    inversePrimary = Gold,
    secondary = AccentInk, onSecondary = Color.White,
    secondaryContainer = Gold, onSecondaryContainer = Ink,
    tertiary = AccentInk, onTertiary = Color.White,
)

private fun inkDark() = greySafeTonalSpot(SITE_ACCENT, true).siteNight().copy(
    primary = NightInk, onPrimary = NightGround,
    primaryContainer = NightSurface3, onPrimaryContainer = NightInk,
    inversePrimary = Ink,
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3812), onSecondaryContainer = Color(0xFFFFDFA0),
    tertiary = Gold, onTertiary = Ink,
)
