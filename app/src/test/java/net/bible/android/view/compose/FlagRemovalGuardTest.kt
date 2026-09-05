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
 * reader is misled about which paths are live. The epilogue rewrote ~33 such comments to land this.
 *
 * **What "production" means here, stated exactly, because the class of bug this guard exists to
 * catch is a claim wider than its evidence.** [roots] covers EVERY shipping source set of `:app` —
 * `src/main` plus the three flavor sets `src/standard`, `src/discrete`, `src/debug` — and the whole
 * of `sharedCore/src` and `sharedUi/src`. The flavor sets were added in Task 7's fix round 1: they
 * hold only 14 files between them, but one of them is `app/src/standard/AndroidManifest.xml`, which
 * this very batch made load-bearing when slice S16 moved the InstallZip intent-filters onto the
 * Compose host. A guard whose stated purpose is not to overclaim must not itself overclaim.
 *
 * `app/src/test` and `app/src/androidTest` are NOT scanned — the same exclusion, for the same
 * reason, that [ClassicRemovalScan.appSources] makes: they are test code, not app code. Here it is
 * load-bearing rather than merely tidy, because a guard's own detector has to be able to name the
 * token it hunts, and `ClassicRemovalScan`'s branch detector plus this file's own message both
 * contain it. The cost of that exemption is that a stale test-side comment is unpoliced; the
 * epilogue read all ~20 surviving test-tree mentions by hand instead. Note the asymmetry, which is
 * deliberate and recorded rather than evened out: the two shared modules have no such carve-out, so
 * `sharedCore/src/commonTest` IS scanned.
 *
 * Known ceiling, so nobody reads a green run as more than it is: this matches the literal TOKEN.
 * Paraphrase survives it — "flag-OFF", "flag-routed", "the classic path" — and such prose is swept
 * by hand, not by this test.
 */
class FlagRemovalGuardTest {
    private val roots = listOf(
        "app/src/main",
        "app/src/standard",
        "app/src/discrete",
        "app/src/debug",
        "sharedCore/src",
        "sharedUi/src",
    )

    /**
     * Anti-vacuity, PER ROOT — the property `RoutingSeamGuardTest` carried as
     * `theWalkActuallySeesSource`, and the one this guard's shape most needs. A single global floor
     * would let one mistyped or renamed root hide behind the others' file counts (`app/src/main`
     * alone would swallow all five), leaving a whole source set unscanned while the absence
     * assertion below still passed green.
     *
     * The floors sit safely under today's counts (866 / 1 / 8 / 5 / 283 / 147). Said plainly: for
     * the three flavor roots the floor is a weak quantity check and is not doing the real work —
     * `app/src/standard` legitimately holds ONE file, so its floor can only be 1. What actually
     * defends those three is [scannableFilesIn]'s eager `isDirectory` assert, which fires in BOTH
     * tests, so a renamed or removed root FAILS rather than degrading into a silent empty walk. The
     * floor's remaining job there is the narrow one it can do: catch a root that exists but has
     * stopped holding any `.kt`/`.xml` at all.
     */
    @Test
    fun everyRootActuallyContainsSourceToScan() {
        val floors = mapOf(
            "app/src/main" to 500,
            "app/src/standard" to 1,
            "app/src/discrete" to 5,
            "app/src/debug" to 3,
            "sharedCore/src" to 150,
            "sharedUi/src" to 80,
        )
        val thin = roots.mapNotNull { root ->
            val count = scannableFilesIn(root).count()
            val floor = floors.getValue(root)
            if (count < floor) "$root: $count scannable files (expected at least $floor)" else null
        }
        assertEquals(
            "a scan root yielded (almost) nothing, so the absence assertion below would pass " +
                "vacuously for it — the root string is wrong, or the source set has been emptied",
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
