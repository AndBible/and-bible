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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec §8: the app's UI has no platform dialogs. Scans IMPORTS and fully-qualified names, never call
 * shapes (call shapes are unbounded — `NavHostRoutingGuardTest` needed three hardening rounds; a
 * platform dialog cannot be built without naming its package). Discriminates by PACKAGE:
 * `androidx.compose.material3.AlertDialog` and `androidx.compose.ui.window.Dialog` are the correct
 * thing and never match.
 *
 * Baseline mode (run 1): [BASELINE] is the set of files that still have one. The set may only
 * SHRINK — a task that removes a file's last platform dialog deletes its line here. Run 3 empties it.
 *
 * Controller ruling (Task 6): scans only CODE lines, via
 * [ClassicRemovalScan.codeLinesOf]`(path, keepImports = true)`, not raw `readLines()`. The brief's
 * original code scanned raw lines and only stripped a trailing `//`, so KDoc/block-comment prose —
 * e.g. NavHostComposeActivity.kt's "the delete confirmation (an `android.app.AlertDialog`; …)" lines
 * — would QUALIFIED-match and pin that file in [BASELINE] forever, making run 3's guard-to-zero
 * unreachable. `codeLinesOf` drops lines starting with `//`, `*` and a block-comment opener before
 * this scan ever sees them.
 */
class PlatformDialogRemovalGuardTest {

    companion object {
        private const val PKG = """(android\.app\.(AlertDialog|ProgressDialog|Dialog|DialogFragment)|androidx\.appcompat\.app\.AlertDialog|androidx\.fragment\.app\.DialogFragment)"""
        val IMPORT = Regex("""^\s*import\s+$PKG\b(\.\w+)*(\s+as\s+\w+)?\s*;?\s*$""")
        /** A fully-qualified use outside an import line, e.g. `android.app.AlertDialog.Builder(`. */
        val QUALIFIED = Regex("""(?<![\w.])$PKG\b""")

        /**
         * Relative to `app/`. Task 0's measured baseline was 23 files, confirmed unchanged by
         * Task 6. Platform-dialog removal Task 10 shrinks it to 16: `LlmDialogHelper.kt` (deleted
         * outright), `OptionsMenuItems.kt` (its three remaining `AlertDialog` builders --
         * StrongsPreference/PageScrollAmountPreference/ScrollHelperLineStylePreference -- and the
         * `FontSizePreference`/`TopMarginPreference`/`FontFamilyPreference`/`LineSpacingPreference`/
         * `MarginSizePreference` `openDialog` bodies that called the three widget files below, are
         * all deleted; `CommandPreference`/`ColorPreference`/`HideLabelsPreference`/
         * `AutoAssignPreference`'s `openDialog`s launch activities/intents, never an `AlertDialog`,
         * so they were never what put this file here), and `FontSizeWidget.kt`/`LineSpacing.kt`/
         * `MarginSizeWidget.kt` (the widget-side `AlertDialog.Builder`s those five preferences
         * called; `LineSpacing.kt`/`MarginSizeWidget.kt` are deleted entirely, `FontSizeWidget.kt`
         * keeps only `FontDefinition`/`availableFonts`, still used by
         * `TextDisplaySettingsServiceImpl.fontFamilyEntries`).
         *
         * Platform-dialog removal Task 18 shrinks it to 13: `BibleJavascriptInterface.kt`
         * (`helpDialog`/`helpBookmarks`/`deleteMyDocumentPage` moved to the reading view's own
         * `ReadingDialog` state), `ComposeReadingViewHost.kt` (`showSpeakHelp`/`showAdvancedSpeakHelp`
         * now render `AbMessageDialog` inside `SpeakSettingsSlot`, and the BJI dialogs render through
         * the new `ReadingDialogSlot`), and `BibleView.kt` (its only `AlertDialog` reference was an
         * unused `import android.app.AlertDialog`, dropped alongside).
         */
        val BASELINE: Set<String> = setOf(
            "src/main/java/net/bible/android/control/backup/BackupControl.kt",
            "src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt",
            "src/main/java/net/bible/android/control/page/window/WindowControl.kt",
            "src/main/java/net/bible/android/control/report/ErrorReportControl.kt",
            "src/main/java/net/bible/android/view/activity/StartupActivity.kt",
            "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt",
            "src/main/java/net/bible/android/view/activity/page/ReadingAppBootstrap.kt",
            "src/main/java/net/bible/android/view/activity/progress/ReadHistoryDialog.kt",
            "src/main/java/net/bible/android/view/util/widget/ShareWidget.kt",
            "src/main/java/net/bible/service/cloudsync/CloudSync.kt",
            "src/main/java/net/bible/service/common/CommonUtils.kt",
            "src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt",
        )

        private fun stripComment(line: String): String = line.substringBefore("//")

        /** A single file's already-code-filtered lines (see class KDoc) is "offending" if any line
         * either imports a banned dialog type, or fully-qualifies one outside an import line. */
        fun offendingLines(lines: List<String>): Boolean = lines.any { line ->
            IMPORT.matches(line) ||
                (!line.trimStart().startsWith("import ") && QUALIFIED.containsMatchIn(stripComment(line)))
        }

        fun offendingFiles(sources: List<File>): Set<String> = sources.filter { f ->
            offendingLines(ClassicRemovalScan.codeLinesOf(f.path, keepImports = true).split("\n"))
        }.map { it.path }.toSet()
    }

    @Test fun noFileOutsideTheBaselineHasAPlatformDialog() {
        val found = offendingFiles(ClassicRemovalScan.appSources())
        assertEquals(
            "a platform dialog appeared in a new file; use AppDialogController (owner-less) or the " +
                "feature's own Compose dialog state instead — spec §5",
            emptySet<String>(), found - BASELINE,
        )
    }

    @Test fun theBaselineHasNoStaleEntries() {
        val found = offendingFiles(ClassicRemovalScan.appSources())
        assertEquals(
            "these baseline files no longer have a platform dialog — delete their lines from BASELINE",
            emptySet<String>(), BASELINE - found,
        )
    }

    @Test fun theImportPatternMatchesEveryBannedFormAndNoComposeForm() {
        listOf(
            "import android.app.AlertDialog",
            "import androidx.appcompat.app.AlertDialog",
            "import android.app.ProgressDialog",
            "import android.app.Dialog",
            "import androidx.fragment.app.DialogFragment",
            "import android.app.AlertDialog as PlatformDialog",
            "import android.app.AlertDialog.Builder",
        ).forEach { assertTrue(it, IMPORT.matches(it)) }
        listOf(
            "import androidx.compose.material3.AlertDialog",
            "import androidx.compose.material3.AlertDialog as ComposeAlertDialog",
            "import androidx.compose.ui.window.Dialog",
            "import android.app.Activity",
            "import android.app.DialogInterface",   // not a dialog; it is a callback type
        ).forEach { assertTrue(it, !IMPORT.matches(it)) }
    }

    @Test fun theQualifiedPatternSeesAnInlineBuilder() {
        assertTrue(QUALIFIED.containsMatchIn("val d = android.app.AlertDialog.Builder(this)"))
        assertTrue(!QUALIFIED.containsMatchIn("androidx.compose.material3.AlertDialog(onDismissRequest = {})"))
    }

    /**
     * KDoc/block-comment prose and a real import must be told apart, via the SAME pipeline the
     * guard tests use ([ClassicRemovalScan.codeLinesOf] filtering, then [offendingLines]) — never by
     * calling [offendingLines] directly on a raw, unfiltered KDoc line: filtering `*`-prefixed prose
     * out is [ClassicRemovalScan.codeLinesOf]'s job, not [offendingLines]'s, so a KDoc line fed
     * straight to [offendingLines] would still match and this test would prove nothing.
     */
    @Test fun commentProseDoesNotCount() {
        val kdocProse = " * an `android.app.AlertDialog` is shown here for the delete confirmation"
        val lineComment = "// android.app.AlertDialog"
        val realImport = "import android.app.AlertDialog"

        val tmp = File.createTempFile("guardProbeComments", ".kt")
        try {
            tmp.writeText(
                """
                /**
                 * $kdocProse
                 */
                $lineComment
                class NotOffending
                """.trimIndent(),
            )
            val codeLines = ClassicRemovalScan.codeLinesOf(tmp.path, keepImports = true).split("\n")
            assertTrue(!offendingLines(codeLines))

            tmp.writeText(realImport + "\n" + tmp.readText())
            val codeLinesWithImport = ClassicRemovalScan.codeLinesOf(tmp.path, keepImports = true).split("\n")
            assertTrue(offendingLines(codeLinesWithImport))
        } finally {
            tmp.delete()
        }
    }

    @Test fun theScanSeesTheSourceTree() =
        assertTrue("run from app/ — the scan would pass vacuously", File("src/main/java").isDirectory)
}
