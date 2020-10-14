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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.crosswire.jsword.versification.BibleBook

@Dao
interface BookmarkDao {
    @Query("SELECT * from Bookmark")
    fun allBookmarks(): List<BookmarkEntities.Bookmark>

    @Query("SELECT * from bookmark")
    fun allBookmarksSorted(): List<BookmarkEntities.Bookmark>

    @Query("SELECT * from Bookmark where id = :bookmarkId")
    fun bookmarkById(bookmarkId: Long): BookmarkEntities.Bookmark

    @Query("SELECT * from Bookmark where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: LongArray): List<BookmarkEntities.Bookmark>

    @Query("SELECT * from Bookmark where `key` = :osisRef")
    fun bookmarkByOsisRefExact(osisRef: String): BookmarkEntities.Bookmark?

    @Query("SELECT * from Bookmark where `key` LIKE :osisRef")
    fun bookmarksByOsisRefLike(osisRef: String): List<BookmarkEntities.Bookmark>

    fun bookmarksByOsisRefStarts(osisRef: String) = bookmarksByOsisRefLike("$osisRef-%")

    fun bookmarksInBook(book: BibleBook) = bookmarksByOsisRefLike("${book.osis}.%")

    fun bookmarkByOsisRef(osisRef: String): BookmarkEntities.Bookmark? =
        bookmarkByOsisRefExact(osisRef) ?: bookmarksByOsisRefStarts(osisRef).firstOrNull()

    @Insert
    fun insert(entity: BookmarkEntities.Bookmark): Long

    @Update
    fun update(entity: BookmarkEntities.Bookmark)

    fun updateBookmarkDate(entity: BookmarkEntities.Bookmark): BookmarkEntities.Bookmark {
        entity.createdOn = System.currentTimeMillis()
        update(entity)
        return entity
    }

    @Delete fun delete(b: BookmarkEntities.Bookmark)

    @Query("""
        SELECT * FROM Bookmark WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE Bookmark.id = BookmarkToLabel.bookmarkId)
        """)
    fun unlabelledBookmarks(): List<BookmarkEntities.Bookmark>

    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId
        """)
    fun bookmarksWithLabel(labelId: Long): List<BookmarkEntities.Bookmark>

    @Query("SELECT * from Label")
    fun allLabels(): List<BookmarkEntities.Label>

    @Update
    fun update(entity: BookmarkEntities.Label)

    @Delete fun delete(b: BookmarkEntities.Label)

    @Query("""
        SELECT Label.* from Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN Bookmark ON BookmarkToLabel.bookmarkId = Bookmark.id 
            WHERE Bookmark.id = :bookmarkId
    """)
    fun labelsForBookmark(bookmarkId: Long): List<BookmarkEntities.Label>

    @Insert
    fun insert(entity: BookmarkEntities.Label): Long

    @Insert
    fun insert(entity: BookmarkEntities.BookmarkToLabel): Long

    @Delete
    fun delete(entity: BookmarkEntities.BookmarkToLabel): Int

    @Delete
    fun delete(entities: List<BookmarkEntities.BookmarkToLabel>): Int

    @Insert
    fun insert(entities: List<BookmarkEntities.BookmarkToLabel>): List<Long>

    @Query("SELECT * from Label WHERE bookmarkStyle = 'SPEAK'")
    fun speakLabel(): BookmarkEntities.Label?

    @Transaction
    fun getOrCreateSpeakLabel(): BookmarkEntities.Label {
        return speakLabel()?: BookmarkEntities.Label(name = "", bookmarkStyle = "SPEAK").apply {
            id = insert(this)
        }
    }

}
