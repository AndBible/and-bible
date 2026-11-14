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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where the opt-in "Use system colors (Material You)" scheme ranks inside [abColorScheme]. */
class SystemColorsTest {
    private val fakeLight = lightColorScheme(primary = Color(0xFF00AA11), surface = Color(0xFFEEFFEE))
    private val fakeDark = darkColorScheme(primary = Color(0xFF88FF99), surface = Color(0xFF001100))
    private val requested = mutableListOf<Boolean>()

    private fun <T> withProvider(provider: ((Boolean) -> ColorScheme)?, proposal: DefaultThemeProposal = DefaultThemeProposal.DEFAULT, block: () -> T): T {
        val savedProvider = systemColorSchemeProvider
        val savedProposal = activeDefaultThemeProposal
        systemColorSchemeProvider = provider
        activeDefaultThemeProposal = proposal
        try { return block() } finally {
            systemColorSchemeProvider = savedProvider
            activeDefaultThemeProposal = savedProposal
        }
    }

    private val fake: (Boolean) -> ColorScheme = { dark -> requested += dark; if (dark) fakeDark else fakeLight }

    @Test
    fun systemSchemeReplacesTheUnsetNormalSchemeWhenOptedIn() = withProvider(fake, DefaultThemeProposal.INK) {
        for (dark in listOf(false, true)) for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
            val expected = if (dark) fakeDark else fakeLight
            assertEquals(expected.toString(), abColorScheme(seed, dark, DisplayColorMode.NORMAL, useSystemColors = true).toString())
        }
        assertEquals(listOf(false, false, true, true), requested)
    }

    @Test
    fun switchOffKeepsProposalOrStockAndNeverAsksTheProvider() = withProvider(fake, DefaultThemeProposal.SAGE) {
        for (dark in listOf(false, true)) {
            assertEquals(DefaultThemeProposal.SAGE.scheme(dark).toString(), abColorScheme(null, dark, DisplayColorMode.NORMAL).toString())
        }
        assertTrue(requested.isEmpty())
    }

    @Test
    fun workspaceColourStillWins() = withProvider(fake) {
        val blue = 0xFF1565C0.toInt()
        for (dark in listOf(false, true)) {
            assertEquals(greySafeTonalSpot(blue, dark).toString(), abColorScheme(blue, dark, DisplayColorMode.NORMAL, useSystemColors = true).toString())
        }
        assertTrue(requested.isEmpty())
    }

    @Test
    fun einkAndMonoModesIgnoreTheSystemScheme() {
        for (dark in listOf(false, true)) for (mode in DisplayColorMode.entries - DisplayColorMode.NORMAL) {
            val baseline = withProvider(null) { abColorScheme(null, dark, mode).toString() }
            withProvider(fake) {
                assertEquals(baseline, abColorScheme(null, dark, mode, useSystemColors = true).toString(), "$mode dark=$dark")
            }
        }
        assertTrue(requested.isEmpty())
    }

    /** iOS, desktop and Android < 12 install no provider: the switch is then a no-op. */
    @Test
    fun withoutAPlatformProviderTheSwitchIsANoOp() = withProvider(null) {
        for (dark in listOf(false, true)) {
            assertEquals(abColorScheme(null, dark, DisplayColorMode.NORMAL).toString(), abColorScheme(null, dark, DisplayColorMode.NORMAL, useSystemColors = true).toString())
        }
    }

    /** The BibleView chrome follows the same scheme, so Vue and Compose cannot disagree. */
    @Test
    fun bibleViewPayloadFollowsTheSystemScheme() = withProvider(fake) {
        val json = themeColorsJson(null, false, DisplayColorMode.NORMAL, useSystemColors = true)
        assertTrue(json.contains("\"primary\":\"#00AA11\""), json)
        assertEquals("null", themeColorsJson(null, false, DisplayColorMode.MONOCHROME, useSystemColors = true))
    }
}
