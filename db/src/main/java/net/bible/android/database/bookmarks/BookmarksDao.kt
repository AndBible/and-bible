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
import androidx.room.Update
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import java.util.*

const val orderByString = """
CASE
    WHEN :orderBy = 'BIBLE_ORDER' THEN Bookmark.kjvOrdinalStart
    WHEN :orderBy = 'CREATED_AT' THEN Bookmark.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN Bookmark.lastUpdatedOn
END"""

@Dao
interface BookmarkDao {
    @Query("SELECT * from Bookmark ORDER BY $orderByString")
    fun allBookmarks(orderBy: String): List<Bookmark>
    fun allBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        allBookmarks(orderBy.name)

    @Query("SELECT * from Bookmark WHERE notes IS NOT NULL ORDER BY $orderByString")
    fun allBookmarksWithNotes(orderBy: String): List<Bookmark>
    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<Bookmark> =
        allBookmarksWithNotes(orderBy.name)

    @Query("SELECT * from Bookmark where id = :bookmarkId")
    fun bookmarkById(bookmarkId: Long): Bookmark

    @Query("SELECT * from Bookmark where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: List<Long>): List<Bookmark>

    @Query("""SELECT * from Bookmark where
        kjvOrdinalStart BETWEEN :rangeStart AND :rangeEnd OR
        kjvOrdinalEnd BETWEEN :rangeStart AND :rangeEnd OR 
        (:rangeStart BETWEEN kjvOrdinalStart AND kjvOrdinalEnd AND 
         :rangeEnd BETWEEN kjvOrdinalStart AND kjvOrdinalEnd
        )
        """)
    fun bookmarksForKjvOrdinalRange(rangeStart: Int, rangeEnd: Int): List<Bookmark>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<Bookmark> {
        val v = converter.convert(verseRange, KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<Bookmark> = bookmarksForVerseRange(KJVA.allVerses)

    @Query("SELECT * from Bookmark where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<Bookmark>
    fun bookmarksForVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinal(converter.convert(verse, KJVA).ordinal)

    @Query("""SELECT * from Bookmark where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<Bookmark>
    fun bookmarksStartingAtVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinalStart(converter.convert(verse, KJVA).ordinal)

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

    @Insert fun insert(entity: Bookmark): Long

    @Update
    fun update(entity: Bookmark)
    fun updateBookmarkDate(entity: Bookmark): Bookmark {
        entity.createdAt = Date(System.currentTimeMillis())
        update(entity)
        return entity
    }

    @Delete fun deleteBookmarks(bs: List<Bookmark>)
    @Delete fun delete(b: Bookmark)


    @Query("DELETE FROM Bookmark WHERE id IN (:bs)")
    fun deleteBookmarksById(bs: List<Long>)

    @Query("""
        SELECT * FROM Bookmark WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE Bookmark.id = BookmarkToLabel.bookmarkId)
            ORDER BY $orderByString
        """)
    fun unlabelledBookmarks(orderBy: String): List<Bookmark>
    fun unlabelledBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        unlabelledBookmarks(orderBy.name)


    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY $orderByString
        """)
    fun bookmarksWithLabel(labelId: Long, orderBy: String): List<Bookmark>
    fun bookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark>
        = bookmarksWithLabel(label.id, orderBy.name)

    @Query("UPDATE Bookmark SET notes=:notes WHERE id=:bookmarkId")
    fun saveBookmarkNote(bookmarkId: Long, notes: String?)

    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Insert fun insert(entity: Label): Long

    @Update fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query("""
        SELECT Label.* from Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN Bookmark ON BookmarkToLabel.bookmarkId = Bookmark.id 
            WHERE Bookmark.id = :bookmarkId
    """)
    fun labelsForBookmark(bookmarkId: Long): List<Label>

    @Insert fun insert(entity: BookmarkToLabel): Long

    @Delete fun delete(entity: BookmarkToLabel): Int

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun clearLabels(bookmarkId: Long)
    fun clearLabels(bookmark: Bookmark) = clearLabels(bookmark.id)

    @Delete fun delete(entities: List<BookmarkToLabel>): Int

    @Insert fun insert(entities: List<BookmarkToLabel>): List<Long>

    @Query("SELECT * from Label WHERE bookmarkStyle = 'SPEAK' LIMIT 1")
    fun speakLabel(): Label?
    fun getOrCreateSpeakLabel(): Label {
        return speakLabel()?: Label(name = "", bookmarkStyle = BookmarkStyle.SPEAK).apply {
            id = insert(this)
        }
    }

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun deleteLabels(bookmarkId: Long)
    fun deleteLabels(bookmark: BookmarkEntities.Bookmark) = deleteLabels(bookmark.id)
}
