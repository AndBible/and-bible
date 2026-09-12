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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F49: the migrated menus must go through `AbMenuItem`, not call `DropdownMenuItem` directly —
 * that seam is what carries the leading icon, the single trailing-check style and the shared
 * `MenuItemColors`. A direct call silently opts out of all three.
 *
 * Deliberately a source scan: the property is "no call site anywhere in these files", which no
 * runtime assertion can express. Two traps this test is written to avoid, both of which this repo
 * has hit before: a guard satisfied by the mere presence of an `import` line, and a guard that
 * passes vacuously because the paths it scans no longer exist.
 */
class MenuSeamGuardTest {

    /** Relative to the `:app` module dir, which is the working directory for its unit tests. */
    private val migratedFiles = listOf(
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/ReadingOverflowMenu.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/WindowPaneMenu.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/QuickDocMenu.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/ReadingToolbar.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/bookmark/BookmarksScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/bookmark/ManageLabelsScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/workspaces/WorkspaceSelectorScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/navigation/GridPassageScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/progress/ReadingProgressScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/readingplan/DailyReadingScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/search/SearchScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/search/BibleSearchResultsContent.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/search/EpubSearchScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/mydocuments/MyDocumentsScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/mydocuments/MyDocumentPagesScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/BibleSpeakScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/AdvancedSpeakSettingsScreen.kt",
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/cloud/CloudDocumentsScreen.kt",
        "src/main/java/net/bible/android/view/activity/navigation/ChooseDocumentComposeActivity.kt",
        // The nav host. It carries the label manager's overflow menu (`ManageLabelsActions`, seven
        // `AbMenuItem` calls) since nav-graph slices 2+4 Task 7 deleted `ManageLabelsComposeActivity`
        // and moved that menu here. The guard's `migratedFiles` dropped the deleted Activity in the
        // same task but did not follow the menu to its new home, so for one batch those seven items
        // were unscanned -- see [preExistingExceptions] for the one site in this file that this
        // guard cannot hold to the rule yet.
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt",
    )

    /**
     * Direct `DropdownMenuItem(` call sites that PREDATE this guard's coverage of their file and are
     * therefore not failed here, keyed by scanned path and matched on the offending line's exact
     * trimmed text.
     *
     * Exactly one entry, and it names its one subject rather than excusing a file: the `⋮` help menu
     * of `AiConnectionHelpAction` in the nav host. It was written before `AbMenuItem` existed (it is
     * present unchanged at the batch base `cfddb559`, where the host had ZERO `AbMenuItem` calls) and
     * belongs to the AI-settings cluster, not to the bookmark migration that brought this file into
     * the scan. Converting it is a real change to a different cluster's menu and is left to whoever
     * owns that work; leaving the whole file unscanned to avoid mentioning it would have hidden seven
     * menu items that ARE this batch's.
     *
     * An exception is a liability, so it is bounded in both directions by
     * [every_declared_exception_still_matches_exactly_one_line]: it must match exactly one line, so
     * it can never quietly cover a second offender, and it must keep matching, so it cannot outlive
     * the site it names.
     */
    private val preExistingExceptions: Map<String, List<String>> = mapOf(
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt" to listOf(
            "DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {",
        ),
    )

    private val directCall = Regex("""\bDropdownMenuItem\s*\(""")

    @Test
    fun the_guard_can_tell_a_call_from_an_import() {
        assertTrue(directCall.containsMatchIn("        DropdownMenuItem("))
        assertTrue(directCall.containsMatchIn("DropdownMenuItem(text = { Text(x) })"))
        assertFalse(directCall.containsMatchIn("import androidx.compose.material3.DropdownMenuItem"))
        assertFalse(directCall.containsMatchIn(" * see DropdownMenuItem for the slot semantics"))
    }

    @Test
    fun every_scanned_path_exists() {
        val missing = migratedFiles.filterNot { File(it).isFile }
        assertEquals("scanned paths that no longer exist (guard would pass vacuously)", emptyList<String>(), missing)
    }

    /** Every direct `DropdownMenuItem(` call in [path], as `trimmed line text` -> 1-based line number. */
    private fun directCallSites(path: String): List<Pair<String, Int>> =
        File(path).readLines().withIndex().mapNotNull { (index, line) ->
            val trimmed = line.trimStart()
            val isProse = trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
            if (!isProse && directCall.containsMatchIn(line)) trimmed to (index + 1) else null
        }

    @Test
    fun migrated_menus_call_AbMenuItem_not_DropdownMenuItem() {
        val offenders = migratedFiles.flatMap { path ->
            val excepted = preExistingExceptions[path].orEmpty()
            directCallSites(path)
                .filterNot { (trimmed, _) -> trimmed in excepted }
                .map { (trimmed, lineNumber) -> "$path:$lineNumber: $trimmed" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * The bound on [preExistingExceptions], in both directions. An exception that matched nothing
     * would be a stale licence sitting in the file waiting to cover some future line; one that
     * matched twice would already be covering a second site nobody signed off on. Either way the
     * fix is to edit the exception, not to widen it.
     */
    @Test
    fun every_declared_exception_still_matches_exactly_one_line() {
        val problems = preExistingExceptions.flatMap { (path, exceptions) ->
            assertTrue("excepted path is not scanned at all: $path", path in migratedFiles)
            val sites = directCallSites(path)
            exceptions.mapNotNull { exception ->
                val hits = sites.count { (trimmed, _) -> trimmed == exception }
                if (hits == 1) null else "$path: exception matched $hits lines (expected exactly 1): $exception"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }
}
