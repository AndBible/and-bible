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

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

enum class LogEntryTypes {
    UPSERT,
    DELETE
}

@Entity(
    primaryKeys = ["tableName", "entityId1", "entityId2"],
    indices = [Index("lastUpdated"), Index("sourceDevice")]
)
data class LogEntry(
    val tableName: String,
    val entityId1: IdType,
    @ColumnInfo(defaultValue = "") val entityId2: IdType,
    val type: LogEntryTypes,
    @ColumnInfo(defaultValue = "0") val lastUpdated: Long,
    val sourceDevice: String,
) {
    override fun toString(): String = "$tableName $type $entityId1 $entityId2 ($lastUpdated) (device: $sourceDevice)"
}

@Entity
data class SyncConfiguration(
    @PrimaryKey val keyName: String,
    val stringValue: String? = null,
    val longValue: Long? = null,
    val booleanValue: Boolean? = null,
)

@Entity(
    primaryKeys = ["sourceDevice", "patchNumber"],
)
data class SyncStatus(
    val sourceDevice: String,
    val patchNumber: Long,
    val sizeBytes: Long,
    val appliedDate: Long,
)

@Dao
interface SyncDao {
    @Query("SELECT COUNT(*) FROM LogEntry WHERE sourceDevice=:deviceId AND lastUpdated > :lastPatchWritten")
    suspend fun countNewLogEntries(lastPatchWritten: Long, deviceId: String): Long

    @Query("SELECT * FROM LogEntry WHERE lastUpdated > :lastSynchronized AND sourceDevice != :exceptDevice")
    suspend fun newLogEntries(lastSynchronized: Long, exceptDevice: String): List<LogEntry>

    @Query("SELECT * FROM LogEntry ORDER BY lastUpdated, tableName, type")
    suspend fun allLogEntries(): List<LogEntry>

    @Query("SELECT * FROM LogEntry WHERE tableName=:tableName AND type=:type")
    suspend fun findLogEntries(tableName: String, type: String): List<LogEntry>

    @Query("DELETE FROM LogEntry")
    suspend fun clearLog()

    @Query("SELECT patchNumber FROM SyncStatus WHERE sourceDevice=:deviceId ORDER BY patchNumber DESC LIMIT 1")
    suspend fun lastPatchNum(deviceId: String): Long?

    @Query("SELECT * FROM SyncStatus")
    suspend fun allSyncStatus(): List<SyncStatus>

    @Insert
    suspend fun addStatus(status: SyncStatus)

    @Query("DELETE FROM SyncStatus")
    suspend fun clearSyncStatus()

    @Query("SELECT stringValue FROM SyncConfiguration WHERE keyName = :keyName")
    suspend fun getString(keyName: String): String?

    @Query("SELECT longValue FROM SyncConfiguration WHERE keyName = :keyName")
    suspend fun getLong(keyName: String): Long?

    @Query("SELECT booleanVAlue FROM SyncConfiguration WHERE keyName = :keyName")
    suspend fun getBoolean(keyName: String): Boolean?

    @Query("SELECT * FROM SyncConfiguration WHERE keyName = :keyName")
    suspend fun getConfig(keyName: String): SyncConfiguration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: SyncConfiguration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(configs: List<SyncConfiguration>)

    @Query("DELETE FROM SyncConfiguration WHERE keyName = :keyName")
    suspend fun removeConfig(keyName: String)

    @Query("DELETE FROM SyncConfiguration")
    suspend fun clearSyncConfiguration()

    suspend fun setConfig(key: String, value: Long) = setConfig(SyncConfiguration(key, longValue = value))
    suspend fun setConfig(key: String, value: String) = setConfig(SyncConfiguration(key, stringValue = value))
    suspend fun setConfig(key: String, value: Boolean) = setConfig(SyncConfiguration(key, booleanValue = value))

    @Query("SELECT * FROM LogEntry WHERE type = 'DELETE'")
    suspend fun allDeletions(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addStatuses(syncStatuses: List<SyncStatus>)

    @Query("SELECT * from SyncStatus WHERE sourceDevice=:name AND patchNumber=:patchNumber")
    suspend fun syncStatus(name: String, patchNumber: Long): SyncStatus?
    @Query("SELECT SUM(sizeBytes) from SyncStatus")
    suspend fun totalBytesUsed(): Long
}


abstract class SyncableRoomDatabase: RoomDatabase() {
    abstract fun syncDao(): SyncDao
}
