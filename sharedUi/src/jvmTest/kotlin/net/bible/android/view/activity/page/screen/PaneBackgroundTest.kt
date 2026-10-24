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
package net.bible.android.view.activity.page.screen

import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PaneBackgroundTest {
    private val colors = TextDisplaySettings.default.colors!!.copy(dayBackground = 0x112233, nightBackground = 0x445566)

    @Test
    fun theDayAndNightBackgroundsFollowTheMode() {
        assertEquals(0x112233, paneBackgroundArgb(colors, nightMode = false, monochrome = false))
        assertEquals(0x445566, paneBackgroundArgb(colors, nightMode = true, monochrome = false))
    }

    @Test
    fun monochromeForcesBlackAtNightAndWhiteByDay() {
        assertEquals(TextDisplaySettings.white, paneBackgroundArgb(colors, nightMode = false, monochrome = true))
        assertEquals(TextDisplaySettings.black, paneBackgroundArgb(colors, nightMode = true, monochrome = true))
        assertEquals(TextDisplaySettings.black, paneBackgroundArgb(null, nightMode = true, monochrome = true))
    }

    @Test
    fun noColoursMeansNoBackground() {
        assertEquals(null, paneBackgroundArgb(null, nightMode = false, monochrome = false))
        assertEquals(null, paneBackgroundArgb(colors.copy(nightBackground = null), nightMode = true, monochrome = false))
    }

    @Test
    fun theDefaultsEqualAndroidsBibleBackgroundResources() {
        // R.color.bible_background_night = #000000, R.color.bible_background_day = #ffffff (opaque ARGB ints)
        assertEquals(-16777216, READING_BACKGROUND_NIGHT)
        assertEquals(-1, READING_BACKGROUND_DAY)
        assertEquals(0xFF000000.toInt(), READING_BACKGROUND_NIGHT)
        assertEquals(0xFFFFFFFF.toInt(), READING_BACKGROUND_DAY)
    }

    @Test
    fun readingBackgroundFallsBackToTheDefaultsWhenNoColourIsNamed() {
        assertEquals(READING_BACKGROUND_DAY, readingBackgroundArgb(null, nightMode = false, monochrome = false))
        assertEquals(READING_BACKGROUND_NIGHT, readingBackgroundArgb(null, nightMode = true, monochrome = false))
        assertEquals(READING_BACKGROUND_NIGHT, readingBackgroundArgb(colors.copy(nightBackground = null), nightMode = true, monochrome = false))
        assertEquals(READING_BACKGROUND_DAY, readingBackgroundArgb(colors.copy(dayBackground = null), nightMode = false, monochrome = false))
    }

    @Test
    fun readingBackgroundUsesTheWindowColoursAndMonochromeOverridesThem() {
        assertEquals(0x112233, readingBackgroundArgb(colors, nightMode = false, monochrome = false))
        assertEquals(0x445566, readingBackgroundArgb(colors, nightMode = true, monochrome = false))
        assertEquals(-1, readingBackgroundArgb(colors, nightMode = false, monochrome = true))
        assertEquals(-16777216, readingBackgroundArgb(colors, nightMode = true, monochrome = true))
        assertEquals(-16777216, readingBackgroundArgb(null, nightMode = true, monochrome = true))
        assertEquals(-1, readingBackgroundArgb(null, nightMode = false, monochrome = true))
    }
}
