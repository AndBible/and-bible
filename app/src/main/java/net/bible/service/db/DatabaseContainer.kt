/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.database.AppDatabase
import net.bible.android.database.DATABASE_VERSION
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.service.common.CommonUtils
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition
import net.bible.service.db.mynote.MyNoteDatabaseDefinition
import net.bible.service.db.readingplan.ReadingPlanDatabaseOperations
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.io.File
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.*

import androidx.room.migration.Migration as RoomMigration


const val DATABASE_NAME = "andBibleDatabase.db"
const val TAG = "Migration"

abstract class Migration(startVersion: Int, endVersion: Int): RoomMigration(startVersion, endVersion) {
    abstract fun doMigrate(db: SupportSQLiteDatabase)
    
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Migrating from version $startVersion to $endVersion")
        doMigrate(db)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        MyNoteDatabaseDefinition.instance.onCreate(db)
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion3(db)
        MyNoteDatabaseDefinition.instance.upgradeToVersion3(db)
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion4(db)
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion5(db)

    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        ReadingPlanDatabaseOperations.instance.onCreate(db)
        ReadingPlanDatabaseOperations.instance.migratePrefsToDatabase(db)
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DROP TRIGGER IF EXISTS bookmark_cleanup;")
            execSQL("DROP TRIGGER IF EXISTS label_cleanup;")
            execSQL("CREATE TABLE `bookmark_label_new` (`bookmark_id` INTEGER NOT NULL, `label_id` INTEGER NOT NULL, PRIMARY KEY(`bookmark_id`, `label_id`), FOREIGN KEY(`bookmark_id`) REFERENCES `bookmark`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`label_id`) REFERENCES `label`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );");
            execSQL("INSERT INTO bookmark_label_new SELECT * from bookmark_label;")
            execSQL("DROP TABLE bookmark_label;")
            execSQL("ALTER TABLE bookmark_label_new RENAME TO bookmark_label;")
            execSQL("CREATE INDEX IF NOT EXISTS `code_day` ON `readingplan_status` (`plan_code`, `plan_day`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_readingplan_plan_code` ON `readingplan` (`plan_code`)")
            execSQL("CREATE INDEX IF NOT EXISTS `mynote_key` ON `mynote` (`key`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_bookmark_label_label_id` ON `bookmark_label` (`label_id`)")
        }
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            execSQL("CREATE TABLE IF NOT EXISTS `Window` (`workspaceId` INTEGER NOT NULL, `isSynchronized` INTEGER NOT NULL, `wasMinimised` INTEGER NOT NULL, `isLinksWindow` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL, `window_layout_state` TEXT NOT NULL, `window_layout_weight` REAL NOT NULL, FOREIGN KEY(`workspaceId`) REFERENCES `Workspace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Window_workspaceId` ON `Window` (`workspaceId`)")
            execSQL("CREATE TABLE IF NOT EXISTS `HistoryItem` (`windowId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `document` TEXT NOT NULL, `key` TEXT NOT NULL, `yOffsetRatio` REAL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_HistoryItem_windowId` ON `HistoryItem` (`windowId`)")
            execSQL("CREATE TABLE IF NOT EXISTS `PageManager` (`windowId` INTEGER NOT NULL, `currentCategoryName` TEXT NOT NULL, `bible_document` TEXT, `bible_verse_versification` TEXT NOT NULL, `bible_verse_bibleBook` INTEGER NOT NULL, `bible_verse_chapterNo` INTEGER NOT NULL, `bible_verse_verseNo` INTEGER NOT NULL, `commentary_document` TEXT, `dictionary_document` TEXT, `dictionary_key` TEXT, `general_book_document` TEXT, `general_book_key` TEXT, `map_document` TEXT, `map_key` TEXT, PRIMARY KEY(`windowId`), FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PageManager_windowId` ON `PageManager` (`windowId`)")
        }
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `PageManager` ADD `commentary_currentYOffsetRatio` REAL")
            execSQL("ALTER TABLE `PageManager` ADD `dictionary_currentYOffsetRatio` REAL")
            execSQL("ALTER TABLE `PageManager` ADD `general_book_currentYOffsetRatio` REAL")
            execSQL("ALTER TABLE `PageManager` ADD `map_currentYOffsetRatio` REAL")
        }
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE TABLE IF NOT EXISTS `readingplan_new` (`_id` INTEGER, `plan_code` TEXT NOT NULL, `plan_start_date` INTEGER NOT NULL, `plan_current_day` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`_id`))")
            execSQL("INSERT INTO readingplan_new SELECT * from readingplan;")
            execSQL("DROP TABLE readingplan;")
            execSQL("ALTER TABLE readingplan_new RENAME TO readingplan;")
            execSQL("CREATE INDEX IF NOT EXISTS `index_readingplan_plan_code` ON `readingplan` (`plan_code`)")
        }
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `window_behavior_settings_enableTiltToScroll` INTEGER DEFAULT FALSE, `window_behavior_settings_enableReverseSplitMode` INTEGER DEFAULT FALSE".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
            }

            val colDefs2 = "`text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL".split(",")
            colDefs2.forEach {
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_marginSize` INTEGER DEFAULT NULL".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
            }

            val colDefs2 = "`text_display_settings_marginSize` INTEGER DEFAULT NULL".split(",")
            colDefs2.forEach {
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_margin_size_marginSize` INTEGER DEFAULT NULL, `text_display_settings_margin_size_left` INTEGER DEFAULT NULL, `text_display_settings_margin_size_right` INTEGER DEFAULT NULL".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

fun createMarginSizeColumns(db: SupportSQLiteDatabase) {
    db.apply {

        val colDefs = "`text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL".split(",")
        colDefs.forEach {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
            execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
        }
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun doMigrate(db: SupportSQLiteDatabase) = createMarginSizeColumns(db)

}

private val MIGRATION_11_15 = object : Migration(11, 15) {
    override fun doMigrate(db: SupportSQLiteDatabase) = createMarginSizeColumns(db)

}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("PRAGMA foreign_keys=OFF;")

            execSQL("ALTER TABLE Workspace RENAME TO Workspace_old;")
            execSQL("ALTER TABLE PageManager RENAME TO PageManager_old;")


            execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `window_behavior_settings_enableTiltToScroll` INTEGER DEFAULT FALSE, `window_behavior_settings_enableReverseSplitMode` INTEGER DEFAULT FALSE)")

            execSQL("CREATE TABLE IF NOT EXISTS `PageManager` (`windowId` INTEGER NOT NULL, `currentCategoryName` TEXT NOT NULL, `bible_document` TEXT, `bible_verse_versification` TEXT NOT NULL, `bible_verse_bibleBook` INTEGER NOT NULL, `bible_verse_chapterNo` INTEGER NOT NULL, `bible_verse_verseNo` INTEGER NOT NULL, `commentary_document` TEXT, `commentary_currentYOffsetRatio` REAL, `dictionary_document` TEXT, `dictionary_key` TEXT, `dictionary_currentYOffsetRatio` REAL, `general_book_document` TEXT, `general_book_key` TEXT, `general_book_currentYOffsetRatio` REAL, `map_document` TEXT, `map_key` TEXT, `map_currentYOffsetRatio` REAL, `text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, PRIMARY KEY(`windowId`), FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            execSQL("INSERT INTO Workspace SELECT `name`, `id`, `text_display_settings_fontSize`, `text_display_settings_showStrongs`, `text_display_settings_showMorphology`, `text_display_settings_showFootNotes`, `text_display_settings_showRedLetters`, `text_display_settings_showSectionTitles`, `text_display_settings_showVerseNumbers`, `text_display_settings_showVersePerLine`, `text_display_settings_showBookmarks`, `text_display_settings_showMyNotes`, `text_display_settings_margin_size_marginLeft`, `text_display_settings_margin_size_marginRight`, `window_behavior_settings_enableTiltToScroll`, `window_behavior_settings_enableReverseSplitMode` from Workspace_old;")
            execSQL("INSERT INTO PageManager SELECT `windowId`, `currentCategoryName`, `bible_document`, `bible_verse_versification`, `bible_verse_bibleBook`, `bible_verse_chapterNo`, `bible_verse_verseNo`, `commentary_document`, `commentary_currentYOffsetRatio`, `dictionary_document`, `dictionary_key`, `dictionary_currentYOffsetRatio` , `general_book_document` , `general_book_key` , `general_book_currentYOffsetRatio` , `map_document` , `map_key` , `map_currentYOffsetRatio` , `text_display_settings_fontSize` , `text_display_settings_showStrongs` , `text_display_settings_showMorphology` , `text_display_settings_showFootNotes` , `text_display_settings_showRedLetters` , `text_display_settings_showSectionTitles` , `text_display_settings_showVerseNumbers` , `text_display_settings_showVersePerLine` , `text_display_settings_showBookmarks` , `text_display_settings_showMyNotes` , `text_display_settings_margin_size_marginLeft` , `text_display_settings_margin_size_marginRight` from PageManager_old;")

            execSQL("DROP TABLE Workspace_old;")
            execSQL("DROP TABLE PageManager_old;")

            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PageManager_windowId` ON `PageManager` (`windowId`)")

            execSQL("PRAGMA foreign_keys=ON;")
        }
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL ".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, `text_display_settings_font_lineSpacing` INTEGER DEFAULT NULL".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`orderNumber` INTEGER NOT NULL DEFAULT 0, `contentsText` TEXT".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`isSwapMode` INTEGER NOT NULL DEFAULT 0".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Window` ADD COLUMN $it")
            }
        }
    }
}


private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_autoPin` INTEGER DEFAULT 1")
        }
    }
}


fun getColumnNames(db: SupportSQLiteDatabase, tableName: String): String {
    val cursor = db.query("PRAGMA table_info($tableName)")
    val columnNameIdx = cursor.getColumnIndex("name")
    cursor.moveToFirst()
    val columnNames = mutableListOf<String>()

    while(!cursor.isAfterLast) {
        columnNames.add(cursor.getString(columnNameIdx))
        cursor.moveToNext()
    }
    return columnNames.joinToString(",", transform = { "`${it}`" })
}

private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        val colNameStr = getColumnNames(db, "Workspace")
        db.apply {
            execSQL("PRAGMA foreign_keys=OFF;")
            execSQL("ALTER TABLE Workspace RENAME TO Workspace_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `contentsText` TEXT, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT 0, `text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_font_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, `window_behavior_settings_enableTiltToScroll` INTEGER DEFAULT 0, `window_behavior_settings_enableReverseSplitMode` INTEGER DEFAULT 0, `window_behavior_settings_autoPin` INTEGER DEFAULT 1)")
            execSQL("INSERT INTO Workspace ($colNameStr) SELECT $colNameStr from Workspace_old;")
            execSQL("DROP TABLE Workspace_old;")
            execSQL("PRAGMA foreign_keys=ON;")
        }
    }
}

private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("UPDATE `Window` SET window_layout_state = 'SPLIT' WHERE window_layout_state = 'MAXIMISED'")
        }
    }
}

private val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `unPinnedWeight` REAL DEFAULT NULL")
        }
    }
}

private val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            val colDefs = "`text_display_settings_hyphenation` INTEGER DEFAULT NULL".split(",")
            colDefs.forEach {
                execSQL("ALTER TABLE `Workspace` ADD COLUMN $it")
                execSQL("ALTER TABLE `PageManager` ADD COLUMN $it")
            }
        }
    }
}

private val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `maximizedWindowId` INTEGER DEFAULT NULL")
        }
    }
}

private val CLEANUP_MIGRATION_26_27 = object : Migration(26, 27) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        val wsOldCols = "`name`,`contentsText`,`id`,`orderNumber`,`text_display_settings_showStrongs`,`text_display_settings_showMorphology`," +
            "`text_display_settings_showFootNotes`,`text_display_settings_showRedLetters`,`text_display_settings_showSectionTitles`,`text_display_settings_showVerseNumbers`,`text_display_settings_showVersePerLine`," +
            "`text_display_settings_showBookmarks`,`text_display_settings_showMyNotes`,`text_display_settings_justifyText`,`text_display_settings_font_lineSpacing`,`text_display_settings_margin_size_marginLeft`," +
            "`text_display_settings_margin_size_marginRight`,`text_display_settings_margin_size_maxWidth`,`text_display_settings_colors_dayTextColor`," +
            "`text_display_settings_colors_dayBackground`,`text_display_settings_colors_dayNoise`,`text_display_settings_colors_nightTextColor`,`text_display_settings_colors_nightBackground`," +
            "`text_display_settings_colors_nightNoise`,`text_display_settings_font_fontSize`,`text_display_settings_font_fontFamily`,`window_behavior_settings_enableTiltToScroll`," +
            "`window_behavior_settings_enableReverseSplitMode`,`window_behavior_settings_autoPin`,`unPinnedWeight`,`text_display_settings_hyphenation`,`maximizedWindowId`"


        val wsNewCols = "`name`,`contentsText`,`id`,`orderNumber`,`text_display_settings_showStrongs`,`text_display_settings_showMorphology`," +
            "`text_display_settings_showFootNotes`,`text_display_settings_showRedLetters`,`text_display_settings_showSectionTitles`,`text_display_settings_showVerseNumbers`,`text_display_settings_showVersePerLine`," +
            "`text_display_settings_showBookmarks`,`text_display_settings_showMyNotes`,`text_display_settings_justifyText`,`text_display_settings_lineSpacing`,`text_display_settings_margin_size_marginLeft`," +
            "`text_display_settings_margin_size_marginRight`,`text_display_settings_margin_size_maxWidth`,`text_display_settings_colors_dayTextColor`," +
            "`text_display_settings_colors_dayBackground`,`text_display_settings_colors_dayNoise`,`text_display_settings_colors_nightTextColor`,`text_display_settings_colors_nightBackground`," +
            "`text_display_settings_colors_nightNoise`,`text_display_settings_font_fontSize`,`text_display_settings_font_fontFamily`,`window_behavior_settings_enableTiltToScroll`," +
            "`window_behavior_settings_enableReverseSplitMode`,`window_behavior_settings_autoPin`,`unPinnedWeight`,`text_display_settings_hyphenation`,`maximizedWindowId`"


        val pgOldCols = "`windowId`,`currentCategoryName`,`bible_document`,`bible_verse_versification`,`bible_verse_bibleBook`,`bible_verse_chapterNo`,`bible_verse_verseNo`," +
            "`commentary_document`,`commentary_currentYOffsetRatio`,`dictionary_document`,`dictionary_key`,`dictionary_currentYOffsetRatio`,`general_book_document`,`general_book_key`," +
            "`general_book_currentYOffsetRatio`,`map_document`,`map_key`,`map_currentYOffsetRatio`,`text_display_settings_showStrongs`," +
            "`text_display_settings_showMorphology`,`text_display_settings_showFootNotes`,`text_display_settings_showRedLetters`,`text_display_settings_showSectionTitles`," +
            "`text_display_settings_showVerseNumbers`,`text_display_settings_showVersePerLine`,`text_display_settings_showBookmarks`,`text_display_settings_showMyNotes`," +
            "`text_display_settings_margin_size_marginLeft`,`text_display_settings_margin_size_marginRight`,`text_display_settings_colors_dayTextColor`," +
            "`text_display_settings_colors_dayBackground`,`text_display_settings_colors_dayNoise`,`text_display_settings_colors_nightTextColor`," +
            "`text_display_settings_colors_nightBackground`,`text_display_settings_colors_nightNoise`,`text_display_settings_justifyText`,`text_display_settings_margin_size_maxWidth`," +
            "`text_display_settings_font_fontSize`,`text_display_settings_font_fontFamily`,`text_display_settings_font_lineSpacing`,`text_display_settings_hyphenation`"

        val pgNewCols = "`windowId`,`currentCategoryName`,`bible_document`,`bible_verse_versification`,`bible_verse_bibleBook`,`bible_verse_chapterNo`,`bible_verse_verseNo`," +
            "`commentary_document`,`commentary_currentYOffsetRatio`,`dictionary_document`,`dictionary_key`,`dictionary_currentYOffsetRatio`,`general_book_document`,`general_book_key`," +
            "`general_book_currentYOffsetRatio`,`map_document`,`map_key`,`map_currentYOffsetRatio`,`text_display_settings_showStrongs`," +
            "`text_display_settings_showMorphology`,`text_display_settings_showFootNotes`,`text_display_settings_showRedLetters`,`text_display_settings_showSectionTitles`," +
            "`text_display_settings_showVerseNumbers`,`text_display_settings_showVersePerLine`,`text_display_settings_showBookmarks`,`text_display_settings_showMyNotes`," +
            "`text_display_settings_margin_size_marginLeft`,`text_display_settings_margin_size_marginRight`,`text_display_settings_colors_dayTextColor`," +
            "`text_display_settings_colors_dayBackground`,`text_display_settings_colors_dayNoise`,`text_display_settings_colors_nightTextColor`," +
            "`text_display_settings_colors_nightBackground`,`text_display_settings_colors_nightNoise`,`text_display_settings_justifyText`,`text_display_settings_margin_size_maxWidth`," +
            "`text_display_settings_font_fontSize`,`text_display_settings_font_fontFamily`,`text_display_settings_lineSpacing`,`text_display_settings_hyphenation`"

        val winNewCols = "workspaceId, isSynchronized, isPinMode, isLinksWindow, window_layout_state, window_layout_weight, id, orderNumber"
        val winOldCols = "workspaceId, isSynchronized, isSwapMode, isLinksWindow, window_layout_state, window_layout_weight, id, orderNumber"
        db.apply {
            execSQL("PRAGMA foreign_keys=OFF;")

            execSQL("ALTER TABLE Workspace RENAME TO Workspace_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `contentsText` TEXT, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT 0, `unPinnedWeight` REAL DEFAULT NULL, `maximizedWindowId` INTEGER, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, `window_behavior_settings_enableTiltToScroll` INTEGER DEFAULT 0, `window_behavior_settings_enableReverseSplitMode` INTEGER DEFAULT 0, `window_behavior_settings_autoPin` INTEGER DEFAULT 1)")
            execSQL("INSERT INTO Workspace ($wsNewCols) SELECT $wsOldCols from Workspace_old;")
            execSQL("DROP TABLE Workspace_old;")

            execSQL("ALTER TABLE Window RENAME TO Window_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `Window` (`workspaceId` INTEGER NOT NULL, `isSynchronized` INTEGER NOT NULL, `isPinMode` INTEGER NOT NULL, `isLinksWindow` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL, `window_layout_state` TEXT NOT NULL, `window_layout_weight` REAL NOT NULL, FOREIGN KEY(`workspaceId`) REFERENCES `Workspace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("INSERT INTO Window ($winNewCols) SELECT $winOldCols from Window_old;")
            execSQL("DROP INDEX `index_Window_workspaceId`")
            execSQL("DROP TABLE Window_old;")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Window_workspaceId` ON `Window` (`workspaceId`)")

            execSQL("ALTER TABLE PageManager RENAME TO PageManager_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `PageManager` (`windowId` INTEGER NOT NULL, `currentCategoryName` TEXT NOT NULL, `bible_document` TEXT, `bible_verse_versification` TEXT NOT NULL, `bible_verse_bibleBook` INTEGER NOT NULL, `bible_verse_chapterNo` INTEGER NOT NULL, `bible_verse_verseNo` INTEGER NOT NULL, `commentary_document` TEXT, `commentary_currentYOffsetRatio` REAL, `dictionary_document` TEXT, `dictionary_key` TEXT, `dictionary_currentYOffsetRatio` REAL, `general_book_document` TEXT, `general_book_key` TEXT, `general_book_currentYOffsetRatio` REAL, `map_document` TEXT, `map_key` TEXT, `map_currentYOffsetRatio` REAL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, PRIMARY KEY(`windowId`), FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("INSERT INTO PageManager ($pgNewCols) SELECT $pgOldCols from PageManager_old;")
            execSQL("DROP INDEX `index_PageManager_windowId`")
            execSQL("DROP TABLE PageManager_old;")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PageManager_windowId` ON `PageManager` (`windowId`)")

            execSQL("PRAGMA foreign_keys=ON;")
        }
    }
}

private val SQUASH_MIGRATION_10_27 = object : Migration(10, 27) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("PRAGMA foreign_keys=OFF;")
            val wsOldCols = "`name`,`id`"
            val wsNewCols = "`name`,`id`"

            val pgOldCols = "`windowId`,`currentCategoryName`,`bible_document`,`bible_verse_versification`,`bible_verse_bibleBook`,`bible_verse_chapterNo`,`bible_verse_verseNo`,`commentary_document`," +
                "`dictionary_document`,`dictionary_key`,`general_book_document`,`general_book_key`,`map_document`,`map_key`,`commentary_currentYOffsetRatio`,`dictionary_currentYOffsetRatio`," +
               "`general_book_currentYOffsetRatio`,`map_currentYOffsetRatio`"

            val pgNewCols = "`windowId`,`currentCategoryName`,`bible_document`,`bible_verse_versification`,`bible_verse_bibleBook`,`bible_verse_chapterNo`,`bible_verse_verseNo`,`commentary_document`," +
                "`dictionary_document`,`dictionary_key`,`general_book_document`,`general_book_key`,`map_document`,`map_key`,`commentary_currentYOffsetRatio`,`dictionary_currentYOffsetRatio`," +
                "`general_book_currentYOffsetRatio`,`map_currentYOffsetRatio`"

            val winOldCols = "`workspaceId`,`isSynchronized`,0,`isLinksWindow`,`id`,`orderNumber`,`window_layout_state`,`window_layout_weight`"
            val winNewCols = "workspaceId, isSynchronized, isPinMode, isLinksWindow, id, orderNumber, window_layout_state, window_layout_weight"

            execSQL("ALTER TABLE Workspace RENAME TO Workspace_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `contentsText` TEXT, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT 0, `unPinnedWeight` REAL DEFAULT NULL, `maximizedWindowId` INTEGER, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, `window_behavior_settings_enableTiltToScroll` INTEGER DEFAULT 0, `window_behavior_settings_enableReverseSplitMode` INTEGER DEFAULT 0, `window_behavior_settings_autoPin` INTEGER DEFAULT 1)")
            execSQL("INSERT INTO Workspace ($wsNewCols) SELECT $wsOldCols from Workspace_old;")
            execSQL("DROP TABLE Workspace_old;")

            execSQL("ALTER TABLE Window RENAME TO Window_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `Window` (`workspaceId` INTEGER NOT NULL, `isSynchronized` INTEGER NOT NULL, `isPinMode` INTEGER NOT NULL, `isLinksWindow` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` INTEGER NOT NULL, `window_layout_state` TEXT NOT NULL, `window_layout_weight` REAL NOT NULL, FOREIGN KEY(`workspaceId`) REFERENCES `Workspace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("INSERT INTO Window ($winNewCols) SELECT $winOldCols from Window_old;")
            execSQL("DROP INDEX `index_Window_workspaceId`")
            execSQL("DROP TABLE Window_old;")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Window_workspaceId` ON `Window` (`workspaceId`)")

            execSQL("ALTER TABLE PageManager RENAME TO PageManager_old;")
            execSQL("CREATE TABLE IF NOT EXISTS `PageManager` (`windowId` INTEGER NOT NULL, `currentCategoryName` TEXT NOT NULL, `bible_document` TEXT, `bible_verse_versification` TEXT NOT NULL, `bible_verse_bibleBook` INTEGER NOT NULL, `bible_verse_chapterNo` INTEGER NOT NULL, `bible_verse_verseNo` INTEGER NOT NULL, `commentary_document` TEXT, `commentary_currentYOffsetRatio` REAL, `dictionary_document` TEXT, `dictionary_key` TEXT, `dictionary_currentYOffsetRatio` REAL, `general_book_document` TEXT, `general_book_key` TEXT, `general_book_currentYOffsetRatio` REAL, `map_document` TEXT, `map_key` TEXT, `map_currentYOffsetRatio` REAL, `text_display_settings_showStrongs` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `text_display_settings_font_fontSize` INTEGER DEFAULT NULL, `text_display_settings_font_fontFamily` TEXT DEFAULT NULL, PRIMARY KEY(`windowId`), FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("INSERT INTO PageManager ($pgNewCols) SELECT $pgOldCols from PageManager_old;")
            execSQL("DROP INDEX `index_PageManager_windowId`")
            execSQL("DROP TABLE PageManager_old;")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PageManager_windowId` ON `PageManager` (`windowId`)")

            execSQL("PRAGMA foreign_keys=ON;")

            execSQL("UPDATE `Window` SET window_layout_state = 'SPLIT' WHERE window_layout_state = 'MAXIMISED'")
        }
    }
}

private val MIGRATION_27_28 = object : Migration(27, 28) {
    // Added autogenerate=true for readingplan and readingplan_status. Some db schemas may already have this, but makes sure
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            // readingplan recreate to add AUTOINCREMENT. MIGRATION_9_10 removed it
            execSQL("CREATE TABLE `readingplan_new` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `plan_code` TEXT NOT NULL, `plan_start_date` INTEGER NOT NULL, `plan_current_day` INTEGER NOT NULL DEFAULT 1);")
            execSQL("INSERT INTO readingplan_new SELECT * FROM readingplan;")
            execSQL("DROP TABLE readingplan;")
            execSQL("ALTER TABLE readingplan_new RENAME TO readingplan;")

            // readingplan make index unique
            execSQL("DROP INDEX IF EXISTS index_readingplan_plan_code;")
            try {
                execSQL("CREATE UNIQUE INDEX `index_readingplan_plan_code` ON `readingplan` (`plan_code`);")
            } catch (e: SQLException) {
                // in case table already has data that prevents unique index from being created (should never be)
                try {
                    execSQL("CREATE INDEX `index_readingplan_plan_code` ON `readingplan` (`plan_code`);")
                } finally { }
            }

            // readingplan_status make index unique
            execSQL("DROP INDEX IF EXISTS `code_day`;")
            try {
                execSQL("CREATE UNIQUE INDEX `code_day` ON `readingplan_status` (`plan_code`, `plan_day`);")
            } catch (e: SQLException) {
                // in case table already has data that prevents unique index from being created (should never be)
                try {
                    execSQL("CREATE INDEX `code_day` ON `readingplan_status` (`plan_code`, `plan_day`);")
                } finally { }
            }
        }
    }
}

private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `Document` USING FTS4(`osisId` TEXT NOT NULL, `abbreviation` TEXT NOT NULL, `name` TEXT NOT NULL, `language` TEXT NOT NULL)")
        }
    }
}

private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("DROP TABLE IF EXISTS `Document`")
            execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `Document` USING FTS4(`osisId` TEXT NOT NULL, `abbreviation` TEXT NOT NULL, `name` TEXT NOT NULL, `language` TEXT NOT NULL, `repository` TEXT NOT NULL)")
        }
    }
}


private fun clearVerse0(db: SupportSQLiteDatabase) {
    db.apply {
        execSQL("DELETE FROM bookmark WHERE `key` LIKE '%.0-%'") // for key like Gen.1.0-Gen.1.1
        execSQL("DELETE FROM bookmark WHERE `key` LIKE '%.0'")
        execSQL("DELETE FROM bookmark WHERE `key` LIKE '%.0.%'")
        execSQL("DELETE FROM mynote WHERE `key` LIKE '%.0'")
        execSQL("DELETE FROM mynote WHERE `key` LIKE '%.0.%'")
    }
}

private val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        clearVerse0(db)
    }
}

private val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        clearVerse0(db);
    }
}

private val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        clearVerse0(db);
    }
}

private val SQUASH_30_33 = object : Migration(30, 33) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        clearVerse0(db);
    }
}

private val MIGRATION_33_34_Bookmarks = object : Migration(33, 34) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE bookmark RENAME TO bookmark_old;")
            execSQL("ALTER TABLE label RENAME TO label_old;")

            execSQL("CREATE TABLE IF NOT EXISTS `Bookmark` (`kjvOrdinalStart` INTEGER NOT NULL, `kjvOrdinalEnd` INTEGER NOT NULL, `ordinalStart` INTEGER NOT NULL, `ordinalEnd` INTEGER NOT NULL, `v11n` TEXT NOT NULL, `playbackSettings` TEXT, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_kjvOrdinalStart` ON `Bookmark` (`kjvOrdinalStart`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_kjvOrdinalEnd` ON `Bookmark` (`kjvOrdinalEnd`)")
            execSQL("CREATE TABLE IF NOT EXISTS `Label` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `bookmarkStyle` TEXT)")
            execSQL("CREATE TABLE IF NOT EXISTS `BookmarkToLabel` (`bookmarkId` INTEGER NOT NULL, `labelId` INTEGER NOT NULL, PRIMARY KEY(`bookmarkId`, `labelId`), FOREIGN KEY(`bookmarkId`) REFERENCES `Bookmark`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_BookmarkToLabel_labelId` ON `BookmarkToLabel` (`labelId`)")

            val c = db.query("SELECT * from bookmark_old")
            val keyIdx = c.getColumnIndex("key")
            val createdOnIdx = c.getColumnIndex("created_on")
            val v11nIdx = c.getColumnIndex("versification")
            val speakSettingsIdx = c.getColumnIndex("speak_settings")
            val idIdx = c.getColumnIndex("_id")

            c.moveToFirst()
            while(!c.isAfterLast) {
                val id = c.getLong(idIdx)
                val key = c.getString(keyIdx)
                var v11n: Versification? = null
                var verseRange: VerseRange? = null
                var verseRangeInKjv: VerseRange? = null

                try {
                    v11n = Versifications.instance().getVersification(
                        c.getString(v11nIdx) ?: Versifications.DEFAULT_V11N
                    )
                    verseRange = VerseRangeFactory.fromString(v11n, key)
                    verseRangeInKjv = verseRange.toV11n(KJVA)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate bookmark: v11n:$v11n verseRange:$verseRange verseRangeInKjv:$verseRangeInKjv", e)
                    c.moveToNext()
                    continue
                }

                //Created date
                val createdAt = c.getLong(createdOnIdx)
                val playbackSettingsStr = c.getString(speakSettingsIdx)
                val newValues = ContentValues()
                newValues.apply {
                    put("id", id)
                    put("v11n", v11n.name)
                    put("kjvOrdinalStart", verseRangeInKjv.start.ordinal)
                    put("kjvOrdinalEnd", verseRangeInKjv.end.ordinal)
                    put("ordinalStart", verseRange.start.ordinal)
                    put("ordinalEnd", verseRange.end.ordinal)
                    put("createdAt", createdAt)
                    put("playbackSettings", playbackSettingsStr)
                }
                db.insert("Bookmark", CONFLICT_FAIL, newValues)
                c.moveToNext()
            }

            execSQL("INSERT INTO Label SELECT * from label_old;")
            execSQL("INSERT INTO BookmarkToLabel SELECT * from bookmark_label;")

            execSQL("DROP TABLE bookmark_old;")
            execSQL("DROP TABLE label_old;")
            execSQL("DROP TABLE bookmark_label;")
        }
    }
}

private val BOOKMARKS_BOOK_34_35 = object : Migration(34, 35) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Bookmark` ADD COLUMN `book` TEXT")
        db.execSQL("ALTER TABLE `Bookmark` ADD COLUMN `startOffset` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `Bookmark` ADD COLUMN `endOffset` INTEGER DEFAULT NULL")
    }
}

private val WORKSPACE_BOOKMARK_35_36 = object : Migration(35, 36) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_bookmarks_showAll` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_bookmarks_showLabels` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_bookmarks_assignLabels` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_bookmarks_showAll` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_bookmarks_showLabels` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_bookmarks_assignLabels` TEXT DEFAULT NULL")
    }
}

private val BOOKMARKS_BOOK_36_37 = object : Migration(36, 37) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Bookmark` ADD COLUMN `notes` TEXT DEFAULT NULL")
    }
}

private val MIGRATION_37_38_MyNotes_To_Bookmarks = object : Migration(37, 38) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Bookmark` ADD COLUMN `lastUpdatedOn` INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE Bookmark SET lastUpdatedOn=createdAt")

            val c = db.query("SELECT * from mynote")
            val idIdx = c.getColumnIndex("_id")
            val keyIdx = c.getColumnIndex("key")
            val v11nIdx = c.getColumnIndex("versification")
            val myNoteIdx = c.getColumnIndex("mynote")
            val lastUpdatedOnIdx = c.getColumnIndex("last_updated_on")
            val createdOnIdx = c.getColumnIndex("created_on")

            c.moveToFirst()

            var labelId = -1L
            if(!c.isAfterLast) {
                val labelValues = ContentValues().apply {
                    put("name", BibleApplication.application.getString(R.string.migrated_my_notes))
                }
                labelId = db.insert("Label", CONFLICT_FAIL, labelValues)
            }

            while(!c.isAfterLast) {
                val id = c.getLong(idIdx)
                val key = c.getString(keyIdx)
                var v11n: Versification? = null
                var verseRange: VerseRange? = null
                var verseRangeInKjv: VerseRange? = null

                try {
                    v11n = Versifications.instance().getVersification(
                        c.getString(v11nIdx) ?: Versifications.DEFAULT_V11N
                    )
                    verseRange = VerseRangeFactory.fromString(v11n, key)
                    verseRangeInKjv = verseRange.toV11n(KJVA)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate bookmark: v11n:$v11n verseRange:$verseRange verseRangeInKjv:$verseRangeInKjv", e)
                    c.moveToNext()
                    continue
                }

                val createdAt = c.getLong(createdOnIdx)
                val lastUpdatedOn = c.getLong(lastUpdatedOnIdx)
                val myNote = c.getString(myNoteIdx)
                val newValues = ContentValues()
                newValues.apply {
                    put("v11n", v11n.name)
                    put("kjvOrdinalStart", verseRangeInKjv.start.ordinal)
                    put("kjvOrdinalEnd", verseRangeInKjv.end.ordinal)
                    put("ordinalStart", verseRange.start.ordinal)
                    put("ordinalEnd", verseRange.end.ordinal)
                    put("createdAt", createdAt)
                    put("lastUpdatedOn", lastUpdatedOn)
                    put("notes", myNote)
                }
                val bookmarkId = db.insert("Bookmark", CONFLICT_FAIL, newValues)

                val bookmarkLabelValues = ContentValues().apply {
                    put("bookmarkId", bookmarkId)
                    put("labelId", labelId)
                }
                db.insert("BookmarkToLabel", CONFLICT_FAIL, bookmarkLabelValues)
                c.moveToNext()
            }
            execSQL("DROP TABLE mynote;")
        }
    }
}

private val BOOKMARKS_LABEL_COLOR_38_39 = object : Migration(38, 39) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE Label SET name='${SPEAK_LABEL_NAME}' WHERE bookmarkStyle = 'SPEAK'")
        db.execSQL("ALTER TABLE `Label` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
        val c = db.query("SELECT * from Label")
        val idIdx = c.getColumnIndex("id")
        val bookmarkStyleIdx = c.getColumnIndex("bookmarkStyle")
        c.moveToFirst()
        while(!c.isAfterLast) {
            val id = c.getLong(idIdx)
            val bookmarkStyle = try {BookmarkStyle.valueOf(c.getString(bookmarkStyleIdx)) }
                                catch (e: Exception) {BookmarkStyle.BLUE_HIGHLIGHT}

            val newColor = bookmarkStyle.backgroundColor
            val newValues = ContentValues().apply {
                put("color", newColor)
            }
            db.update("Label", CONFLICT_FAIL, newValues, "id = ?", arrayOf(id));
            c.moveToNext()
        }
    }
}

private val JOURNAL_39_40 = object : Migration(39, 40) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `JournalTextEntry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `labelId` INTEGER NOT NULL, `text` TEXT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT -1, `indentLevel` INTEGER NOT NULL, FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_JournalTextEntry_labelId` ON `JournalTextEntry` (`labelId`)")

        db.execSQL("ALTER TABLE `BookmarkToLabel` ADD COLUMN `orderNumber` INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE `BookmarkToLabel` ADD COLUMN `indentLevel` INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_40_41_DocumentBackup = object : Migration(40, 41) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("""CREATE TABLE IF NOT EXISTS `DocumentBackup` (`osisId` TEXT PRIMARY KEY NOT NULL, `abbreviation` TEXT NOT NULL, `name` TEXT NOT NULL, `language` TEXT NOT NULL, `repository` TEXT NOT NULL);""")
        }
    }
}

private val MIGRATION_41_42_cipherKey = object : Migration(41, 42) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `DocumentBackup` ADD COLUMN `cipherKey` TEXT DEFAULT NULL")
            // Let's empty the db as we changed from book.osisId -> book.initials
            execSQL("DELETE FROM DocumentBackup")
        }
    }
}


private val MIGRATION_42_43_expandContent = object : Migration(42, 43) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `BookmarkToLabel` ADD COLUMN `expandContent` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

private val MIGRATION_43_44_topMargin = object : Migration(43, 44) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_topMargin` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_topMargin` INTEGER DEFAULT NULL")
        }
    }
}


private val MIGRATION_44_45_nullColors = object : Migration(44, 45) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        val white = -1
        val black = -16777216
        db.apply {
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_dayTextColor=${black} WHERE text_display_settings_colors_dayTextColor IS NULL");
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_nightTextColor=${white} WHERE text_display_settings_colors_nightTextColor IS NULL");
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_nightBackground=${black} WHERE text_display_settings_colors_nightBackground IS NULL");
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_dayBackground=${white} WHERE text_display_settings_colors_dayBackground IS NULL");
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_dayNoise=0 WHERE text_display_settings_colors_dayNoise IS NULL");
            db.execSQL("UPDATE `Workspace` SET text_display_settings_colors_nightNoise=0 WHERE text_display_settings_colors_nightNoise IS NULL");

            val isSpecific = """ (
                text_display_settings_colors_dayTextColor IS NOT NULL
                 OR text_display_settings_colors_nightTextColor IS NOT NULL
                 OR text_display_settings_colors_nightBackground IS NOT NULL
                 OR text_display_settings_colors_dayBackground IS NOT NULL
                 OR text_display_settings_colors_dayNoise IS NOT NULL
                 OR text_display_settings_colors_nightNoise IS NOT NULL
                )"""

            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_dayTextColor=${black} WHERE text_display_settings_colors_dayTextColor IS NULL AND $isSpecific")
            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_nightTextColor=${white} WHERE text_display_settings_colors_nightTextColor IS NULL AND $isSpecific");
            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_nightBackground=${black} WHERE text_display_settings_colors_nightBackground IS NULL AND $isSpecific");
            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_dayBackground=${white} WHERE text_display_settings_colors_dayBackground IS NULL AND $isSpecific");
            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_dayNoise=0 WHERE text_display_settings_colors_dayNoise IS NULL AND $isSpecific");
            db.execSQL("UPDATE `PageManager` SET text_display_settings_colors_nightNoise=0 WHERE text_display_settings_colors_nightNoise IS NULL AND $isSpecific");
        }
    }
}

private val MIGRATION_45_46_workspaceSpeakSettings = object : Migration(45, 46) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_speakSettings` TEXT DEFAULT NULL")
        }
    }
}

private val MIGRATION_46_47_primaryLabel = object : Migration(46, 47) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Bookmark` ADD COLUMN `primaryLabelId` INTEGER DEFAULT NULL REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
            execSQL("UPDATE `Bookmark` SET primaryLabelId = (SELECT labelId FROM BookmarkToLabel WHERE bookmarkId=Bookmark.id LIMIT 1)")
        }
    }
}

private val MIGRATION_47_48_autoAssignLabels = object : Migration(47, 48) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_favouriteLabels` TEXT DEFAULT NULL")
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_autoAssignLabels` TEXT DEFAULT NULL")
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_autoAssignPrimaryLabel` INTEGER DEFAULT NULL")
        }
    }
}

private val MIGRATION_48_49_anchorOrdinal = object : Migration(48, 49) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `PageManager` ADD COLUMN `commentary_anchorOrdinal` INTEGER DEFAULT NULL")
            execSQL("ALTER TABLE `PageManager` ADD COLUMN `dictionary_anchorOrdinal` INTEGER DEFAULT NULL")
            execSQL("ALTER TABLE `PageManager` ADD COLUMN `general_book_anchorOrdinal` INTEGER DEFAULT NULL")
            execSQL("ALTER TABLE `PageManager` ADD COLUMN `map_anchorOrdinal` INTEGER DEFAULT NULL")
            execSQL("ALTER TABLE `HistoryItem` ADD COLUMN `anchorOrdinal` INTEGER DEFAULT NULL")
        }
    }
}

private val MIGRATION_49_50_wholeVerseBookmark = object : Migration(49, 50) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Bookmark` ADD COLUMN `wholeVerse` INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE `Bookmark` SET wholeVerse = startOffset IS NULL")
        }
    }
}

private val MIGRATION_50_51_underlineStyleAndRecentLabels = object : Migration(50, 51) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Label` ADD COLUMN `underlineStyle` INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE `Label` ADD COLUMN `underlineStyleWholeVerse` INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_recentLabels` TEXT DEFAULT NULL")
        }
    }
}

private val MIGRATION_51_52_compareDocuments = object : Migration(51, 52) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_hideCompareDocuments` TEXT DEFAULT NULL")
        }
    }
}

private val MIGRATION_52_53_underline = object : Migration(52, 53) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
             execSQL("UPDATE `Label` SET underlineStyleWholeVerse=1, underlineStyle=1 WHERE bookmarkStyle='UNDERLINE'")
        }
    }
}

private val MIGRATION_53_54_booleanSettings = object : Migration(53, 54) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_primaryLabelId` ON `Bookmark` (`primaryLabelId`)")
            execSQL("CREATE TABLE IF NOT EXISTS `BooleanSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            execSQL("CREATE TABLE IF NOT EXISTS `StringSetting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `LongSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `DoubleSetting` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))")
            val sharedPreferences = CommonUtils.realSharedPreferences
            for((k, v) in sharedPreferences.all) {
                val values = ContentValues()
                values.put("key", k)
                when(v) {
                    is Long -> {
                        values.put("value", v)
                        db.insert("LongSetting", CONFLICT_IGNORE, values)
                    }
                    is Int -> {
                        values.put("value", v)
                        db.insert("LongSetting", CONFLICT_IGNORE, values)
                    }
                    is Boolean -> {
                        values.put("value", v)
                        db.insert("BooleanSetting", CONFLICT_IGNORE, values)
                    }
                    is String -> {
                        values.put("value", v)
                        db.insert("StringSetting", CONFLICT_IGNORE, values)
                    }
                    is Float -> {
                        values.put("value", v)
                        db.insert("DoubleSetting", CONFLICT_IGNORE, values)
                    }
                    is Double -> {
                        values.put("value", v)
                        db.insert("DoubleSetting", CONFLICT_IGNORE, values)
                    }
                    else -> {
                        Log.e(TAG, "Illegal value '$k', $v")
                    }
                }
            }
        }
    }
}

private val MIGRATION_54_55_bookmarkType = object : Migration(54, 55) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE Label ADD COLUMN type TEXT DEFAULT NULL")
            execSQL("ALTER TABLE Bookmark ADD COLUMN type TEXT DEFAULT NULL")
        }
    }
}

private val MIGRATION_55_56_limitAmbiguousSize = object : Migration(55, 56) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Workspace` ADD COLUMN `window_behavior_settings_limitAmbiguousModalSize` INTEGER DEFAULT 0")
        }
    }
}

private val MIGRATION_56_57_breaklines_in_notes = object : Migration(56, 57) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("UPDATE `Bookmark` SET notes = REPLACE(notes, '\n', '<br>') WHERE notes IS NOT NULL")
        }
    }
}

class DataBaseNotReady: Exception()

object DatabaseContainer {
    private var instance: AppDatabase? = null

    var ready: Boolean = false

    private fun backupDatabaseIfNeeded() {
        val dbPath = BibleApplication.application.getDatabasePath(DATABASE_NAME)
        var dbVersion: Int? = null
        try {
            val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, OPEN_READONLY)
            db.use { d ->
                val cursor = d.rawQuery("PRAGMA user_version", null)
                cursor.use { c ->
                    while (c.moveToNext()) {
                        dbVersion = c.getIntOrNull(0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Could not backup database. Maybe fresh install.")
        }
        if(dbVersion != null && dbVersion != DATABASE_VERSION) {
            val backupPath = CommonUtils.dbBackupPath
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupPath, "dbBackup-$dbVersion-$timeStamp.db")
            dbPath.copyTo(backupFile)
        }
    }

    val db: AppDatabase
        get () {
            if(!ready && !BibleApplication.application.isRunningTests) throw DataBaseNotReady()

            return instance ?: synchronized(this) {
                backupDatabaseIfNeeded()

                instance ?: Room.databaseBuilder(
                    BibleApplication.application, AppDatabase::class.java, DATABASE_NAME
                )
                    .allowMainThreadQueries()
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_11_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25,
                        MIGRATION_25_26,
                        CLEANUP_MIGRATION_26_27,
                        SQUASH_MIGRATION_10_27,
                        MIGRATION_27_28,
                        MIGRATION_28_29,
                        MIGRATION_29_30,
                        MIGRATION_30_31,
                        MIGRATION_31_32,
                        SQUASH_30_33,
                        MIGRATION_32_33,
                        MIGRATION_33_34_Bookmarks,
                        BOOKMARKS_BOOK_34_35,
                        WORKSPACE_BOOKMARK_35_36,
                        BOOKMARKS_BOOK_36_37,
                        MIGRATION_37_38_MyNotes_To_Bookmarks,
                        BOOKMARKS_LABEL_COLOR_38_39,
                        JOURNAL_39_40,
                        MIGRATION_40_41_DocumentBackup,
                        MIGRATION_41_42_cipherKey,
                        MIGRATION_42_43_expandContent,
                        MIGRATION_43_44_topMargin,
                        MIGRATION_44_45_nullColors,
                        MIGRATION_45_46_workspaceSpeakSettings,
                        MIGRATION_46_47_primaryLabel,
                        MIGRATION_47_48_autoAssignLabels,
                        MIGRATION_48_49_anchorOrdinal,
                        MIGRATION_49_50_wholeVerseBookmark,
                        MIGRATION_50_51_underlineStyleAndRecentLabels,
                        MIGRATION_51_52_compareDocuments,
                        MIGRATION_52_53_underline,
                        MIGRATION_53_54_booleanSettings,
                        MIGRATION_54_55_bookmarkType,
                        MIGRATION_55_56_limitAmbiguousSize,
                        MIGRATION_56_57_breaklines_in_notes,
                        // When adding new migrations, remember to increment DATABASE_VERSION too
                    )
                    .build()
                    .also { instance = it }
            }
        }
    fun reset() {
        synchronized(this) {
            db.close()
            instance = null
        }
    }
}
