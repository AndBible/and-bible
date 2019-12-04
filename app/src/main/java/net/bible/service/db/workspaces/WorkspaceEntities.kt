/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.service.db.workspaces

import net.bible.android.control.page.window.WindowLayout.WindowState
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.PrimaryKey

import java.util.*

class WorkspaceEntities {
    data class Page(
        val document: String,
        val key: String
    )

    data class Verse(
        val versification: String,
        val bibleBook: Int,
        val chapterNo: Int,
        val verseNo: Int
    )

    data class BiblePage(
        val document: String,
        @Embedded(prefix="verse") val verse: Verse
    )

    data class CommentaryPage(
        val document: String
    )

    data class PageManager(
        @Embedded(prefix="bible") val biblePage: BiblePage,
        @Embedded(prefix="commentary") val commentaryPage: CommentaryPage,
        @Embedded(prefix="dictionary") val dictionaryPage: Page,
        @Embedded(prefix="general_book") val generalBookPage: Page,
        @Embedded(prefix="map") val mapPage: Page
    )

    data class WindowLayout(
        val state: WindowState,
        val weight: Float = 1.0f
    )

    @Entity
    data class Workspace(
        @PrimaryKey(autoGenerate = true) val id: Long,
        val name: String
    )

    @Entity(foreignKeys = [
        ForeignKey(entity = Window::class, parentColumns = ["id"], childColumns = ["windowId"], onDelete = CASCADE)
    ])
    data class HistoryItem(
        @PrimaryKey(autoGenerate = true) val id: Long,
        val windowId: Int,
        val createdAt: Date = Date(System.currentTimeMillis()),
        val document: String,
        val key: String,
        val yOffsetRatio: Float
    )

    @Entity(foreignKeys = [
        ForeignKey(entity = Workspace::class, parentColumns = ["id"], childColumns = ["workspaceId"], onDelete = CASCADE)
    ])
    data class Window(
        @PrimaryKey(autoGenerate = true) val id: Long,
        val workspaceId: Int,
        val screenNo: Int,
        val isSynchronized: Boolean,
        val wasMinimised: Boolean,
        val orderNumber: Int,
        val isLinksWindow: Boolean,
        @Embedded(prefix="window_layout") val windowLayout: WindowLayout,
        @Embedded(prefix="page_manager") val pageManager: PageManager
    )
}

