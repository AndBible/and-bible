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
package net.bible.sharedlogic.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupFixtureTest {
    @Test
    fun embedsTheWholeRawTextModuleByteForByte() {
        val root = java.io.File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        assertEquals(
            setOf("mods.d/K4RawText.conf", "modules/K4RawText/nt", "modules/K4RawText/nt.vss", "modules/K4RawText/ot", "modules/K4RawText/ot.vss"),
            StartupFixture.files.keys,
        )
        StartupFixture.files.forEach { (name, bytes) ->
            assertTrue(bytes.isNotEmpty(), "$name is empty")
            assertTrue(root.resolve(name).readBytes().contentEquals(bytes), "$name differs from the corpus file")
        }
        assertTrue(StartupFixture.files.getValue("mods.d/K4RawText.conf").decodeToString().contains("[K4RawText]"))
    }
}
