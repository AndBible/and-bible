/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "Document") @Fts4
data class DocumentSearch(
    var osisId: String,
    var abbreviation: String,
    var name: String,
    var language: String,
    var repository: String,

    @PrimaryKey
    @ColumnInfo(name="rowid")
    val rowId: Long = 0
)

@Dao
interface DocumentSearchDao {
    @Insert
    fun insertDocuments(documentSearches: List<DocumentSearch>)

    @Query("""SELECT osisId from Document WHERE Document MATCH :search""")
    fun search(search: String): List<String>

    @Query("DELETE FROM Document")
    fun clear()
}
