/*
 * Copyright (c) 2023-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.migrations

import android.util.Log
import androidx.room3.migration.Migration as RoomMigration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import net.bible.service.db.queryLong

abstract class Migration(startVersion: Int, endVersion: Int): RoomMigration(startVersion, endVersion) {
    abstract fun doMigrate(connection: SQLiteConnection)

    /** Room 3's `migrate` is `suspend`; [doMigrate] stays blocking (it only uses the synchronous connection API). */
    override suspend fun migrate(connection: SQLiteConnection) {
        Log.i(TAG, "Migrating from version $startVersion to $endVersion")
        disableSyncTriggers(connection)
        try {
            doMigrate(connection)
        } finally {
            enableSyncTriggers(connection)
        }
    }
}

/**
 * Disable sync triggers during migrations to prevent unnecessary LogEntry rows
 * from data-shuffling operations (e.g. column migrations with UPDATE statements).
 * SyncConfiguration table may not exist yet in early migrations, so we check first.
 */
private fun disableSyncTriggers(connection: SQLiteConnection) {
    if (hasSyncConfigurationTable(connection)) {
        connection.execSQL("INSERT OR REPLACE INTO SyncConfiguration (keyName, booleanValue) VALUES ('triggersDisabled', 1)")
    }
}

private fun enableSyncTriggers(connection: SQLiteConnection) {
    if (hasSyncConfigurationTable(connection)) {
        connection.execSQL("DELETE FROM SyncConfiguration WHERE keyName = 'triggersDisabled'")
    }
}

private fun hasSyncConfigurationTable(connection: SQLiteConnection): Boolean =
    (connection.queryLong("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='SyncConfiguration'") ?: 0L) > 0L

fun makeMigration(versionRange: IntRange, migration: (connection: SQLiteConnection) -> Unit): Migration =
    object: Migration(versionRange.first, versionRange.last) {
        override fun doMigrate(connection: SQLiteConnection) {
            migration.invoke(connection)
        }
    }
