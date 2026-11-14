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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DefaultThemeProposalTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun <T> withProposal(p: DefaultThemeProposal, block: () -> T): T {
        val saved = activeDefaultThemeProposal
        activeDefaultThemeProposal = p
        try { return block() } finally { activeDefaultThemeProposal = saved }
    }

    private val proposals = DefaultThemeProposal.entries - DefaultThemeProposal.CURRENT

    @Test
    fun defaultSwitchIsCurrentAndUnknownValuesFallBack() {
        assertEquals(DefaultThemeProposal.CURRENT, activeDefaultThemeProposal)
        assertEquals(DefaultThemeProposal.CURRENT, DefaultThemeProposal.parse(null))
        assertEquals(DefaultThemeProposal.CURRENT, DefaultThemeProposal.parse("purple"))
        assertEquals(DefaultThemeProposal.AMBER, DefaultThemeProposal.parse(" amber "))
    }

    @Test
    fun proposalReplacesOnlyTheUnsetNormalScheme() {
        for (p in proposals) for (dark in listOf(false, true)) withProposal(p) {
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                assertEquals(p.scheme(dark).toString(), abColorScheme(seed, dark, DisplayColorMode.NORMAL).toString())
            }
            // A user-chosen workspace colour still wins.
            val blue = 0xFF1565C0.toInt()
            assertEquals(greySafeTonalSpot(blue, dark).toString(), abColorScheme(blue, dark, DisplayColorMode.NORMAL).toString())
        }
    }

    @Test
    fun greyModesIgnoreTheProposal() {
        for (dark in listOf(false, true)) for (mode in DisplayColorMode.entries - DisplayColorMode.NORMAL) {
            val baseline = abColorScheme(null, dark, mode).toString()
            for (p in proposals) withProposal(p) {
                assertEquals(baseline, abColorScheme(null, dark, mode).toString(), "$p $mode dark=$dark")
            }
        }
    }

    /**
     * WCAG AA (4.5:1) for the text-bearing pairs: toolbar, buttons, body text, links/accents.
     * CURRENT (stock) is only printed, as the reference for the comparison sheet.
     */
    @Test
    fun keyPairsMeetWcagAa() {
        for (p in DefaultThemeProposal.entries) for (dark in listOf(false, true)) {
            val s = p.scheme(dark) ?: abColorScheme(null, dark, DisplayColorMode.NORMAL)
            val pairs = mapOf(
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                "onSurface/surface" to (s.onSurface to s.surface),
                "primary/surface" to (s.primary to s.surface),
                "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
                "onBackground/background" to (s.onBackground to s.background),
                "secondary/surface" to (s.secondary to s.surface),
            )
            println("SCHEME $p dark=$dark primary=${s.primary.hex()} onPrimary=${s.onPrimary.hex()} " +
                "primaryContainer=${s.primaryContainer.hex()} secondaryContainer=${s.secondaryContainer.hex()} " +
                "surface=${s.surface.hex()} onSurface=${s.onSurface.hex()} background=${s.background.hex()}")
            for ((name, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                println("CONTRAST $p dark=$dark $name ${pair.first.hex()} on ${pair.second.hex()} = ${(ratio * 100).toInt() / 100f}")
                if (p != DefaultThemeProposal.CURRENT) assertTrue(ratio >= 4.5f, "$p dark=$dark $name = $ratio")
            }
            if (p != DefaultThemeProposal.CURRENT) assertNotEquals(abColorScheme(null, dark, DisplayColorMode.NORMAL).toString(), s.toString())
        }
    }

    /**
     * Round 2 seeds land in the hue family they are named for (HCT hue): E is blue, not teal
     * (teal sits around 180-220) or violet (CURRENT, ~280+); F is a near-neutral slate; G is green.
     * Seeds with chroma below TonalSpot's 36 stay muted through [greySafeTonalSpot]'s cap.
     */
    @Test
    fun roundTwoSeedsAreInTheirHueFamilies() {
        fun hct(argb: Int) = com.materialkolor.hct.Hct.fromInt(argb)
        for ((name, seed) in listOf("SITE_ACCENT" to SITE_ACCENT, "BRONZE" to BRONZE_SEED, "SILVER_BLUE" to SILVER_BLUE_SEED,
            "SLATE" to SLATE_SEED, "SAGE" to SAGE_SEED)) {
            println("SEED $name hue=${hct(seed).hue.toInt()} chroma=${hct(seed).chroma.toInt()} tone=${hct(seed).tone.toInt()}")
        }
        assertTrue(hct(SILVER_BLUE_SEED).hue in 235.0..275.0, "silver blue hue ${hct(SILVER_BLUE_SEED).hue}")
        assertTrue(hct(SILVER_BLUE_SEED).chroma in 15.0..36.0, "silver blue chroma ${hct(SILVER_BLUE_SEED).chroma}")
        assertTrue(hct(SLATE_SEED).chroma < 10.0, "slate chroma ${hct(SLATE_SEED).chroma}")
        assertTrue(hct(SAGE_SEED).hue in 115.0..160.0, "sage hue ${hct(SAGE_SEED).hue}")
        assertTrue(hct(SAGE_SEED).chroma < 30.0, "sage chroma ${hct(SAGE_SEED).chroma}")
        // D2 must be a different hue from D, not just a darker tone of it (TonalSpot normalises tone away).
        assertTrue(hct(SITE_ACCENT).hue - hct(BRONZE_SEED).hue >= 15.0)
    }

    private fun Color.hex() = "#" + (0xFFFFFF and toArgb()).toString(16).uppercase().padStart(6, '0')
}
