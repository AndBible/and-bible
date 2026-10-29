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
package net.bible.sharedlogic.platform.desktop

import kotlinx.coroutines.runBlocking
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.DatabaseHostSupport
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.settings.store.SettingsStore
import org.koin.core.error.InstanceCreationException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [DatabaseHost] on the desktop. Databases live in `<data>/databases` (through [env]) unless [dbRoot] overrides the
 * directory. There are no legacy monolithic migrations: the desktop never had the pre-Room databases.
 */
class DesktopDatabaseHost(
    private val env: DesktopAppEnvironment,
    private val dbRoot: Path? = null,
) : DatabaseHost {
    private val directory: Path
        get() = dbRoot?.also { Files.createDirectories(it) } ?: Path.of(env.databasePath("x")).parent

    override val isRunningTests: Boolean get() = false
    override fun databasePath(name: String): String = directory.resolve(name).toString()

    override fun deleteDatabase(name: String): Boolean {
        val path = directory.resolve(name)
        var removed = false
        listOf(path, directory.resolve("$name-wal"), directory.resolve("$name-shm")).forEach { file ->
            try {
                if (Files.deleteIfExists(file) && file == path) removed = true
            } catch (e: Exception) {
                Log.w(TAG, "Database removal failed: $file", e)
                return false
            }
        }
        return removed
    }

    override fun databaseList(): List<String> = Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.toList() }

    override val dbBackupPath: String get() = env.dbBackupPath
    override val applicationVersionNumber: Int get() = env.versionNumber.toInt()
    override val deviceIdentifier: String get() = env.deviceIdentifier

    override fun formatTimestamp(epochMs: Long, pattern: String, deviceLocale: Boolean): String =
        SimpleDateFormat(pattern, if (deviceLocale) Locale.getDefault() else Locale.US).format(Date(epochMs))

    override fun migrateOldSettingsKeys() = Unit

    /**
     * The pre-upgrade safety backup. `BackupControl` is resolved from Koin; only a Koin resolution failure skips the
     * backup (with a warning: a fresh install has nothing to back up). A failure of `zipDatabaseFiles()` itself
     * propagates, because a migration must not run without its safety backup.
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
        runBlocking { DatabaseHostSupport.flushOrderedWrites(store, timeoutMs) } // DatabaseHost sync contract, off the main thread
    }

    override fun openContainer(): DatabaseContainer = runBlocking { DatabaseContainer.create() } // DatabaseHost sync contract, off the main thread

    private companion object { const val TAG = "DesktopDatabaseHost" }
}
