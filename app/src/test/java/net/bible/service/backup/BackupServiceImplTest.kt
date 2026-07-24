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
package net.bible.service.backup

import kotlinx.coroutines.runBlocking
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.report.LAST_CRASH_STACKTRACE_FILE
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.backup.ToggleKind
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies [BackupServiceImpl]'s mapping from disk/[CommonUtils.settings] state to [net.bible.sharedcore.backup.BackupState]
 * (file-row / reset-row / crash mapping, toggle persistence), plus the pure toggle→action dispatch
 * selection ([backupActionsFor]/[restoreActionsFor]) that [BackupServiceImpl.backup]/[BackupServiceImpl.restore]
 * use internally.
 *
 * [BackupServiceImpl.backup]/[restore]/[exportFile]/[restoreFile]/[resetDb] themselves are NOT
 * exercised here: they delegate to classic `BackupControl`'s suspend engine functions, which pop
 * `AlertDialog`s and launch SAF `Intent`s via `activity.awaitIntent` -- there is no DI seam to
 * substitute a fake for `BackupControl` (it's a top-level `object`, not an injected dependency), and
 * driving the real dialogs/intents under Robolectric would be a brittle, UI-flow-shaped test rather
 * than a unit test. So the DISPATCH SELECTION logic (which toggles cause which named action, and in
 * what order) is exercised directly via the pure, `internal` [backupActionsFor]/[restoreActionsFor]
 * helpers [BackupServiceImpl.backup]/[restore] call -- same production code, no re-implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupServiceImplTest {

    /** Bare host stand-in: [BackupServiceImpl] only needs a real [ActivityBase] for `getString` in
     *  these tests (no dialog/SAF call is exercised) -- same minimal-probe pattern as
     *  `net.bible.android.view.compose.ComposeHostActionBarTest`. */
    class TestHostActivity : ActivityBase() {
        override val doNotInitializeApp = true
    }

    private lateinit var activity: TestHostActivity
    private lateinit var service: BackupServiceImpl

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(TestHostActivity::class.java).create().get()
        service = BackupServiceImpl(activity)
        CommonUtils.dbBackupPath.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        CommonUtils.dbBackupPath.listFiles()?.forEach { it.delete() }
        listOf("backup_application", "backup_database", "backup_documents", "restore_database", "restore_documents")
            .forEach { CommonUtils.settings.removeBoolean(it) }
        CommonUtils.realSharedPreferences.edit().remove("app-crashed-time").apply()
        File(SharedConstants.internalFilesDir, "log/$LAST_CRASH_STACKTRACE_FILE").delete()
        DatabaseResetter.resetDatabase()
    }

    // --- load(): toggles ---

    @Test fun load_defaultToggles_matchClassicDefaults() = runBlocking {
        val state = service.load()
        assertEquals(false, state.toggles[ToggleKind.BackupApp])
        assertEquals(true, state.toggles[ToggleKind.BackupDatabase])
        assertEquals(false, state.toggles[ToggleKind.BackupDocuments])
        assertEquals(true, state.toggles[ToggleKind.RestoreDatabase])
        assertEquals(false, state.toggles[ToggleKind.RestoreDocuments])
    }

    @Test fun setToggle_persistsToClassicSettingsKey_andIsReflectedOnNextLoad() = runBlocking {
        service.setToggle(ToggleKind.BackupApp, true)
        assertTrue(CommonUtils.settings.getBoolean("backup_application", false))
        service.setToggle(ToggleKind.RestoreDocuments, true)
        assertTrue(CommonUtils.settings.getBoolean("restore_documents", false))

        val state = service.load()
        assertEquals(true, state.toggles[ToggleKind.BackupApp])
        assertEquals(true, state.toggles[ToggleKind.RestoreDocuments])
    }

    // --- load(): resettableDbs ---

    @Test fun load_resettableDbs_allEightInClassicOrderWithNonBlankTitles() = runBlocking {
        val state = service.load()
        val expectedOrder = listOf(
            BookmarkDatabase.dbFileName,
            WorkspaceDatabase.dbFileName,
            ReadingPlanDatabase.dbFileName,
            RepoDatabase.dbFileName,
            SettingsDatabase.dbFileName,
            MyDocumentDatabase.dbFileName,
            AiSettingsDatabase.dbFileName,
            ProgressDatabase.dbFileName,
        )
        assertEquals(expectedOrder, state.resettableDbs.map { it.dbFileName })
        assertTrue(state.resettableDbs.all { it.title.isNotBlank() })
    }

    // --- load(): backupFiles ---

    @Test fun load_noBackupFiles_emptyList() = runBlocking {
        assertTrue(service.load().backupFiles.isEmpty())
    }

    @Test fun load_parsesBackupFileName_intoDisplayDateAndDetail() = runBlocking {
        val file = File(CommonUtils.dbBackupPath, "dbBackup-123-20260724-080000.abdb.zip")
        file.writeBytes(ByteArray(2 * 1024 * 1024)) // 2 MB -- forces the "N MB" branch

        val rows = service.load().backupFiles
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals("dbBackup-123-20260724-080000.abdb.zip", row.token)
        assertEquals("2026-07-24 08:00:00", row.displayDate)
        assertTrue("detail should read 'App v123, 2 MB': ${row.detail}", row.detail.contains("123") && row.detail.contains("MB"))
    }

    @Test fun load_unrecognizedFileName_fallsBackToNameAndSizeOnlyDetail() = runBlocking {
        val file = File(CommonUtils.dbBackupPath, "not-a-recognized-name.zip")
        file.writeBytes(ByteArray(10))

        val rows = service.load().backupFiles
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals("not-a-recognized-name.zip", row.token)
        assertEquals("not-a-recognized-name.zip", row.displayDate)
        assertTrue(row.detail.endsWith("KB"))
    }

    @Test fun load_backupFiles_sortedDescendingByName() = runBlocking {
        File(CommonUtils.dbBackupPath, "dbBackup-1-20260101-000000.abdb.zip").writeBytes(ByteArray(1))
        File(CommonUtils.dbBackupPath, "dbBackup-1-20260701-000000.abdb.zip").writeBytes(ByteArray(1))

        val rows = service.load().backupFiles
        assertEquals(
            listOf("dbBackup-1-20260701-000000.abdb.zip", "dbBackup-1-20260101-000000.abdb.zip"),
            rows.map { it.token },
        )
    }

    // --- load(): crash ---

    @Test fun load_noCrashFile_crashIsNull() = runBlocking {
        assertNull(service.load().crash)
    }

    @Test fun load_crashTimeZero_crashIsNullEvenIfFileExists() = runBlocking {
        val logDir = File(SharedConstants.internalFilesDir, "log").apply { mkdirs() }
        File(logDir, LAST_CRASH_STACKTRACE_FILE).writeText("boom")
        // app-crashed-time left at 0 (default) -- classic guards on crashTime > 0 too.
        assertNull(service.load().crash)
    }

    @Test fun load_crashPresent_populatesCrashInfo() = runBlocking {
        val logDir = File(SharedConstants.internalFilesDir, "log").apply { mkdirs() }
        File(logDir, LAST_CRASH_STACKTRACE_FILE).writeText("java.lang.RuntimeException: boom")
        CommonUtils.realSharedPreferences.edit().putLong("app-crashed-time", 1_000_000L).apply()

        val crash = service.load().crash
        assertTrue(crash != null)
        assertTrue(crash!!.text.contains("boom"))
        assertTrue(crash.time.isNotBlank())
    }

    @Test fun load_blankCrashFile_crashIsNull() = runBlocking {
        val logDir = File(SharedConstants.internalFilesDir, "log").apply { mkdirs() }
        File(logDir, LAST_CRASH_STACKTRACE_FILE).writeText("   \n  ")
        CommonUtils.realSharedPreferences.edit().putLong("app-crashed-time", 1_000_000L).apply()
        assertNull(service.load().crash)
    }

    // --- backup()/restore() dispatch selection (pure helpers; see class kdoc) ---

    @Test fun backupActionsFor_none_whenNothingChecked() {
        assertEquals(emptyList<BackupAction>(), backupActionsFor(backupApp = false, backupDatabase = false, backupDocuments = false))
    }

    @Test fun backupActionsFor_eachTogglesItsOwnAction() {
        assertEquals(listOf(BackupAction.App), backupActionsFor(backupApp = true, backupDatabase = false, backupDocuments = false))
        assertEquals(listOf(BackupAction.Database), backupActionsFor(backupApp = false, backupDatabase = true, backupDocuments = false))
        assertEquals(listOf(BackupAction.Documents), backupActionsFor(backupApp = false, backupDatabase = false, backupDocuments = true))
    }

    @Test fun backupActionsFor_allChecked_firesAllInClassicOrder() {
        assertEquals(
            listOf(BackupAction.App, BackupAction.Database, BackupAction.Documents),
            backupActionsFor(backupApp = true, backupDatabase = true, backupDocuments = true),
        )
    }

    @Test fun restoreActionsFor_none_whenNothingChecked() {
        assertEquals(emptyList<RestoreAction>(), restoreActionsFor(restoreDatabase = false, restoreDocuments = false))
    }

    @Test fun restoreActionsFor_allChecked_firesBothInClassicOrder() {
        assertEquals(
            listOf(RestoreAction.Database, RestoreAction.Documents),
            restoreActionsFor(restoreDatabase = true, restoreDocuments = true),
        )
    }
}
