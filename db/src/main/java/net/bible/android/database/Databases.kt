/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.migrations.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities

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
    val entityId1: String,
    @ColumnInfo(defaultValue = "") val entityId2: String,
    val type: LogEntryTypes,
    @ColumnInfo(defaultValue = "0") val lastUpdated: Long,
    val sourceDevice: String,
) {
    override fun toString(): String = "$tableName $type $entityId1 $entityId2 ($lastUpdated) (device: $sourceDevice)"
}

@Entity
class SyncConfiguration(
    @PrimaryKey val keyName: String,
    val stringValue: String? = null,
    val longValue: Long? = null,
    val booleanValue: Boolean? = null,
)

@Entity(
    primaryKeys = ["sourceDevice", "patchNumber"],
)
class SyncStatus(
    val sourceDevice: String,
    val patchNumber: Long,
    val sizeBytes: Long,
    val appliedDate: Long,
)

@Dao
interface SyncDao {

    @Query("SELECT patchNumber FROM SyncStatus WHERE sourceDevice = :deviceId ORDER BY patchNumber DESC LIMIT 1")
    fun lastPatchNum(deviceId: String): Long?

    @Query("SELECT COUNT(*) FROM LogEntry WHERE sourceDevice=:deviceId AND lastUpdated > :lastPatchWritten")
    fun countNewLogEntries(lastPatchWritten: Long, deviceId: String): Long

    @Query("SELECT * FROM LogEntry ORDER BY lastUpdated, tableName, type")
    fun allLogEntries(): List<LogEntry>

    @Query("SELECT * FROM LogEntry WHERE tableName=:tableName AND type=:type")
    fun findLogEntries(tableName: String, type: String): List<LogEntry>

    @Query("DELETE FROM LogEntry")
    fun clearLog()

    @Query("SELECT * FROM SyncStatus")
    fun allSyncStatus(): List<SyncStatus>

    @Insert
    fun addStatus(status: SyncStatus): Long

    @Query("SELECT stringValue FROM SyncConfiguration WHERE keyName = :keyName")
    fun getString(keyName: String): String?

    @Query("SELECT longValue FROM SyncConfiguration WHERE keyName = :keyName")
    fun getLong(keyName: String): Long?

    @Query("SELECT booleanVAlue FROM SyncConfiguration WHERE keyName = :keyName")
    fun getBoolean(keyName: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setConfig(config: SyncConfiguration)

    @Query("DELETE FROM SyncConfiguration WHERE keyName = :keyName")
    fun removeConfig(keyName: String)

    fun setConfig(key: String, value: Long) = setConfig(SyncConfiguration(key, longValue = value))
    fun setConfig(key: String, value: String) = setConfig(SyncConfiguration(key, stringValue = value))
    fun setConfig(key: String, value: Boolean) = setConfig(SyncConfiguration(key, booleanValue = value))

    @Query("SELECT * FROM LogEntry WHERE type = 'DELETE'")
    fun allDeletions(): List<LogEntry>

    @Insert
    fun addStatuses(syncStatuses: List<SyncStatus>)

    @Query("SELECT * from SyncStatus WHERE sourceDevice=:name AND patchNumber=:patchNumber")
    fun syncStatus(name: String, patchNumber: Long): SyncStatus?
}


abstract class SyncableRoomDatabase: RoomDatabase() {
    abstract fun syncDao(): SyncDao
}


@Database(
    entities = [
        BookmarkEntities.Bookmark::class,
        BookmarkEntities.Label::class,
        BookmarkEntities.StudyPadTextEntry::class,
        BookmarkEntities.BookmarkToLabel::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = BOOKMARK_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class BookmarkDatabase: SyncableRoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    companion object {
        const val dbFileName = "bookmarks.sqlite3"
    }
}

@Database(
    entities = [
        ReadingPlanEntities.ReadingPlan::class,
        ReadingPlanEntities.ReadingPlanStatus::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = READING_PLAN_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class ReadingPlanDatabase: SyncableRoomDatabase() {
    abstract fun readingPlanDao(): ReadingPlanDao
    companion object {
        const val dbFileName = "readingplans.sqlite3"
    }
}

@Database(
    entities = [
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = WORKSPACE_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class WorkspaceDatabase: SyncableRoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    companion object {
        const val dbFileName = "workspaces.sqlite3"
    }
}

const val TEMPORARY_DATABASE_VERSION = 1

@Database(
    entities = [
        DocumentSearch::class,
    ],
    version = TEMPORARY_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class TemporaryDatabase: RoomDatabase() {
    abstract fun documentSearchDao(): DocumentSearchDao
    companion object {
        const val dbFileName = "temporary.sqlite3"
    }
}

const val REPO_DATABASE_VERSION = 1

@Database(
    entities = [
        CustomRepository::class,
        SwordDocumentInfo::class,
    ],
    version = REPO_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class RepoDatabase: RoomDatabase() {
    abstract fun swordDocumentInfoDao(): SwordDocumentInfoDao
    abstract fun customRepositoryDao(): CustomRepositoryDao
    companion object {
        const val dbFileName = "repositories.sqlite3"
    }
}

const val SETTINGS_DATABASE_VERSION = 1

@Database(
    entities = [
        BooleanSetting::class,
        StringSetting::class,
        LongSetting::class,
        DoubleSetting::class,
    ],
    version = SETTINGS_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class SettingsDatabase: RoomDatabase() {
    abstract fun booleanSettingDao(): BooleanSettingDao
    abstract fun stringSettingDao(): StringSettingDao
    abstract fun longSettingDao(): LongSettingDao
    abstract fun doubleSettingDao(): DoubleSettingDao
    companion object {
        const val dbFileName = "settings.sqlite3"
    }    
}
