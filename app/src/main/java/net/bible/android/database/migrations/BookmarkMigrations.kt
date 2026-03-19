/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_NAME

private val separateText = makeMigration(1..2) { _db ->
    _db.execSQL("CREATE TABLE IF NOT EXISTS `BookmarkNotes` (`bookmarkId` BLOB NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`bookmarkId`), FOREIGN KEY(`bookmarkId`) REFERENCES `Bookmark`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
    _db.execSQL("CREATE TABLE IF NOT EXISTS `StudyPadTextEntryText` (`studyPadTextEntryId` BLOB NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`studyPadTextEntryId`), FOREIGN KEY(`studyPadTextEntryId`) REFERENCES `StudyPadTextEntry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
    _db.execSQL("INSERT INTO BookmarkNotes (bookmarkId, notes) SELECT id, notes FROM Bookmark WHERE notes IS NOT NULL")
    _db.execSQL("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) SELECT id, text FROM StudyPadTextEntry")
    _db.execSQL("ALTER TABLE Bookmark DROP COLUMN notes")
    _db.execSQL("ALTER TABLE StudyPadTextEntry DROP COLUMN text")
    _db.execSQL("CREATE VIEW `BookmarkWithNotes` AS SELECT b.*, bn.notes FROM Bookmark b LEFT OUTER JOIN BookmarkNotes bn ON b.id = bn.bookmarkId");
    _db.execSQL("CREATE VIEW `StudyPadTextEntryWithText` AS SELECT e.*, t.text FROM StudyPadTextEntry e INNER JOIN StudyPadTextEntryText t ON e.id = t.studyPadTextEntryId");
}
private val genericTables = makeMigration(2..3) { _db ->
    _db.execSQL("CREATE TABLE IF NOT EXISTS `GenericBookmark` (`id` BLOB NOT NULL, `key` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `book` TEXT, `ordinalStart` INTEGER NOT NULL, `ordinalEnd` INTEGER NOT NULL, `startOffset` INTEGER, `endOffset` INTEGER, `primaryLabelId` BLOB DEFAULT NULL, `lastUpdatedOn` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`primaryLabelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )");
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_GenericBookmark_book_key` ON `GenericBookmark` (`book`, `key`)");
    _db.execSQL("CREATE TABLE IF NOT EXISTS `GenericBookmarkNotes` (`bookmarkId` BLOB NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`bookmarkId`), FOREIGN KEY(`bookmarkId`) REFERENCES `GenericBookmark`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
    _db.execSQL("CREATE TABLE IF NOT EXISTS `GenericBookmarkToLabel` (`bookmarkId` BLOB NOT NULL, `labelId` BLOB NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT -1, `indentLevel` INTEGER NOT NULL DEFAULT 0, `expandContent` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`bookmarkId`, `labelId`), FOREIGN KEY(`bookmarkId`) REFERENCES `GenericBookmark`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_GenericBookmarkToLabel_labelId` ON `GenericBookmarkToLabel` (`labelId`)");
    _db.execSQL("CREATE VIEW `GenericBookmarkWithNotes` AS SELECT b.*, bn.notes FROM GenericBookmark b LEFT OUTER JOIN GenericBookmarkNotes bn ON b.id = bn.bookmarkId");
    _db.execSQL("ALTER TABLE Bookmark RENAME TO BibleBookmark")
    _db.execSQL("ALTER TABLE BookmarkNotes RENAME TO BibleBookmarkNotes")
    _db.execSQL("ALTER TABLE BookmarkToLabel RENAME TO BibleBookmarkToLabel")
    _db.execSQL("UPDATE LogEntry SET tableName='BibleBookmark' WHERE tableName='Bookmark'")
    _db.execSQL("UPDATE LogEntry SET tableName='BibleBookmarkNotes' WHERE tableName='BookmarkNotes'")
    _db.execSQL("UPDATE LogEntry SET tableName='BibleBookmarkToLabel' WHERE tableName='BookmarkToLabel'")
    _db.execSQL("DROP VIEW BookmarkWithNotes")
    _db.execSQL("CREATE VIEW `BibleBookmarkWithNotes` AS SELECT b.*, bn.notes FROM BibleBookmark b LEFT OUTER JOIN BibleBookmarkNotes bn ON b.id = bn.bookmarkId");
}
private val genericBookmark = makeMigration(3..4) { _db ->
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN bookInitials TEXT NOT NULL DEFAULT ''")
    _db.execSQL("UPDATE GenericBookmark SET bookInitials = book")
    _db.execSQL("DROP INDEX `index_GenericBookmark_book_key`");
    _db.execSQL("ALTER TABLE GenericBookmark DROP COLUMN book")
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_GenericBookmark_bookInitials_key` ON `GenericBookmark` (`bookInitials`, `key`)")
}

private val wholeVerse = makeMigration(4..5) { _db ->
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN wholeVerse INTEGER NOT NULL DEFAULT 0")
}

private val playbackSettings = makeMigration(5..6) { _db ->
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN playbackSettings TEXT DEFAULT NULL")
}

private val genBookmarkIndex = makeMigration(6..7) {_db ->
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_GenericBookmark_primaryLabelId` ON `GenericBookmark` (`primaryLabelId`)")
}

private val labelFields = makeMigration(7..8) { _db  ->
    _db.execSQL("ALTER TABLE Label ADD COLUMN hideStyle INTEGER NOT NULL DEFAULT 0")
    _db.execSQL("ALTER TABLE Label ADD COLUMN hideStyleWholeVerse INTEGER NOT NULL DEFAULT 0")
    _db.execSQL("ALTER TABLE Label ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_Label_favourite` ON `Label` (`favourite`)")
}

private val customIconMigration = makeMigration(8..9) { _db ->
    _db.execSQL("ALTER TABLE Label ADD COLUMN customIcon TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE BibleBookmark ADD COLUMN customIcon TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN customIcon TEXT DEFAULT NULL")
}

private val editActionMigration = makeMigration(9..10) { _db ->
    // Add edit action fields to BibleBookmark
    _db.execSQL("ALTER TABLE BibleBookmark ADD COLUMN editAction_mode TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE BibleBookmark ADD COLUMN editAction_content TEXT DEFAULT NULL")

    // Add edit action fields to GenericBookmark
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN editAction_mode TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN editAction_content TEXT DEFAULT NULL")
}

private val aiFieldsMigration = makeMigration(10..11) { _db ->
    // BibleBookmark - add sourcePromptId
    _db.execSQL("ALTER TABLE BibleBookmark ADD COLUMN sourcePromptId BLOB DEFAULT NULL")

    // GenericBookmark - add sourcePromptId + make ordinals nullable
    // SQLite doesn't support ALTER COLUMN, so we need to: rename → add nullable → migrate → drop old
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN sourcePromptId BLOB DEFAULT NULL")
    _db.execSQL("ALTER TABLE GenericBookmark RENAME COLUMN ordinalStart TO ordinalStart_old")
    _db.execSQL("ALTER TABLE GenericBookmark RENAME COLUMN ordinalEnd TO ordinalEnd_old")
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN ordinalStart INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE GenericBookmark ADD COLUMN ordinalEnd INTEGER DEFAULT NULL")
    _db.execSQL("UPDATE GenericBookmark SET ordinalStart = ordinalStart_old, ordinalEnd = ordinalEnd_old")
    _db.execSQL("ALTER TABLE GenericBookmark DROP COLUMN ordinalStart_old")
    _db.execSQL("ALTER TABLE GenericBookmark DROP COLUMN ordinalEnd_old")

    // BibleBookmarkNotes - add contentType and sourcePromptId
    _db.execSQL("ALTER TABLE BibleBookmarkNotes ADD COLUMN contentType TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE BibleBookmarkNotes ADD COLUMN sourcePromptId BLOB DEFAULT NULL")

    // GenericBookmarkNotes - add contentType and sourcePromptId
    _db.execSQL("ALTER TABLE GenericBookmarkNotes ADD COLUMN contentType TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE GenericBookmarkNotes ADD COLUMN sourcePromptId BLOB DEFAULT NULL")

    // StudyPadTextEntry - add contentType and sourcePromptId
    _db.execSQL("ALTER TABLE StudyPadTextEntry ADD COLUMN contentType TEXT DEFAULT NULL")
    _db.execSQL("ALTER TABLE StudyPadTextEntry ADD COLUMN sourcePromptId BLOB DEFAULT NULL")

    // Recreate views (Room requires this when views depend on changed tables)
    // Note: View names must be quoted with backticks to match Room's expected format
    _db.execSQL("DROP VIEW IF EXISTS BibleBookmarkWithNotes")
    _db.execSQL("CREATE VIEW `BibleBookmarkWithNotes` AS SELECT b.*, bn.notes, bn.contentType AS notesContentType, bn.sourcePromptId AS notesSourcePromptId FROM BibleBookmark b LEFT OUTER JOIN BibleBookmarkNotes bn ON b.id = bn.bookmarkId")

    _db.execSQL("DROP VIEW IF EXISTS GenericBookmarkWithNotes")
    _db.execSQL("CREATE VIEW `GenericBookmarkWithNotes` AS SELECT b.*, bn.notes, bn.contentType AS notesContentType, bn.sourcePromptId AS notesSourcePromptId FROM GenericBookmark b LEFT OUTER JOIN GenericBookmarkNotes bn ON b.id = bn.bookmarkId")

    _db.execSQL("DROP VIEW IF EXISTS StudyPadTextEntryWithText")
    _db.execSQL("CREATE VIEW `StudyPadTextEntryWithText` AS SELECT e.*, t.text FROM StudyPadTextEntry e INNER JOIN StudyPadTextEntryText t ON e.id = t.studyPadTextEntryId")
}

/**
 * Merge all special labels with the same name into a single label with a fixed canonical UUID.
 * Remaps all bookmark-to-label references and deletes duplicates.
 *
 * Used by the 11→12 migration and also run on incoming sync patches (which go through
 * Room migrations before being applied to the local database).
 */
fun deduplicateSpecialLabels(db: SupportSQLiteDatabase) {
    data class SpecialLabel(val name: String, val hexId: String)
    val specialLabels = listOf(
        SpecialLabel(SPEAK_LABEL_NAME, SPEAK_LABEL_ID.toHex()),
        SpecialLabel(UNLABELED_NAME, UNLABELED_LABEL_ID.toHex()),
        SpecialLabel(PARAGRAH_BREAK_LABEL_NAME, PARAGRAPH_BREAK_LABEL_ID.toHex()),
    )

    for (label in specialLabels) {
        val name = label.name
        val hex = label.hexId

        // Insert canonical label with fixed ID, copying data from existing label
        db.execSQL("""
            INSERT OR IGNORE INTO Label (id, name, color, markerStyle, markerStyleWholeVerse,
                underlineStyle, underlineStyleWholeVerse, hideStyle, hideStyleWholeVerse,
                favourite, type, customIcon)
            SELECT X'$hex', name, color, markerStyle, markerStyleWholeVerse,
                underlineStyle, underlineStyleWholeVerse, hideStyle, hideStyleWholeVerse,
                favourite, type, customIcon
            FROM Label WHERE name = '$name' ORDER BY id LIMIT 1
        """)

        // Remap BibleBookmarkToLabel (composite PK: INSERT OR IGNORE + DELETE old)
        db.execSQL("""
            INSERT OR IGNORE INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent)
            SELECT bookmarkId, X'$hex', orderNumber, indentLevel, expandContent
            FROM BibleBookmarkToLabel
            WHERE labelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)
        db.execSQL("""
            DELETE FROM BibleBookmarkToLabel
            WHERE labelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)

        // Remap GenericBookmarkToLabel (same composite PK handling)
        db.execSQL("""
            INSERT OR IGNORE INTO GenericBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent)
            SELECT bookmarkId, X'$hex', orderNumber, indentLevel, expandContent
            FROM GenericBookmarkToLabel
            WHERE labelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)
        db.execSQL("""
            DELETE FROM GenericBookmarkToLabel
            WHERE labelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)

        // Remap BibleBookmark.primaryLabelId
        db.execSQL("""
            UPDATE BibleBookmark SET primaryLabelId = X'$hex'
            WHERE primaryLabelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)

        // Remap GenericBookmark.primaryLabelId
        db.execSQL("""
            UPDATE GenericBookmark SET primaryLabelId = X'$hex'
            WHERE primaryLabelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)

        // Remap StudyPadTextEntry.labelId
        db.execSQL("""
            UPDATE StudyPadTextEntry SET labelId = X'$hex'
            WHERE labelId IN (SELECT id FROM Label WHERE name = '$name' AND id != X'$hex')
        """)

        // Delete duplicate labels (keep only canonical)
        db.execSQL("DELETE FROM Label WHERE name = '$name' AND id != X'$hex'")
    }
}

private val fixedSpecialLabelIds = makeMigration(11..12) { db ->
    deduplicateSpecialLabels(db)
}

val bookmarkMigrations: Array<Migration> = arrayOf(
    separateText,
    genericTables,
    genericBookmark,
    wholeVerse,
    playbackSettings,
    genBookmarkIndex,
    labelFields,
    customIconMigration,
    editActionMigration,
    aiFieldsMigration,
    fixedSpecialLabelIds,
)

const val BOOKMARK_DATABASE_VERSION = 12
