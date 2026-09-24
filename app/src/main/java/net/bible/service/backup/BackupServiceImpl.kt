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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.databaseTitleResIds
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
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.backup.BackupFileRow
import net.bible.sharedcore.backup.BackupService
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.backup.CrashInfo
import net.bible.sharedcore.backup.ResetDbRow
import net.bible.sharedcore.backup.ToggleKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One classic-`BackupControl` engine call [BackupServiceImpl.backup] can dispatch, in the fixed
 * order [backupActionsFor] returns them. Extracted from [BackupServiceImpl.backup] as a pure,
 * `internal`-visible selection so the toggle→action mapping is unit-testable without invoking the
 * real (`AlertDialog`/SAF-based) [BackupControl] functions -- see `BackupServiceImplTest`.
 */
internal enum class BackupAction { App, Database, Documents }

/** Same idea as [BackupAction], for [BackupServiceImpl.restore] / [restoreActionsFor]. */
internal enum class RestoreAction { Database, Documents }

/**
 * Backs up every CHECKED backup toggle, app then database then documents -- the same order the
 * classic screen lists them in. **This is "back up all checked", not classic's first-match
 * `when`** -- see [BackupServiceImpl] kdoc for why.
 */
internal fun backupActionsFor(backupApp: Boolean, backupDatabase: Boolean, backupDocuments: Boolean): List<BackupAction> =
    buildList {
        if (backupApp) add(BackupAction.App)
        if (backupDatabase) add(BackupAction.Database)
        if (backupDocuments) add(BackupAction.Documents)
    }

/** Restore analogue of [backupActionsFor]: database then documents. */
internal fun restoreActionsFor(restoreDatabase: Boolean, restoreDocuments: Boolean): List<RestoreAction> =
    buildList {
        if (restoreDatabase) add(RestoreAction.Database)
        if (restoreDocuments) add(RestoreAction.Documents)
    }

/**
 * Android impl of [BackupService] -- the `:app`-layer host seam for the Backup destination of
 * [net.bible.android.view.activity.nav.NavHostComposeActivity] (slice 8 C3). Wraps the classic
 * [BackupControl] engine + [CommonUtils.settings] persistence key-for-key with classic
 * `BackupActivity` (`app/.../control/backup/BackupControl.kt`, ~line 1030).
 *
 * Constructed with the host [activity] directly (NOT a Koin singleton): [BackupControl]'s
 * backup/restore/export/reset functions are suspend functions that need the CALLING `ActivityBase`
 * for dialogs and SAF intents, so a single global instance can't serve every host -- same reasoning
 * as `SyncSettingsServiceImpl` (see its kdoc for the precedent).
 *
 * **backup()/restore() dispatch = "run every CHECKED toggle", not classic's first-match `when`.**
 * `:sharedUi`'s `BackupRestoreScreen` (Task 5) already renders the five toggles as independent
 * switches rather than classic's two `RadioGroup`s, so more than one can be checked at once; this
 * is the approved, intentional divergence for that model -- see [backupActionsFor]/[restoreActionsFor].
 */
class BackupServiceImpl(private val activity: ActivityBase) : BackupService {

    override suspend fun load(): BackupState = withContext(Dispatchers.IO) {
        BackupState(
            toggles = mapOf(
                ToggleKind.BackupApp to CommonUtils.settings.getBoolean(KEY_BACKUP_APPLICATION, false),
                ToggleKind.BackupDatabase to CommonUtils.settings.getBoolean(KEY_BACKUP_DATABASE, true),
                ToggleKind.BackupDocuments to CommonUtils.settings.getBoolean(KEY_BACKUP_DOCUMENTS, false),
                ToggleKind.RestoreDatabase to CommonUtils.settings.getBoolean(KEY_RESTORE_DATABASE, true),
                ToggleKind.RestoreDocuments to CommonUtils.settings.getBoolean(KEY_RESTORE_DOCUMENTS, false),
            ),
            backupFiles = loadBackupFiles(),
            resettableDbs = resettableDbTable.map { db ->
                ResetDbRow(dbFileName = db.dbFileName, title = activity.getString(databaseTitleResIds.getValue(db.dbFileName)))
            },
            crash = loadCrash(),
        )
    }

    override fun setToggle(kind: ToggleKind, value: Boolean) {
        val key = when (kind) {
            ToggleKind.BackupApp -> KEY_BACKUP_APPLICATION
            ToggleKind.BackupDatabase -> KEY_BACKUP_DATABASE
            ToggleKind.BackupDocuments -> KEY_BACKUP_DOCUMENTS
            ToggleKind.RestoreDatabase -> KEY_RESTORE_DATABASE
            ToggleKind.RestoreDocuments -> KEY_RESTORE_DOCUMENTS
        }
        CommonUtils.settings.setBoolean(key, value)
    }

    override suspend fun backup() {
        val actions = backupActionsFor(
            backupApp = CommonUtils.settings.getBoolean(KEY_BACKUP_APPLICATION, false),
            backupDatabase = CommonUtils.settings.getBoolean(KEY_BACKUP_DATABASE, true),
            backupDocuments = CommonUtils.settings.getBoolean(KEY_BACKUP_DOCUMENTS, false),
        )
        for (action in actions) {
            when (action) {
                BackupAction.App -> BackupControl.backupApp(activity)
                BackupAction.Database -> BackupControl.startBackupAppDatabase(activity)
                BackupAction.Documents -> BackupControl.backupModulesViaIntent(activity)
            }
        }
    }

    override suspend fun restore() {
        val actions = restoreActionsFor(
            restoreDatabase = CommonUtils.settings.getBoolean(KEY_RESTORE_DATABASE, true),
            restoreDocuments = CommonUtils.settings.getBoolean(KEY_RESTORE_DOCUMENTS, false),
        )
        for (action in actions) {
            when (action) {
                RestoreAction.Database -> BackupControl.restoreAppDatabaseViaIntent(activity)
                RestoreAction.Documents -> BackupControl.restoreModulesViaIntent(activity)
            }
        }
    }

    override suspend fun exportFile(token: String) {
        BackupControl.saveDbBackupFileViaIntent(activity, File(CommonUtils.dbBackupPath, token))
    }

    override suspend fun restoreFile(token: String) {
        BackupControl.restoreFromLocalBackupFile(activity, File(CommonUtils.dbBackupPath, token))
    }

    override suspend fun resetDb(dbFileName: String) {
        val entry = resettableDbTable.find { it.dbFileName == dbFileName } ?: return
        BackupControl.resetDatabase(activity, dbFileName, databaseTitleResIds.getValue(dbFileName), entry.syncCategory)
    }

    /** Classic `BackupActivity.onCreate`'s file-list section, verbatim (sort + parse + detail text). */
    private fun loadBackupFiles(): List<BackupFileRow> {
        val files = CommonUtils.dbBackupPath.listFiles()?.sortedByDescending { it.name } ?: return emptyList()
        return BackupControl.parseBackupFiles(files).map { info ->
            val sizeKb = info.file.length() / 1024
            val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
            val detail = if (info.appVersion != null)
                activity.getString(R.string.backup_file_info, info.appVersion, sizeStr)
            else sizeStr
            BackupFileRow(token = info.file.name, displayDate = info.displayDate, detail = detail)
        }
    }

    /** Classic `BackupActivity.onCreate`'s last-crash panel, verbatim. */
    private fun loadCrash(): CrashInfo? {
        val crashFile = File(SharedConstants.internalFilesDir, "log/$LAST_CRASH_STACKTRACE_FILE")
        val crashTime = CommonUtils.realSharedPreferences.getLong(KEY_APP_CRASHED_TIME, 0L)
        if (!crashFile.exists() || crashTime <= 0) return null
        return try {
            val stackTrace = crashFile.readText()
            if (stackTrace.isBlank()) return null
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(crashTime))
            CrashInfo(time = timeStr, text = stackTrace)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading crash info", e)
            null
        }
    }

    private data class ResettableDb(val dbFileName: String, val syncCategory: SyncableDatabaseDefinition?)

    companion object {
        private const val TAG = "BackupServiceImpl"

        private const val KEY_BACKUP_APPLICATION = "backup_application"
        private const val KEY_BACKUP_DATABASE = "backup_database"
        private const val KEY_BACKUP_DOCUMENTS = "backup_documents"
        private const val KEY_RESTORE_DATABASE = "restore_database"
        private const val KEY_RESTORE_DOCUMENTS = "restore_documents"
        private const val KEY_APP_CRASHED_TIME = "app-crashed-time"

        // Same 8 entries, same order, as classic BackupActivity.onCreate's local `resettableDbs` list.
        private val resettableDbTable = listOf(
            ResettableDb(BookmarkDatabase.dbFileName, SyncableDatabaseDefinition.BOOKMARKS),
            ResettableDb(WorkspaceDatabase.dbFileName, SyncableDatabaseDefinition.WORKSPACES),
            ResettableDb(ReadingPlanDatabase.dbFileName, SyncableDatabaseDefinition.READINGPLANS),
            ResettableDb(RepoDatabase.dbFileName, null),
            ResettableDb(SettingsDatabase.dbFileName, null),
            ResettableDb(MyDocumentDatabase.dbFileName, SyncableDatabaseDefinition.MYDOCUMENTS),
            ResettableDb(AiSettingsDatabase.dbFileName, SyncableDatabaseDefinition.AI_SETTINGS),
            ResettableDb(ProgressDatabase.dbFileName, SyncableDatabaseDefinition.PROGRESS),
        )
    }
}
