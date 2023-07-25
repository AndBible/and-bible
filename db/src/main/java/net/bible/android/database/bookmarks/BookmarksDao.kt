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
import net.bible.android.database.IdType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import java.util.*

const val orderBy = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BookmarkWithNotes.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BookmarkWithNotes.startOffset END,
CASE WHEN :orderBy = 'CREATED_AT_DESC' THEN -BookmarkWithNotes.createdAt END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN BookmarkWithNotes.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN BookmarkWithNotes.lastUpdatedOn
END"""

const val orderBy2 = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BookmarkWithNotes.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BookmarkWithNotes.startOffset END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN BookmarkWithNotes.createdAt
    WHEN :orderBy = 'CREATED_AT_DESC' THEN -BookmarkWithNotes.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN BookmarkWithNotes.lastUpdatedOn
    WHEN :orderBy = 'ORDER_NUMBER' THEN BookmarkToLabel.orderNumber
END"""

@Dao
interface BookmarkDao {
    @Query("SELECT * from BookmarkWithNotes ORDER BY $orderBy")
    fun allBookmarks(orderBy: String): List<BookmarkWithNotes>
    fun allBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BookmarkWithNotes> =
        allBookmarks(orderBy.name)

    @Query("SELECT * from BookmarkWithNotes WHERE notes IS NOT NULL ORDER BY $orderBy")
    fun allBookmarksWithNotes(orderBy: String): List<BookmarkWithNotes>
    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<BookmarkWithNotes> =
        allBookmarksWithNotes(orderBy.name)

    @Query("SELECT * from BookmarkWithNotes where id = :bookmarkId")
    fun bookmarkById(bookmarkId: IdType): BookmarkWithNotes?

    @Query("SELECT * from GenericBookmarkWithNotes where id = :bookmarkId")
    fun genericBookmarkById(bookmarkId: IdType): GenericBookmarkWithNotes?

    @Query("SELECT * from BookmarkWithNotes where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: List<IdType>): List<BookmarkWithNotes>

    @Query("SELECT * from GenericBookmarkWithNotes where id IN (:bookmarkIds)")
    fun genericBookmarksByIds(bookmarkIds: List<IdType>): List<GenericBookmarkWithNotes>

    //https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap
    @Query(
        """SELECT * from BookmarkWithNotes where
        kjvOrdinalStart <= :rangeEnd AND :rangeStart <= kjvOrdinalEnd
        ORDER BY kjvOrdinalStart, startOffset
        """
    )
    fun bookmarksForKjvOrdinalRange(rangeStart: Int, rangeEnd: Int): List<BookmarkWithNotes>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<BookmarkWithNotes> {
        val v = verseRange.toV11n(KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<BookmarkWithNotes> = bookmarksForVerseRange(KJVA.allVerses)

    @Query("SELECT * from BookmarkWithNotes where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<BookmarkWithNotes>
    fun bookmarksForVerse(verse: Verse): List<BookmarkWithNotes> =
        bookmarksForKjvOrdinal(verse.toV11n(KJVA).ordinal)

    @Query("""SELECT * from BookmarkWithNotes where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<BookmarkWithNotes>
    fun bookmarksStartingAtVerse(verse: Verse): List<BookmarkWithNotes> =
        bookmarksForKjvOrdinalStart(verse.toV11n(KJVA).ordinal)

    @Query("SELECT count(*) > 0 from BookmarkWithNotes where kjvOrdinalStart <= :verseOrdinal AND :verseOrdinal <= kjvOrdinalEnd LIMIT 1")
    fun hasBookmarksForVerse(verseOrdinal: Int): Boolean
    fun hasBookmarksForVerse(verse: Verse): Boolean = hasBookmarksForVerse(verse.toV11n(KJVA).ordinal)

    @Query(
        """
        SELECT BookmarkWithNotes.* FROM BookmarkWithNotes 
            JOIN BookmarkToLabel ON BookmarkWithNotes.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId AND BookmarkWithNotes.kjvOrdinalStart = :startOrdinal
        """
    )
    fun bookmarksForVerseStartWithLabel(labelId: IdType, startOrdinal: Int): List<BookmarkWithNotes>
    fun bookmarksForVerseStartWithLabel(verse: Verse, label: Label): List<BookmarkWithNotes> =
        bookmarksForVerseStartWithLabel(label.id, verse.toV11n(KJVA).ordinal)

    @Insert fun insert(entity: Bookmark)
    @Insert fun insert(entity: GenericBookmark)

    fun insert(entity: BaseBookmark) = when(entity) {
        is Bookmark -> insert(entity)
        is GenericBookmark -> insert(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Insert fun insert(entity: BookmarkNotes)
    @Insert fun insert(entity: GenericBookmarkNotes)

    fun insert(entity: BaseBookmarkNotes)  = when(entity) {
        is BookmarkNotes -> insert(entity)
        is GenericBookmarkNotes -> insert(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Update fun update(entity: Bookmark)
    @Update fun update(entity: GenericBookmark)

    fun update(entity: BaseBookmark) = when(entity) {
        is Bookmark -> update(entity)
        is GenericBookmark -> update(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Update fun update(entity: BookmarkNotes)
    @Update fun update(entity: GenericBookmarkNotes)

    fun update(entity: BaseBookmarkNotes) = when(entity) {
        is BookmarkNotes -> update(entity)
        is GenericBookmarkNotes -> update(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Query("DELETE FROM BookmarkNotes WHERE bookmarkId=:id")
    fun deleteBookmarkNotes(id: IdType)
    @Query("DELETE FROM GenericBookmarkNotes WHERE bookmarkId=:id")
    fun deleteGenericBookmarkNotes(id: IdType)
    fun deleteBookmarkNotes(bookmark: BookmarkWithNotes) = deleteBookmarkNotes(bookmark.id)
    fun deleteBookmarkNotes(bookmark: GenericBookmarkWithNotes) = deleteGenericBookmarkNotes(bookmark.id)
    fun deleteBookmarkNotes(bookmark: BaseBookmarkWithNotes) = when(bookmark) {
        is BookmarkWithNotes -> deleteBookmarkNotes(bookmark.id)
        is GenericBookmarkWithNotes -> deleteGenericBookmarkNotes(bookmark.id)
        else -> throw RuntimeException("Wrong type")
    }

    @Query("UPDATE Bookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:id")
    fun updateBookmarkDate(id: IdType, lastUpdatedOn: Date = Date(System.currentTimeMillis()))

    fun deleteBookmarks(bs: List<BookmarkWithNotes>) = deleteBookmarksById(bs.map {it.id})

    @Query("DELETE FROM Bookmark WHERE id=:id")
    fun deleteBookmarkById(id: IdType)

    fun delete(b: BookmarkWithNotes) = deleteBookmarkById(b.id)

    @Query("DELETE FROM Bookmark WHERE id IN (:bs)")
    fun deleteBookmarksById(bs: List<IdType>)

    @Query(
        """
        SELECT * FROM BookmarkWithNotes WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE BookmarkWithNotes.id = BookmarkToLabel.bookmarkId)
            ORDER BY $orderBy
        """
    )
    fun unlabelledBookmarks(orderBy: String): List<BookmarkWithNotes>
    fun unlabelledBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BookmarkWithNotes> =
        unlabelledBookmarks(orderBy.name)


    @Query(
        """
        SELECT BookmarkWithNotes.* FROM BookmarkWithNotes 
            JOIN BookmarkToLabel ON BookmarkWithNotes.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY $orderBy2
        """
    )
    fun bookmarksWithLabel(labelId: IdType, orderBy: String): List<BookmarkWithNotes>
    fun bookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BookmarkWithNotes>
        = bookmarksWithLabel(label.id, orderBy.name)
    fun bookmarksWithLabel(labelId: IdType, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BookmarkWithNotes>
        = bookmarksWithLabel(labelId, orderBy.name)

    @Query("""INSERT INTO BookmarkNotes VALUES (:bookmarkId, :notes) ON CONFLICT DO UPDATE SET notes=:notes WHERE bookmarkId=:bookmarkId""")
    fun _saveBookmarkNote(bookmarkId: IdType, notes: String?)

    @Query("""INSERT INTO GenericBookmarkNotes VALUES (:bookmarkId, :notes) ON CONFLICT DO UPDATE SET notes=:notes WHERE bookmarkId=:bookmarkId""")
    fun _saveGenericBookmarkNote(bookmarkId: IdType, notes: String?)
     @Query("""UPDATE Bookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:bookmarkId""")
    fun saveBookmarkLastUpdatedOn(bookmarkId: IdType, lastUpdatedOn: Long)

    @Query("""UPDATE GenericBookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:bookmarkId""")
    fun saveGenericBookmarkLastUpdatedOn(bookmarkId: IdType, lastUpdatedOn: Long)

    fun saveBookmarkNote(bookmarkId: IdType, notes: String?) {
        _saveBookmarkNote(bookmarkId, notes)
        saveBookmarkLastUpdatedOn(bookmarkId, System.currentTimeMillis())
    }

    fun saveGenericBookmarkNote(bookmarkId: IdType, notes: String?) {
        _saveGenericBookmarkNote(bookmarkId, notes)
        saveGenericBookmarkLastUpdatedOn(bookmarkId, System.currentTimeMillis())
    }

    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Query("SELECT * from Label WHERE id=:id")
    fun labelById(id: IdType): Label?

    @Query("SELECT * from Label WHERE id IN (:ids)")
    fun labelsById(ids: List<IdType>): List<Label>

    @Query("SELECT * from StudyPadTextEntryWithText WHERE labelId=:id ORDER BY orderNumber")
    fun studyPadTextEntriesByLabelId(id: IdType): List<BookmarkEntities.StudyPadTextEntryWithText>

    @Query("SELECT * from StudyPadTextEntryWithText WHERE id=:id")
    fun studyPadTextEntryById(id: IdType): BookmarkEntities.StudyPadTextEntryWithText?

    @Insert fun insert(entity: BookmarkEntities.StudyPadTextEntry)
    @Insert fun insert(entity: BookmarkEntities.StudyPadTextEntryText)

    @Update fun update(entity: BookmarkEntities.StudyPadTextEntry)
    @Update fun update(entity: BookmarkEntities.StudyPadTextEntryText)

    @Insert fun insert(entity: Label)
    @Insert fun insertLabels(entity: List<Label>): List<Long>

    @Update fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query(
        """
        SELECT Label.* FROM Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN BookmarkWithNotes ON BookmarkToLabel.bookmarkId = BookmarkWithNotes.id 
            WHERE BookmarkWithNotes.id = :bookmarkId
    """
    )
    fun labelsForBookmark(bookmarkId: IdType): List<Label>

    fun labelsForBookmark(bookmark: BaseBookmarkWithNotes): List<Label> = when(bookmark) {
        is BookmarkWithNotes -> labelsForBookmark(bookmark.id)
        is GenericBookmarkWithNotes -> labelsForGenericBookmark(bookmark.id)
        else -> throw RuntimeException("Illegal type")
    }

    @Query(
        """
        SELECT Label.* FROM Label 
            JOIN GenericBookmarkToLabel ON Label.id = GenericBookmarkToLabel.labelId 
            JOIN GenericBookmarkWithNotes ON GenericBookmarkToLabel.bookmarkId = GenericBookmarkWithNotes.id 
            WHERE GenericBookmarkWithNotes.id = :bookmarkId
    """
    )
    fun labelsForGenericBookmark(bookmarkId: IdType): List<Label>

    @Query("""SELECT * FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId""")
    fun getBookmarkToLabelsForBookmark(bookmarkId: IdType): List<BookmarkToLabel>

    @Query("""SELECT * FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId""")
    fun getGenericBookmarkToLabelsForBookmark(bookmarkId: IdType): List<GenericBookmarkToLabel>

    fun getBookmarkToLabelsForBookmark(bookmark: BaseBookmarkWithNotes): List<BaseBookmarkToLabel> =
        when(bookmark) {
            is BookmarkWithNotes -> getBookmarkToLabelsForBookmark(bookmark.id)
            is GenericBookmarkWithNotes -> getGenericBookmarkToLabelsForBookmark(bookmark.id)
            else -> throw RuntimeException("Illegal type")
        }

    @Query("""SELECT * FROM BookmarkToLabel WHERE labelId=:labelId ORDER BY orderNumber""")
    fun getBookmarkToLabelsForLabel(labelId: IdType): List<BookmarkToLabel>

    @Query("""SELECT * FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId=:labelId""")
    fun getBookmarkToLabel(bookmarkId: IdType, labelId: IdType): BookmarkToLabel?

    @Insert fun insert(entity: BookmarkToLabel)

    @Delete fun delete(entity: BookmarkToLabel): Int

    @Update fun update(entity: BookmarkToLabel)

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun clearLabels(bookmarkId: IdType)
    fun clearLabels(bookmark: BookmarkWithNotes) = clearLabels(bookmark.id)

    @Delete fun delete(entities: List<BookmarkToLabel>): Int

    @Delete fun delete(e: BookmarkEntities.StudyPadTextEntry)

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId IN (:labels)")
    fun _deleteLabelsFromBookmark(bookmarkId: IdType, labels: List<IdType>): Int
    @Query("DELETE FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId IN (:labels)")
    fun _deleteLabelsFromGenericBookmark(bookmarkId: IdType, labels: List<IdType>): Int
    fun deleteLabelsFromBookmark(bookmarkId: IdType, labels: List<IdType>): Int {
        if (labels.isEmpty()) return 0
        return _deleteLabelsFromBookmark(bookmarkId, labels)
    }

    fun deleteLabelsFromBookmark(bookmark: BaseBookmarkWithNotes, labels: List<IdType>): Int {
        if (labels.isEmpty()) return 0
        return when(bookmark) {
            is BookmarkWithNotes -> _deleteLabelsFromBookmark(bookmark.id, labels)
            is GenericBookmarkWithNotes -> _deleteLabelsFromGenericBookmark(bookmark.id, labels)
            else -> throw RuntimeException("Illegal type")
        }
    }

    fun deleteLabelsFromBookmark(bookmark: BookmarkWithNotes, labels: List<Label>): Int = deleteLabelsFromBookmark(bookmark.id, labels.map { it.id })

    @Insert fun insertBookmarkToLabels(entities: List<BookmarkToLabel>)
    @Insert fun insertGenericBookmarkToLabels(entities: List<GenericBookmarkToLabel>)

    @Query("SELECT * from Label WHERE name = '${SPEAK_LABEL_NAME}' LIMIT 1")
    fun speakLabelByName(): Label?

    @Query("SELECT * from Label WHERE name = '${UNLABELED_NAME}' LIMIT 1")
    fun unlabeledLabelByName(): Label?

    @Query("DELETE FROM BookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun deleteLabels(bookmarkId: IdType)
    fun deleteLabels(bookmark: BookmarkWithNotes) = deleteLabels(bookmark.id)

    @Query("SELECT COUNT(*) FROM BookmarkToLabel WHERE labelId=:labelId")
    fun countBookmarkEntities(labelId: IdType): Int

    @Query("SELECT COUNT(*) FROM GenericBookmarkToLabel WHERE labelId=:labelId")
    fun countGenericBookmarkEntities(labelId: IdType): Int

    @Query("SELECT COUNT(*) FROM StudyPadTextEntryWithText WHERE labelId=:labelId")
    fun countStudyPadTextEntities(labelId: IdType): Int

    fun countStudyPadEntities(labelId: IdType) = countBookmarkEntities(labelId) + countGenericBookmarkEntities(labelId)+ countStudyPadTextEntities(labelId)

    @Query("DELETE FROM Label WHERE id IN (:toList)")
    fun deleteLabelsByIds(toList: List<IdType>)

    @Update fun updateBookmarkToLabels(bookmarkToLabels: List<BookmarkToLabel>)

    @Update fun updateStudyPadTextEntries(studyPadTextEntries: List<BookmarkEntities.StudyPadTextEntry>)

    @Query("SELECT * FROM GenericBookmarkWithNotes WHERE book=:document AND `key`=:key")
    fun genericBookmarksFor(document: String, key: String): List<GenericBookmarkWithNotes>
    fun genericBookmarksFor(document: Book, key: Key): List<GenericBookmarkWithNotes> =
        genericBookmarksFor(document.initials, key.osisRef)
}
