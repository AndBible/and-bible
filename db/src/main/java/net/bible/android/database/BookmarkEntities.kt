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
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

class BookmarkEntities {
    @Entity(tableName = "bookmark")
    data class Bookmark(
        @PrimaryKey @ColumnInfo(name = "_id") val id: Int?,
        @ColumnInfo(name = "created_on") val createdOn: Int?,
        val key: String,
        val versification: String?,
        @ColumnInfo(name = "speak_settings") val speakSettings: String?
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
        @ColumnInfo(name = "bookmark_id") val bookmarkId: Int,
        @ColumnInfo(name = "label_id") val labelId: Int
    )

    @Entity(tableName = "label")
    data class Label(
        @PrimaryKey @ColumnInfo(name = "_id") val id: Int?,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "bookmark_style") val bookmarkStyle: String?
    )
}
