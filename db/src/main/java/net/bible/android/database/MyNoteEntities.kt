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
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "mynote", indices = [Index(name="mynote_key", value=["key"])])
data class MyNote(
    @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
    val key: String,
    val versification: String?,
    @ColumnInfo(name = "mynote") val myNote: String,
    @ColumnInfo(name = "last_updated_on") val lastUpdatedOn: Int?,
    @ColumnInfo(name = "created_on") val createdOn: Int?
)
