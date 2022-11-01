/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "DocumentBackup")
data class SwordDocumentInfo(
    @PrimaryKey @ColumnInfo(name = "osisId") var initials: String,
    var name: String,
    var abbreviation: String,
    var language: String,
    var repository: String,
    var cipherKey: String? = null,
)

@Dao
interface SwordDocumentInfoDao {
    @Insert(onConflict = REPLACE)
    fun insert(documents: List<SwordDocumentInfo>)

    @Insert(onConflict = REPLACE)
    fun insert(documents: SwordDocumentInfo)

    @Update
    fun update(doc: SwordDocumentInfo)

    @Query("""SELECT * FROM DocumentBackup WHERE osisId = :initials""")
    fun getBook(initials: String): SwordDocumentInfo?

    @Query("""SELECT * from DocumentBackup""")
    fun getKnownInstalled(): List<SwordDocumentInfo>

    @Query("""SELECT * from DocumentBackup WHERE cipherKey IS NOT NULL""")
    fun getUnlocked(): List<SwordDocumentInfo>

    @Query("""SELECT * from DocumentBackup""")
    fun getAll(): List<SwordDocumentInfo>

    @Query("""DELETE FROM DocumentBackup WHERE osisId = :initials""")
    fun deleteByOsisId(initials: String)

}
