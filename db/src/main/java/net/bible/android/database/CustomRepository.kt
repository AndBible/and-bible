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

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.*

@Serializable
class SwordRepositoryManifest(
    val name: String,
    val description: String,
    val manifestUrl: String,
    val type: String,
    val host: String,
    val catalogDirectory: String,
    val packageDirectory: String
)

@Entity
@Serializable
data class CustomRepository(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var manifestUrl: String? = null,
    var manifestJsonContent: String? = null,
    @ColumnInfo(defaultValue = "0") var manifestLastUpdated: Long = System.currentTimeMillis(),
) {
    fun toJSON(): String = json.encodeToString(serializer(), this)

    val displayName: String get() = manifest?.name?: manifestUrl?: ""
    val displayDescription: String get() = manifest?.description?: ""

    val manifest: SwordRepositoryManifest? get() =
        if(manifestJsonContent == null) null
        else try {json.decodeFromString(SwordRepositoryManifest.serializer(), manifestJsonContent!!) }
        catch (e: SerializationException) {
            Log.e("CustomRepository", "Could not deserialize manifest")
            null
        }

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

    @Query("SELECT * from CustomRepository")
    fun all(): List<CustomRepository>
}
