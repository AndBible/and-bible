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

package net.bible.android.view.activity.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressActivityScaleTest {

    @Test
    fun `book percent scale max stays at 100 percent when nothing exceeds it`() {
        assertEquals(1.0f, ReadingProgressActivity.resolveBookPercentScaleMax(null), 0.001f)
        assertEquals(1.0f, ReadingProgressActivity.resolveBookPercentScaleMax(0.95f), 0.001f)
        assertEquals(1.0f, ReadingProgressActivity.resolveBookPercentScaleMax(1.0f), 0.001f)
    }

    @Test
    fun `book percent scale max expands to next 25 percent boundary above 100`() {
        assertEquals(1.25f, ReadingProgressActivity.resolveBookPercentScaleMax(1.01f), 0.001f)
        assertEquals(1.25f, ReadingProgressActivity.resolveBookPercentScaleMax(1.24f), 0.001f)
        assertEquals(2.75f, ReadingProgressActivity.resolveBookPercentScaleMax(2.61f), 0.001f)
    }

    @Test
    fun `book percent scale steps use 25 percent increments up to max`() {
        assertEquals(listOf(25, 50, 75, 100), ReadingProgressActivity.buildBookPercentScaleSteps(1.0f))
        assertEquals(listOf(25, 50, 75, 100, 125), ReadingProgressActivity.buildBookPercentScaleSteps(1.25f))
        assertEquals(listOf(25, 50, 75, 100, 125, 150, 175), ReadingProgressActivity.buildBookPercentScaleSteps(1.75f))
    }
}


