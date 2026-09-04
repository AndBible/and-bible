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

/**
 * Shared scanning helpers for the Batch Z-late phase 1 removal guards, one per slice
 * (`ClassicSearchRemovalGuardTest`, `ClassicReadingPlanRemovalGuardTest`, …).
 *
 * Extracted in slice S2 rather than copied: the two subtleties below were each got wrong once
 * while S1 was being written, and there are ~17 slices left. Paths are relative to the `:app`
 * module dir, which is the working directory for its unit tests.
 */
object ClassicRemovalScan {
    /**
     * The manifests a slice may have to sweep, discovered by globbing rather than a static list —
     * a future flavor manifest (a new source set gaining its own `AndroidManifest.xml`) is picked
     * up automatically instead of being silently skipped. Not every variant declares one; callers
     * filter.
     */
    val manifestPaths: List<String>
        get() = (File("src").listFiles { f -> f.isDirectory } ?: emptyArray())
            .map { "src/${it.name}/AndroidManifest.xml" }
            .filter { File(it).isFile }
            .sorted()

    /**
     * Non-prose lines only: a comment must not satisfy or defeat a scan. `import` lines are
     * dropped too by default — for a "does this file still compare X" scan an import is noise —
     * but a fully-qualified-name sweep passes `keepImports = true`, because there an import IS
     * the reference being hunted, and with imports stripped such a sweep finds nothing at all
     * and passes vacuously.
     */
    fun codeLinesOf(path: String, keepImports: Boolean = false): String =
        File(path).readLines().filterNot { line ->
            val trimmed = line.trimStart()
            (!keepImports && trimmed.startsWith("import ")) || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")

    /**
     * Fully-qualified names as regexes with a TRAILING non-identifier boundary only — there is no
     * leading boundary. The trailing boundary is not decoration: a deleted `…search.EpubSearch`
     * must not match the surviving `…search.EpubSearchResultKey`, and a deleted
     * `…readingplan.DailyReading` must not match the surviving
     * `…readingplan.DailyReadingComposeActivity`. With a plain `contains`, a slice whose Compose
     * twins are named after their classic originals — which is all of them — has a sweep that can
     * never pass.
     *
     * One-sided on purpose, not an oversight. Every caller today passes a FULLY-QUALIFIED name, for
     * which the missing leading boundary is harmless in practice. A leading boundary such as
     * `(?<![A-Za-z0-9_.])` would be the right form for a FQN, but is fiddlier than it looks: it
     * would be actively WRONG the day a caller passes a bare (non-qualified) class name instead,
     * since a bare name is routinely preceded by a `.` (ordinary qualified access, e.g.
     * `readingplan.DailyReading`) — exactly the character that form of leading boundary rejects.
     * Left out rather than added speculatively; add a leading boundary only alongside a caller that
     * actually needs one, matched to what that caller passes.
     */
    fun refsFor(fqNames: List<String>): List<Regex> =
        fqNames.map { Regex(Regex.escape(it) + "(?![A-Za-z0-9_])") }

    /**
     * Every Kotlin/Java source under every SHIPPING source set (`src/main`, `src/debug`,
     * `src/standard`, `src/discrete`, …), for a whole-tree reference sweep — `src/test` and
     * `src/androidTest` are excluded, because they are test code, not app code: they cannot
     * themselves be launched or shipped, so a class name surviving only there is not the kind of
     * reintroduction this scan exists to catch, and test fixtures legitimately reference doomed
     * classes right up until the guard test for them is deleted. Originally `mainSources()` /
     * `src/main`-only, widened in slice S2's fix wave once a helper meant for ~17 slices was found
     * to encode "all classic code lives in `src/main`" as a silent premise; before widening, both
     * `ClassicSearchRemovalGuardTest` and this file's own guard were re-run to confirm the wider
     * scan does not newly catch a real reference in `src/debug`/`src/discrete`/`src/standard`.
     */
    fun appSources(): List<File> =
        File("src").listFiles { f -> f.isDirectory && f.name != "test" && f.name != "androidTest" }
            .orEmpty()
            .flatMap { sourceSet ->
                sourceSet.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            }
}
