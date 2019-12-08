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

package net.bible.android.database.workspaces

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

import java.util.*

// Room entities

@Entity(tableName = "bookmark")
data class Bookmark(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    @ColumnInfo(name = "created_on") val createdOn: Int?,
    val key: String,
    val versification: String?,
    @ColumnInfo(name = "speak_settings") val speakSettings: String?
)


@Entity(tableName = "readingplan", indices = [Index(value=["plan_code"])])
data class ReadingPlan(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    @ColumnInfo(name = "plan_code") val planCode: String,
    @ColumnInfo(name = "plan_start_date") val planStartDate: Int,
    @ColumnInfo(name = "plan_current_day") val planCurrentDay: Int = 1
)

@Entity(tableName = "readingplan_status",
    indices = [Index(name="code_day", value = ["plan_code", "plan_day"])]
)
data class ReadingPlanStatus(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    @ColumnInfo(name = "plan_code") val planCode: String,
    @ColumnInfo(name = "plan_day") val planDay: Int,
    @ColumnInfo(name = "reading_status") val readingStatus: String
)

@Entity(
    tableName = "bookmark_label",
    primaryKeys = ["bookmark_id", "label_id"],
    foreignKeys = [
        ForeignKey(entity = Bookmark::class, parentColumns = ["_id"], childColumns = ["bookmark_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Label::class, parentColumns = ["_id"], childColumns = ["label_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("label_id")
    ]
)
data class BookmarkToLabel(
    @ColumnInfo(name="bookmark_id") val bookmarkId: Int,
    @ColumnInfo(name="label_id") val labelId: Int
)

@Entity(tableName = "label")
data class Label(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "bookmark_style") val bookmarkStyle: String?
)

@Entity(tableName = "mynote", indices = [Index(name="mynote_key", value=["key"])])
data class MyNote(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    val key: String,
    val versification: String?,
    @ColumnInfo(name = "mynote") val myNote: String,
    @ColumnInfo(name = "last_updated_on") val lastUpdatedOn: Int?,
    @ColumnInfo(name = "created_on") val createdOn: Int?
)


class WorkspaceEntities {
    data class Page(
        val document: String?,
        val key: String?
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
        val document: String?
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
        @Embedded(prefix="map_") val mapPage: Page?
    )

    data class WindowLayout(
        val state: String,
        val weight: Float = 1.0f
    )

    @Entity
    data class Workspace(
        val name: String,

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
        val yOffsetRatio: Float,

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

