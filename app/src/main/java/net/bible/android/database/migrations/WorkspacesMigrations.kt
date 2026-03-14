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

private val resetMaximizedWindowId = makeMigration(1..2) { _db ->
    _db.execSQL("UPDATE Workspace SET maximizedWindowId=NULL")
}

private val removeFavouriteLabels = makeMigration(2..3) { _db ->
    _db.execSQL("ALTER TABLE Workspace DROP COLUMN workspace_settings_favouriteLabels")
}

private val addPageNumber = makeMigration(3..4) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showPageNumber` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showPageNumber` INTEGER DEFAULT NULL")
}

private val addCommentarySourceBookAndKey = makeMigration(4..5) { _db ->
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `commentary_sourceBookAndKey` TEXT DEFAULT NULL")
}

private val addPageManagerJsState = makeMigration(5..6) { _db ->
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `jsState` TEXT DEFAULT NULL")
}

private val addStudyPadCursors = makeMigration(6..7) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `workspace_settings_studyPadCursors` TEXT DEFAULT NULL")
}

private val addFootNotesInline = makeMigration(7..8) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showFootNotesInline` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showFootNotesInline` INTEGER DEFAULT NULL")
}

private val addRestoreButtonsVisible = makeMigration(8..9) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `workspace_settings_restoreButtonsVisible` INTEGER DEFAULT 1")
}

private val migrateStrongsMode = makeMigration(9..10) { _db ->
    // Renumber strongs modes: old 0=off,1=inline,2=links,3=hidden -> new 0=hidden,1=inline,2=links
    // Old hidden (3) -> new hidden (0). Old off (0) stays 0 (now means hidden).
    _db.execSQL("UPDATE `Workspace` SET `text_display_settings_strongsMode` = 0 WHERE `text_display_settings_strongsMode` = 3")
    _db.execSQL("UPDATE `PageManager` SET `text_display_settings_strongsMode` = 0 WHERE `text_display_settings_strongsMode` = 3")
}

private val addNonStrongsWordItalic = makeMigration(10..11) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_nonStrongsWordItalic` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_nonStrongsWordItalic` INTEGER DEFAULT NULL")
}

private val addTitleScrollButton = makeMigration(11..12) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showTitleScrollButton` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showTitleScrollButton` INTEGER DEFAULT NULL")
}

private val addLabelOverridesTable = makeMigration(12..13) { _db ->
    _db.execSQL("""
        CREATE TABLE IF NOT EXISTS `WorkspaceLabelOverride` (
            `workspaceId` BLOB NOT NULL,
            `labelId` BLOB NOT NULL,
            `overrideMode` INTEGER DEFAULT NULL,
            PRIMARY KEY(`workspaceId`, `labelId`),
            FOREIGN KEY(`workspaceId`) REFERENCES `Workspace`(`id`) ON DELETE CASCADE
        )
    """)
    _db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorkspaceLabelOverride_workspaceId` ON `WorkspaceLabelOverride` (`workspaceId`)")
}

private val addInfiniteScroll = makeMigration(13..14) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_infiniteScroll` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_infiniteScroll` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE Window ADD COLUMN `text_llmPromptId` BLOB DEFAULT NULL")
    _db.execSQL("ALTER TABLE Workspace ADD COLUMN `text_llmPromptId` BLOB DEFAULT NULL")
}

val workspacesMigrations: Array<Migration> = arrayOf(
    resetMaximizedWindowId,
    removeFavouriteLabels,
    addPageNumber,
    addCommentarySourceBookAndKey,
    addPageManagerJsState,
    addStudyPadCursors,
    addFootNotesInline,
    addRestoreButtonsVisible,
    migrateStrongsMode,
    addNonStrongsWordItalic,
    addTitleScrollButton,
    addLabelOverridesTable,
    addInfiniteScroll,
)

const val WORKSPACE_DATABASE_VERSION = 14
