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

import androidx.room.ColumnInfo
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
        val currentCategoryName: String,
        @Embedded(prefix="text_display_settings_") val textDisplaySettings: TextDisplaySettings?
    )

    data class WindowLayout(
        val state: String,
        val weight: Float = 1.0f
    )

    data class TextDisplaySettings(
        @ColumnInfo(defaultValue = "NULL") var fontSize: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var showStrongs: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMorphology: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showFootNotes: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showRedLetters: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showSectionTitles: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVerseNumbers: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVersePerLine: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showBookmarks: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMyNotes: Boolean? = null
    ) {
        enum class Id {
            STRONGS, MORPH, FOOTNOTES, REDLETTERS, SECTIONTITLES, VERSENUMBERS, VERSEPERLINE, BOOKMARKS, MYNOTES
        }

        fun getBooleanValue(type: Id) = when(type) {
            Id.STRONGS -> showStrongs
            Id.MORPH -> showMorphology
            Id.FOOTNOTES -> showFootNotes
            Id.REDLETTERS -> showRedLetters
            Id.SECTIONTITLES -> showSectionTitles
            Id.VERSENUMBERS -> showVerseNumbers
            Id.VERSEPERLINE -> showVersePerLine
            Id.BOOKMARKS -> showBookmarks
            Id.MYNOTES -> showMyNotes
        }

        fun setBooleanValue(type: Id, value: Boolean?) {
            when(type) {
                Id.STRONGS -> showStrongs = value
                Id.MORPH -> showMorphology = value
                Id.FOOTNOTES -> showFootNotes = value
                Id.REDLETTERS -> showRedLetters = value
                Id.SECTIONTITLES -> showSectionTitles = value
                Id.VERSENUMBERS -> showVerseNumbers = value
                Id.VERSEPERLINE -> showVersePerLine = value
                Id.BOOKMARKS -> showBookmarks = value
                Id.MYNOTES -> showMyNotes = value
            }
        }

        fun setNonSpecific(type: Id) {
            setBooleanValue(type, null)
        }

        companion object {
            val default get() = TextDisplaySettings(
                fontSize = 16,
                showStrongs = false,
                showMorphology = false,
                showFootNotes = false,
                showRedLetters = true,
                showSectionTitles = true,
                showVerseNumbers = true,
                showVersePerLine = false,
                showBookmarks = true,
                showMyNotes = true
            )
        }
    }

    data class WindowBehaviorSettings(
        @ColumnInfo(defaultValue = "FALSE") var enableTiltToScroll: Boolean = false,
        @ColumnInfo(defaultValue = "FALSE") var enableReverseSplitMode: Boolean = false
    ) {
        companion object {
            val default get() = WindowBehaviorSettings(
                enableTiltToScroll = false,
                enableReverseSplitMode = false
            )
        }
    }

    @Entity
    data class Workspace(
        val name: String,
        @PrimaryKey(autoGenerate = true) var id: Long = 0,

        @Embedded(prefix="text_display_settings_") val textDisplaySettings: TextDisplaySettings? = TextDisplaySettings(),
        @Embedded(prefix="window_behavior_settings_") val windowBehaviorSettings: WindowBehaviorSettings? = WindowBehaviorSettings()
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

