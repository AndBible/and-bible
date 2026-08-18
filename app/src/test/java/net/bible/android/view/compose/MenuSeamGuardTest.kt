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
        "src/main/java/net/bible/android/view/activity/bookmark/ManageLabelsComposeActivity.kt",
        "src/main/java/net/bible/android/view/activity/download/DownloadComposeActivity.kt",
        "src/main/java/net/bible/android/view/activity/navigation/ChooseDocumentComposeActivity.kt",
        "src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsComposeActivity.kt",
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

    @Test
    fun migrated_menus_call_AbMenuItem_not_DropdownMenuItem() {
        val offenders = migratedFiles.flatMap { path ->
            File(path).readLines().withIndex().mapNotNull { (index, line) ->
                val trimmed = line.trimStart()
                val isProse = trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                    trimmed.startsWith("*") || trimmed.startsWith("/*")
                if (!isProse && directCall.containsMatchIn(line)) "$path:${index + 1}: $trimmed" else null
            }
        }
        assertEquals(emptyList<String>(), offenders)
    }
}
