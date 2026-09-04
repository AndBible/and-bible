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
 * Batch Z-late phase 1, P4. `ScreenLauncher` is the routing seam; two files had re-implemented its
 * `use_compose_ui` decision themselves, one of them on a notification path the compiler cannot see.
 * This guard enumerates every remaining reader so a new hand-rolled copy fails a test instead of
 * quietly surviving the flag's removal.
 *
 * The scan looks for the READ (`getBoolean("use_compose_ui"`), not for the flag's name: 25 files
 * mention the string, but only 8 actually read it and the rest are KDoc. Matching the name would
 * make this guard a list of documentation.
 */
class RoutingSeamGuardTest {
    private val readPattern = """getBoolean("use_compose_ui""""

    /**
     * Every file that legitimately reads the flag after P4 — verified by
     * `grep -a -rn 'getBoolean("use_compose_ui"' app/src/main`, which found 16 reads in 8 files
     * before this task. The epilogue empties this list when the flag goes.
     */
    private val expectedReaders = listOf(
        "src/main/java/net/bible/android/view/ScreenLauncher.kt",
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt",
        "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt",
        "src/main/java/net/bible/android/view/activity/page/OptionsMenuStateBuilder.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/DocumentViewManager.kt",
        "src/main/java/net/bible/android/view/activity/installzip/InstallZip.kt",
    )

    @Test fun everyExpectedReaderStillExists() {
        // Anti-vacuity: a renamed path must resurface as a failure, not as a silent blind spot.
        val missing = expectedReaders.filterNot { File(it).isFile }
        assertEquals("listed readers that no longer exist", emptyList<String>(), missing)
    }

    @Test fun everyExpectedReaderActuallyStillReadsTheFlag() {
        // The other half of anti-vacuity: a file that stopped reading the flag must be removed from
        // the list, or the list slowly becomes fiction.
        val stale = expectedReaders.filterNot { codeLinesOf(File(it)).contains(readPattern) }
        assertEquals("listed readers that no longer read the flag", emptyList<String>(), stale)
    }

    @Test fun noOtherFileReadsTheComposeUiFlag() {
        val expected = expectedReaders.map { File(it).name }.toSet()
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in expected }
            .filter { codeLinesOf(it).contains(readPattern) }
            .map { it.path.replace('\\', '/') }
            .sorted()
            .toList()
        assertEquals(
            "these files decide classic-vs-Compose themselves instead of asking ScreenLauncher",
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun theWalkActuallySeesSource() {
        assertTrue(File("src/main/java").walkTopDown().count { it.extension == "kt" } > 100)
    }

    /**
     * Spec §4 P5. `WorkspaceSelectorComposeActivity` is the ONE Compose file allowed to name the
     * classic text-display settings activity: it needs that screen's activity result
     * (`settingsBundle` + `reset`), which the Compose screen does not produce (spec §11.4).
     *
     * The scan below is FILENAME-scoped (`it.name.contains("Compose")`), not scoped to actual
     * Compose code — so it only catches Compose *hosts*, whose file names carry "Compose" by
     * convention. A Compose caller living in a file without "Compose" in its name (e.g. a
     * `:sharedUi` screen, controller or composable helper) is invisible to this guard. Do not widen
     * the scan to close that gap late in this branch; it is a known, accepted blind spot.
     */
    @Test fun onlyTheWorkspaceSelectorNamesTheClassicTextDisplaySettings() {
        val allowed = "WorkspaceSelectorComposeActivity.kt"
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.contains("Compose") }
            .filter { it.name != allowed }
            .filter { codeLinesOf(it).contains("TextDisplaySettingsActivity") }
            .map { it.path.replace('\\', '/') }
            .sorted()
            .toList()
        assertEquals(
            "these Compose files launch the CLASSIC text-display settings; route through " +
                "ScreenLauncher.intentFor(this, Screen.TextDisplaySettings) instead",
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun theWorkspaceSelectorStillNeedsTheClassicResult() {
        // Anti-vacuity for the allowlist above: if this file stops naming the classic activity the
        // exemption is stale and must be removed, not left as a blind spot.
        val f = File("src/main/java/net/bible/android/view/activity/workspaces/WorkspaceSelectorComposeActivity.kt")
        assertTrue("the allowlisted file no longer exists", f.isFile)
        assertTrue(
            "WorkspaceSelectorComposeActivity no longer names TextDisplaySettingsActivity — spec " +
                "§11.4 may have been resolved, so drop this exemption and the one above",
            codeLinesOf(f).contains("TextDisplaySettingsActivity"),
        )
    }

    private fun codeLinesOf(file: File): String =
        file.readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
