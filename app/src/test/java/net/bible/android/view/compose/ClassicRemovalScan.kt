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
    /** The manifests a slice may have to sweep. Not every variant declares one; callers filter. */
    val manifestPaths = listOf(
        "src/main/AndroidManifest.xml",
        "src/standard/AndroidManifest.xml",
        "src/discrete/AndroidManifest.xml",
        "src/debug/AndroidManifest.xml",
    )

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
     * Fully-qualified names as regexes with a trailing non-identifier boundary. The boundary is
     * not decoration: a deleted `…search.EpubSearch` must not match the surviving
     * `…search.EpubSearchResultKey`, and a deleted `…readingplan.DailyReading` must not match
     * the surviving `…readingplan.DailyReadingComposeActivity`. With a plain `contains`, a
     * slice whose Compose twins are named after their classic originals — which is all of them
     * — has a sweep that can never pass.
     */
    fun refsFor(fqNames: List<String>): List<Regex> =
        fqNames.map { Regex(Regex.escape(it) + "(?![A-Za-z0-9_])") }

    /** Every Kotlin/Java source under `src/main`, for a whole-tree reference sweep. */
    fun mainSources(): List<File> =
        File("src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
}
