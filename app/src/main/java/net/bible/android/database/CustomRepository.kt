/*
 * Copyright (c) 2023-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.*

val repoJson = Json {
    allowStructuredMapKeys = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Entity(indices = [Index("name", unique = true)])
@Serializable
data class CustomRepository(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val name: String,
    val description: String = "",
    val type: String,
    val host: String = "",
    val catalogDirectory: String = "",
    var packageDirectory: String = "",
    var manifestUrl: String? = null,
) {
    fun toJson(): String {
        return repoJson.encodeToString(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): CustomRepository? {
            return try {repoJson.decodeFromString(serializer(), jsonString) } catch (e: SerializationException) {
                Log.e("CustomRepository", "Could not decode repository", e)
                null
            }
        }
    }

}

@Dao
interface CustomRepositoryDao {
    @Insert
    suspend fun insert(item: CustomRepository)

    @Update
    suspend fun update(item: CustomRepository)

    @Delete
    suspend fun delete(item: CustomRepository)

    @Insert
    suspend fun insert(items: List<CustomRepository>)

    @Query("SELECT * from CustomRepository")
    suspend fun all(): List<CustomRepository>
}
