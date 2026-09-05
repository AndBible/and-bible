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
 * The terminal guard of Batch Z-late phase 1 (spec 10.1, 10.6): `use_compose_ui` and
 * `useComposeFor` appear NOWHERE in production sources or resources, in any of the three modules.
 *
 * Replaces `RoutingSeamGuardTest`, which enumerated the legitimate readers and asserted each still
 * read the flag. That shape could only be emptied, and an emptied allowlist passes vacuously
 * (spec 10.6, D7). This one asserts an absence, so it cannot.
 *
 * It matches the flag's NAME, not the read (`getBoolean("use_compose_ui"`) the old guard hunted.
 * That is the stronger form now and it costs something: a KDoc sentence describing a flag that no
 * longer exists fails here too, deliberately — stale prose about a dead branch is how the next
 * reader is misled about which paths are live. The epilogue rewrote ~25 such comments to land this.
 *
 * `app/src/test` is deliberately NOT scanned: a guard's own detector has to be able to name the
 * token it hunts, and `ClassicRemovalScan`'s branch detector plus this file's own message both
 * contain it. The cost of that exemption is that a stale test-side comment is unpoliced; the
 * epilogue read all ~20 surviving test-tree mentions by hand instead. Note the asymmetry: the two
 * shared modules have no such carve-out, so `sharedCore/src/commonTest` IS scanned.
 */
class FlagRemovalGuardTest {
    private val roots = listOf("app/src/main", "sharedCore/src", "sharedUi/src")

    /**
     * Anti-vacuity, PER ROOT — the property `RoutingSeamGuardTest` carried as
     * `theWalkActuallySeesSource`, and the one this guard's shape most needs. A single global floor
     * would let one mistyped or renamed root out of three hide behind the other two's file counts,
     * leaving a whole module unscanned while the absence assertion below still passed green.
     */
    @Test
    fun everyRootActuallyContainsSourceToScan() {
        val floors = mapOf("app/src/main" to 500, "sharedCore/src" to 150, "sharedUi/src" to 80)
        val thin = roots.mapNotNull { root ->
            val count = scannableFilesIn(root).count()
            val floor = floors.getValue(root)
            if (count < floor) "$root: $count scannable files (expected at least $floor)" else null
        }
        assertEquals(
            "a scan root yielded (almost) nothing, so the absence assertion below would pass " +
                "vacuously for it — the root string is wrong, or the working directory is not the " +
                ":app module dir",
            emptyList<String>(),
            thin,
        )
    }

    @Test
    fun nothingInProductionMentionsTheFlagAnyMore() {
        val offenders = roots.flatMap { root ->
            scannableFilesIn(root)
                .filter { f ->
                    // read as bytes: some sources carry live NUL bytes (spec 9.1)
                    val text = f.readBytes().toString(Charsets.ISO_8859_1)
                    text.contains("use_compose_ui") || text.contains("useComposeFor")
                }
                .map { it.relativeTo(ClassicRemovalScan.repoRoot()).path.replace('\\', '/') }
                .toList()
        }.sorted()
        assertEquals(
            "the flag is dead (spec 10.1); these production files still name it: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** Anti-vacuity's other half: a root that does not exist at all must fail, not scan empty. */
    private fun scannableFilesIn(root: String): Sequence<File> {
        val dir = File(ClassicRemovalScan.repoRoot(), root)
        assertTrue("scan root $root does not exist", dir.isDirectory)
        return dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
    }
}
