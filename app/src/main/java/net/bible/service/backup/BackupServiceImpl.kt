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


import kotlinx.coroutines.IO
import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.report.LAST_CRASH_STACKTRACE_FILE
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.sharedcore.backup.BackupFileRow
import net.bible.sharedcore.backup.BackupService
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.backup.CrashInfo
import net.bible.sharedcore.backup.ResetDbRow
import net.bible.sharedcore.backup.ToggleKind
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.DateTimeFormats
import org.koin.mp.KoinPlatform

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
 * Host-scoped backup screen service over the application-scoped engine and portable seams.
 * Runs every checked toggle in app/database/documents order, preserving the screen's independent switches.
 */
class BackupServiceImpl(
    private val host: net.bible.android.control.backup.BackupHost,
    private val backupControl: BackupControl,
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val env: net.bible.sharedcore.platform.AppEnvironment,
    private val strings: net.bible.sharedcore.platform.CoreStrings,
    private val dirs: AppDirectories = KoinPlatform.getKoin().get(),
    private val dateTimeFormats: DateTimeFormats = KoinPlatform.getKoin().get(),
) : BackupService {

    override suspend fun load(): BackupState = withContext(Dispatchers.IO) {
        BackupState(
            toggles = mapOf(
                ToggleKind.BackupApp to settings.getBoolean(KEY_BACKUP_APPLICATION, false),
                ToggleKind.BackupDatabase to settings.getBoolean(KEY_BACKUP_DATABASE, true),
                ToggleKind.BackupDocuments to settings.getBoolean(KEY_BACKUP_DOCUMENTS, false),
                ToggleKind.RestoreDatabase to settings.getBoolean(KEY_RESTORE_DATABASE, true),
                ToggleKind.RestoreDocuments to settings.getBoolean(KEY_RESTORE_DOCUMENTS, false),
            ),
            backupFiles = loadBackupFiles(),
            resettableDbs = resettableDbTable.map { db ->
                ResetDbRow(dbFileName = db.dbFileName, title = strings.databaseTitle(db.dbFileName))
            },
            crash = loadCrash(),
        )
    }

    override fun setToggle(kind: ToggleKind, value: Boolean) {
        // Fix batch 1 section 2.4 (F103): the crash sheet opens this screen before DB init, where the
        // settings setters throw DataBaseNotReady. Classic BackupActivity simply did not save then;
        // BackupController keeps the value as screen state and hands it to backup()/restore().
        if (!env.isInitialized) return
        val key = when (kind) {
            ToggleKind.BackupApp -> KEY_BACKUP_APPLICATION
            ToggleKind.BackupDatabase -> KEY_BACKUP_DATABASE
            ToggleKind.BackupDocuments -> KEY_BACKUP_DOCUMENTS
            ToggleKind.RestoreDatabase -> KEY_RESTORE_DATABASE
            ToggleKind.RestoreDocuments -> KEY_RESTORE_DOCUMENTS
        }
        settings.setBoolean(key, value)
    }

    override suspend fun backup(toggles: Map<ToggleKind, Boolean>) {
        val actions = backupActionsFor(
            backupApp = toggles[ToggleKind.BackupApp] ?: false,
            backupDatabase = toggles[ToggleKind.BackupDatabase] ?: true,
            backupDocuments = toggles[ToggleKind.BackupDocuments] ?: false,
        )
        for (action in actions) {
            when (action) {
                BackupAction.App -> backupControl.backupApp(host)
                BackupAction.Database -> backupControl.startBackupAppDatabase(host)
                BackupAction.Documents -> backupControl.backupModulesViaIntent(host)
            }
        }
    }

    override suspend fun restore(toggles: Map<ToggleKind, Boolean>) {
        val actions = restoreActionsFor(
            restoreDatabase = toggles[ToggleKind.RestoreDatabase] ?: true,
            restoreDocuments = toggles[ToggleKind.RestoreDocuments] ?: false,
        )
        for (action in actions) {
            when (action) {
                RestoreAction.Database -> backupControl.restoreAppDatabaseViaIntent(host)
                RestoreAction.Documents -> host.restoreModules()
            }
        }
    }

    override suspend fun exportFile(token: String) {
        backupControl.saveDbBackupFileViaIntent(host, env.dbBackupPath.toPath() / token)
    }

    override suspend fun restoreFile(token: String) {
        backupControl.restoreFromLocalBackupFile(host, env.dbBackupPath.toPath() / token)
    }

    override suspend fun resetDb(dbFileName: String) {
        val entry = resettableDbTable.find { it.dbFileName == dbFileName } ?: return
        backupControl.resetDatabase(dbFileName, entry.syncCategory)
    }

    /** Classic `BackupActivity.onCreate`'s file-list section, verbatim (sort + parse + detail text). */
    private fun loadBackupFiles(): List<BackupFileRow> {
        val files = FileSystem.SYSTEM.listOrNull(env.dbBackupPath.toPath())?.sortedByDescending { it.name } ?: return emptyList()
        return backupControl.parseBackupFiles(files).map { info ->
            val sizeKb = (FileSystem.SYSTEM.metadataOrNull(info.file)?.size ?: 0L) / 1024
            val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
            val detail = if (info.appVersion != null)
                strings.backupFileInfo(info.appVersion, sizeStr)
            else sizeStr
            BackupFileRow(token = info.file.name, displayDate = info.displayDate, detail = detail)
        }
    }

    /** Classic `BackupActivity.onCreate`'s last-crash panel, verbatim. */
    private fun loadCrash(): CrashInfo? {
        val crashFile = dirs.internalFilesDir / "log" / LAST_CRASH_STACKTRACE_FILE
        val crashTime = env.lastCrashTime
        if (!FileSystem.SYSTEM.exists(crashFile) || crashTime <= 0) return null
        return try {
            val stackTrace = FileSystem.SYSTEM.read(crashFile) { readUtf8() }
            if (stackTrace.isBlank()) return null
            val timeStr = dateTimeFormats.pattern("yyyy-MM-dd HH:mm:ss", crashTime)
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
