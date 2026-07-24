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

package net.bible.sharedcore.backup

/** Kind of backup/restore toggle on the Backup & Restore screen (classic `BackupActivity` checkboxes). */
enum class ToggleKind { BackupApp, BackupDatabase, BackupDocuments, RestoreDatabase, RestoreDocuments }

/**
 * One existing on-device backup file. Addressed by a stable [token] (the backup filename) rather
 * than a `java.io.File` -- `:sharedCore` stays iOS-clean; the `:app`-layer [BackupService]
 * implementation resolves [token] back to a file (Task 6).
 */
data class BackupFileRow(val token: String, val displayDate: String, val detail: String)

/** One resettable database entry (classic `BackupActivity`'s per-db "Reset" button list). */
data class ResetDbRow(val dbFileName: String, val title: String)

/** Last-crash stack trace, shown at the bottom of the screen when present. */
data class CrashInfo(val time: String, val text: String)

/** View-data for the Backup & Restore screen. */
data class BackupState(
    val toggles: Map<ToggleKind, Boolean> = emptyMap(),
    val backupFiles: List<BackupFileRow> = emptyList(),
    val resettableDbs: List<ResetDbRow> = emptyList(),
    val crash: CrashInfo? = null,
)

/**
 * Service seam the host implements (`:app`-layer, Task 6): persisted toggle state (classic
 * `CommonUtils.settings`), the actual backup/restore/export/reset engine (delegating to classic
 * `BackupControl`), and resolving a [BackupFileRow.token] to its on-disk file. `:sharedCore` only
 * depends on this interface -- see [BackupController].
 */
interface BackupService {
    /** Loads the current toggle values, backup-file list, resettable-db list and crash info. */
    suspend fun load(): BackupState

    /** Persists [kind] = [value] (classic `CommonUtils.settings.setBoolean`). */
    fun setToggle(kind: ToggleKind, value: Boolean)

    /** Runs the backup dispatched by whichever [ToggleKind] backup toggle is currently on. */
    suspend fun backup()

    /** Runs the restore dispatched by whichever [ToggleKind] restore toggle is currently on. */
    suspend fun restore()

    /** Exports (save/share) the local backup file addressed by [token]. */
    suspend fun exportFile(token: String)

    /** Restores from the local backup file addressed by [token]. */
    suspend fun restoreFile(token: String)

    /** Deletes and re-creates the database file named [dbFileName]. */
    suspend fun resetDb(dbFileName: String)
}
