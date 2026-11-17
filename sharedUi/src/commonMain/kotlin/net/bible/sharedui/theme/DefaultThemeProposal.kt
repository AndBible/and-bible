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
 * Built-in default themes, inspired by andbible.org (warm paper, ink, amber/gold).
 *
 * A proposal replaces ONLY the scheme used in [net.bible.service.common.DisplayColorMode.NORMAL]
 * when the workspace colour is not set (the user's Settings > Colors workspace colour always wins,
 * and BW / MONOCHROME / COLOR_EINK never see a proposal). The maintainer chose [INK] (proposal C,
 * round 2) as the shipped default, [DEFAULT]. The other proposals, and [VIOLET] (the stock
 * Material 3 baseline that was the default before), stay selectable through the dev switch
 * (`-PdefaultThemeProposal=...`, debug sysprop `debug.andbible.theme_proposal`) for comparison.
 *
 * Site tokens (website/assets/css/site.css): light ground #F7F1E3, surface #FFFAF0, surface-2
 * #F3EAD7, ink #2A2620, accent #D99A2B, accent-ink #9A5B10; dark ground #161616, surface #1C1C1B,
 * ink #EFE9DD, accent #FEBA2A. Logo (ic_logo.xml): #7B4639 bronze, #D97E35, #DF983B, #FEBA2A.
 */
enum class DefaultThemeProposal {
    /** The stock Material 3 light/dark scheme (baseline violet); the default before [INK]. */
    VIOLET,

    /** A: the site's amber/gold. Gold toolbar on warm paper; amber-ink buttons. */
    AMBER,

    /** B: the logo's bronze brown as a strong toolbar; warm paper surfaces. */
    BRONZE,

    /** C: the site's ink-on-paper look. Paper toolbar, ink buttons, amber selection accent. The default. */
    INK,

    /** D: the site accent #D99A2B through the same seeded TonalSpot path as a user workspace colour. */
    SEEDED,

    /**
     * C2 (round 2): C with cool neutral paper and a stronger, cooler ink; the amber accent is kept.
     * Changes exactly one thing against C, the warmth of the neutrals, so the poll can tell whether
     * C won for its ink-on-paper structure or for the warm paper tone.
     */
    INK_COOL,

    /** D2 (round 2): D's TonalSpot path with a bronze seed #B5652A (hue ~45 instead of ~70). */
    SEEDED_BRONZE,

    /** E (round 2): VIOLET's calm Material 3 feel with a light silvery blue (seed #6F87AE) instead of violet. */
    SILVER_BLUE,

    /** F (round 2): graphite with a slate hint (seed #5A5E65, chroma ~7): nearly neutral blue-grey, the quietest coloured option. */
    SLATE,

    /** G (round 2): muted sage green (seed #66845F): a calm natural hue outside the logo's brown/orange range. */
    SAGE;

    /** The proposal's scheme, or null for [VIOLET] (the caller then uses the stock scheme). */
    fun scheme(dark: Boolean): ColorScheme? = when (this) {
        VIOLET -> null
        AMBER -> if (dark) amberDark() else amberLight()
        BRONZE -> if (dark) bronzeDark() else bronzeLight()
        INK -> if (dark) inkDark() else inkLight()
        SEEDED -> greySafeTonalSpot(SITE_ACCENT, dark)
        INK_COOL -> if (dark) inkCoolDark() else inkCoolLight()
        SEEDED_BRONZE -> greySafeTonalSpot(BRONZE_SEED, dark)
        SILVER_BLUE -> greySafeTonalSpot(SILVER_BLUE_SEED, dark)
        SLATE -> greySafeTonalSpot(SLATE_SEED, dark)
        SAGE -> greySafeTonalSpot(SAGE_SEED, dark)
    }

    companion object {
        /** The shipped default theme: C, "Ink on paper". */
        val DEFAULT: DefaultThemeProposal = INK

        /**
         * Parses a build/debug switch value; blank or unknown values fall back to [DEFAULT].
         * `CURRENT`, the old name of [VIOLET], is still accepted so earlier capture scripts keep working.
         */
        fun parse(value: String?): DefaultThemeProposal {
            val v = value?.trim()
            if (v.equals("CURRENT", ignoreCase = true)) return VIOLET
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: DEFAULT
        }

        /**
         * The host's startup choice. [buildValue] is the build switch (`-PdefaultThemeProposal`, blank
         * when not given); [debugOverride] the debug-only system property, honoured only in a
         * debuggable build that is not a video-look capture build: a sysprop left on a reused
         * emulator must never leak a proposal into a video capture, while an explicit build switch
         * still applies.
         */
        fun resolve(buildValue: String?, debugOverride: String?, debuggable: Boolean, videoLook: Boolean): DefaultThemeProposal {
            val override = debugOverride?.takeIf { debuggable && !videoLook && it.isNotBlank() }
            return parse(override ?: buildValue)
        }
    }
}

/**
 * The proposal used for unset workspace colours: [DefaultThemeProposal.DEFAULT] unless the host
 * picks another at startup (Android: `BuildConfig.DEFAULT_THEME_PROPOSAL`, debug builds may
 * override it). iOS and desktop do not set it and so use the default.
 */
var activeDefaultThemeProposal: DefaultThemeProposal = DefaultThemeProposal.DEFAULT

internal const val SITE_ACCENT = 0xFFD99A2B.toInt()
private const val LOGO_BRONZE = 0xFF7B4639.toInt()
internal const val BRONZE_SEED = 0xFFB5652A.toInt()
internal const val SILVER_BLUE_SEED = 0xFF6F87AE.toInt()
internal const val SLATE_SEED = 0xFF5A5E65.toInt()
internal const val SAGE_SEED = 0xFF66845F.toInt()

private val Paper = Color(0xFFFFFAF0)
private val Ground = Color(0xFFF7F1E3)
private val Paper2 = Color(0xFFF3EAD7)
private val Border = Color(0xFFE3D8C0)
private val Ink = Color(0xFF2A2620)
private val Muted = Color(0xFF5D5546)
private val Faint = Color(0xFF8A806D)
private val AccentInk = Color(0xFF9A5B10)
private val Gold = Color(0xFFFEBA2A)
// INK's Honey accents soften selection fills and night accents without changing brand gold.
private val HoneyTint = Color(0xFFFFE0A3)
private val NightHoney = Color(0xFFE9BC6A)
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
    secondaryContainer = HoneyTint, onSecondaryContainer = Ink,
    tertiary = AccentInk, onTertiary = Color.White,
)

private fun inkDark() = greySafeTonalSpot(SITE_ACCENT, true).siteNight().copy(
    primary = NightInk, onPrimary = NightGround,
    primaryContainer = NightSurface3, onPrimaryContainer = NightInk,
    inversePrimary = Ink,
    secondary = NightHoney, onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3812), onSecondaryContainer = Color(0xFFFFDFA0),
    tertiary = NightHoney, onTertiary = Ink,
)

/** C2's cool neutral paper: the same structure as [sitePaper] with the yellow taken out. */
private fun ColorScheme.coolPaper(): ColorScheme = copy(
    background = CoolGround, onBackground = CoolInk,
    surface = CoolPaper, onSurface = CoolInk, surfaceVariant = CoolPaper2, onSurfaceVariant = CoolMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = CoolPaper, surfaceDim = Color(0xFFE0E1DE),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = CoolPaper,
    surfaceContainer = CoolGround, surfaceContainerHigh = CoolPaper2, surfaceContainerHighest = CoolBorder,
    outline = CoolFaint, outlineVariant = CoolBorder,
    inverseSurface = CoolInk, inverseOnSurface = CoolGround,
)

/** C2's cool night neutrals. */
private fun ColorScheme.coolNight(): ColorScheme = copy(
    background = CoolNightGround, onBackground = CoolNightInk,
    surface = CoolNightSurface, onSurface = CoolNightInk, surfaceVariant = CoolNightSurface3, onSurfaceVariant = CoolNightMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = CoolNightSurface3, surfaceDim = Color(0xFF101213),
    surfaceContainerLowest = Color(0xFF101213), surfaceContainerLow = CoolNightGround,
    surfaceContainer = CoolNightSurface, surfaceContainerHigh = CoolNightSurface2, surfaceContainerHighest = CoolNightSurface3,
    outline = CoolNightFaint, outlineVariant = Color(0xFF363A3F),
    inverseSurface = CoolNightInk, inverseOnSurface = CoolNightGround,
)

private val CoolGround = Color(0xFFF2F3F1)
private val CoolPaper = Color(0xFFFAFAF9)
private val CoolPaper2 = Color(0xFFE9EAE7)
private val CoolBorder = Color(0xFFD6D8D4)
private val CoolInk = Color(0xFF1C2024)
private val CoolMuted = Color(0xFF4C5258)
private val CoolFaint = Color(0xFF7A8087)
private val CoolNightGround = Color(0xFF131517)
private val CoolNightSurface = Color(0xFF191B1D)
private val CoolNightSurface2 = Color(0xFF212427)
private val CoolNightSurface3 = Color(0xFF2A2D31)
private val CoolNightInk = Color(0xFFEDEFF1)
private val CoolNightMuted = Color(0xFFA0A6AC)
private val CoolNightFaint = Color(0xFF777D84)

private fun inkCoolLight() = greySafeTonalSpot(SITE_ACCENT, false).coolPaper().copy(
    primary = CoolInk, onPrimary = CoolGround,
    primaryContainer = CoolPaper2, onPrimaryContainer = CoolInk,
    inversePrimary = Gold,
    secondary = AccentInk, onSecondary = Color.White,
    secondaryContainer = Gold, onSecondaryContainer = CoolInk,
    tertiary = AccentInk, onTertiary = Color.White,
)

private fun inkCoolDark() = greySafeTonalSpot(SITE_ACCENT, true).coolNight().copy(
    primary = CoolNightInk, onPrimary = CoolNightGround,
    primaryContainer = CoolNightSurface3, onPrimaryContainer = CoolNightInk,
    inversePrimary = CoolInk,
    secondary = Gold, onSecondary = CoolInk,
    secondaryContainer = Color(0xFF4A3812), onSecondaryContainer = Color(0xFFFFDFA0),
    tertiary = Gold, onTertiary = CoolInk,
)
