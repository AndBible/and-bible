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

    override fun zipDatabaseFilesForUpgradeBackup(): String? =
        KoinPlatform.getKoinOrNull()?.getOrNull<BackupControl>()?.zipDatabaseFiles()?.toString()

    override val databaseBackupSuffix: String get() = DATABASE_BACKUP_SUFFIX
    override val legacyMonolithicMigrations: Array<out androidx.room3.migration.Migration> get() = emptyArray()

    override suspend fun awaitWorkspacePersisted() = DatabaseHostSupport.awaitWorkspacePersisted()

    override fun flushSettingsBeforeExit(store: SettingsStore?, timeoutMs: Long?) {
        runBlocking { DatabaseHostSupport.flushOrderedWrites(store, timeoutMs) }
    }

    override fun openContainer(): DatabaseContainer = runBlocking { DatabaseContainer.create() }

    private companion object { const val TAG = "IosDatabaseHost" }
}
