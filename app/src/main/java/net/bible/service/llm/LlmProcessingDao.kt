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

package net.bible.service.llm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LlmProcessingDao {
    @Query("""
        SELECT * FROM LlmProcessingCacheEntry
        WHERE documentInitials = :documentInitials
        AND keyName = :keyName
        AND processingType = :processingType
        AND processingParams = :processingParams
        AND modelId = :modelId
    """)
    fun get(
        documentInitials: String,
        keyName: String,
        processingType: String,
        processingParams: String,
        modelId: String
    ): LlmProcessingCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: LlmProcessingCacheEntry)

    @Query("SELECT COUNT(*) FROM LlmProcessingCacheEntry")
    fun count(): Int

    @Query("SELECT COUNT(*) FROM LlmProcessingCacheEntry WHERE processingType = :processingType")
    fun countByType(processingType: String): Int

    @Query("DELETE FROM LlmProcessingCacheEntry")
    fun deleteAll()

    @Query("DELETE FROM LlmProcessingCacheEntry WHERE modelId = :modelId")
    fun deleteByModel(modelId: String)

    @Query("DELETE FROM LlmProcessingCacheEntry WHERE processingType = :processingType")
    fun deleteByType(processingType: String)

    @Query("DELETE FROM LlmProcessingCacheEntry WHERE documentInitials = :documentInitials")
    fun deleteByDocument(documentInitials: String)

    @Query("""
        SELECT documentInitials, keyName, processingType, processingParams,
               modelId, createdAt, languageCode, LENGTH(processedXml) as xmlSize
        FROM LlmProcessingCacheEntry
        ORDER BY createdAt DESC
    """)
    fun getAllSummaries(): List<CacheEntrySummary>

    @Query("SELECT DISTINCT documentInitials FROM LlmProcessingCacheEntry ORDER BY documentInitials")
    fun getDistinctDocuments(): List<String>

    @Query("SELECT DISTINCT processingType FROM LlmProcessingCacheEntry ORDER BY processingType")
    fun getDistinctProcessingTypes(): List<String>

    @Query("SELECT DISTINCT modelId FROM LlmProcessingCacheEntry ORDER BY modelId")
    fun getDistinctModels(): List<String>

    @Query("DELETE FROM LlmProcessingCacheEntry WHERE createdAt >= :fromTimestamp AND createdAt <= :toTimestamp")
    fun deleteByDateRange(fromTimestamp: Long, toTimestamp: Long)

    @Query("""
        DELETE FROM LlmProcessingCacheEntry
        WHERE documentInitials = :documentInitials
        AND keyName = :keyName
        AND processingType = :processingType
        AND processingParams = :processingParams
    """)
    fun deleteEntry(documentInitials: String, keyName: String, processingType: String, processingParams: String)

    @Query("SELECT COUNT(*) as entryCount, COALESCE(SUM(LENGTH(processedXml)), 0) as totalSize FROM LlmProcessingCacheEntry")
    fun getCacheStats(): CacheStats
}
