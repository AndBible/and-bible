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


package net.bible.sharedui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #4064: the release notice card takes its colours from the app theme, not from a fixed andbible.org palette. */
class ReleaseNoticePaletteTest {
    private val light = lightColorScheme(
        primary = Color(0xFF1565C0), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36), surfaceContainerHigh = Color(0xFFECEEF4),
        surfaceContainerHighest = Color(0xFFE6E8EE), onSurface = Color(0xFF1A1C1E), onSurfaceVariant = Color(0xFF43474E),
        outlineVariant = Color(0xFFC3C7CF),
    )
    private val dark = darkColorScheme(
        primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258), primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF), surfaceContainerHigh = Color(0xFF282A2D),
        surfaceContainerHighest = Color(0xFF333538), onSurface = Color(0xFFE2E2E6), onSurfaceVariant = Color(0xFFC3C7CF),
        outlineVariant = Color(0xFF43474E),
    )

    @Test
    fun lightAndDarkFollowTheColorScheme() {
        for (scheme in listOf(light, dark)) {
            val p = releaseNoticePalette(scheme, DisplayColorMode.NORMAL)
            assertEquals(scheme.surfaceContainerHigh, p.panel)
            assertEquals(scheme.onSurface, p.ink)
            assertEquals(scheme.onSurfaceVariant, p.muted)
            assertEquals(scheme.outlineVariant, p.border)
            assertEquals(scheme.surfaceContainerHighest, p.tile)
            assertEquals(scheme.primary, p.accent)
            assertEquals(scheme.primary, p.accentInk)
            assertEquals(scheme.primaryContainer, p.highlight)
            assertEquals(scheme.onPrimaryContainer, p.highlightInk)
            assertEquals(scheme.primary, p.buttonBg)
            assertEquals(scheme.onPrimary, p.buttonInk)
        }
    }

    /** The glow is the theme primary at a low alpha, so it tints the panel without fighting the text. */
    @Test
    fun glowIsLowAlphaThemePrimary() {
        for (scheme in listOf(light, dark)) {
            val glow = releaseNoticePalette(scheme, DisplayColorMode.NORMAL).glow!!
            assertEquals(scheme.primary.copy(alpha = 1f), glow.copy(alpha = 1f))
            assertTrue(glow.alpha in 0.05f..0.25f, "alpha ${glow.alpha}")
        }
    }

    /** Monochrome and BW: flat surface, no glow, the ink colour for every accent. */
    @Test
    fun greyModesAreFlatWithoutGlow() {
        for (mode in listOf(DisplayColorMode.MONOCHROME, DisplayColorMode.BW)) {
            val p = releaseNoticePalette(light, mode)
            assertNull(p.glow, "$mode")
            assertEquals(light.surface, p.panel)
            assertEquals(light.onSurface, p.accent)
            assertEquals(light.onSurface, p.accentInk)
            assertEquals(light.surface, p.highlight)
            assertEquals(light.onSurface, p.highlightInk)
        }
    }

    /** Colour e-ink keeps the theme colours but draws no gradient: a soft glow only dithers on an e-ink panel. */
    @Test
    fun colorEinkFollowsThemeWithoutGlow() {
        val p = releaseNoticePalette(light, DisplayColorMode.COLOR_EINK)
        assertNull(p.glow)
        assertEquals(light.surfaceContainerHigh, p.panel)
        assertEquals(light.primary, p.accent)
    }
}
