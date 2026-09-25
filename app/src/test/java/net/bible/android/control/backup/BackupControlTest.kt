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

package net.bible.android.control.backup

import androidx.core.content.FileProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupControlTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
    }

    private fun activity(): ActivityBase =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    /**
     * [BackupControl.askIfRestoreOrImport] hops via `withContext(Dispatchers.Main)`. Under plain
     * `runTest`, `Dispatchers.Main` resolves to the real Robolectric main Looper, which nothing
     * pumps -- the coroutine deadlocks forever (same trap `DialogsShimTest.runOnTestMain` documents
     * for `Hourglass`). Binding Main to this runTest's own `testScheduler` makes `advanceUntilIdle()`
     * drive it instead.
     */
    private fun <T> runOnTestMain(block: suspend kotlinx.coroutines.test.TestScope.() -> T) = runTest(timeout = 10.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }
    /**
     * Regression guard for the "Unknown database file: ..." crash (e.g. progress.sqlite3).
     * Every database that can appear in a backup/restore must have a title mapping,
     * otherwise [selectDatabaseSections]/the reset section throw IllegalStateException.
     */
    @Test
    fun everyBackedUpDatabaseHasATitle() {
        val missing = ALL_DB_FILENAMES.filterNot { databaseTitleResIds.containsKey(it) }
        assertTrue(
            "Database filenames missing from databaseTitleResIds: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun allTitleResourcesResolveToNonBlankStrings() {
        val context = RuntimeEnvironment.getApplication()
        for ((filename, resId) in databaseTitleResIds) {
            assertNotEquals("Title resId for $filename must not be 0", 0, resId)
            val title = context.getString(resId)
            assertFalse("Title for $filename must not be blank", title.isBlank())
        }
    }

    @Test
    fun databaseTitlesHaveNoExtraEntries() {
        // Keeps the map in sync with the canonical list — no orphaned/typo'd filenames.
        val unexpected = databaseTitleResIds.keys.filterNot { it in ALL_DB_FILENAMES }
        assertTrue(
            "databaseTitleResIds has entries not in ALL_DB_FILENAMES: $unexpected",
            unexpected.isEmpty()
        )
    }

    /**
     * Regression guard for the "Failed to find configured root" crash when exporting/sharing
     * a My Document page. [saveOrShare] always calls [FileProvider.getUriForFile], which throws
     * IllegalArgumentException unless the file's directory is declared in res/xml/file_paths.xml.
     * Every directory that backs a saveOrShare() call must therefore have a matching <files-path>.
     */
    @Test
    fun fileProviderResolvesAllSaveOrShareDirectories() {
        val context = RuntimeEnvironment.getApplication()
        val authority = "${context.packageName}.provider"
        // Directories (relative to filesDir) that saveOrShare() callers write into.
        val saveOrShareDirs = listOf("backup", "export")
        for (dir in saveOrShareDirs) {
            val targetDir = File(context.filesDir, dir).apply { mkdirs() }
            val file = File(targetDir, "sample.txt").apply { writeText("x") }
            val uri = FileProvider.getUriForFile(context, authority, file)
            assertNotNull("FileProvider must resolve a uri for files under $dir/", uri)
        }
    }

    // -- askIfRestoreOrImport (D8-1) --

    /**
     * D8-1: back / scrim must answer like the Cancel button (`null`), not fall through to the
     * "import" branch. The old `AlertDialog`'s `setOnCancelListener { it.resume(false) }` aliased
     * back/scrim onto the SAME value as the Import button, so a user who backed out of the question
     * silently got an import instead of no-op.
     */
    @Test
    fun restoreOrImportBackIsCancelNotImport() = runOnTestMain {
        val activity = activity()
        val file = File(activity.filesDir, "backup.zip")
        val answer = async {
            BackupControl.askIfRestoreOrImport(SyncableDatabaseDefinition.WORKSPACES, file, activity)
        }
        advanceUntilIdle()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }

    @Test
    fun restoreOrImportSelectingRestoreReturnsTrue() = runOnTestMain {
        val activity = activity()
        val file = File(activity.filesDir, "backup.zip")
        val answer = async {
            BackupControl.askIfRestoreOrImport(SyncableDatabaseDefinition.WORKSPACES, file, activity)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(2, head.options.size)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[0].value))
        assertEquals(true, answer.await())
    }

    @Test
    fun restoreOrImportSelectingImportReturnsFalse() = runOnTestMain {
        val activity = activity()
        val file = File(activity.filesDir, "backup.zip")
        val answer = async {
            BackupControl.askIfRestoreOrImport(SyncableDatabaseDefinition.WORKSPACES, file, activity)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[1].value))
        assertEquals(false, answer.await())
    }

    // -- saveOrShare's classicDestinationPrompt fallback (Task 24 Step 2) --

    /**
     * [BackupControl.classicDestinationPrompt] doesn't hop via `withContext(Dispatchers.Main)`
     * (unlike [BackupControl.askIfRestoreOrImport]), so plain `runTest` + `advanceUntilIdle()` is
     * enough here -- no `runOnTestMain`/`Dispatchers.setMain` needed.
     */
    @Test
    fun classicDestinationPromptSelectingShareReturnsShare() = runTest {
        val activity = activity()
        val answer = async {
            BackupControl.classicDestinationPrompt(activity, R.string.backup_backup_title, R.string.backup_backup_message)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertTrue(head.asActionSheet)
        assertEquals(2, head.options.size)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[0].value))
        assertEquals(SaveOrShare.SHARE, answer.await())
    }

    @Test
    fun classicDestinationPromptSelectingPhoneStorageReturnsSave() = runTest {
        val activity = activity()
        val answer = async {
            BackupControl.classicDestinationPrompt(activity, R.string.backup_backup_title, R.string.backup_backup_message)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[1].value))
        assertEquals(SaveOrShare.SAVE, answer.await())
    }

    @Test
    fun classicDestinationPromptDismissReturnsNull() = runTest {
        val activity = activity()
        val answer = async {
            BackupControl.classicDestinationPrompt(activity, R.string.backup_backup_title, R.string.backup_backup_message)
        }
        advanceUntilIdle()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }
}
