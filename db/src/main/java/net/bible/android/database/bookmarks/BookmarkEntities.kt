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

package net.bible.android.database.bookmarks

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

class BookmarkEntities {
    @Entity
    data class Bookmark(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        var createdOn: Long? = null,
        val key: String,
        val versification: String?,
        val playbackSettings: String?
    )


    @Entity(
        primaryKeys = ["bookmarkId", "labelId"],
        foreignKeys = [
            ForeignKey(entity = Bookmark::class, parentColumns = ["id"], childColumns = ["bookmarkId"], onDelete = ForeignKey.CASCADE),
            ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            Index("labelId")
        ]
    )
    data class BookmarkToLabel(
        val bookmarkId: Long,
        val labelId: Long
    )

    @Entity
    data class Label(
        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        val name: String,
        val bookmarkStyle: String?
    )
}
