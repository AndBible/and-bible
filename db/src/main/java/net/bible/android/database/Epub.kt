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
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.bible.android.database.migrations.Migration
import net.bible.android.database.migrations.makeMigration
import org.jdom2.Element

@Entity
class EpubHtmlToFrag(
    @PrimaryKey val htmlId: String, // contains filename and id
    val fragId: Long,
)

@Entity
class EpubFragment(
    val epubDocumentId: Long,
    val originalHtmlFileName: String,
    val ordinalStart: Int,
    val ordinalEnd: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
) {
    @Ignore var element: Element? = null
}

@Dao
interface EpubDao {
    @Insert fun insert(vararg items: EpubFragment)
    @Insert fun insert(vararg items: EpubHtmlToFrag)

    @Query(
        "SELECT f.* FROM EpubFragment f " +
        "JOIN EpubHtmlToFrag e2f ON e2f.fragId = f.id " +
        "WHERE e2f.htmlId=:htmlId"
    )
    fun getFragment(htmlId: String): EpubFragment

    @Query("SELECT * from EpubFragment WHERE id=:id")
    fun getFragment(id: Long): EpubFragment

    @Query("SELECT f.* FROM EpubFragment f")
    fun fragments(): List<EpubFragment>
}


const val EPUB_DATABASE_VERSION = 1

val epubMigrations = arrayOf<Migration>()

@Database(
    entities = [
        EpubHtmlToFrag::class,
        EpubFragment::class,
    ],
    version = EPUB_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class EpubDatabase: RoomDatabase() {
    abstract fun epubDao(): EpubDao
}
