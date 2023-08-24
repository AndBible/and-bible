/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

val bookmarkMigrations: Array<Migration> = arrayOf(
    separateText,
    genericTables,
    genericBookmark,
    wholeVerse,
    playbackSettings,
    genBookmarkIndex,
    labelFields,
)

const val BOOKMARK_DATABASE_VERSION = 8
