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

package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A literal NUL byte in a source file makes `grep -r` treat the file as binary and skip it with no
 * output at all, so a reference sweep over the tree silently misses everything in it. Batch Z-late
 * phase 1 proves its deletions by reference sweep, so that blind spot has to be impossible rather
 * than merely fixed once.
 */
class SourceEncodingGuardTest {
    @Test fun noSourceFileContainsANulByte() {
        val root = File("src/main/java")
        // Anti-vacuity: if the walk sees no source at all, the assertion below proves nothing.
        assertTrue("src/main/java is not a directory — the scan would pass vacuously", root.isDirectory)
        val sources = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
        assertTrue("the walk found no Kotlin/Java sources", sources.isNotEmpty())

        val offenders = sources
            .filter { it.readBytes().contains(0.toByte()) }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            "these sources contain a literal NUL byte, which makes `grep -r` classify them as " +
                "binary and skip them silently — use a unicode escape in the string literal instead",
            emptyList<String>(),
            offenders,
        )
    }
}
