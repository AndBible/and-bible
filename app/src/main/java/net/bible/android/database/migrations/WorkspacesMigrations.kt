/*
 * Copyright (c) 2023-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

val workspacesMigrations: Array<Migration> = arrayOf(
    resetMaximizedWindowId,
    removeFavouriteLabels,
    addPageNumber
)

const val WORKSPACE_DATABASE_VERSION = 4
