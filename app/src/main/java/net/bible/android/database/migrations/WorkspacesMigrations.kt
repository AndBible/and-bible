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
}

private val addGlobalTextDisplaySettings = makeMigration(14..15) { _db ->
    // Create GlobalTextDisplaySettings table with all TDS columns (same @Embedded prefix as Workspace)
    _db.execSQL("""
        CREATE TABLE IF NOT EXISTS `GlobalTextDisplaySettings` (
            `id` INTEGER NOT NULL PRIMARY KEY,
            `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL,
            `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL,
            `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL,
            `text_display_settings_strongsMode` INTEGER DEFAULT NULL,
            `text_display_settings_showMorphology` INTEGER DEFAULT NULL,
            `text_display_settings_showFootNotes` INTEGER DEFAULT NULL,
            `text_display_settings_showFootNotesInline` INTEGER DEFAULT NULL,
            `text_display_settings_expandXrefs` INTEGER DEFAULT NULL,
            `text_display_settings_showXrefs` INTEGER DEFAULT NULL,
            `text_display_settings_showRedLetters` INTEGER DEFAULT NULL,
            `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL,
            `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL,
            `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL,
            `text_display_settings_showBookmarks` INTEGER DEFAULT NULL,
            `text_display_settings_showMyNotes` INTEGER DEFAULT NULL,
            `text_display_settings_justifyText` INTEGER DEFAULT NULL,
            `text_display_settings_hyphenation` INTEGER DEFAULT NULL,
            `text_display_settings_topMargin` INTEGER DEFAULT NULL,
            `text_display_settings_fontSize` INTEGER DEFAULT NULL,
            `text_display_settings_fontFamily` TEXT DEFAULT NULL,
            `text_display_settings_lineSpacing` INTEGER DEFAULT NULL,
            `text_display_settings_bookmarksHideLabels` TEXT DEFAULT NULL,
            `text_display_settings_showPageNumber` INTEGER DEFAULT NULL,
            `text_display_settings_infiniteScroll` INTEGER DEFAULT NULL,
            `text_display_settings_nonStrongsWordItalic` INTEGER DEFAULT NULL,
            `text_display_settings_showTitleScrollButton` INTEGER DEFAULT NULL
        )
    """)

    // Null out workspace TDS fields that match hardcoded defaults so they inherit from global
    // Boolean defaults: true=1, false=0
    _db.execSQL("UPDATE Workspace SET text_display_settings_fontSize = NULL WHERE text_display_settings_fontSize = 16")
    _db.execSQL("UPDATE Workspace SET text_display_settings_fontFamily = NULL WHERE text_display_settings_fontFamily = 'sans-serif'")
    _db.execSQL("UPDATE Workspace SET text_display_settings_strongsMode = NULL WHERE text_display_settings_strongsMode = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showMorphology = NULL WHERE text_display_settings_showMorphology = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_expandXrefs = NULL WHERE text_display_settings_expandXrefs = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showFootNotes = NULL WHERE text_display_settings_showFootNotes = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showFootNotesInline = NULL WHERE text_display_settings_showFootNotesInline = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showXrefs = NULL WHERE text_display_settings_showXrefs = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showRedLetters = NULL WHERE text_display_settings_showRedLetters = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showSectionTitles = NULL WHERE text_display_settings_showSectionTitles = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showVerseNumbers = NULL WHERE text_display_settings_showVerseNumbers = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showVersePerLine = NULL WHERE text_display_settings_showVersePerLine = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showMyNotes = NULL WHERE text_display_settings_showMyNotes = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_justifyText = NULL WHERE text_display_settings_justifyText = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_hyphenation = NULL WHERE text_display_settings_hyphenation = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_topMargin = NULL WHERE text_display_settings_topMargin = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_lineSpacing = NULL WHERE text_display_settings_lineSpacing = 16")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showBookmarks = NULL WHERE text_display_settings_showBookmarks = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showPageNumber = NULL WHERE text_display_settings_showPageNumber = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_infiniteScroll = NULL WHERE text_display_settings_infiniteScroll = 1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_nonStrongsWordItalic = NULL WHERE text_display_settings_nonStrongsWordItalic = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_showTitleScrollButton = NULL WHERE text_display_settings_showTitleScrollButton = 0")
    // Colors: null out if they match default (white=-1, black=-16777216)
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_dayBackground = NULL WHERE text_display_settings_colors_dayBackground = -1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_dayTextColor = NULL WHERE text_display_settings_colors_dayTextColor = -16777216")
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_nightBackground = NULL WHERE text_display_settings_colors_nightBackground = -16777216")
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_nightTextColor = NULL WHERE text_display_settings_colors_nightTextColor = -1")
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_dayNoise = NULL WHERE text_display_settings_colors_dayNoise = 0")
    _db.execSQL("UPDATE Workspace SET text_display_settings_colors_nightNoise = NULL WHERE text_display_settings_colors_nightNoise = 0")
    // MarginSize: null out if matches default (3, 3, 170)
    _db.execSQL("UPDATE Workspace SET text_display_settings_margin_size_marginLeft = NULL WHERE text_display_settings_margin_size_marginLeft = 3")
    _db.execSQL("UPDATE Workspace SET text_display_settings_margin_size_marginRight = NULL WHERE text_display_settings_margin_size_marginRight = 3")
    _db.execSQL("UPDATE Workspace SET text_display_settings_margin_size_maxWidth = NULL WHERE text_display_settings_margin_size_maxWidth = 170")
}

private val migrateGlobalTdsIdToBlob = makeMigration(15..16) { _db ->
    // Recreate GlobalTextDisplaySettings with BLOB primary key (IdType) instead of INTEGER.
    // This is needed for device sync compatibility (sync system expects BLOB PKs).
    _db.execSQL("ALTER TABLE GlobalTextDisplaySettings RENAME TO GlobalTextDisplaySettings_old")
    _db.execSQL("""CREATE TABLE IF NOT EXISTS GlobalTextDisplaySettings (
            `id` BLOB NOT NULL,
            `text_display_settings_strongsMode` INTEGER DEFAULT NULL,
            `text_display_settings_showMorphology` INTEGER DEFAULT NULL,
            `text_display_settings_showFootNotes` INTEGER DEFAULT NULL,
            `text_display_settings_showFootNotesInline` INTEGER DEFAULT NULL,
            `text_display_settings_expandXrefs` INTEGER DEFAULT NULL,
            `text_display_settings_showXrefs` INTEGER DEFAULT NULL,
            `text_display_settings_showRedLetters` INTEGER DEFAULT NULL,
            `text_display_settings_showSectionTitles` INTEGER DEFAULT NULL,
            `text_display_settings_showVerseNumbers` INTEGER DEFAULT NULL,
            `text_display_settings_showVersePerLine` INTEGER DEFAULT NULL,
            `text_display_settings_showBookmarks` INTEGER DEFAULT NULL,
            `text_display_settings_showMyNotes` INTEGER DEFAULT NULL,
            `text_display_settings_justifyText` INTEGER DEFAULT NULL,
            `text_display_settings_hyphenation` INTEGER DEFAULT NULL,
            `text_display_settings_topMargin` INTEGER DEFAULT NULL,
            `text_display_settings_fontSize` INTEGER DEFAULT NULL,
            `text_display_settings_fontFamily` TEXT DEFAULT NULL,
            `text_display_settings_lineSpacing` INTEGER DEFAULT NULL,
            `text_display_settings_bookmarksHideLabels` TEXT DEFAULT NULL,
            `text_display_settings_showPageNumber` INTEGER DEFAULT NULL,
            `text_display_settings_infiniteScroll` INTEGER DEFAULT NULL,
            `text_display_settings_nonStrongsWordItalic` INTEGER DEFAULT NULL,
            `text_display_settings_showTitleScrollButton` INTEGER DEFAULT NULL,
            `text_display_settings_margin_size_marginLeft` INTEGER DEFAULT NULL,
            `text_display_settings_margin_size_marginRight` INTEGER DEFAULT NULL,
            `text_display_settings_margin_size_maxWidth` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayTextColor` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayBackground` INTEGER DEFAULT NULL,
            `text_display_settings_colors_dayNoise` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightTextColor` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightBackground` INTEGER DEFAULT NULL,
            `text_display_settings_colors_nightNoise` INTEGER DEFAULT NULL,
            PRIMARY KEY(`id`)
        )""")
    // Copy data with fixed BLOB id (00000000-0000-0000-0000-000000000001)
    _db.execSQL("""INSERT INTO GlobalTextDisplaySettings
        SELECT X'00000000000000000000000000000001',
            text_display_settings_strongsMode,
            text_display_settings_showMorphology,
            text_display_settings_showFootNotes,
            text_display_settings_showFootNotesInline,
            text_display_settings_expandXrefs,
            text_display_settings_showXrefs,
            text_display_settings_showRedLetters,
            text_display_settings_showSectionTitles,
            text_display_settings_showVerseNumbers,
            text_display_settings_showVersePerLine,
            text_display_settings_showBookmarks,
            text_display_settings_showMyNotes,
            text_display_settings_justifyText,
            text_display_settings_hyphenation,
            text_display_settings_topMargin,
            text_display_settings_fontSize,
            text_display_settings_fontFamily,
            text_display_settings_lineSpacing,
            text_display_settings_bookmarksHideLabels,
            text_display_settings_showPageNumber,
            text_display_settings_infiniteScroll,
            text_display_settings_nonStrongsWordItalic,
            text_display_settings_showTitleScrollButton,
            text_display_settings_margin_size_marginLeft,
            text_display_settings_margin_size_marginRight,
            text_display_settings_margin_size_maxWidth,
            text_display_settings_colors_dayTextColor,
            text_display_settings_colors_dayBackground,
            text_display_settings_colors_dayNoise,
            text_display_settings_colors_nightTextColor,
            text_display_settings_colors_nightBackground,
            text_display_settings_colors_nightNoise
        FROM GlobalTextDisplaySettings_old LIMIT 1""")
    _db.execSQL("DROP TABLE GlobalTextDisplaySettings_old")
}

private val addMarkAsReadButton = makeMigration(16..17) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showMarkAsReadButton` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showMarkAsReadButton` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showMarkAsReadButton` INTEGER DEFAULT NULL")
}

private val addMemorizationIndicators = makeMigration(17..18) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showMemorizationIndicators` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showMemorizationIndicators` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showMemorizationIndicators` INTEGER DEFAULT NULL")
}

private val addAutoTrackReading = makeMigration(18..19) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_autoTrackReading` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_autoTrackReading` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_autoTrackReading` INTEGER DEFAULT NULL")
}

private val addAiDocMarkers = makeMigration(19..20) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showAiDocMarkers` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showAiDocMarkers` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showAiDocMarkers` INTEGER DEFAULT NULL")
}

private val addPageScrollSettings = makeMigration(20..21) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_pageScrollAmount` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_pageScrollAmount` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_pageScrollAmount` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_scrollHelperLines` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_scrollHelperLines` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_scrollHelperLines` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_scrollHelperLineStyle` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_scrollHelperLineStyle` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_scrollHelperLineStyle` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showPageButtons` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showPageButtons` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showPageButtons` INTEGER DEFAULT NULL")
}

private val addShowOrdinals = makeMigration(21..22) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showOrdinals` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showOrdinals` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showOrdinals` INTEGER DEFAULT NULL")
}

private val addShowReadingProgress = makeMigration(22..23) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
}

private val addBackgroundImage = makeMigration(23..24) { _db ->
    for (table in listOf("Workspace", "PageManager", "GlobalTextDisplaySettings")) {
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_dayBackgroundImage` TEXT DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_nightBackgroundImage` TEXT DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_dayBackgroundImageOpacity` INTEGER DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_nightBackgroundImageOpacity` INTEGER DEFAULT NULL")
    }
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
    addGlobalTextDisplaySettings,
    migrateGlobalTdsIdToBlob,
    addMarkAsReadButton,
    addMemorizationIndicators,
    addAutoTrackReading,
    addAiDocMarkers,
    addPageScrollSettings,
    addShowOrdinals,
    addShowReadingProgress,
    addBackgroundImage,
)

const val WORKSPACE_DATABASE_VERSION = 24
