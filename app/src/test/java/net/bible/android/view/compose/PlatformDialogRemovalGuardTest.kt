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
 * SHRINK — a task that removes a file's last platform dialog deletes its line here. Run 3 Task 29
 * empties it, and Task 31 retires the two BASELINE-relative tests for the single zero-tolerance
 * [theAppHasNoPlatformDialogs] (BASELINE itself stays, as the historical record its kdoc is).
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
        /**
         * Task 31 addendum item 1 (rulings R3-3/R3-4): widened from the run-1/2 set to also match
         * `DatePickerDialog`/`TimePickerDialog`, `AppCompatDialog`/`AppCompatDialogFragment`, Material's
         * `BottomSheetDialog`/`BottomSheetDialogFragment`/`MaterialAlertDialogBuilder`. Each named form
         * stays an exact class name (never a prefix match) so a near-miss like `android.app.Notification`,
         * `androidx.appcompat.app.AppCompatActivity` or
         * `com.google.android.material.datepicker.MaterialDatePicker` (a real platform dialog, but not
         * one this guard was asked to ban) never matches.
         */
        private const val PKG = """(android\.app\.(AlertDialog|ProgressDialog|Dialog|DialogFragment|DatePickerDialog|TimePickerDialog)|androidx\.appcompat\.app\.(AlertDialog|AppCompatDialog|AppCompatDialogFragment)|androidx\.fragment\.app\.DialogFragment|com\.google\.android\.material\.bottomsheet\.(BottomSheetDialog|BottomSheetDialogFragment)|com\.google\.android\.material\.dialog\.MaterialAlertDialogBuilder)"""
        /** A wildcard import of either banned package, e.g. `import android.app.*` — banned outright
         *  (R3-3) once its only two production users (the notification managers) were made explicit,
         *  because a wildcard hides every future `AlertDialog`/`DatePickerDialog`/… it would silently
         *  bring in reach without adding a new import line for [QUALIFIED] to ever see. */
        private const val WILDCARD = """(android\.app|androidx\.appcompat\.app)\.\*"""
        val IMPORT = Regex("""^\s*import\s+($PKG\b(\.\w+)*(\s+as\s+\w+)?|$WILDCARD)\s*;?\s*$""")
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
         *
         * Run 3 Task 28 shrinks it to 1: `ReadingAppBootstrap.kt`'s `showStableNotice`/
         * `showBetaNotice` and `CommonUtils.kt`'s `showHelp` all move onto
         * `AppDialogRequest.Notice`/`AbNoticeDialog` (the app-logo/inline-icon shape). Clearing
         * `CommonUtils.kt` also required deleting `fixAlertDialogButtons` — dead code (no callers
         * anywhere) whose `dialog: AlertDialog` parameter type was the file's last remaining
         * platform-dialog reference.
         *
         * Run 3 Task 29 shrinks it to 0: `StartupActivity.kt`'s `checkWebView` `AlertDialog.Builder`
         * moves onto `AppDialogController` (a non-cancellable `Confirm`, branch-mapping extracted to
         * the testable `webViewTooOldRequest`). BASELINE is empty.
         *
         * Run 3 Task 31 replaces this set with [theAppHasNoPlatformDialogs] — a single zero-tolerance
         * test, BASELINE kept (still empty) only as the historical record above documents. [PKG] was
         * also widened this task (addendum item 1) to `DatePickerDialog`/`TimePickerDialog`/
         * `AppCompatDialog`/`AppCompatDialogFragment`/`BottomSheetDialog`/`BottomSheetDialogFragment`/
         * `MaterialAlertDialogBuilder`, and a wildcard `import android.app.*`/`import
         * androidx.appcompat.app.*` is banned outright (R3-3): the app's only two wildcard importers,
         * `ProgressNotificationManager.kt`/`TextToSpeechNotificationManager.kt`, were switched to
         * explicit imports in the addendum item 2 commit first, so this widening finds nothing new.
         * Task 30b (correction 10 / ruling R3-2) ported `NavHostComposeActivity.kt`'s
         * `android.app.DatePickerDialog` off the platform type before this widening landed, so the
         * newly-banned `DatePickerDialog` form also finds nothing.
         */
        val BASELINE: Set<String> = setOf()

        private fun stripComment(line: String): String = line.substringBefore("//")

        /**
         * A single file's already-code-filtered lines (see class KDoc) is "offending" if any line
         * either imports a banned dialog type (including a banned wildcard) or fully-qualifies one
         * outside an import line. The trailing-comment strip now runs before the IMPORT check too
         * (Task 31 addendum item 1: "strip a trailing `// …` before the IMPORT match") — previously
         * only the QUALIFIED branch stripped it, so `import android.app.DatePickerDialog // needed`
         * would NOT have matched IMPORT (its `\s*;?\s*$` anchor does not tolerate trailing prose),
         * silently missing a real import that happened to carry a trailing comment.
         */
        fun offendingLines(lines: List<String>): Boolean = lines.any { line ->
            val code = stripComment(line)
            IMPORT.matches(code) || (!code.trimStart().startsWith("import ") && QUALIFIED.containsMatchIn(code))
        }

        fun offendingFiles(sources: List<File>): Set<String> = sources.filter { f ->
            offendingLines(ClassicRemovalScan.codeLinesOf(f.path, keepImports = true).split("\n"))
        }.map { it.path }.toSet()
    }

    /** Spec §1/§8 goal, reached: zero platform dialogs anywhere in `app/src/main`. Replaces the
     *  run-1/2 pair ([BASELINE]-relative "nothing new"/"nothing stale") now that [BASELINE] is
     *  permanently empty — a single hint naming the fix (spec §5) is clearer than two asymmetric
     *  set-difference assertions that can never again both be non-trivial. */
    @Test fun theAppHasNoPlatformDialogs() {
        assertEquals(
            "a platform dialog appeared; use AppDialogController (owner-less) or the feature's own " +
                "Compose dialog state instead — spec §5",
            emptySet<String>(), offendingFiles(ClassicRemovalScan.appSources()),
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
            "import android.app.DatePickerDialog",
            "import android.app.TimePickerDialog",
            "import androidx.appcompat.app.AppCompatDialog",
            "import androidx.appcompat.app.AppCompatDialogFragment",
            "import com.google.android.material.bottomsheet.BottomSheetDialog",
            "import com.google.android.material.bottomsheet.BottomSheetDialogFragment",
            "import com.google.android.material.dialog.MaterialAlertDialogBuilder",
            "import android.app.*",
            "import androidx.appcompat.app.*",
        ).forEach { assertTrue(it, IMPORT.matches(it)) }
        listOf(
            "import androidx.compose.material3.AlertDialog",
            "import androidx.compose.material3.AlertDialog as ComposeAlertDialog",
            "import androidx.compose.ui.window.Dialog",
            "import android.app.Activity",
            "import android.app.DialogInterface",   // not a dialog; it is a callback type
            "import android.app.Notification",      // near miss for the newly-banned android.app.* wildcard
            "import androidx.appcompat.app.AppCompatActivity", // near miss for AppCompatDialog(Fragment)
            "import com.google.android.material.bottomsheet.BottomSheetBehavior", // near miss for BottomSheetDialog
            "import com.google.android.material.datepicker.MaterialDatePicker", // a real dialog, but not one PKG bans
        ).forEach { assertTrue(it, !IMPORT.matches(it)) }
    }

    @Test fun aTrailingCommentOnAnImportLineDoesNotHideIt() {
        // Task 31 addendum item 1: offendingLines strips a trailing `//` before the IMPORT check,
        // not just before QUALIFIED — this is the case that would otherwise slip through.
        assertTrue(offendingLines(listOf("import android.app.DatePickerDialog // needed for the picker")))
        assertTrue(offendingLines(listOf("import android.app.* // notifications")))
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
