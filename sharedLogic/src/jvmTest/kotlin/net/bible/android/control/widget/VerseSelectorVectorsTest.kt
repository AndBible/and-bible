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
package net.bible.android.control.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [VerseSelector.pick] to a committed vector file that the Swift port in the iOS widget extension is tested against
 * (`iosApp/iosAppUnitTests/VerseTimelineTests.swift`, `swiftPickMatchesKotlinVectors`). Both sides must produce the
 * same index, or an iOS widget would show another verse than the app's logic picks. If the algorithm changes on
 * purpose, delete the file, run this test once (it writes the file and fails) and port the change to Swift.
 */
class VerseSelectorVectorsTest {
    private val file = File("../iosApp/test-fixtures/verse-selector-vectors.json")

    private fun vectors(): String {
        val seeds = listOf(0L, 1L, -1L, 123456789012345L, Long.MIN_VALUE, Long.MAX_VALUE)
        val periods = listOf(0L, 1L, -5L, 20443L, 20443L * 8 + 7)
        val shuffles = listOf(0, 1, 7)
        val sizes = listOf(1, 2, 30, 6600)
        val rows = buildList {
            for (size in sizes) for (seed in seeds) for (period in periods) for (shuffle in shuffles) {
                val index = VerseSelector.pick(size, seed, period, shuffle)
                add("""  {"poolSize": $size, "seed": "$seed", "period": $period, "shuffle": $shuffle, "index": $index}""")
            }
        }
        return rows.joinToString(",\n", "[\n", "\n]\n")
    }

    @Test fun committedVectorsMatchTheKotlinImplementation() {
        val expected = vectors()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(expected)
            throw AssertionError("Wrote ${file.absolutePath}; commit it and rerun")
        }
        assertEquals(expected, file.readText())
        assertTrue(expected.count { it == '\n' } > 300)
    }
}
