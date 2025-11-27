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

package net.bible.android.database.migrations

// Migration from hash-based cache to document+key based cache
// Since the schema changes significantly (different primary key structure),
// we drop and recreate the table, losing any cached translations.
private val migrateToDocumentKeyCache = makeMigration(1..2) { db ->
    db.execSQL("DROP TABLE IF EXISTS TranslationCacheEntry")
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS TranslationCacheEntry (
            documentInitials TEXT NOT NULL,
            keyName TEXT NOT NULL,
            targetLanguage TEXT NOT NULL,
            modelId TEXT NOT NULL,
            translatedXml TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            lastAccessedAt INTEGER NOT NULL,
            PRIMARY KEY (documentInitials, keyName, targetLanguage, modelId)
        )
    """)
    db.execSQL("CREATE INDEX IF NOT EXISTS index_TranslationCacheEntry_targetLanguage ON TranslationCacheEntry (targetLanguage)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_TranslationCacheEntry_createdAt ON TranslationCacheEntry (createdAt)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_TranslationCacheEntry_lastAccessedAt ON TranslationCacheEntry (lastAccessedAt)")
}

val translationMigrations: Array<Migration> = arrayOf(
    migrateToDocumentKeyCache,
)
