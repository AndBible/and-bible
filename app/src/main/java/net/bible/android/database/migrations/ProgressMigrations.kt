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

private val addMemorizationTarget = makeMigration(1..2) { db ->
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS MemorizationTarget (
            id BLOB NOT NULL PRIMARY KEY,
            kjvOrdinalStart INTEGER NOT NULL,
            kjvOrdinalEnd INTEGER NOT NULL,
            createdAt INTEGER NOT NULL
        )
    """)
}

private val addGlobalReadingProgressSettings = makeMigration(2..3) { db ->
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS GlobalReadingProgressSettings (
            id BLOB NOT NULL PRIMARY KEY,
            autoTrackReading INTEGER NOT NULL DEFAULT 0,
            autoMarkMemorized INTEGER NOT NULL DEFAULT 1
        )
    """)
}

private val addMemorizeTypeSettings = makeMigration(3..4) { db ->
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN memorizeTypeFullWords INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN memorizeWordVisibility TEXT NOT NULL DEFAULT 'light'")
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN memorizeErrorHeatmap INTEGER NOT NULL DEFAULT 1")
}

private val addActiveCycle = makeMigration(4..5) { db ->
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN activeCycle INTEGER NOT NULL DEFAULT 0")
}

private val addScrambleHideUsed = makeMigration(5..6) { db ->
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN memorizeScrambleHideUsed INTEGER NOT NULL DEFAULT 0")
}

private val addMemorizeIncludeReference = makeMigration(6..7) { db ->
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN memorizeIncludeReference INTEGER NOT NULL DEFAULT 0")
}

private val addChapterReadHistoryTable = makeMigration(7..8) { db ->
    // Create final ChapterReadHistory schema in one shot
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS ChapterReadHistory (
            id BLOB NOT NULL PRIMARY KEY,
            kjvBookOrdinal INTEGER NOT NULL,
            chapter INTEGER NOT NULL,
            cycle INTEGER NOT NULL DEFAULT 1,
            readAt INTEGER NOT NULL,
            bookInitials TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT 'MANUAL'
        )
    """)
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ChapterReadHistory_kjvBookOrdinal_chapter_cycle` ON ChapterReadHistory (kjvBookOrdinal, chapter, cycle)")

    // Migrate existing ChapterReadingRecord rows 1:1 to ChapterReadHistory.
    // bookInitials is unknown for old records (the legacy table never tracked it);
    // source is preserved from the legacy table where it was already present.
    db.execSQL("""
        INSERT INTO ChapterReadHistory (id, kjvBookOrdinal, chapter, cycle, readAt, bookInitials, source)
        SELECT randomblob(16), kjvBookOrdinal, chapter, cycle, readAt, '', source
        FROM ChapterReadingRecord
    """)

    // Drop the legacy table — ChapterReadHistory is now the single source of truth.
    db.execSQL("DROP TABLE IF EXISTS ChapterReadingRecord")
}

// Flip the column DEFAULT for memorizeIncludeReference from 0 to 1 so that fresh installs
// (and any future inserts that omit the column) get verse-reference memorisation enabled
// by default. Existing rows are preserved as-is — users who already have a stored value
// keep it. SQLite has no ALTER COLUMN SET DEFAULT, so the table is recreated.
private val flipMemorizeIncludeReferenceDefault = makeMigration(8..9) { db ->
    db.execSQL("""
        CREATE TABLE GlobalReadingProgressSettings_new (
            id BLOB NOT NULL PRIMARY KEY,
            autoTrackReading INTEGER NOT NULL DEFAULT 0,
            autoMarkMemorized INTEGER NOT NULL DEFAULT 1,
            memorizeTypeFullWords INTEGER NOT NULL DEFAULT 0,
            memorizeWordVisibility TEXT NOT NULL DEFAULT 'light',
            memorizeErrorHeatmap INTEGER NOT NULL DEFAULT 1,
            memorizeScrambleHideUsed INTEGER NOT NULL DEFAULT 0,
            memorizeIncludeReference INTEGER NOT NULL DEFAULT 1,
            activeCycle INTEGER NOT NULL DEFAULT 0
        )
    """)
    db.execSQL("""
        INSERT INTO GlobalReadingProgressSettings_new (
            id, autoTrackReading, autoMarkMemorized, memorizeTypeFullWords,
            memorizeWordVisibility, memorizeErrorHeatmap, memorizeScrambleHideUsed,
            memorizeIncludeReference, activeCycle
        )
        SELECT id, autoTrackReading, autoMarkMemorized, memorizeTypeFullWords,
               memorizeWordVisibility, memorizeErrorHeatmap, memorizeScrambleHideUsed,
               memorizeIncludeReference, activeCycle
        FROM GlobalReadingProgressSettings
    """)
    db.execSQL("DROP TABLE GlobalReadingProgressSettings")
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings_new RENAME TO GlobalReadingProgressSettings")
}

val progressMigrations: Array<Migration> = arrayOf(
    addMemorizationTarget,
    addGlobalReadingProgressSettings,
    addMemorizeTypeSettings,
    addActiveCycle,
    addScrambleHideUsed,
    addMemorizeIncludeReference,
    addChapterReadHistoryTable,
    flipMemorizeIncludeReferenceDefault,
)
