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
         *
         * Run 2 Task 19 shrinks it to 10: `MenuCommandHandler.kt` (rate, licence — both onto
         * `AppDialogController`), `WindowControl.kt` (`chooseSettingsToCopy`, now `Dialogs.multiselect`),
         * and `BookmarkControl.kt` (`importFromUri`'s error dialog) are clean. `ReadingAppBootstrap.kt`
         * stays — `showStableNotice`/`showBetaNotice` (run 3, Task 28) still build one — and so does
         * `BackupControl.kt` — `saveOrShare`'s `platformPrompt` and `askIfRestoreOrImport` (run 3, Task 24)
         * still do too.
         *
         * Run 3 Task 23 shrinks it to 9: `NavHostComposeActivity.kt`'s last two `AlertDialog.Builder`s
         * (`askIfWantToProceedWithDownload`, `warnUserBooksNotDownloaded`) move onto
         * `DocumentSelectionController`'s `askProceed`/`showBooksNotDownloaded`, and the now-dead
         * `import android.app.AlertDialog` goes with them — the file's remaining `android.app
         * .AlertDialog`/`android.app.DatePickerDialog` mentions are all KDoc/comment prose (filtered
         * by `codeLinesOf`) except `import android.app.DatePickerDialog` itself, which `PKG` does not
         * match (correction 10 / ruling R3-2, Task 30b).
         *
         * Run 3 Task 24 shrinks it to 6: `BackupControl.kt` (`saveOrShare`'s `platformPrompt` fallback
         * and `askIfRestoreOrImport` both move onto `AppDialogController`; D8-1 also fixed there --
         * back/scrim now answers Cancel, not the old accidental alias to Import), `CloudSync.kt`
         * (`initializeSync`'s fetch/create/disable question), and `ErrorReportControl.kt`
         * (`showErrorDialog`'s actions, dropping the dead `report = false` arm and the double
         * `setPositiveButton`) are clean.
         *
         * Run 3 Task 25 shrinks it to 5: `GetCommentariesTool.kt`'s `showFilterDialog` moves onto
         * `AppDialogController` (`AppDialogRequest.MultiChoice.footerFor`, a live token-total sheet
         * footer) and the 500 ms `CurrentActivityHolder` poll it existed for is deleted with it --
         * the controller's queue is the wait now.
         *
         * Run 3 Task 26 shrinks it to 4: `ShareWidget.kt` is deleted outright — its `AlertDialog`
         * (three-button verse-share prompt) is replaced by `ReadingQuickSheet.Share` /
         * `ShareVersesSheet`, a quick sheet rendered by `ComposeReadingViewHost.QuickSheetSlot`.
         *
         * Run 3 Task 27 shrinks it to 3: `ReadHistoryDialog.kt` is deleted outright — its
         * `showForChapter` `AlertDialog` is replaced by `ReadingQuickSheet.ReadHistory` /
         * `AbReadHistorySheet`, the same `QuickSheetSlot` mount point Task 26 used;
         * `showForBook`/`showForDay` already had no callers.
         */
        val BASELINE: Set<String> = setOf(
            "src/main/java/net/bible/android/view/activity/StartupActivity.kt",
            "src/main/java/net/bible/android/view/activity/page/ReadingAppBootstrap.kt",
            "src/main/java/net/bible/service/common/CommonUtils.kt",
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
