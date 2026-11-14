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

    /** WCAG AA (4.5:1) for the text-bearing pairs: toolbar, buttons, body text, links/accents. */
    @Test
    fun keyPairsMeetWcagAa() {
        for (p in proposals) for (dark in listOf(false, true)) {
            val s = p.scheme(dark)!!
            val pairs = mapOf(
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                "onSurface/surface" to (s.onSurface to s.surface),
                "primary/surface" to (s.primary to s.surface),
            )
            for ((name, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                println("CONTRAST $p dark=$dark $name ${pair.first.hex()} on ${pair.second.hex()} = ${(ratio * 100).toInt() / 100f}")
                assertTrue(ratio >= 4.5f, "$p dark=$dark $name = $ratio")
            }
            assertNotEquals(abColorScheme(null, dark, DisplayColorMode.NORMAL).toString(), s.toString())
        }
    }

    private fun Color.hex() = "#" + (0xFFFFFF and toArgb()).toString(16).uppercase().padStart(6, '0')
}
