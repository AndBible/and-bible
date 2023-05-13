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

package net.bible.android.database.bookmarks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import net.bible.android.common.toV11n
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import java.util.*

const val orderBy = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN Bookmark.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN Bookmark.startOffset END,
CASE WHEN :orderBy = 'CREATED_AT_DESC' THEN -Bookmark.createdAt END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN Bookmark.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN Bookmark.lastUpdatedOn
END"""

const val orderBy2 = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN Bookmark.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN Bookmark.startOffset END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN Bookmark.createdAt
    WHEN :orderBy = 'CREATED_AT_DESC' THEN -Bookmark.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN Bookmark.lastUpdatedOn
    WHEN :orderBy = 'ORDER_NUMBER' THEN BookmarkToLabel.orderNumber
END"""

@Dao
interface BookmarkDao {
    @Query("SELECT * from Bookmark ORDER BY $orderBy")
    fun allBookmarks(orderBy: String): List<Bookmark>
    fun allBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        allBookmarks(orderBy.name)

    @Query("SELECT * from Bookmark WHERE notes IS NOT NULL ORDER BY $orderBy")
    fun allBookmarksWithNotes(orderBy: String): List<Bookmark>
    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<Bookmark> =
        allBookmarksWithNotes(orderBy.name)

    @Query("SELECT * from Bookmark where id = :bookmarkId")
    fun bookmarkById(bookmarkId: String): Bookmark?

    @Query("SELECT * from Bookmark where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: List<String>): List<Bookmark>

    //https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap
    @Query("""SELECT * from Bookmark where
        kjvOrdinalStart <= :rangeEnd AND :rangeStart <= kjvOrdinalEnd
        ORDER BY kjvOrdinalStart, startOffset
        """)
    fun bookmarksForKjvOrdinalRange(rangeStart: Int, rangeEnd: Int): List<Bookmark>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<Bookmark> {
        val v = verseRange.toV11n(KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<Bookmark> = bookmarksForVerseRange(KJVA.allVerses)

    @Query("SELECT * from Bookmark where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<Bookmark>
    fun bookmarksForVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinal(verse.toV11n(KJVA).ordinal)

    @Query("""SELECT * from Bookmark where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<Bookmark>
    fun bookmarksStartingAtVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinalStart(verse.toV11n(KJVA).ordinal)

    @Query("SELECT count(*) > 0 from Bookmark where kjvOrdinalStart <= :verseOrdinal AND :verseOrdinal <= kjvOrdinalEnd LIMIT 1")
    fun hasBookmarksForVerse(verseOrdinal: Int): Boolean
    fun hasBookmarksForVerse(verse: Verse): Boolean = hasBookmarksForVerse(verse.toV11n(KJVA).ordinal)

    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId AND Bookmark.kjvOrdinalStart = :startOrdinal
        """)
    fun bookmarksForVerseStartWithLabel(labelId: String, startOrdinal: Int): List<Bookmark>
    fun bookmarksForVerseStartWithLabel(verse: Verse, label: Label): List<Bookmark> =
        bookmarksForVerseStartWithLabel(label.id, verse.toV11n(KJVA).ordinal)

    @Insert fun insert(entity: Bookmark)

    @Update
    fun update(entity: Bookmark)

    fun updateBookmarkDate(entity: Bookmark): Bookmark {
        entity.lastUpdatedOn = Date(System.currentTimeMillis())
        update(entity)
        return entity
    }

    @Delete fun deleteBookmarks(bs: List<Bookmark>)
    @Delete fun delete(b: Bookmark)


    @Query("DELETE FROM Bookmark WHERE id IN (:bs)")
    fun deleteBookmarksById(bs: List<String>)

    @Query("""
        SELECT * FROM Bookmark WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE Bookmark.id = BookmarkToLabel.bookmarkId)
            ORDER BY $orderBy
        """)
    fun unlabelledBookmarks(orderBy: String): List<Bookmark>
    fun unlabelledBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        unlabelledBookmarks(orderBy.name)


    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY $orderBy2
        """)
    fun bookmarksWithLabel(labelId: String, orderBy: String): List<Bookmark>
    fun bookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark>
        = bookmarksWithLabel(label.id, orderBy.name)
    fun bookmarksWithLabel(labelId: String, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark>
        = bookmarksWithLabel(labelId, orderBy.name)

    @Query("""UPDATE Bookmark SET notes=:notes, lastUpdatedOn=:lastUpdatedOn WHERE id=:bookmarkId""")
    fun saveBookmarkNote(bookmarkId: String, notes: String?, lastUpdatedOn: Long)
    fun saveBookmarkNote(bookmarkId: String, notes: String?) = saveBookmarkNote(bookmarkId, notes, System.currentTimeMillis())

    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Query("SELECT * from Label WHERE id=:id")
    fun labelById(id: String): Label?

    @Query("SELECT * from StudyPadTextEntry WHERE labelId=:id ORDER BY orderNumber")
    fun journalTextEntriesByLabelId(id: String): List<BookmarkEntities.StudyPadTextEntry>

    @Query("SELECT * from StudyPadTextEntry WHERE id=:id")
    fun studyPadTextEntryById(id: String): BookmarkEntities.StudyPadTextEntry?

    @Insert fun insert(entity: BookmarkEntities.StudyPadTextEntry)

    @Update fun update(entity: BookmarkEntities.StudyPadTextEntry)

    @Insert fun insert(entity: Label)
    @Insert fun insertLabels(entity: List<Label>): List<Long>

    @Update fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query("""
        SELECT Label.* FROM Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN Bookmark ON BookmarkToLabel.bookmarkId = Bookmark.id 
            WHERE Bookmark.id = :bookmarkId
    """)
    fun labelsForBookmark(bookmarkId: String): List<Label>

    @Query("""SELECT * FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId""")
    fun getBookmarkToLabelsForBookmark(bookmarkId: String): List<BookmarkToLabel>

    @Query("""SELECT * FROM BookmarkToLabel WHERE labelId=:labelId ORDER BY orderNumber""")
    fun getBookmarkToLabelsForLabel(labelId: String): List<BookmarkToLabel>

    @Query("""SELECT * FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId=:labelId""")
    fun getBookmarkToLabel(bookmarkId: String, labelId: String): BookmarkToLabel?

    @Insert fun insert(entity: BookmarkToLabel)

    @Delete fun delete(entity: BookmarkToLabel): Int

    @Update fun update(entity: BookmarkToLabel)

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun clearLabels(bookmarkId: String)
    fun clearLabels(bookmark: Bookmark) = clearLabels(bookmark.id)

    @Delete fun delete(entities: List<BookmarkToLabel>): Int

    @Delete fun delete(e: BookmarkEntities.StudyPadTextEntry)

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId IN (:labels)")
    fun deleteLabelsFromBookmark(bookmarkId: String, labels: List<String>): Int
    fun deleteLabelsFromBookmark(bookmark: Bookmark, labels: List<Label>): Int = deleteLabelsFromBookmark(bookmark.id, labels.map { it.id })

    @Insert fun insert(entities: List<BookmarkToLabel>)

    @Query("SELECT * from Label WHERE name = '${SPEAK_LABEL_NAME}' LIMIT 1")
    fun speakLabelByName(): Label?

    @Query("SELECT * from Label WHERE name = '${UNLABELED_NAME}' LIMIT 1")
    fun unlabeledLabelByName(): Label?

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun deleteLabels(bookmarkId: String)
    fun deleteLabels(bookmark: Bookmark) = deleteLabels(bookmark.id)

    @Query("SELECT COUNT(*) FROM BookmarkToLabel WHERE labelId=:labelId")
    fun countBookmarkEntities(labelId: String): Int

    @Query("SELECT COUNT(*) FROM StudyPadTextEntry WHERE labelId=:labelId")
    fun countStudyPadTextEntities(labelId: String): Int

    fun countStudyPadEntities(labelId: String) = countBookmarkEntities(labelId) + countStudyPadTextEntities(labelId)

    @Query("DELETE FROM Label WHERE id IN (:toList)")
    fun deleteLabelsByIds(toList: List<String>)

    @Update fun updateBookmarkToLabels(bookmarkToLabels: List<BookmarkToLabel>)
    @Update fun updateStudyPadTextEntries(studyPadTextEntries: List<BookmarkEntities.StudyPadTextEntry>)
}
