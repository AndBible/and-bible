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
package net.bible.sharedlogic.platform.ios

import kotlinx.coroutines.runBlocking
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.DatabaseHostSupport
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.IosAppEnvironment
import net.bible.sharedcore.settings.store.SettingsStore
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.error.InstanceCreationException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * The iOS [DatabaseHost]: database files live in the environment's `databases` directory (or [dbRoot], for tests),
 * everything else comes from [env]. There are no legacy monolithic migrations and no old settings keys to migrate on iOS.
 */
class IosDatabaseHost(
    private val env: IosAppEnvironment,
    private val dbRoot: String? = null,
) : DatabaseHost {
    private val files = FileSystem.SYSTEM

    // env.databasePath() creates the databases directory, which databaseList() relies on.
    private val directory: String
        get() = dbRoot?.also { files.createDirectories(it.toPath()) } ?: env.databasePath("x").substringBeforeLast('/')

    override val isRunningTests: Boolean get() = false
    override fun databasePath(name: String): String = "$directory/$name"

    override fun deleteDatabase(name: String): Boolean {
        val path = databasePath(name).toPath()
        var removed = false
        listOf(path, "$path-wal".toPath(), "$path-shm".toPath()).forEach { file ->
            if (files.exists(file)) {
                try {
                    files.delete(file)
                    if (file == path) removed = true
                } catch (e: Exception) {
                    Log.w(TAG, "Database removal failed: $file", e)
                    return false
                }
            }
        }
        return removed
    }

    override fun databaseList(): List<String> = files.list(directory.toPath()).map { it.name }

    override val dbBackupPath: String get() = env.dbBackupPath
    override val applicationVersionNumber: Int get() = env.versionNumber.toInt()
    override val deviceIdentifier: String get() = env.deviceIdentifier

    override fun formatTimestamp(epochMs: Long, pattern: String, deviceLocale: Boolean): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = pattern
            locale = if (deviceLocale) NSLocale.currentLocale else NSLocale("en_US_POSIX")
        }
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
    }

    override fun migrateOldSettingsKeys() = Unit

    /**
     * The pre-upgrade safety backup. `BackupControl` is resolved from Koin, and phase 1 does not bind every port it
     * needs (Stream D's sync ports), so it may be unresolvable; only THAT (a Koin resolution failure) skips the
     * backup, with a warning (a fresh install has nothing to back up). A failure of `zipDatabaseFiles()` itself is
     * NOT swallowed: a migration must not run without its safety backup (the container's own rule on Android), so
     * the I/O error propagates and `openDatabases` reports it.
     */
    override fun zipDatabaseFilesForUpgradeBackup(): String? {
        val backup = try {
            KoinPlatform.getKoinOrNull()?.getOrNull<BackupControl>()
        } catch (e: NoDefinitionFoundException) {
            Log.w(TAG, "Upgrade backup skipped: BackupControl is not available", e)
            null
        } catch (e: InstanceCreationException) {
            Log.w(TAG, "Upgrade backup skipped: BackupControl cannot be created", e)
            null
        }
        return backup?.zipDatabaseFiles()?.toString()
    }

    override val databaseBackupSuffix: String get() = DATABASE_BACKUP_SUFFIX
    override val legacyMonolithicMigrations: Array<out androidx.room3.migration.Migration> get() = emptyArray()

    override suspend fun awaitWorkspacePersisted() = DatabaseHostSupport.awaitWorkspacePersisted()

    override fun flushSettingsBeforeExit(store: SettingsStore?, timeoutMs: Long?) {
        runBlocking { DatabaseHostSupport.flushOrderedWrites(store, timeoutMs) } // L1-edge: DatabaseHost sync contract, off the main thread (C2)
    }

    override fun openContainer(): DatabaseContainer = runBlocking { DatabaseContainer.create() } // L1-edge: DatabaseHost sync contract, called off the main thread (C2)

    private companion object { const val TAG = "IosDatabaseHost" }
}
