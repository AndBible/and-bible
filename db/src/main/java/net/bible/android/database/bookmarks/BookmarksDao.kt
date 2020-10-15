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
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import java.util.*

@Dao
interface BookmarkDao {
    @Query("SELECT * from Bookmark")
    fun allBookmarks(): List<Bookmark>

    @Query("SELECT * from Bookmark where id = :bookmarkId")
    fun bookmarkById(bookmarkId: Long): Bookmark

    @Query("SELECT * from Bookmark where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: List<Long>): List<Bookmark>

    @Query("SELECT * from Bookmark where kjvOrdinalStart >= :start AND kjvOrdinalEnd <= :end")
    fun bookmarksForKjvOrdinalRange(start: Int, end: Int): List<Bookmark>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<Bookmark> {
        val v = converter.convert(verseRange, KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<Bookmark> {
        val lastChap = KJVA.getLastChapter(book)
        val lastVerse = KJVA.getLastVerse(book, lastChap)
        val startVerse = Verse(KJVA, book, 0, 0).ordinal
        val endVerse = Verse(KJVA, book, lastChap, lastVerse).ordinal
        return bookmarksForKjvOrdinalRange(startVerse, endVerse)
    }

    @Query("SELECT * from Bookmark where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<Bookmark>
    fun bookmarksForVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinal(converter.convert(verse, KJVA).ordinal)

    @Query("""SELECT * from Bookmark where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<Bookmark>
    fun bookmarksStartingAtVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinalStart(converter.convert(verse, KJVA).ordinal)

    // Not sure if we ever need this though, and it gives ONLY verses that vere stored in specified v11n
    //@Query("SELECT * from Bookmark where ordinalStart >= :start AND ordinalEnd <= :end AND v11n = :v11n")
    //fun bookmarksForOrdinalRange(start: Int, end: Int, v11n: String): List<Bookmark>

    @Query("SELECT count(*) > 0 from Bookmark where kjvOrdinalStart <= :verseOrdinal AND :verseOrdinal <= kjvOrdinalEnd LIMIT 1")
    fun hasBookmarksForVerse(verseOrdinal: Int): Boolean
    fun hasBookmarksForVerse(verse: Verse): Boolean = hasBookmarksForVerse(converter.convert(verse, KJVA).ordinal)

    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId AND Bookmark.kjvOrdinalStart = :startOrdinal
        """)
    fun bookmarksForVerseStartWithLabel(labelId: Long, startOrdinal: Int): List<Bookmark>
    fun bookmarksForVerseStartWithLabel(verse: Verse, label: Label): List<Bookmark> =
        bookmarksForVerseStartWithLabel(label.id, converter.convert(verse, KJVA).ordinal)

    @Insert
    fun insert(entity: Bookmark): Long

    @Update
    fun update(entity: Bookmark)

    fun updateBookmarkDate(entity: Bookmark): Bookmark {
        entity.createdAt = Date(System.currentTimeMillis())
        update(entity)
        return entity
    }

    @Delete fun delete(b: Bookmark)

    @Query("""
        SELECT * FROM Bookmark WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE Bookmark.id = BookmarkToLabel.bookmarkId)
        """)
    fun unlabelledBookmarks(): List<Bookmark>

    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId
        """)
    fun bookmarksWithLabel(labelId: Long): List<Bookmark>


    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Insert
    fun insert(entity: Label): Long

    @Update
    fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query("""
        SELECT Label.* from Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN Bookmark ON BookmarkToLabel.bookmarkId = Bookmark.id 
            WHERE Bookmark.id = :bookmarkId
    """)
    fun labelsForBookmark(bookmarkId: Long): List<Label>

    @Insert
    fun insert(entity: BookmarkToLabel): Long

    @Delete
    fun delete(entity: BookmarkToLabel): Int

    @Delete
    fun delete(entities: List<BookmarkToLabel>): Int

    @Insert
    fun insert(entities: List<BookmarkToLabel>): List<Long>

    @Query("SELECT * from Label WHERE bookmarkStyle = 'SPEAK' LIMIT 1")
    fun speakLabel(): Label?

    @Transaction
    fun getOrCreateSpeakLabel(): Label {
        return speakLabel()?: Label(name = "", bookmarkStyle = BookmarkStyle.SPEAK).apply {
            id = insert(this)
        }
    }

}
