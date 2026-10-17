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

package net.bible.service.db

/**
 * Everything [DatabaseContainer] and the database file helpers need from the platform: file locations, backup
 * helpers, app/device identity, timestamp formatting and the workspace/settings write queues. Installed once at
 * startup with [DatabaseContainer.installHost].
 */
interface DatabaseHost {
    val isRunningTests: Boolean

    /** Absolute path of a file named [name] in the database directory. */
    fun databasePath(name: String): String
    fun deleteDatabase(name: String): Boolean
    fun databaseList(): List<String>

    /** Backup directory; it exists after the read. */
    val dbBackupPath: String
    val applicationVersionNumber: Int
    val deviceIdentifier: String

    /** [epochMs] formatted with [pattern] in the device locale or, when [deviceLocale] is false, `Locale.US`. */
    fun formatTimestamp(epochMs: Long, pattern: String, deviceLocale: Boolean): String
    fun migrateOldSettingsKeys()

    /** Path of a zip of the database files for the upgrade backup, null when there is none. */
    fun zipDatabaseFilesForUpgradeBackup(): String?
    val databaseBackupSuffix: String
    val legacyMonolithicMigrations: Array<out androidx.room3.migration.Migration>

    /** Waits for queued workspace writes to reach the database. Lookup failures are swallowed. */
    suspend fun awaitWorkspacePersisted()

    /** Flushes [store] and the ordered write queues; never throws. */
    fun flushSettingsBeforeExit(store: net.bible.sharedcore.settings.store.SettingsStore?, timeoutMs: Long?)

    /** Synchronous cold open of a new container. */
    fun openContainer(): DatabaseContainer
}
