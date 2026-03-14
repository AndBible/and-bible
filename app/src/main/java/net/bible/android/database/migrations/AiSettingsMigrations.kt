/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room.migration.Migration

private val addLlmProcessingCacheEntry = makeMigration(1..2) { db ->
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS `LlmProcessingCacheEntry` (
            `documentInitials` TEXT NOT NULL,
            `keyName` TEXT NOT NULL,
            `processingType` TEXT NOT NULL,
            `processingParams` TEXT NOT NULL,
            `modelId` TEXT NOT NULL,
            `processedXml` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `languageCode` TEXT DEFAULT NULL,
            PRIMARY KEY(`documentInitials`, `keyName`, `processingType`, `processingParams`, `modelId`)
        )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmProcessingCacheEntry_processingType` ON `LlmProcessingCacheEntry` (`processingType`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmProcessingCacheEntry_modelId` ON `LlmProcessingCacheEntry` (`modelId`)")
}

val aiSettingsMigrations: Array<Migration> = arrayOf(
    addLlmProcessingCacheEntry,
)
