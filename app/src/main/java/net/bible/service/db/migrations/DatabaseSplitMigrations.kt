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

package net.bible.service.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import androidx.core.database.getStringOrNull
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.json

// from https://stackoverflow.com/questions/17277735/using-uuids-in-sqlite
const val GENERATE_UUID4_SQL =
    "LOWER(HEX(RANDOMBLOB(4)) || '-' || HEX(RANDOMBLOB(2)) || '-' || '4' || " +
    "SUBSTR(HEX(RANDOMBLOB(2)), 2) || '-' || SUBSTR('AB89', 1 + (ABS(RANDOM()) % 4) , 1) " +
    "|| SUBSTR(HEX(RANDOMBLOB(2)), 2) || '-' || HEX(RANDOMBLOB(6)))"

class DatabaseSplitMigrations(private val oldDb: SupportSQLiteDatabase) {

    private fun setPragmas(db: SQLiteDatabase)  = db.run {
        db.version = 1
        rawQuery("PRAGMA journal_mode=WAL;", null).close()
    }

    fun migrateAll() {
        readingPlanDb()
        repoDb()

        bookmarkDb()
        workspaceDb()
        settingsDb()
    }

    private fun copyData(db: SupportSQLiteDatabase, tableName: String, newTableName_: String? = null) = db.run {
        val newTableName = newTableName_?: tableName
        val cols = getColumnNamesJoined(oldDb, newTableName, "new")
        execSQL("INSERT INTO new.$newTableName ($cols) SELECT $cols FROM $tableName")
    }

    private fun bookmarkDb() {
        val dbFileName = application.getDatabasePath(BookmarkDatabase.dbFileName).absolutePath
        SQLiteDatabase.openDatabase(dbFileName, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { _db ->
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Bookmark` (`kjvOrdinalStart` INTEGER NOT NULL, `kjvOrdinalEnd` INTEGER NOT NULL, `ordinalStart` INTEGER NOT NULL, `ordinalEnd` INTEGER NOT NULL, `v11n` TEXT NOT NULL, `playbackSettings` TEXT, `id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `book` TEXT, `startOffset` INTEGER, `endOffset` INTEGER, `primaryLabelId` TEXT DEFAULT NULL, `notes` TEXT DEFAULT NULL, `lastUpdatedOn` INTEGER NOT NULL DEFAULT 0, `wholeVerse` INTEGER NOT NULL DEFAULT 0, `type` TEXT DEFAULT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`primaryLabelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_kjvOrdinalStart` ON `Bookmark` (`kjvOrdinalStart`)");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_kjvOrdinalEnd` ON `Bookmark` (`kjvOrdinalEnd`)");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_primaryLabelId` ON `Bookmark` (`primaryLabelId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Label` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL DEFAULT 0, `markerStyle` INTEGER NOT NULL DEFAULT 0, `markerStyleWholeVerse` INTEGER NOT NULL DEFAULT 0, `underlineStyle` INTEGER NOT NULL DEFAULT 0, `underlineStyleWholeVerse` INTEGER NOT NULL DEFAULT 0, `type` TEXT DEFAULT NULL, PRIMARY KEY(`id`))");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `StudyPadTextEntry` (`id` TEXT NOT NULL, `labelId` TEXT NOT NULL, `text` TEXT NOT NULL, `orderNumber` INTEGER NOT NULL, `indentLevel` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_StudyPadTextEntry_labelId` ON `StudyPadTextEntry` (`labelId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `BookmarkToLabel` (`bookmarkId` TEXT NOT NULL, `labelId` TEXT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT -1, `indentLevel` INTEGER NOT NULL DEFAULT 0, `expandContent` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`bookmarkId`, `labelId`), FOREIGN KEY(`bookmarkId`) REFERENCES `Bookmark`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_BookmarkToLabel_labelId` ON `BookmarkToLabel` (`labelId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Edit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tableName` TEXT NOT NULL, `entityId` TEXT NOT NULL, `editType` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL DEFAULT 0)");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Edit_tableName_entityId` ON `Edit` (`tableName`, `entityId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ad8bce0fdae538e87d8960774e021957')");
            setPragmas(_db)
        }
        oldDb.apply {
            execSQL("ATTACH DATABASE '${dbFileName}' AS new")
            execSQL("PRAGMA foreign_keys=OFF;")

            // Create temporary UUID mapping tables
            execSQL("CREATE TABLE LabelMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO LabelMap SELECT id, $GENERATE_UUID4_SQL FROM Label")
            execSQL("CREATE TABLE BookmarkMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO BookmarkMap SELECT id, $GENERATE_UUID4_SQL FROM Bookmark")
            execSQL("CREATE TABLE StudyPadMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO StudyPadMap SELECT id, $GENERATE_UUID4_SQL FROM JournalTextEntry")

            val labelNewNames = getColumnNames(oldDb, "Label", "new").map {
                when (it) {
                    "id" -> "m.`uuid`"
                    else -> "l.`${it}`"
                }
            }
            execSQL("INSERT INTO new.Label SELECT ${labelNewNames.joinToString(",")} FROM Label l INNER JOIN LabelMap m ON m.id = l.id")

            val bmNewNames = getColumnNames(oldDb, "Bookmark", "new").map {
                when (it) {
                    "id" -> "bm.`uuid`"
                    "primaryLabelId" -> "lm.`uuid`"
                    else -> "b.`${it}`"
                }
            }
            execSQL(
                "INSERT INTO new.Bookmark SELECT ${bmNewNames.joinToString(",")} FROM Bookmark b " +
                "INNER JOIN BookmarkMap bm ON b.id = bm.id " +
                "LEFT OUTER JOIN LabelMap lm ON b.primaryLabelId = lm.id"
            )

            val studyPadNames = getColumnNames(oldDb, "StudyPadTextEntry", "new").map {
                when (it) {
                    "id" -> "m.`uuid`"
                    "labelId" -> "l.`uuid`"
                    else -> "j.`${it}`"
                }
            }
            execSQL("INSERT INTO new.StudyPadTextEntry SELECT ${studyPadNames.joinToString(",")} FROM JournalTextEntry j " +
                "INNER JOIN StudyPadMap m ON m.id = j.id " +
                "INNER JOIN LabelMap l ON j.labelId = l.id")

            val bookmarkToLabelNames = getColumnNames(oldDb, "BookmarkToLabel", "new").map {
                when (it) {
                    "labelId" -> "lm.`uuid`"
                    "bookmarkId" -> "bm.`uuid`"
                    else -> "bl.`${it}`"
                }
            }

            execSQL("INSERT INTO new.BookmarkToLabel SELECT ${bookmarkToLabelNames.joinToString(",")} FROM BookmarkToLabel bl " +
                "INNER JOIN BookmarkMap bm ON bl.bookmarkId = bm.id " +
                "INNER JOIN LabelMap lm ON bl.labelId = lm.id")

            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE new")
        }
    }

    private fun readingPlanDb() {
        val dbFileName = application.getDatabasePath(ReadingPlanDatabase.dbFileName).absolutePath
        SQLiteDatabase.openDatabase(dbFileName, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { _db ->
            _db.execSQL("CREATE TABLE IF NOT EXISTS `ReadingPlan` (`planCode` TEXT NOT NULL, `planStartDate` INTEGER NOT NULL, `planCurrentDay` INTEGER NOT NULL DEFAULT 1, `id` TEXT NOT NULL, PRIMARY KEY(`id`))");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ReadingPlan_planCode` ON `ReadingPlan` (`planCode`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `ReadingPlanStatus` (`planCode` TEXT NOT NULL, `planDay` INTEGER NOT NULL, `readingStatus` TEXT NOT NULL, `id` TEXT NOT NULL, PRIMARY KEY(`id`))");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ReadingPlanStatus_planCode_planDay` ON `ReadingPlanStatus` (`planCode`, `planDay`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Edit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tableName` TEXT NOT NULL, `entityId` TEXT NOT NULL, `editType` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL DEFAULT 0)");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Edit_tableName_entityId` ON `Edit` (`tableName`, `entityId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ffa05a357b03967674b9317e22ef7fc2')");
            setPragmas(_db)
        }
        oldDb.apply {
            execSQL("ATTACH DATABASE '${dbFileName}' AS new")
            execSQL("PRAGMA foreign_keys=OFF;")

            execSQL("CREATE TABLE ReadingPlanMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO ReadingPlanMap SELECT _id, $GENERATE_UUID4_SQL FROM readingplan")
            execSQL("CREATE TABLE ReadingPlanStatusMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO ReadingPlanStatusMap SELECT _id, $GENERATE_UUID4_SQL FROM readingplan_status")

            val readingPlanNames = getColumnNames(oldDb, "ReadingPlan", "new").map {
                when (it) {
                    "id" -> "m.`uuid`"
                    "planCode" -> "r.plan_code"
                    "planStartDate" -> "r.plan_start_date"
                    "planCurrentDay" -> "r.plan_current_day"
                    else -> throw IllegalArgumentException("Unknown column name: $it")
                }
            }
            execSQL("INSERT INTO new.ReadingPlan SELECT ${readingPlanNames.joinToString(",")} FROM " +
                "readingplan r INNER JOIN ReadingPlanMap m ON m.id = r._id")

            val readingPlanStatusNames = getColumnNames(oldDb, "ReadingPlanStatus", "new").map {
                when (it) {
                    "id" -> "m.`uuid`"
                    "planCode" -> "r.plan_code"
                    "planDay" -> "r.plan_day"
                    "readingStatus" -> "r.reading_status"
                    else -> throw IllegalArgumentException("Unknown column name: $it")
                }
            }
            execSQL("INSERT INTO new.ReadingPlanStatus SELECT ${readingPlanStatusNames.joinToString(",")} FROM " +
                "readingplan_status r INNER JOIN ReadingPlanStatusMap m ON m.id = r._id")

            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE new")
        }
    }

    private fun workspaceDb() {
        val dbFileName = application.getDatabasePath(WorkspaceDatabase.dbFileName).absolutePath
        SQLiteDatabase.openDatabase(
            dbFileName,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        ).use { _db ->
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Workspace` (`name` TEXT NOT NULL, `contentsText` TEXT, `id` TEXT NOT NULL, `orderNumber` INTEGER NOT NULL DEFAULT 0, `unPinnedWeight` REAL DEFAULT NULL, `maximizedWindowId` TEXT, `primaryTargetLinksWindowId` TEXT DEFAULT NULL, `text_display_settings_strongsMode` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_expandXrefs` INTEGER DEFAULT NULL, `text_display_settings_showXrefs` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_topMargin` INTEGER DEFAULT NULL, `text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_fontFamily` TEXT DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_bookmarksHideLabels` TEXT DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, `workspace_settings_enableTiltToScroll` INTEGER DEFAULT 0, `workspace_settings_enableReverseSplitMode` INTEGER DEFAULT 0, `workspace_settings_autoPin` INTEGER DEFAULT 1, `workspace_settings_speakSettings` TEXT DEFAULT NULL, `workspace_settings_recentLabels` TEXT DEFAULT NULL, `workspace_settings_favouriteLabels` TEXT DEFAULT NULL, `workspace_settings_autoAssignLabels` TEXT DEFAULT NULL, `workspace_settings_autoAssignPrimaryLabel` TEXT DEFAULT NULL, `workspace_settings_hideCompareDocuments` TEXT DEFAULT NULL, `workspace_settings_limitAmbiguousModalSize` INTEGER DEFAULT 0, `workspace_settings_workspaceColor` INTEGER DEFAULT NULL, PRIMARY KEY(`id`))");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Window` (`workspaceId` TEXT NOT NULL, `isSynchronized` INTEGER NOT NULL, `isPinMode` INTEGER NOT NULL, `isLinksWindow` INTEGER NOT NULL, `id` TEXT NOT NULL, `orderNumber` INTEGER NOT NULL, `targetLinksWindowId` TEXT DEFAULT NULL, `syncGroup` INTEGER NOT NULL DEFAULT 0, `window_layout_state` TEXT NOT NULL, `window_layout_weight` REAL NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workspaceId`) REFERENCES `Workspace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_Window_workspaceId` ON `Window` (`workspaceId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `HistoryItem` (`windowId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `document` TEXT NOT NULL, `key` TEXT NOT NULL, `anchorOrdinal` INTEGER DEFAULT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            _db.execSQL("CREATE INDEX IF NOT EXISTS `index_HistoryItem_windowId` ON `HistoryItem` (`windowId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `PageManager` (`windowId` TEXT NOT NULL, `currentCategoryName` TEXT NOT NULL, `bible_document` TEXT, `bible_verse_versification` TEXT NOT NULL, `bible_verse_bibleBook` INTEGER NOT NULL, `bible_verse_chapterNo` INTEGER NOT NULL, `bible_verse_verseNo` INTEGER NOT NULL, `commentary_document` TEXT, `commentary_anchorOrdinal` INTEGER DEFAULT NULL, `dictionary_document` TEXT, `dictionary_key` TEXT, `dictionary_anchorOrdinal` INTEGER DEFAULT NULL, `general_book_document` TEXT, `general_book_key` TEXT, `general_book_anchorOrdinal` INTEGER DEFAULT NULL, `map_document` TEXT, `map_key` TEXT, `map_anchorOrdinal` INTEGER DEFAULT NULL, `text_display_settings_strongsMode` INTEGER DEFAULT NULL, `text_display_settings_showMorphology` INTEGER DEFAULT NULL, `text_display_settings_showFootNotes` INTEGER DEFAULT NULL, `text_display_settings_expandXrefs` INTEGER DEFAULT NULL, `text_display_settings_showXrefs` INTEGER DEFAULT NULL, `text_display_settings_showRedLetters` INTEGER DEFAULT NULL, `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL, `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL, `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL, `text_display_settings_showBookmarks` INTEGER DEFAULT NULL, `text_display_settings_showMyNotes` INTEGER DEFAULT NULL, `text_display_settings_justifyText` INTEGER DEFAULT NULL, `text_display_settings_hyphenation` INTEGER DEFAULT NULL, `text_display_settings_topMargin` INTEGER DEFAULT NULL, `text_display_settings_fontSize` INTEGER DEFAULT NULL, `text_display_settings_fontFamily` TEXT DEFAULT NULL, `text_display_settings_lineSpacing` INTEGER DEFAULT NULL, `text_display_settings_bookmarksHideLabels` TEXT DEFAULT NULL, `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL, `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL, `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL, `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL, `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL, `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL, `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL, PRIMARY KEY(`windowId`), FOREIGN KEY(`windowId`) REFERENCES `Window`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PageManager_windowId` ON `PageManager` (`windowId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS `Edit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tableName` TEXT NOT NULL, `entityId` TEXT NOT NULL, `editType` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL DEFAULT 0)");
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Edit_tableName_entityId` ON `Edit` (`tableName`, `entityId`)");
            _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'edae5d5529763a48e424de74fa72b232')");
            setPragmas(_db);
        }
        oldDb.apply {
            execSQL("ATTACH DATABASE '${dbFileName}' AS new")
            execSQL("PRAGMA foreign_keys=OFF;")

            execSQL("CREATE TABLE WorkspaceMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO WorkspaceMap SELECT id, $GENERATE_UUID4_SQL FROM Workspace")
            execSQL("CREATE TABLE WindowMap (id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO WindowMap SELECT id, $GENERATE_UUID4_SQL FROM Window")

            val workspaceNames = getColumnNames(oldDb, "Workspace", "new").map {
                when {
                    it == "id" -> "m.`uuid`"
                    it == "primaryTargetLinksWindowId" -> "win.`uuid`"
                    it == "workspace_settings_autoAssignPrimaryLabel" -> "l.`uuid`"
                    it == "text_display_settings_bookmarksHideLabels" -> "ws.`text_display_settings_bookmarks_showLabels`"
                    it == "text_display_settings_fontSize" -> "ws.`text_display_settings_font_fontSize`"
                    it == "text_display_settings_fontFamily" -> "ws.`text_display_settings_font_fontFamily`"
                    it == "text_display_settings_strongsMode" -> "ws.`text_display_settings_showStrongs`"
                    it.startsWith("workspace_settings_") -> "ws.`${it.replace("workspace_settings_", "window_behavior_settings_")}`"
                    else -> "ws.`${it}`"
                }
            }
            execSQL(
                "INSERT INTO new.Workspace SELECT ${workspaceNames.joinToString(",")} FROM Workspace ws " +
                "INNER JOIN WorkspaceMap m ON ws.id = m.id " +
                "OUTER LEFT JOIN WindowMap win ON ws.primaryTargetLinksWindowId = win.id " +
                "OUTER LEFT JOIN LabelMap l ON ws.window_behavior_settings_autoAssignPrimaryLabel = l.id "
            )

            val windowNames = getColumnNames(oldDb, "Window", "new").map {
                when (it) {
                    "id" -> "m.`uuid`"
                    "workspaceId" -> "ws.`uuid`"
                    "targetLinksWindowId" -> "tw.`uuid`"
                    else -> "win.`${it}`"
                }
            }
            execSQL(
                "INSERT INTO new.Window SELECT ${windowNames.joinToString(",")} FROM Window win " +
                "INNER JOIN WindowMap m ON win.id = m.id " +
                "INNER JOIN WorkspaceMap ws ON win.workspaceId = ws.id " +
                "OUTER LEFT JOIN WindowMap tw ON win.targetLinksWindowId = tw.id"
            )

            val historyItemNames = getColumnNames(oldDb, "HistoryItem", "new").map {
                when (it) {
                    "windowId" -> "win.`uuid`"
                    else -> "h.`${it}`"
                }
            }
            execSQL(
                "INSERT INTO new.HistoryItem SELECT ${historyItemNames.joinToString(",")} FROM HistoryItem h " +
                "INNER JOIN WindowMap win ON h.windowId = win.id"
            )

            val pageManagerNames = getColumnNames(oldDb, "PageManager", "new").map {
                when (it) {
                    "windowId" -> "m.`uuid`"
                    "text_display_settings_bookmarksHideLabels" -> "p.`text_display_settings_bookmarks_showLabels`"
                    "text_display_settings_fontSize" -> "p.`text_display_settings_font_fontSize`"
                    "text_display_settings_fontFamily" -> "p.`text_display_settings_font_fontFamily`"
                    "text_display_settings_strongsMode" -> "p.`text_display_settings_showStrongs`"
                    else -> "p.`${it}`"
                }
            }
            execSQL(
                "INSERT INTO new.PageManager SELECT ${pageManagerNames.joinToString(",")} FROM PageManager p " +
                    "INNER JOIN WindowMap m ON p.windowId = m.id"
            )

            @Serializable
            data class OldRecentLabel(val labelId: Long, var lastAccess: Long)
            @Serializable
            data class NewRecentLabel(val labelId: String, var lastAccess: Long)

            val workspaceSettings = arrayOf(
                "recentLabels",
                "favouriteLabels",
                "autoAssignLabels",
            ).joinToString(",") { "`workspace_settings_$it`" }

            val labelMap = mutableMapOf<Long, String>()
            query("SELECT id,uuid FROM LabelMap").use {
                while (it.moveToNext()) {
                    labelMap[it.getLong(0)] = it.getString(1)
                }
            }

            query("SELECT id,$workspaceSettings,`text_display_settings_bookmarksHideLabels` FROM new.Workspace").use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getString(0)
                    val recentLabels: List<OldRecentLabel> = json.decodeFromString(serializer(), cur.getString(1))
                    val favouriteLabels: List<Long> = json.decodeFromString(serializer(), cur.getString(2))
                    val autoAssignLabels: List<Long> = json.decodeFromString(serializer(), cur.getString(3))
                    val newRecentLabels: String = json.encodeToString(serializer(), recentLabels.mapNotNull { OldRecentLabel ->
                        val labelId = labelMap[OldRecentLabel.labelId] ?: return@mapNotNull null
                        NewRecentLabel(labelId, OldRecentLabel.lastAccess)
                    })
                    val newFavouriteLabels: String = json.encodeToString(serializer(), favouriteLabels.mapNotNull { labelMap[it] })
                    val newAutoAssignLabels: String = json.encodeToString(serializer(), autoAssignLabels.mapNotNull { labelMap[it] })
                    val bookmarksHideLabelsStr = cur.getStringOrNull(4)
                    val newBookmarksHideLabels: String? = if(bookmarksHideLabelsStr != null) {
                        val bookmarksHideLabels: List<Long> =
                            json.decodeFromString(serializer(), bookmarksHideLabelsStr)
                        json.encodeToString(serializer(), bookmarksHideLabels.mapNotNull { labelMap[it] })
                    } else null

                    update("new.Workspace", CONFLICT_ABORT, ContentValues().apply {
                        put("workspace_settings_recentLabels", newRecentLabels)
                        put("workspace_settings_favouriteLabels", newFavouriteLabels)
                        put("workspace_settings_autoAssignLabels", newAutoAssignLabels)
                        put("text_display_settings_bookmarksHideLabels", newBookmarksHideLabels)
                    }, "id = ?", arrayOf(id))
                }
            }

            query("SELECT windowId,text_display_settings_bookmarksHideLabels FROM new.PageManager WHERE text_display_settings_bookmarksHideLabels IS NOT NULL").use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getString(0)
                    val bookmarksHideLabelsStr = cur.getString(1)
                    val newBookmarksHideLabels: String = run {
                        val bookmarksHideLabels: List<Long> =
                            json.decodeFromString(serializer(), bookmarksHideLabelsStr)
                        json.encodeToString(serializer(), bookmarksHideLabels.mapNotNull { labelMap[it] })
                    }
                    update("new.PageManager", CONFLICT_ABORT, ContentValues().apply {
                        put("text_display_settings_bookmarksHideLabels", newBookmarksHideLabels)
                    }, "windowId = ?", arrayOf(id))
                }
            }

            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE new")
        }
    }

    private fun repoDb() {
        val dbFileName = application.getDatabasePath(RepoDatabase.dbFileName).absolutePath
        SQLiteDatabase.openDatabase(dbFileName, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db -> db.run {
            execSQL("CREATE TABLE IF NOT EXISTS `CustomRepository` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `type` TEXT NOT NULL, `host` TEXT NOT NULL, `catalogDirectory` TEXT NOT NULL, `packageDirectory` TEXT NOT NULL, `manifestUrl` TEXT)");
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_CustomRepository_name` ON `CustomRepository` (`name`)");
            execSQL("CREATE TABLE IF NOT EXISTS `SwordDocumentInfo` (`initials` TEXT NOT NULL, `name` TEXT NOT NULL, `abbreviation` TEXT NOT NULL, `language` TEXT NOT NULL, `repository` TEXT NOT NULL, `cipherKey` TEXT, PRIMARY KEY(`initials`))");
            execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c1bf74115c370f8176d77ec922e6a1f9')");
            setPragmas(this)
        }}
        oldDb.apply {
            execSQL("ATTACH DATABASE '${dbFileName}' AS new")
            execSQL("PRAGMA foreign_keys=OFF;")
            copyData(oldDb, "CustomRepository")
            val cols = getColumnNames(oldDb, "SwordDocumentInfo", "new").map {
                when (it) {
                    "initials" -> "`osisId`"
                    else -> "`${it}`"
                }
            }
            execSQL("INSERT INTO new.SwordDocumentInfo SELECT ${cols.joinToString(",")} FROM DocumentBackup")
            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE new")
        }
    }

    private fun settingsDb() {
        val dbFileName = application.getDatabasePath(SettingsDatabase.dbFileName).absolutePath
        SQLiteDatabase.openDatabase(dbFileName, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            db.run {
                execSQL("CREATE TABLE IF NOT EXISTS `BooleanSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
                execSQL("CREATE TABLE IF NOT EXISTS `StringSetting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
                execSQL("CREATE TABLE IF NOT EXISTS `LongSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
                execSQL("CREATE TABLE IF NOT EXISTS `DoubleSetting` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))");
                execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
                execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '766325557c50c33c7c4f6857d0cba8ad')");
                setPragmas(this)
            }
        }
        oldDb.apply {
            execSQL("ATTACH DATABASE '${dbFileName}' AS new")
            execSQL("PRAGMA foreign_keys=OFF;")
            copyData(oldDb, "BooleanSetting")
            copyData(oldDb, "StringSetting")
            copyData(oldDb, "LongSetting")
            copyData(oldDb, "DoubleSetting")

            // Migrate current_workspace_id
            val oldWorkspaceId = query("SELECT value FROM new.LongSetting WHERE key = 'current_workspace_id'").use { cur ->
                if (cur.moveToNext()) cur.getLong(0) else null
            }
            delete("new.LongSetting", "key = 'current_workspace_id'", null)

            val newWorkspaceId = query("SELECT uuid FROM WorkspaceMap WHERE id = ?", arrayOf(oldWorkspaceId)).use { cur ->
                if (cur.moveToNext()) cur.getString(0) else null
            }
            if(oldWorkspaceId != null && newWorkspaceId != null) {
                insert("new.StringSetting", CONFLICT_ABORT, ContentValues().apply {
                    put("key", "current_workspace_id")
                    put("value", newWorkspaceId)
                })
            }

            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE new")
        }
    }
}
