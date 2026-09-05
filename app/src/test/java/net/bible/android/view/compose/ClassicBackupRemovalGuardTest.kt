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

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Batch Z-late phase 1, slice S17: `class BackupActivity` — which lived at the END of
 * `control/backup/BackupControl.kt`, not in a file of its own — was deleted with its two layouts,
 * and `ScreenLauncher`'s arm collapsed to `BackupComposeActivity`.
 *
 * Six assertions, not five, because no file is deleted. Spec §5 calls this a "split"; nothing is
 * split out — the doomed declaration was the file's last, so the operation is a truncation plus an
 * import prune. `assertPathsGone` cannot express "the file survives but this declaration does not",
 * so `theTruncationTookExactlyTheActivity` below reads the surviving file directly. It is the only
 * gate that can catch a cut that took too much or too little: an over-cut removes `object
 * BackupControl`'s tail and fails the compile in dozens of places, while an under-cut leaves a class
 * that no longer compiles because its layout binding is gone.
 *
 * Spec §4 P4 and Appendix B A3 say `backupPopup` reads the flag directly, making this arm dead code.
 * That was true before the prologue and is false at BASE — `backupPopup` routes through
 * `ScreenLauncher.intentFor`, so S17 is a real routing change.
 */
class ClassicBackupRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.control.backup.BackupActivity",
    )

    private val doomedPaths = listOf(
        "src/main/res/layout/backup_view.xml",
        "src/main/res/layout/backup_file_list_item.xml",
    )

    private val backupControl = "src/main/java/net/bible/android/control/backup/BackupControl.kt"

    @Test fun theClassicBackupLayoutsAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "the classic backup layouts should have been deleted in S17. backup_view.xml was " +
                "view-binding-only; both referrers lived inside the deleted class body.",
        )
    }

    @Test fun noSourceFileNamesTheClassicBackupActivity() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name control.backup.BackupActivity deleted in S17. The surviving " +
                "twin is view.activity.backup.BackupComposeActivity — a different package as well " +
                "as a different name.",
        )
    }

    @Test fun noManifestEntryNamesTheClassicBackupActivity() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the class S17 deletes",
        )
    }

    @Test fun screenLauncherDoesNotBranchForBackup() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.Backup"),
            "the Backup arm still branches on the flag (or is missing entirely) — S17 collapses it " +
                "to BackupComposeActivity unconditionally",
        )
    }

    @Test fun backupControlItselfSurvives() {
        ClassicRemovalScan.assertPathsPresent(
            listOf(backupControl),
            "S17 truncates BackupControl.kt; it must not be deleted. Its object and top-level " +
                "declarations have consumers across :app, including two surviving Compose activities.",
        )
    }

    @Test fun theTruncationTookExactlyTheActivity() {
        val code = ClassicRemovalScan.codeLinesOf(backupControl, keepImports = true)
        assertFalse(
            "BackupControl.kt still declares class BackupActivity — S17's truncation did not land",
            code.contains("class BackupActivity"),
        )
        listOf(
            "object BackupControl",
            "enum class SaveOrShare",
            "fun resolveDestination",
            "val databaseTitleResIds",
            "const val DATABASE_BACKUP_SUFFIX",
        ).forEach { survivor ->
            assertTrue(
                "BackupControl.kt no longer declares `$survivor` — S17's truncation cut too far. " +
                    "The boundary is the blank line before `class BackupActivity`; everything " +
                    "above it survives and has consumers elsewhere in :app.",
                code.contains(survivor),
            )
        }
        assertFalse(
            "the BackupViewBinding import is still present, but backup_view.xml is gone — this is " +
                "an unresolved reference, not a tidiness issue",
            code.contains("databinding.BackupViewBinding"),
        )
    }
}
