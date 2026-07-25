/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database.headings

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import net.bible.android.database.IdType

@Entity(indices = [Index("bookInitials", "ordinal")])
data class CustomHeading(
    @PrimaryKey var id: IdType = IdType(),
    var bookInitials: String,
    var v11n: String,
    var ordinal: Int,
    var level: Int,
    var text: String,
)

@Entity(indices = [Index(value = ["bookInitials", "ordinal", "titleIndex"], unique = true)])
data class HeadingOverride(
    @PrimaryKey var id: IdType = IdType(),
    var bookInitials: String,
    var v11n: String,
    var ordinal: Int,
    var titleIndex: Int,
    var newText: String? = null,
    var newLevel: Int? = null,
    var deleted: Boolean = false,
)

@Dao
interface HeadingsDao {
    @Insert
    fun insert(heading: CustomHeading)

    @Update
    fun update(heading: CustomHeading)

    @Query("DELETE FROM CustomHeading WHERE id = :id")
    fun deleteCustomHeading(id: IdType)

    @Query("SELECT * FROM CustomHeading WHERE id = :id")
    fun customHeadingById(id: IdType): CustomHeading?

    @Query("SELECT * FROM CustomHeading WHERE bookInitials = :bookInitials AND ordinal BETWEEN :startOrdinal AND :endOrdinal ORDER BY ordinal")
    fun customHeadingsForRange(bookInitials: String, startOrdinal: Int, endOrdinal: Int): List<CustomHeading>

    @Query("SELECT * FROM CustomHeading WHERE bookInitials = :bookInitials ORDER BY ordinal")
    fun allCustomHeadingsFor(bookInitials: String): List<CustomHeading>

    @Insert
    fun insert(override: HeadingOverride)

    @Update
    fun update(override: HeadingOverride)

    @Query("DELETE FROM HeadingOverride WHERE id = :id")
    fun deleteHeadingOverride(id: IdType)

    @Query("SELECT * FROM HeadingOverride WHERE id = :id")
    fun headingOverrideById(id: IdType): HeadingOverride?

    @Query("SELECT * FROM HeadingOverride WHERE bookInitials = :bookInitials AND ordinal = :ordinal AND titleIndex = :titleIndex")
    fun headingOverrideFor(bookInitials: String, ordinal: Int, titleIndex: Int): HeadingOverride?

    @Query("SELECT * FROM HeadingOverride WHERE bookInitials = :bookInitials AND ordinal BETWEEN :startOrdinal AND :endOrdinal ORDER BY ordinal, titleIndex")
    fun headingOverridesForRange(bookInitials: String, startOrdinal: Int, endOrdinal: Int): List<HeadingOverride>

    @Query("SELECT * FROM HeadingOverride WHERE bookInitials = :bookInitials ORDER BY ordinal, titleIndex")
    fun allHeadingOverridesFor(bookInitials: String): List<HeadingOverride>
}
