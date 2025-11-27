/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.translation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationDao {
    @Query("SELECT * FROM TranslationCacheEntry WHERE contentHash = :hash")
    fun getByHash(hash: String): TranslationCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: TranslationCacheEntry)

    @Query("UPDATE TranslationCacheEntry SET lastAccessedAt = :time WHERE contentHash = :hash")
    fun updateLastAccessed(hash: String, time: Long)

    @Query("DELETE FROM TranslationCacheEntry WHERE lastAccessedAt < :threshold")
    fun evictOlderThan(threshold: Long)

    @Query("SELECT COUNT(*) FROM TranslationCacheEntry")
    fun count(): Int

    @Query("DELETE FROM TranslationCacheEntry")
    fun deleteAll()
}
