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

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Singleton row of the user's document-sync preferences. Device-local (lives in
 * [DocumentSyncDatabase], never backed up or synced). Fixed primary key so there is exactly one row.
 */
@Entity
data class DocumentSyncPreferences(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val wifiOnly: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoDownload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoUpload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoDelete: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowDownload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowUpload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowDelete: Boolean = true,
    @ColumnInfo(defaultValue = "0") val showRemovedDocuments: Boolean = false,
    val blockList: Set<String> = emptySet(),
) {
    companion object {
        val SINGLETON_ID = IdType.fromString("d0c00000-0000-0000-0000-000000000001")
    }
}

/** Singleton row holding the incremental-listing watermark (max observed cloud meta createdTime). */
@Entity
data class CloudListingState(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0") val watermark: Long = 0L,
) {
    companion object {
        val SINGLETON_ID = IdType.fromString("d0c00000-0000-0000-0000-000000000002")
    }
}

/** This device's last-sync timestamp for one document, keyed by initials. */
@Entity
data class CloudDocumentSyncTimestamp(
    @PrimaryKey val initials: String,
    val timestamp: Long,
)

@Dao
interface DocumentSyncPreferencesDao {
    @Query("SELECT * FROM DocumentSyncPreferences LIMIT 1")
    fun get(): DocumentSyncPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(prefs: DocumentSyncPreferences)

    @Query("DELETE FROM DocumentSyncPreferences")
    fun clear()
}

@Dao
interface CloudListingStateDao {
    @Query("SELECT * FROM CloudListingState LIMIT 1")
    fun get(): CloudListingState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(state: CloudListingState)

    @Query("DELETE FROM CloudListingState")
    fun clear()
}

@Dao
interface CloudDocumentSyncTimestampDao {
    @Query("SELECT timestamp FROM CloudDocumentSyncTimestamp WHERE initials = :initials")
    fun get(initials: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(row: CloudDocumentSyncTimestamp)

    @Query("DELETE FROM CloudDocumentSyncTimestamp")
    fun clear()
}
