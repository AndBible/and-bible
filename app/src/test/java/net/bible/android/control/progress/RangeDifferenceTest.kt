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

package net.bible.android.control.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeDifferenceTest {

    @Test
    fun `remove middle of a target splits into two`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 13,
            removeEnd = 16,
        )
        assertEquals(listOf(10 to 12, 17 to 20), result.remaining)
        assertEquals((13..16).toList(), result.removed)
    }

    @Test
    fun `remove entire target leaves nothing`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 10,
            removeEnd = 20,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals((10..20).toList(), result.removed)
    }

    @Test
    fun `remove larger range than target removes entire target`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 5,
            removeEnd = 25,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals((10..20).toList(), result.removed)
    }

    @Test
    fun `remove left portion leaves right remainder`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 10,
            removeEnd = 15,
        )
        assertEquals(listOf(16 to 20), result.remaining)
        assertEquals((10..15).toList(), result.removed)
    }

    @Test
    fun `remove right portion leaves left remainder`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 15,
            removeEnd = 25,
        )
        assertEquals(listOf(10 to 14), result.remaining)
        assertEquals((15..20).toList(), result.removed)
    }

    @Test
    fun `no overlap returns empty removed`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 25,
            removeEnd = 30,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals(emptyList<Int>(), result.removed)
    }

    @Test
    fun `empty targets list`() {
        val result = computeRangeDifference(
            targets = emptyList(),
            removeStart = 10,
            removeEnd = 20,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals(emptyList<Int>(), result.removed)
    }

    @Test
    fun `multiple overlapping targets`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 15, 18 to 25),
            removeStart = 13,
            removeEnd = 20,
        )
        assertEquals(listOf(10 to 12, 21 to 25), result.remaining)
        assertEquals((13..15).toList() + (18..20).toList(), result.removed)
    }

    @Test
    fun `remove single ordinal from middle`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 20),
            removeStart = 15,
            removeEnd = 15,
        )
        assertEquals(listOf(10 to 14, 16 to 20), result.remaining)
        assertEquals(listOf(15), result.removed)
    }

    @Test
    fun `single ordinal target fully removed`() {
        val result = computeRangeDifference(
            targets = listOf(15 to 15),
            removeStart = 15,
            removeEnd = 15,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals(listOf(15), result.removed)
    }

    @Test
    fun `adjacent but non-overlapping target is not affected`() {
        val result = computeRangeDifference(
            targets = listOf(10 to 14, 16 to 20),
            removeStart = 15,
            removeEnd = 15,
        )
        assertEquals(emptyList<Pair<Int, Int>>(), result.remaining)
        assertEquals(emptyList<Int>(), result.removed)
    }

    @Test
    fun `multiple targets some overlapping some not`() {
        val result = computeRangeDifference(
            targets = listOf(5 to 8, 10 to 20, 25 to 30),
            removeStart = 12,
            removeEnd = 27,
        )
        assertEquals(listOf(10 to 11, 28 to 30), result.remaining)
        assertEquals((12..20).toList() + (25..27).toList(), result.removed)
    }
}
