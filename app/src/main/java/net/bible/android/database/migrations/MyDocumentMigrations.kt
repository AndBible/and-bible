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

private val addSourceModelName = makeMigration(1..2) { db ->
    db.execSQL("ALTER TABLE `AiPageCacheEntry` ADD COLUMN `sourceModelName` TEXT DEFAULT NULL")
    // Recreate view to include the new column (SQL must match Room's expected output exactly)
    db.execSQL("DROP VIEW IF EXISTS `AiCachedPageWithContent`")
    db.execSQL("CREATE VIEW `AiCachedPageWithContent` AS SELECT c.pageId, c.sourcePromptId, c.sourceContext, c.kjvOrdinalStart,\n           c.kjvOrdinalEnd, c.contextHash, c.usedWriteTools, c.sourceModelName,\n           p.title, p.pageKey, p.contentType, p.documentId,\n           p.orderNumber, p.createdAt, p.updatedAt, p.languageCode, cnt.content\n    FROM AiPageCacheEntry c\n    INNER JOIN MyDocumentPage p ON c.pageId = p.id\n    LEFT OUTER JOIN MyDocumentPageContent cnt ON p.id = cnt.pageId")
}

private val addOrdinalRangeIndex = makeMigration(2..3) { db ->
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AiPageCacheEntry_kjvOrdinalStart_kjvOrdinalEnd` ON `AiPageCacheEntry` (`kjvOrdinalStart`, `kjvOrdinalEnd`)")
}

private val addSourceBookFields = makeMigration(3..4) { db ->
    db.execSQL("ALTER TABLE `AiPageCacheEntry` ADD COLUMN `sourceBookInitials` TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE `AiPageCacheEntry` ADD COLUMN `sourceBookKey` TEXT DEFAULT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AiPageCacheEntry_sourceBookInitials_sourceBookKey` ON `AiPageCacheEntry` (`sourceBookInitials`, `sourceBookKey`)")
    db.execSQL("DROP VIEW IF EXISTS `AiCachedPageWithContent`")
    db.execSQL("CREATE VIEW `AiCachedPageWithContent` AS SELECT c.pageId, c.sourcePromptId, c.sourceContext, c.kjvOrdinalStart,\n           c.kjvOrdinalEnd, c.contextHash, c.usedWriteTools, c.sourceModelName,\n           c.sourceBookInitials, c.sourceBookKey,\n           p.title, p.pageKey, p.contentType, p.documentId,\n           p.orderNumber, p.createdAt, p.updatedAt, p.languageCode, cnt.content\n    FROM AiPageCacheEntry c\n    INNER JOIN MyDocumentPage p ON c.pageId = p.id\n    LEFT OUTER JOIN MyDocumentPageContent cnt ON p.id = cnt.pageId")
}

val myDocumentMigrations: Array<Migration> = arrayOf(addSourceModelName, addOrdinalRangeIndex, addSourceBookFields)
