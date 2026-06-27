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

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import net.bible.service.cloudsync.documents.DocumentSyncMeta
import net.bible.service.cloudsync.documents.DocumentType

/**
 * A cached snapshot of one cloud document's metadata (mirrors DocumentSyncMeta).
 * Lives in DocumentSyncDatabase — never backed up, never synced. Pure derived data.
 */
@Entity(tableName = "CachedCloudDocument")
data class CachedCloudDocument(
    @PrimaryKey val initials: String,
    val name: String,
    val documentType: String,
    val version: String,
    val size: Long,
    val language: String,
    val category: String,
    val sourceDevice: String,
    val timestamp: Long,
    val cipherKey: String?,
    val deleted: Boolean,
)

@Dao
interface CloudDocumentCacheDao {
    @Query("SELECT * FROM CachedCloudDocument")
    fun all(): List<CachedCloudDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<CachedCloudDocument>)

    @Query("DELETE FROM CachedCloudDocument")
    fun clear()

    /** Drops one cached entry (e.g. an optimistic purge of a removed-document marker). */
    @Query("DELETE FROM CachedCloudDocument WHERE initials = :initials")
    fun deleteByInitials(initials: String)

    /** Marks one cached entry as a tombstone (e.g. an optimistic remove-from-cloud). */
    @Query("UPDATE CachedCloudDocument SET deleted = 1 WHERE initials = :initials")
    fun markDeleted(initials: String)

    @Transaction
    fun replaceAll(items: List<CachedCloudDocument>) {
        clear()
        insertAll(items)
    }
}

fun CachedCloudDocument.toMeta(): DocumentSyncMeta = DocumentSyncMeta(
    initials = initials,
    name = name,
    documentType = DocumentType.valueOf(documentType),
    version = version,
    size = size,
    language = language,
    category = category,
    sourceDevice = sourceDevice,
    timestamp = timestamp,
    cipherKey = cipherKey,
    deleted = deleted,
)
