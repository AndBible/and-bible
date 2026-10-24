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

import androidx.sqlite.execSQL
import androidx.sqlite.SQLiteConnection
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
fun deduplicateSpecialLabels(db: SQLiteConnection) {
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

/**
 * The six style booleans become two enum columns. The CASE cascades are the reader's own precedence
 * (`hide > marker > underline > highlight`, `bibleview-js/src/composables/bookmarks.ts:583-604`), so
 * a row carrying a dominated flag collapses to what was already on screen — the migration cannot
 * change any label's appearance. A whole-verse axis equal to the selection axis becomes NULL, which
 * means "inherit". Spec: docs/superpowers/specs/2026-08-19-label-style-schema-migration-design.md
 */
private val labelDisplayStyleEnum = makeMigration(12..13) { db ->
    // Create-copy-drop-rename rather than six ALTER TABLE ... DROP COLUMNs: the app bundles a
    // SQLite new enough for DROP COLUMN (>= 3.35), but Robolectric's bundled native SQLite is not,
    // so the migration test could not run the real statements. Rebuilding the table is supported
    // everywhere and is the procedure SQLite itself documents for removing columns.
    //
    // Label_new below must describe the SAME TABLE as the v13 entity: Room validates the migrated
    // database by comparing `TableInfo` (columns, affinities, notNull, defaults, primary key,
    // indices) against the entity, never the DDL text — so keep it equivalent to
    // `schemas/net.bible.android.database.BookmarkDatabase/13.json`, do not try to diff the strings.
    //
    // `BibleBookmarkToLabel`/`GenericBookmarkToLabel` reference `Label` with ON DELETE CASCADE, and
    // `BibleBookmark`/`GenericBookmark.primaryLabelId` with ON DELETE SET NULL. `DROP TABLE Label`
    // below would fire those actions if foreign keys were enforced, deleting every bookmark<->label
    // association. Room only turns FKs on in `onOpen`, after `onUpgrade` runs, so this is not live
    // today — but this migration also runs against sync patch files applied through other paths, so
    // it must be safe regardless of the caller's FK setting, the same way
    // `OldMonolithicAppDatabaseMigrations.kt`'s `MIGRATION_14_15` guards its own table rebuild.
    db.execSQL("PRAGMA foreign_keys=OFF")
    db.execSQL("""
        CREATE TABLE `Label_new` (
            `id` BLOB NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL DEFAULT 0,
            `displayStyle` INTEGER NOT NULL DEFAULT 0, `displayStyleWholeVerse` INTEGER DEFAULT 1,
            `favourite` INTEGER NOT NULL DEFAULT 0, `type` TEXT DEFAULT NULL,
            `customIcon` TEXT DEFAULT NULL, PRIMARY KEY(`id`)
        )
    """)
    db.execSQL("""
        INSERT INTO Label_new (id, name, color, displayStyle, displayStyleWholeVerse, favourite, type, customIcon)
        SELECT id, name, color,
            CASE WHEN hideStyle THEN 3 WHEN markerStyle THEN 2 WHEN underlineStyle THEN 1 ELSE 0 END,
            CASE WHEN hideStyleWholeVerse THEN 3 WHEN markerStyleWholeVerse THEN 2 WHEN underlineStyleWholeVerse THEN 1 ELSE 0 END,
            favourite, type, customIcon
        FROM Label
    """)
    db.execSQL("UPDATE Label_new SET displayStyleWholeVerse = NULL WHERE displayStyleWholeVerse = displayStyle")
    db.execSQL("DROP TABLE Label")
    db.execSQL("ALTER TABLE Label_new RENAME TO Label")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_Label_favourite` ON `Label` (`favourite`)")
    db.execSQL("PRAGMA foreign_keys=ON")
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
    labelDisplayStyleEnum,
)

const val BOOKMARK_DATABASE_VERSION = 13
