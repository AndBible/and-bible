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

package net.bible.sharedui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source-scan guard: no test may use the v1 `androidx.compose.ui.test.runComposeUiTest`.
 *
 * Why: v1 runs test coroutines unconfined, so Compose's JVM-wide GlobalSnapshotManager (which applies
 * snapshots on the AWT thread) can resume them on another thread; snapshot and sheet tests then flaked
 * about 1 run in 10 (issue #3948). Use `androidx.compose.ui.test.v2.runComposeUiTest` and collect a
 * `snapshotFlow` through a `Channel` received on the test dispatcher.
 *
 * A file that uses `runComposeUiTest` must import the v2 one explicitly (an explicit import wins over a
 * `androidx.compose.ui.test.*` star import) and must not import the v1 one. Tests that truly cannot move
 * go into [allowlist] with a reason.
 */
class V1ComposeUiTestGuardTest {
    /** File name (relative to `src`) to the reason it may keep the v1 API. Empty: every test moved. */
    private val allowlist: Map<String, String> = emptyMap()

    private val v1Import = Regex("^import\\s+androidx\\.compose\\.ui\\.test\\.runComposeUiTest\\b", RegexOption.MULTILINE)
    private val v2Import = Regex("^import\\s+androidx\\.compose\\.ui\\.test\\.v2\\.runComposeUiTest\\b", RegexOption.MULTILINE)

    /** Returns the violations among [files] (path to message), ignoring [allowlist]. */
    private fun violations(files: List<Pair<String, String>>): List<String> = files.mapNotNull { (path, text) ->
        when {
            path in allowlist -> null
            !text.contains("runComposeUiTest") -> null
            v1Import.containsMatchIn(text) -> "$path imports the v1 runComposeUiTest"
            !v2Import.containsMatchIn(text) -> "$path uses runComposeUiTest without the explicit v2 import"
            else -> null
        }
    }

    @Test
    fun noTestUsesTheV1RunComposeUiTest() {
        val src = File("src")
        val files = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.invariantSeparatorsPath.contains("Test/") }
            .filter { it.name != "V1ComposeUiTestGuardTest.kt" }
            .map { it.relativeTo(src).invariantSeparatorsPath to it.readText() }
            .toList()
        assertTrue(files.isNotEmpty(), "the scan found no test sources; the path is wrong")
        val bad = violations(files)
        assertTrue(bad.isEmpty(), "v1 runComposeUiTest (issue #3948):\n" + bad.joinToString("\n"))
    }

    @Test
    fun theScanFlagsV1AndStarImportOnlyFiles() {
        val v1 = "import androidx.compose.ui.test.runComposeUiTest\nfun t() = runComposeUiTest {}"
        val star = "import androidx.compose.ui.test.*\nfun t() = runComposeUiTest {}"
        val ok = "import androidx.compose.ui.test.*\nimport androidx.compose.ui.test.v2.runComposeUiTest\nfun t() = runComposeUiTest {}"
        assertTrue(violations(listOf("a" to v1, "b" to star, "c" to ok)).size == 2)
    }
}
