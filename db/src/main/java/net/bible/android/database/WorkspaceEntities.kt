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

package net.bible.android.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

import java.util.*

class WorkspaceEntities {
    data class Page(
        val document: String?,
        val key: String?,
        val currentYOffsetRatio: Float?
    )

    data class Verse(
        val versification: String,
        val bibleBook: Int,
        val chapterNo: Int,
        val verseNo: Int
    )

    data class BiblePage(
        val document: String?,
        @Embedded(prefix="verse_") val verse: Verse
    )

    data class CommentaryPage(
        val document: String?,
        val currentYOffsetRatio: Float?
    )

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Window::class,
                parentColumns = ["id"],
                childColumns = ["windowId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("windowId", unique = true)
        ]
    )

    data class PageManager(
        @PrimaryKey var windowId: Long,
        @Embedded(prefix="bible_") val biblePage: BiblePage,
        @Embedded(prefix="commentary_") val commentaryPage: CommentaryPage?,
        @Embedded(prefix="dictionary_") val dictionaryPage: Page?,
        @Embedded(prefix="general_book_") val generalBookPage: Page?,
        @Embedded(prefix="map_") val mapPage: Page?,
        @Embedded(prefix="text_display_settings_") val textDisplaySettings: TextDisplaySettings?,

        val currentCategoryName: String
    )

    data class WindowLayout(
        val state: String,
        val weight: Float = 1.0f
    )

    data class TextDisplaySettings(
        var fontSize: Int? = null,
        var showStrongs: Boolean? = null,
        var showMorphology: Boolean? = null,
        var showFootNotes: Boolean? = null,
        var showRedLetters: Boolean? = null,
        var showSectionTitles: Boolean? = null,
        var showVerseNumbers: Boolean? = null,
        var showVersePerLine: Boolean? = null,
        var showBookmarks: Boolean? = null,
        var showMyNotes: Boolean? = null
    ) {
        companion object {
            val default = TextDisplaySettings(
                16,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                true,
                true
            )
        }
    }

    data class WindowBehaviorSettings(
        var enableTiltToScroll: Boolean = false,
        var enableReverseSplitMode: Boolean = false
    ) {
        companion object {
            var default = WindowBehaviorSettings(false, false)
        }
    }

    @Entity
    data class Workspace(
        val name: String,

        @Embedded(prefix="text_display_settings_") val textDisplaySettings: TextDisplaySettings?,
        @Embedded(prefix="window_behavior_settings_") val windowBehaviorSettings: WindowBehaviorSettings?,
        @PrimaryKey(autoGenerate = true) var id: Long = 0
    )

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Window::class,
                parentColumns = ["id"],
                childColumns = ["windowId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("windowId")
        ]
    )

    data class HistoryItem(
        val windowId: Long,
        val createdAt: Date,
        val document: String,
        val key: String,
        val yOffsetRatio: Float?,

        @PrimaryKey(autoGenerate = true) val id: Long = 0
    )

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Workspace::class,
                parentColumns = ["id"],
                childColumns = ["workspaceId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("workspaceId")
        ]
    )

    data class Window(
        var workspaceId: Long,
        val isSynchronized: Boolean,
        val wasMinimised: Boolean,
        val isLinksWindow: Boolean,
        @Embedded(prefix="window_layout_") val windowLayout: WindowLayout,

        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        var orderNumber: Int = 0
    )
}

