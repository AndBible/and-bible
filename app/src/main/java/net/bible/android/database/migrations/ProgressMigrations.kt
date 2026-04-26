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
    // Create ChapterReadHistory table
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS ChapterReadHistory (
            id BLOB NOT NULL PRIMARY KEY,
            kjvBookOrdinal INTEGER NOT NULL,
            chapter INTEGER NOT NULL,
            readAt INTEGER NOT NULL
        )
    """)

    // Create index on (kjvBookOrdinal, chapter)
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ChapterReadHistory_kjvBookOrdinal_chapter` ON ChapterReadHistory (kjvBookOrdinal, chapter)")

    // Migrate existing ChapterReadingRecord rows to ChapterReadHistory (one entry per record)
    db.execSQL("""
        INSERT INTO ChapterReadHistory (id, kjvBookOrdinal, chapter, readAt)
        SELECT randomblob(16), kjvBookOrdinal, chapter, readAt FROM ChapterReadingRecord
    """)

    // Add useReadCountMode setting to GlobalReadingProgressSettings
    db.execSQL("ALTER TABLE GlobalReadingProgressSettings ADD COLUMN useReadCountMode INTEGER NOT NULL DEFAULT 0")
}

val progressMigrations: Array<Migration> = arrayOf(addMemorizationTarget, addGlobalReadingProgressSettings, addMemorizeTypeSettings, addActiveCycle, addScrambleHideUsed, addMemorizeIncludeReference, addChapterReadHistoryTable)
