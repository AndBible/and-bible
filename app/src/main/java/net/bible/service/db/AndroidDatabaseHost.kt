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

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.BibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.service.common.CommonUtils
import net.bible.service.db.oldmigrations.oldMigrations
import net.bible.sharedcore.settings.store.SettingsStore
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DbContainer"

/** The Android [DatabaseHost]: Context file locations, [CommonUtils] and the Koin graph. */
class AndroidDatabaseHost(private val application: BibleApplication) : DatabaseHost {
    override val isRunningTests: Boolean get() = application.isRunningTests
    override fun databasePath(name: String): String = application.getDatabasePath(name).path
    override fun deleteDatabase(name: String): Boolean = application.deleteDatabase(name)
    override fun databaseList(): List<String> = application.databaseList().toList()
    override val dbBackupPath: String get() = CommonUtils.dbBackupPath.path
    override val applicationVersionNumber: Int get() = CommonUtils.applicationVersionNumber
    override val deviceIdentifier: String get() = CommonUtils.deviceIdentifier

    override fun formatTimestamp(epochMs: Long, pattern: String, deviceLocale: Boolean): String =
        SimpleDateFormat(pattern, if (deviceLocale) Locale.getDefault() else Locale.US).format(Date(epochMs))

    override fun migrateOldSettingsKeys() = CommonUtils.migrateOldSettingsKeys()

    override fun zipDatabaseFilesForUpgradeBackup(): String? =
        GlobalContext.get().get<BackupControl>().zipDatabaseFiles()?.path

    override val databaseBackupSuffix: String get() = DATABASE_BACKUP_SUFFIX
    override val legacyMonolithicMigrations get() = oldMigrations

    override suspend fun awaitWorkspacePersisted() {
        val persistence = runCatching {
            org.koin.java.KoinJavaComponent.getKoin().getOrNull<net.bible.android.control.page.window.WorkspacePersistence>()
        }.getOrNull()
        persistence?.awaitPersisted()
    }

    override fun flushSettingsBeforeExit(store: SettingsStore?, timeoutMs: Long?) {
        try {
            blockingAtEdge { // L1-edge: DatabaseContainer synchronous lifecycle (close: flush before exit, also the crash handler)
                if (store != null) {
                    if (timeoutMs == null) store.flush()
                    else if (withTimeoutOrNull(timeoutMs) { store.flush() } == null) Log.w(TAG, "Settings flush timed out")
                }
                // The ordered write-through keys (workspace, global text display, reading progress) queue their
                // writes off the caller's thread; a few ms before exit they would otherwise die with the process.
                val launcher = runCatching {
                    org.koin.java.KoinJavaComponent.getKoin().getOrNull<net.bible.sharedcore.platform.OrderedLauncher>()
                }.getOrNull() ?: return@blockingAtEdge
                val keys = listOf(
                    net.bible.android.control.page.window.WorkspacePersistence.KEY,
                    net.bible.service.common.GlobalTextDisplaySettingsStore.KEY,
                    net.bible.service.common.ReadingProgressSettingsStore.KEY,
                )
                if (withTimeoutOrNull(timeoutMs ?: ORDERED_WRITES_EXIT_WAIT_MS) { keys.forEach { launcher.join(it) } } == null)
                    Log.w(TAG, "Ordered write queues did not drain before exit")
            }
        } catch (e: Throwable) { Log.e(TAG, "Settings flush failed", e) }
    }

    override fun openContainer(): DatabaseContainer =
        blockingAtEdge { DatabaseContainer.create() } // L1-edge: synchronous DatabaseContainer.instance getter (cold open)

    private companion object {
        /** Bound for the ordered store write queues when the caller gave no timeout. */
        const val ORDERED_WRITES_EXIT_WAIT_MS = 5_000L
    }
}
