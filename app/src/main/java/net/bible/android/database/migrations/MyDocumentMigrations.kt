/*
 * Copyright (c) 2024 Tuomas Airaksinen and the AndBible contributors.
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

/**
 * Add cache fields for LLM agent document caching.
 *
 * New fields:
 * - kjvOrdinalStart, kjvOrdinalEnd: KJVA verse ordinals for cross-version cache lookup
 * - contextHash: SHA-256 hash of full context for strict matching
 * - usedWriteTools: Whether the agent used write tools (bookmarks, notes, etc.)
 *
 * New indices for cache lookup:
 * - (sourcePromptId, contextHash) for strict matching
 * - (sourcePromptId, kjvOrdinalStart, kjvOrdinalEnd) for loose matching
 */
private val addCacheFields = makeMigration(1..2) { db ->
    // Add new columns
    db.execSQL("ALTER TABLE `MyDocumentPage` ADD COLUMN `kjvOrdinalStart` INTEGER")
    db.execSQL("ALTER TABLE `MyDocumentPage` ADD COLUMN `kjvOrdinalEnd` INTEGER")
    db.execSQL("ALTER TABLE `MyDocumentPage` ADD COLUMN `contextHash` TEXT")
    db.execSQL("ALTER TABLE `MyDocumentPage` ADD COLUMN `usedWriteTools` INTEGER NOT NULL DEFAULT 0")

    // Add indices for cache lookup
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_MyDocumentPage_sourcePromptId_contextHash` ON `MyDocumentPage` (`sourcePromptId`, `contextHash`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_MyDocumentPage_sourcePromptId_kjvOrdinalStart_kjvOrdinalEnd` ON `MyDocumentPage` (`sourcePromptId`, `kjvOrdinalStart`, `kjvOrdinalEnd`)")

    // Recreate the view with new columns
    // NOTE: SQL must match exactly what Room generates from the @DatabaseView annotation
    db.execSQL("DROP VIEW IF EXISTS `MyDocumentPageWithContent`")
    db.execSQL("""CREATE VIEW `MyDocumentPageWithContent` AS SELECT p.*, c.content
    FROM MyDocumentPage p
    LEFT OUTER JOIN MyDocumentPageContent c ON p.id = c.pageId""")
}

private val addPageLanguageCode = makeMigration(2..3) { db ->
    db.execSQL("ALTER TABLE `MyDocumentPage` ADD COLUMN `languageCode` TEXT DEFAULT NULL")

    // Recreate the view with the new column
    db.execSQL("DROP VIEW IF EXISTS `MyDocumentPageWithContent`")
    db.execSQL("""CREATE VIEW `MyDocumentPageWithContent` AS SELECT p.*, c.content
    FROM MyDocumentPage p
    LEFT OUTER JOIN MyDocumentPageContent c ON p.id = c.pageId""")
}

val myDocumentMigrations: Array<Migration> = arrayOf(
    addCacheFields,
    addPageLanguageCode,
)
