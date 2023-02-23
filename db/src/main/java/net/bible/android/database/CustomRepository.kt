/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.serialization.Serializable

enum class CustomRepositoryType {
    SWORD_HTTPS, MY_BIBLE, UNSPECIFIED
}

@Entity
@Serializable
data class CustomRepository(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var type: CustomRepositoryType = CustomRepositoryType.UNSPECIFIED,
    var name: String = "",
    var domain: String? = null,
    var zipDir: String? = null,
    var rootDir: String? = null,
    var spec: String? = null,
) {
    fun toJSON(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJSON(str: String): CustomRepository = json.decodeFromString(serializer(), str)
    }
}

@Dao
interface CustomRepositoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: CustomRepository)

    @Delete
    fun delete(item: CustomRepository)

    @Insert
    fun insert(items: List<CustomRepository>)

    @Query("SELECT * from CustomRepository ORDER BY name")
    fun all(): List<CustomRepository>
}
